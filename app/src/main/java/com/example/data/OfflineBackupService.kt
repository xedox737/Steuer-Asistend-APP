package com.example.data

import android.content.Context
import android.database.Cursor
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Expected validation failures with messages intended for the user. */
internal class OfflineBackupValidationException(message: String) : IllegalArgumentException(message)

/** Versioned offline transport. Conflicting existing rows are never overwritten. */
internal class OfflineBackupService(private val context: Context, private val database: AppDatabase) {
    private inline fun requireBackup(condition: Boolean, message: () -> String = { "Die Sicherung ist unvollständig oder beschädigt." }) {
        if (!condition) throw OfflineBackupValidationException(message())
    }
    private inline fun <T : Any> requireBackupValue(value: T?, message: () -> String): T =
        value ?: throw OfflineBackupValidationException(message())

    internal class Prepared internal constructor(internal val root: JSONObject, internal val staging: File) : AutoCloseable {
        val receiptCount: Int get() = root.getJSONObject("tables").getJSONObject("receipts").getJSONArray("rows").length()
        val propertyCount: Int get() = root.getJSONObject("tables").getJSONObject("property_metadata").getJSONArray("rows").length()
        val fileCount: Int get() = root.getJSONArray("files").length()
        override fun close() { staging.deleteRecursively() }
    }

    private data class Column(val name: String, val type: String, val primary: Int, val notNull: Boolean)
    private fun schema(db: SupportSQLiteDatabase): Map<String, List<Column>> {
        val names = db.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }.filterNot { it.startsWith("sqlite_") || it.startsWith("document_search_fts") || it in setOf("room_master_table", "android_metadata") }
        return names.associateWith { name ->
            requireBackup(Regex("[a-z_]+").matches(name)) { "Unbekannte Datenbankstruktur." }
            db.query("PRAGMA table_info(`$name`)").use { cursor -> buildList {
                while (cursor.moveToNext()) add(Column(cursor.getString(1), cursor.getString(2), cursor.getInt(5), cursor.getInt(3) != 0))
            } }
        }
    }
    private fun value(cursor: Cursor, index: Int): Any = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
        Cursor.FIELD_TYPE_BLOB -> JSONObject().put("blob", android.util.Base64.encodeToString(cursor.getBlob(index), android.util.Base64.NO_WRAP))
        else -> cursor.getString(index)
    }
    private fun equivalent(left: Any, right: Any): Boolean =
        if (left === JSONObject.NULL || right === JSONObject.NULL) left === right
        else if (left is Number && right is Number) left.toString().toBigDecimal().compareTo(right.toString().toBigDecimal()) == 0
        else left.toString() == right.toString()
    private fun bound(value: Any): Any? = when {
        value === JSONObject.NULL -> null
        value is JSONObject -> android.util.Base64.decode(value.getString("blob"), android.util.Base64.NO_WRAP)
        else -> value
    }
    private val pathColumns = setOf("imageUrl", "pdfPath", "bildPfad", "localUri")
    private val originalResolver = OfflineBackupResolver(context)

    private fun originalOwner(table: String, columns: JSONArray, row: JSONArray, path: String): String {
        fun field(name: String): String = (0 until columns.length())
            .firstOrNull { columns.getString(it) == name }?.let { text(row.opt(it)) }.orEmpty()
        val kind = when (table) {
            "managed_documents" -> "Dokument"
            "property_metadata" -> "Immobilie"
            else -> "Beleg"
        }
        val title = listOf("title", "aussteller", "name", "beschreibung").firstNotNullOfOrNull {
            field(it).takeIf(String::isNotBlank)
        }.orEmpty()
        val id = listOf("documentId", "internalId", "propertyId", "id").firstNotNullOfOrNull {
            field(it).takeIf(String::isNotBlank)
        }.orEmpty()
        val filename = field("originalFilename").ifBlank { field("storedFilename") }.ifBlank {
            val uri = android.net.Uri.parse(path)
            if (uri.scheme != null) uri.lastPathSegment.orEmpty().ifBlank { "Originaldatei" } else path
        }
            .substringAfterLast('/').substringAfterLast('\\')
        return "$kind „$title“ (ID: $id, Objekt: ${field("propertyId")}, Datei: $filename)"
    }

    /** The document picker must only receive an already prepared, validated archive. */
    suspend fun prepareExport(): File {
        val directory = File(context.filesDir, "offline-backup-staging")
        check(directory.isDirectory || directory.mkdirs())
        val archive = File.createTempFile("offline-backup-", ".zip", directory)
        try {
            archive.outputStream().use { create(it) }
            archive.inputStream().use { inspect(it).close() }
            return archive
        } catch (error: Throwable) {
            archive.delete()
            throw error
        }
    }

    /** URI grants and query credentials are not portable archive references. */
    private fun replacePrivateUriReferences(root: JSONObject, files: JSONArray) {
        val aliases = (0 until files.length()).map { files.getJSONObject(it).getString("path") }
            .filter { path -> android.net.Uri.parse(path).let { it.scheme != null && (it.query != null || it.fragment != null) } }
            .associateWith { "offline-original-${StableDocumentIdentity.sha256(it.toByteArray(Charsets.UTF_8))}" }
        if (aliases.isEmpty()) return
        for (index in 0 until files.length()) {
            val item = files.getJSONObject(index)
            aliases[item.getString("path")]?.let { item.put("path", it) }
        }
        val tables = root.getJSONObject("tables")
        tables.keys().forEach { name ->
            val table = tables.getJSONObject(name)
            val columns = table.getJSONArray("columns")
            val rows = table.getJSONArray("rows")
            for (c in 0 until columns.length()) if (columns.getString(c) in pathColumns) {
                for (r in 0 until rows.length()) {
                    val row = rows.getJSONArray(r)
                    val raw = row.optString(c, "")
                    val direct = aliases[raw]
                    if (direct != null) row.put(c, direct)
                    else if (raw.isNotBlank() && columns.getString(c) != "localUri" &&
                        !(name == "property_metadata" && columns.getString(c) == "bildPfad")) {
                        val paths = ReceiptOriginalChain.paths(raw)
                        if (paths.any { it in aliases }) row.put(c, JSONArray(paths.map { aliases[it] ?: it }).toString())
                    }
                }
            }
        }
    }

    suspend fun create(output: OutputStream) {
        val directory = File(context.cacheDir, "offline-export-${UUID.randomUUID()}").apply { check(mkdirs()) }
        try {
            val root = database.withTransaction {
                val db = database.openHelper.writableDatabase
                val tables = JSONObject()
                schema(db).forEach { (table, columns) ->
                    val rows = JSONArray()
                    db.query("SELECT * FROM `$table`").use { cursor ->
                        while (cursor.moveToNext()) rows.put(JSONArray().apply { columns.forEach { put(value(cursor, cursor.getColumnIndexOrThrow(it.name))) } })
                    }
                    tables.put(table, JSONObject().put("columns", JSONArray(columns.map { it.name })).put("rows", rows))
                }
                JSONObject().put("format", "ImmoPilotOfflineBackup").put("version", 1).put("databaseVersion", db.version)
                    .put("tables", tables).put("preferences", JSONObject().also { PersistentPreferenceInventory.writePayload(context, it) })
            }
            val paths = linkedMapOf<String, MutableSet<String>>()
            val tables = root.getJSONObject("tables")
            val documents = tables.getJSONObject("managed_documents")
            val documentColumns = documents.getJSONArray("columns")
            fun column(name: String) = (0 until documentColumns.length()).single { documentColumns.getString(it) == name }
            val documentRows = documents.getJSONArray("rows")
            fun metadataOnly(row: JSONArray) = row.optString(column("receiptInternalId"), "").isNotBlank() &&
                row.optString(column("sha256"), "").isBlank() && row.optLong(column("fileSizeBytes"), 0) == 0L &&
                row.optString(column("driveFileId"), "").isBlank()
            for (index in 0 until documentRows.length()) {
                val row = documentRows.getJSONArray(index)
                if (row.optString(column("localUri"), "").isBlank() && !metadataOnly(row)) {
                    val id = row.optString(column("documentId"), "")
                    requireBackup(id.isNotBlank()) { "Ein Dokumentoriginal besitzt keine stabile Dokument-ID." }
                    // Archive-only key: a verified local candidate can supply the bytes without repairing the live row.
                    row.put(column("localUri"), "offline-original:${StableDocumentIdentity.sha256(id.toByteArray(Charsets.UTF_8))}")
                }
            }
            val documentValues = tableRows(root, "managed_documents")
            val documentsByPath = documentValues.groupBy { text(it["localUri"]) }
            val documentsByHash = documentValues.groupBy { text(it["sha256"]) }
            val originalDirectory = File(context.filesDir, "managed_documents")
            tables.keys().forEach { name ->
                val table = tables.getJSONObject(name); val columns = table.getJSONArray("columns"); val rows = table.getJSONArray("rows")
                for (c in 0 until columns.length()) if (columns.getString(c) in pathColumns) {
                    for (r in 0 until rows.length()) {
                        val raw = rows.getJSONArray(r).optString(c, "")
                        if (raw.isNotBlank()) {
                            val originals = if (columns.getString(c) == "localUri" || (name == "property_metadata" && columns.getString(c) == "bildPfad")) listOf(raw) else ReceiptOriginalChain.paths(raw)
                            originals.forEach { path ->
                                paths.getOrPut(path) { linkedSetOf() }.add(originalOwner(name, columns, rows.getJSONArray(r), path))
                            }
                        }
                    }
                }
            }
            val files = JSONArray()
            paths.entries.forEachIndexed { index, (path, owners) ->
                val copied = File(directory, index.toString())
                val claims = documentsByPath[path].orEmpty()
                val hashes = claims.map { text(it["sha256"]) }.filter(String::isNotBlank).distinct()
                val sizes = claims.mapNotNull { (it["fileSizeBytes"] as? Number)?.toLong()?.takeIf { size -> size > 0 } }.distinct()
                val owner = owners.joinToString("; ")
                requireBackup(hashes.size <= 1 && sizes.size <= 1) { "$owner: Widersprüchliche Originalmetadaten. Bitte die Datei prüfen." }
                val expected = hashes.singleOrNull()
                val candidates = if (expected == null) emptyList() else {
                    val storedCopies = claims.mapNotNull { claim ->
                        val filename = text(claim["storedFilename"])
                        if (filename.isBlank() || filename in setOf(".", "..") || '/' in filename || '\\' in filename) null
                        else File(originalDirectory, filename).takeIf { candidate ->
                            runCatching { candidate.canonicalFile.parentFile == originalDirectory.canonicalFile }.getOrDefault(false)
                        }?.absolutePath
                    }
                    storedCopies + documentsByHash[expected].orEmpty().map { text(it["localUri"]) }.filter(String::isNotBlank)
                }
                val sha = originalResolver.copy(path, copied, owner, expected, sizes.singleOrNull(), candidates)
                files.put(JSONObject().put("path", path).put("entry", "files/$index").put("sha256", sha).put("size", copied.length()))
            }
            val originalsByPath = (0 until files.length()).associate {
                val file = files.getJSONObject(it); file.getString("path") to file.getString("sha256")
            }
            for (index in 0 until documentRows.length()) {
                val row = documentRows.getJSONArray(index)
                val path = row.optString(column("localUri"), "")
                requireBackup(path.isNotBlank() || metadataOnly(row)) {
                    "Ein Dokumentoriginal fehlt lokal. Bitte zuerst die Originale herunterladen."
                }
                val expected = row.optString(column("sha256"), "")
                requireBackup(path.isBlank() || expected.isBlank() || originalsByPath[path] == expected) {
                    "Ein Original stimmt nicht mit seiner gespeicherten Prüfsumme überein. Bitte zuerst die Datei prüfen."
                }
            }
            val receiptValues = tableRows(root, "receipts")
            receiptValues.forEach { receipt ->
                val claimsOriginal = text(receipt["driveFileId"]).isNotBlank() || text(receipt["originalMimeType"]).isNotBlank() || (receipt["fileSizeBytes"] as? Number)?.toLong()?.let { it > 0 } == true
                requireBackup(!claimsOriginal || text(receipt["imageUrl"]).isNotBlank() || documentValues.any {
                    text(it["receiptInternalId"]) == text(receipt["internalId"]) && text(it["localUri"]).isNotBlank()
                }) { "Ein Belegoriginal liegt nur in der Cloud. Bitte zuerst lokal herunterladen." }
            }
            val availableDriveIds = documentValues.filter { text(it["localUri"]).isNotBlank() }.map { text(it["driveFileId"]) }.toSet() +
                receiptValues.filter { text(it["imageUrl"]).isNotBlank() }.map { text(it["driveFileId"]) }
            requireBackup(tableRows(root, "receipt_documents").all { text(it["driveFileId"]).isBlank() || text(it["driveFileId"]) in availableDriveIds }) {
                "Ein zusätzlicher Beleganhang fehlt lokal. Bitte zuerst die Originale herunterladen."
            }
            requireBackup(files.length() <= 10000 && dataSize(files) <= 2L * 1024 * 1024 * 1024 - 64L * 1024 * 1024) { "Die Sicherung ist zu groß für dieses Format." }
            root.put("files", files)
            replacePrivateUriReferences(root, files)
            val data = root.toString().toByteArray(Charsets.UTF_8)
            requireBackup(data.size <= 64 * 1024 * 1024) { "Die Sicherungsdaten sind zu groß für dieses Format." }
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("snapshot.json")); zip.write(data); zip.closeEntry()
                zip.putNextEntry(ZipEntry("snapshot.sha256")); zip.write(StableDocumentIdentity.sha256(data).toByteArray()); zip.closeEntry()
                for (index in 0 until files.length()) {
                    zip.putNextEntry(ZipEntry("files/$index")); File(directory, index.toString()).inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
        } finally { directory.deleteRecursively() }
    }

    suspend fun inspect(input: InputStream): Prepared {
        val directory = File(context.cacheDir, "offline-import-${UUID.randomUUID()}").apply { check(mkdirs()) }
        try {
            val names = mutableSetOf<String>(); var total = 0L
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    requireBackup(!entry.isDirectory && (entry.name in setOf("snapshot.json", "snapshot.sha256") || Regex("files/[0-9]+").matches(entry.name)) && names.add(entry.name)) { "Ungültiges oder doppeltes Element im Sicherungspaket." }
                    val file = File(directory, entry.name.replace('/', '_'))
                    file.outputStream().use { out ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = zip.read(buffer); if (count < 0) break
                            total += count
                            requireBackup(total <= 2L * 1024 * 1024 * 1024 && file.length() + count <= if (entry.name == "snapshot.json") 64L * 1024 * 1024 else 1024L * 1024 * 1024) { "Das Sicherungspaket ist zu groß." }
                            out.write(buffer, 0, count)
                        }
                    }
                    requireBackup(names.size <= 10002) { "Zu viele Dateien im Sicherungspaket." }
                }
            }
            requireBackup(names.containsAll(setOf("snapshot.json", "snapshot.sha256"))) {
                "Die Datei enthält keine vollständige ImmoPilot-Vollsicherung. Bitte wähle die ZIP-Datei aus „Vollsicherung speichern“."
            }
            val data = File(directory, "snapshot.json").readBytes()
            requireBackup(StableDocumentIdentity.sha256(data) == File(directory, "snapshot.sha256").readText().trim()) { "Die Sicherung ist beschädigt." }
            val root = JSONObject(String(data, Charsets.UTF_8))
            requireBackup(root.getString("format") == "ImmoPilotOfflineBackup" && root.getInt("version") == 1) { "Dieses Sicherungsformat wird nicht unterstützt." }
            val db = database.openHelper.writableDatabase
            requireBackup(root.getInt("databaseVersion") == db.version) { "Die Sicherung benötigt dieselbe Datenbankversion. Bitte zuerst die App-Versionen abgleichen." }
            validateTables(root, db)
            PersistentPreferenceInventory.validatePayload(root.getJSONObject("preferences"))
            val files = root.getJSONArray("files"); val paths = mutableSetOf<String>()
            requireBackup(names == setOf("snapshot.json", "snapshot.sha256") + (0 until files.length()).map { "files/$it" }) { "Die Dateiliste ist unvollständig." }
            for (index in 0 until files.length()) {
                val item = files.getJSONObject(index)
                requireBackup(item.getString("entry") == "files/$index" && paths.add(item.getString("path"))) { "Ungültige Originaldateiliste." }
                val file = File(directory, "files_$index")
                requireBackup(file.length() == item.getLong("size") && file.length() > 0 && com.example.util.ReceiptManifestService.calculateSha256(file) == item.getString("sha256")) { "Eine Originaldatei ist beschädigt oder fehlt." }
            }
            return Prepared(root, directory)
        } catch (error: Exception) { directory.deleteRecursively(); throw error }
    }

    private fun text(value: Any?): String = if (value == null || value === JSONObject.NULL) "" else value.toString()
    private fun dataSize(files: JSONArray): Long = (0 until files.length()).sumOf { files.getJSONObject(it).getLong("size") }
    private fun tableRows(root: JSONObject, name: String): List<Map<String, Any>> {
        val table = root.getJSONObject("tables").getJSONObject(name)
        val columns = table.getJSONArray("columns"); val rows = table.getJSONArray("rows")
        return (0 until rows.length()).map { r -> (0 until columns.length()).associate { c -> columns.getString(c) to rows.getJSONArray(r).get(c) } }
    }

    private suspend fun validateRelationships() {
        val properties = database.propertyDao().getAllProperties()
        requireBackup(properties.map { it.propertyId }.distinct().size == properties.size) { "Doppelte stabile Immobilien-ID in der Sicherung." }
        val prefs = context.getSharedPreferences("wohneinheiten_prefs", Context.MODE_PRIVATE)
        val units = properties.associate { property -> property.propertyId to UnitIdentity.read(property.wohneinheiten, property.propertyId, prefs).also {
            requireBackup(it.error == null) { UnitIdentity.ERROR }
        }.entries.map { it.unitId }.toSet() }
        fun scope(propertyId: String, unitId: String, optional: Boolean = false) {
            if (optional && propertyId.isBlank() && unitId.isBlank()) return
            requireBackup(propertyId in units && (unitId.isBlank() || unitId in units.getValue(propertyId))) { "Objekt- oder Einheitenbeziehung in der Sicherung ist ungültig." }
        }
        val receipts = database.receiptDao().getAllReceiptsIncludingDeletedList()
        val stableIds = receipts.filter { it.internalId.isNotBlank() }.map { it.internalId }
        requireBackup(stableIds.distinct().size == stableIds.size) { "Doppelte stabile Beleg-ID. Es wurden keine Daten übernommen." }
        val byId = receipts.associateBy { it.id }; val byStableId = receipts.associateBy { it.internalId }
        receipts.forEach {
            requireBackup(it.id > 0 && it.internalId.isNotBlank()) { "Belegidentität fehlt in der Sicherung." }
            scope(it.propertyId, it.unitId)
        }
        database.managedDocumentDao().getAll().forEach { doc ->
            scope(doc.propertyId, doc.unitId.orEmpty())
            if (doc.localUri.isNotBlank()) {
                val original = File(doc.localUri)
                requireBackup(original.isFile && (doc.sha256.isBlank() || com.example.util.ReceiptManifestService.calculateSha256(original) == doc.sha256)) { "Dokumentoriginal und gespeicherte Prüfsumme stimmen nicht überein." }
            }
            doc.receiptInternalId?.takeIf(String::isNotBlank)?.let { id ->
                val receipt = requireBackupValue(byStableId[id]) { "Dokument verweist auf einen fehlenden Beleg." }
                requireBackup(receipt.propertyId == doc.propertyId && receipt.unitId == doc.unitId.orEmpty()) { "Dokument und Beleg haben unterschiedliche Objektzuordnungen." }
            }
        }
        val accounts = database.bankDao().getAllAccounts().map { it.accountId }.toSet()
        val transactions = database.bankDao().getAllTransactions().associateBy { it.transactionId }
        transactions.values.forEach { requireBackup(it.accountId in accounts) { "Bankkonto einer Buchung fehlt." }; scope(it.propertyId, it.unitId, optional = true) }
        database.bankDao().getAllLinks().forEach { link ->
            val receipt = requireBackupValue(byId[link.receiptId]) { "Bankzuordnung verweist auf einen fehlenden Beleg." }
            requireBackup(link.transactionId in transactions && (link.receiptInternalId.isBlank() || link.receiptInternalId == receipt.internalId)) { "Bank- und Belegbeziehung ist ungültig." }
        }
        database.bankRentAssignmentDao().getAll().forEach { assignment ->
            requireBackup(assignment.transactionId in transactions) { "Bankbuchung einer Mietzuordnung fehlt." }
            requireBackup(assignment.paymentType !in BankSplitPaymentType.rentScoped || assignment.unitId.isNotBlank()) { "Einheit einer Mietzuordnung fehlt." }
            scope(assignment.propertyId, assignment.unitId, optional = assignment.paymentType !in BankSplitPaymentType.rentScoped)
        }
        val loans = database.loanDao().getAllLoans().associateBy { it.id }
        loans.values.forEach { scope(it.propertyId, "") }
        database.bankLoanAssignmentDao().getAll().forEach { assignment ->
            requireBackup(assignment.transactionId in transactions && loans[assignment.loanId]?.propertyId == assignment.propertyId) { "Darlehenszuordnung ist ungültig." }
        }
        database.logbookDao().getAllTrips().forEach { trip ->
            scope(trip.propertyId, "")
            requireBackup(trip.expenseReceiptId == null || trip.expenseReceiptId in byId) { "Fahrtkostenbeleg fehlt." }
            requireBackup(trip.sourceReceiptId == null || trip.sourceReceiptId in byId) { "Quellbeleg einer Fahrt fehlt." }
        }
    }

    private fun validateTables(root: JSONObject, db: SupportSQLiteDatabase) {
        val tables = root.getJSONObject("tables"); val schema = schema(db)
        requireBackup(tables.keys().asSequence().toSet() == schema.keys) { "Die Sicherung enthält eine andere Datenbankstruktur." }
        schema.forEach { (name, columns) ->
            requireBackup(columns.any { it.primary > 0 }) { "Nicht unterstützte Tabelle." }
            val table = tables.getJSONObject(name); val names = table.getJSONArray("columns")
            requireBackup((0 until names.length()).map { names.getString(it) } == columns.map { it.name }) { "Die Tabellenspalten passen nicht zur App-Version." }
            val rows = table.getJSONArray("rows")
            for (r in 0 until rows.length()) {
                val row = rows.getJSONArray(r); requireBackup(row.length() == columns.size)
                columns.forEachIndexed { c, column ->
                    val value = row.get(c)
                    requireBackup(value !== JSONObject.NULL || (!column.notNull && column.primary == 0)) { "Pflichtfeld fehlt in der Sicherung." }
                    requireBackup(value === JSONObject.NULL || when (column.type.uppercase()) {
                        "INTEGER" -> value is Number && value.toDouble().isFinite() && value.toDouble() == value.toLong().toDouble()
                        "REAL" -> value is Number && value.toDouble().isFinite()
                        "BLOB" -> value is JSONObject && value.has("blob")
                        else -> value is String
                    }) { "Ungültiger Feldtyp in der Sicherung." }
                }
            }
        }
    }

    suspend fun restore(prepared: Prepared) {
        val root = JSONObject(prepared.root.toString())
        val destination = File(context.filesDir, "offline-originals")
        check(destination.isDirectory || destination.mkdirs()) { "Lokaler Originalspeicher nicht verfügbar." }
        val files = root.getJSONArray("files"); val mapping = mutableMapOf<String, String>()
        for (index in 0 until files.length()) {
            val item = files.getJSONObject(index)
            val source = File(prepared.staging, "files_$index")
            val extension = com.example.util.DatevOriginalAttachmentPolicy.detect(source)?.second ?: "bin"
            val pathId = StableDocumentIdentity.sha256(item.getString("path").toByteArray(Charsets.UTF_8))
            val target = File(destination, "$pathId-${item.getString("sha256")}.$extension")
            requireBackup(com.example.util.ReceiptManifestService.calculateSha256(source) == item.getString("sha256")) { "Das vorbereitete Original wurde verändert." }
            if (target.exists()) requireBackup(com.example.util.ReceiptManifestService.calculateSha256(target) == item.getString("sha256")) { "Eine vorhandene Originaldatei steht im Konflikt." }
            else {
                val staging = File.createTempFile("original-", ".tmp", destination)
                try {
                    source.copyTo(staging, overwrite = true)
                    requireBackup(com.example.util.ReceiptManifestService.calculateSha256(staging) == item.getString("sha256")) { "Originaldatei konnte nicht vollständig kopiert werden." }
                    check(staging.renameTo(target)) { "Originaldatei konnte nicht dauerhaft gespeichert werden." }
                } finally { staging.delete() }
            }
            mapping[item.getString("path")] = target.absolutePath
        }
        val tables = root.getJSONObject("tables")
        tables.keys().forEach { name ->
            val table = tables.getJSONObject(name); val columns = table.getJSONArray("columns"); val rows = table.getJSONArray("rows")
            for (c in 0 until columns.length()) if (columns.getString(c) in pathColumns) for (r in 0 until rows.length()) {
                val row = rows.getJSONArray(r); val raw = row.optString(c, "")
                if (raw.isNotBlank()) row.put(c, mapping[raw] ?: ReceiptOriginalChain.encodePaths(ReceiptOriginalChain.paths(raw).map { path -> requireBackupValue(mapping[path]) { "Eine Dateireferenz fehlt im Sicherungspaket." } }))
            }
        }
        val preferencesBefore = JSONObject().also { PersistentPreferenceInventory.writePayload(context, it) }
        try {
            database.withTransaction {
                val db = database.openHelper.writableDatabase
                validateTables(root, db)
                val insertions = mutableListOf<Pair<String, Array<Any?>>>()
                schema(db).forEach { (name, columns) ->
                    val rows = tables.getJSONObject(name).getJSONArray("rows")
                    val keys = columns.withIndex().filter { it.value.primary > 0 }
                    for (r in 0 until rows.length()) {
                        val row = rows.getJSONArray(r)
                        val where = keys.joinToString(" AND ") { "`${it.value.name}` IS ?" }
                        val arguments = keys.map { bound(row.get(it.index)) }.toTypedArray()
                        db.query("SELECT * FROM `$name` WHERE $where", arguments).use { cursor ->
                            if (cursor.moveToFirst()) {
                                requireBackup(columns.indices.all { c -> equivalent(value(cursor, cursor.getColumnIndexOrThrow(columns[c].name)), row.get(c)) }) {
                                    "Vorhandene Daten unterscheiden sich von der Sicherung. Es wurde nichts überschrieben. Bitte auf einem leeren App-Bestand wiederherstellen."
                                }
                            } else insertions += "INSERT INTO `$name` (${columns.joinToString { "`${it.name}`" }}) VALUES (${columns.joinToString { "?" }})" to Array(columns.size) { bound(row.get(it)) }
                        }
                    }
                }
                insertions.forEach { (sql, arguments) -> db.execSQL(sql, arguments) }
                db.query("PRAGMA foreign_key_check").use { requireBackup(!it.moveToFirst()) { "Ungültige Beziehungen in der Sicherung." } }
                database.managedDocumentDao().getAll().forEach { document ->
                    database.managedDocumentDao().deleteSearchEntry(document.documentId)
                    database.managedDocumentDao().insertSearchEntry(DocumentSearchFts(document.documentId, DocumentSearchTextBuilder.build(document)))
                }
                PersistentPreferenceInventory.restorePayload(context, root.getJSONObject("preferences"), RestoreMode.MERGE)
                validateRelationships()
            }
        } catch (error: Exception) {
            PersistentPreferenceInventory.restorePayload(context, preferencesBefore, RestoreMode.REPLACE_FULL)
            throw error
        }
    }
}
