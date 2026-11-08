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

/** Versioned offline transport. Conflicting existing rows are never overwritten. */
internal class OfflineBackupService(private val context: Context, private val database: AppDatabase) {
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
            require(Regex("[a-z_]+").matches(name)) { "Unbekannte Datenbankstruktur." }
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
            val paths = linkedSetOf<String>()
            val tables = root.getJSONObject("tables")
            tables.keys().forEach { name ->
                val table = tables.getJSONObject(name); val columns = table.getJSONArray("columns"); val rows = table.getJSONArray("rows")
                for (c in 0 until columns.length()) if (columns.getString(c) in pathColumns) {
                    for (r in 0 until rows.length()) {
                        val raw = rows.getJSONArray(r).optString(c, "")
                        if (raw.isNotBlank()) paths.addAll(if (columns.getString(c) == "localUri" || (name == "property_metadata" && columns.getString(c) == "bildPfad")) listOf(raw) else ReceiptOriginalChain.paths(raw))
                    }
                }
            }
            val files = JSONArray()
            paths.forEachIndexed { index, path ->
                val source = File(path.removePrefix("file://"))
                require(source.isFile && source.length() > 0 && source.length() <= 1024L * 1024 * 1024) { "Eine Originaldatei fehlt lokal. Bitte zuerst die Originale wiederherstellen." }
                val copied = File(directory, index.toString())
                source.copyTo(copied)
                val sha = com.example.util.ReceiptManifestService.calculateSha256(copied)
                require(sha == com.example.util.ReceiptManifestService.calculateSha256(source)) { "Eine Datei wurde während der Sicherung geändert. Bitte erneut versuchen." }
                files.put(JSONObject().put("path", path).put("entry", "files/$index").put("sha256", sha).put("size", copied.length()))
            }
            val originalsByPath = (0 until files.length()).associate {
                val file = files.getJSONObject(it); file.getString("path") to file.getString("sha256")
            }
            val documents = tables.getJSONObject("managed_documents")
            val documentColumns = documents.getJSONArray("columns")
            fun column(name: String) = (0 until documentColumns.length()).single { documentColumns.getString(it) == name }
            val documentRows = documents.getJSONArray("rows")
            for (index in 0 until documentRows.length()) {
                val row = documentRows.getJSONArray(index)
                val path = row.optString(column("localUri"), "")
                require(path.isNotBlank() || (row.optString(column("receiptInternalId"), "").isNotBlank() &&
                    row.optString(column("sha256"), "").isBlank() && row.optLong(column("fileSizeBytes"), 0) == 0L &&
                    row.optString(column("driveFileId"), "").isBlank())) {
                    "Ein Dokumentoriginal fehlt lokal. Bitte zuerst die Originale herunterladen."
                }
                val expected = row.optString(column("sha256"), "")
                require(path.isBlank() || expected.isBlank() || originalsByPath[path] == expected) {
                    "Ein Original stimmt nicht mit seiner gespeicherten Prüfsumme überein. Bitte zuerst die Datei prüfen."
                }
            }
            val documentValues = tableRows(root, "managed_documents")
            val receiptValues = tableRows(root, "receipts")
            receiptValues.forEach { receipt ->
                val claimsOriginal = text(receipt["driveFileId"]).isNotBlank() || text(receipt["originalMimeType"]).isNotBlank() || (receipt["fileSizeBytes"] as? Number)?.toLong()?.let { it > 0 } == true
                require(!claimsOriginal || text(receipt["imageUrl"]).isNotBlank() || documentValues.any {
                    text(it["receiptInternalId"]) == text(receipt["internalId"]) && text(it["localUri"]).isNotBlank()
                }) { "Ein Belegoriginal liegt nur in der Cloud. Bitte zuerst lokal herunterladen." }
            }
            val availableDriveIds = documentValues.filter { text(it["localUri"]).isNotBlank() }.map { text(it["driveFileId"]) }.toSet() +
                receiptValues.filter { text(it["imageUrl"]).isNotBlank() }.map { text(it["driveFileId"]) }
            require(tableRows(root, "receipt_documents").all { text(it["driveFileId"]).isBlank() || text(it["driveFileId"]) in availableDriveIds }) {
                "Ein zusätzlicher Beleganhang fehlt lokal. Bitte zuerst die Originale herunterladen."
            }
            require(files.length() <= 10000 && dataSize(files) <= 2L * 1024 * 1024 * 1024 - 64L * 1024 * 1024) { "Die Sicherung ist zu groß für dieses Format." }
            root.put("files", files)
            val data = root.toString().toByteArray(Charsets.UTF_8)
            require(data.size <= 64 * 1024 * 1024) { "Die Sicherungsdaten sind zu groß für dieses Format." }
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
                    require(!entry.isDirectory && (entry.name in setOf("snapshot.json", "snapshot.sha256") || Regex("files/[0-9]+").matches(entry.name)) && names.add(entry.name)) { "Ungültiges oder doppeltes Element im Sicherungspaket." }
                    val file = File(directory, entry.name.replace('/', '_'))
                    file.outputStream().use { out ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = zip.read(buffer); if (count < 0) break
                            total += count
                            require(total <= 2L * 1024 * 1024 * 1024 && file.length() + count <= if (entry.name == "snapshot.json") 64L * 1024 * 1024 else 1024L * 1024 * 1024) { "Das Sicherungspaket ist zu groß." }
                            out.write(buffer, 0, count)
                        }
                    }
                    require(names.size <= 10002) { "Zu viele Dateien im Sicherungspaket." }
                }
            }
            val data = File(directory, "snapshot.json").readBytes()
            require(StableDocumentIdentity.sha256(data) == File(directory, "snapshot.sha256").readText().trim()) { "Die Sicherung ist beschädigt." }
            val root = JSONObject(String(data, Charsets.UTF_8))
            require(root.getString("format") == "ImmoPilotOfflineBackup" && root.getInt("version") == 1) { "Dieses Sicherungsformat wird nicht unterstützt." }
            val db = database.openHelper.writableDatabase
            require(root.getInt("databaseVersion") == db.version) { "Die Sicherung benötigt dieselbe Datenbankversion. Bitte zuerst die App-Versionen abgleichen." }
            validateTables(root, db)
            PersistentPreferenceInventory.validatePayload(root.getJSONObject("preferences"))
            val files = root.getJSONArray("files"); val paths = mutableSetOf<String>()
            require(names == setOf("snapshot.json", "snapshot.sha256") + (0 until files.length()).map { "files/$it" }) { "Die Dateiliste ist unvollständig." }
            for (index in 0 until files.length()) {
                val item = files.getJSONObject(index)
                require(item.getString("entry") == "files/$index" && paths.add(item.getString("path"))) { "Ungültige Originaldateiliste." }
                val file = File(directory, "files_$index")
                require(file.length() == item.getLong("size") && file.length() > 0 && com.example.util.ReceiptManifestService.calculateSha256(file) == item.getString("sha256")) { "Eine Originaldatei ist beschädigt oder fehlt." }
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
        require(properties.map { it.propertyId }.distinct().size == properties.size) { "Doppelte stabile Immobilien-ID in der Sicherung." }
        val prefs = context.getSharedPreferences("wohneinheiten_prefs", Context.MODE_PRIVATE)
        val units = properties.associate { property -> property.propertyId to UnitIdentity.read(property.wohneinheiten, property.propertyId, prefs).also {
            require(it.error == null) { UnitIdentity.ERROR }
        }.entries.map { it.unitId }.toSet() }
        fun scope(propertyId: String, unitId: String, optional: Boolean = false) {
            if (optional && propertyId.isBlank() && unitId.isBlank()) return
            require(propertyId in units && (unitId.isBlank() || unitId in units.getValue(propertyId))) { "Objekt- oder Einheitenbeziehung in der Sicherung ist ungültig." }
        }
        val receipts = database.receiptDao().getAllReceiptsIncludingDeletedList()
        val stableIds = receipts.filter { it.internalId.isNotBlank() }.map { it.internalId }
        require(stableIds.distinct().size == stableIds.size) { "Doppelte stabile Beleg-ID. Es wurden keine Daten übernommen." }
        val byId = receipts.associateBy { it.id }; val byStableId = receipts.associateBy { it.internalId }
        receipts.forEach {
            require(it.id > 0 && it.internalId.isNotBlank()) { "Belegidentität fehlt in der Sicherung." }
            scope(it.propertyId, it.unitId)
        }
        database.managedDocumentDao().getAll().forEach { doc ->
            scope(doc.propertyId, doc.unitId.orEmpty())
            if (doc.localUri.isNotBlank()) {
                val original = File(doc.localUri)
                require(original.isFile && (doc.sha256.isBlank() || com.example.util.ReceiptManifestService.calculateSha256(original) == doc.sha256)) { "Dokumentoriginal und gespeicherte Prüfsumme stimmen nicht überein." }
            }
            doc.receiptInternalId?.takeIf(String::isNotBlank)?.let { id ->
                val receipt = requireNotNull(byStableId[id]) { "Dokument verweist auf einen fehlenden Beleg." }
                require(receipt.propertyId == doc.propertyId && receipt.unitId == doc.unitId.orEmpty()) { "Dokument und Beleg haben unterschiedliche Objektzuordnungen." }
            }
        }
        val accounts = database.bankDao().getAllAccounts().map { it.accountId }.toSet()
        val transactions = database.bankDao().getAllTransactions().associateBy { it.transactionId }
        transactions.values.forEach { require(it.accountId in accounts) { "Bankkonto einer Buchung fehlt." }; scope(it.propertyId, it.unitId, optional = true) }
        database.bankDao().getAllLinks().forEach { link ->
            val receipt = requireNotNull(byId[link.receiptId]) { "Bankzuordnung verweist auf einen fehlenden Beleg." }
            require(link.transactionId in transactions && (link.receiptInternalId.isBlank() || link.receiptInternalId == receipt.internalId)) { "Bank- und Belegbeziehung ist ungültig." }
        }
        database.bankRentAssignmentDao().getAll().forEach { assignment ->
            require(assignment.transactionId in transactions) { "Bankbuchung einer Mietzuordnung fehlt." }
            require(assignment.paymentType !in BankSplitPaymentType.rentScoped || assignment.unitId.isNotBlank()) { "Einheit einer Mietzuordnung fehlt." }
            scope(assignment.propertyId, assignment.unitId, optional = assignment.paymentType !in BankSplitPaymentType.rentScoped)
        }
        val loans = database.loanDao().getAllLoans().associateBy { it.id }
        loans.values.forEach { scope(it.propertyId, "") }
        database.bankLoanAssignmentDao().getAll().forEach { assignment ->
            require(assignment.transactionId in transactions && loans[assignment.loanId]?.propertyId == assignment.propertyId) { "Darlehenszuordnung ist ungültig." }
        }
        database.logbookDao().getAllTrips().forEach { trip ->
            scope(trip.propertyId, "")
            require(trip.expenseReceiptId == null || trip.expenseReceiptId in byId) { "Fahrtkostenbeleg fehlt." }
            require(trip.sourceReceiptId == null || trip.sourceReceiptId in byId) { "Quellbeleg einer Fahrt fehlt." }
        }
    }

    private fun validateTables(root: JSONObject, db: SupportSQLiteDatabase) {
        val tables = root.getJSONObject("tables"); val schema = schema(db)
        require(tables.keys().asSequence().toSet() == schema.keys) { "Die Sicherung enthält eine andere Datenbankstruktur." }
        schema.forEach { (name, columns) ->
            require(columns.any { it.primary > 0 }) { "Nicht unterstützte Tabelle." }
            val table = tables.getJSONObject(name); val names = table.getJSONArray("columns")
            require((0 until names.length()).map { names.getString(it) } == columns.map { it.name }) { "Die Tabellenspalten passen nicht zur App-Version." }
            val rows = table.getJSONArray("rows")
            for (r in 0 until rows.length()) {
                val row = rows.getJSONArray(r); require(row.length() == columns.size)
                columns.forEachIndexed { c, column ->
                    val value = row.get(c)
                    require(value !== JSONObject.NULL || (!column.notNull && column.primary == 0)) { "Pflichtfeld fehlt in der Sicherung." }
                    require(value === JSONObject.NULL || when (column.type.uppercase()) {
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
            require(com.example.util.ReceiptManifestService.calculateSha256(source) == item.getString("sha256")) { "Das vorbereitete Original wurde verändert." }
            if (target.exists()) require(com.example.util.ReceiptManifestService.calculateSha256(target) == item.getString("sha256")) { "Eine vorhandene Originaldatei steht im Konflikt." }
            else {
                val staging = File.createTempFile("original-", ".tmp", destination)
                try {
                    source.copyTo(staging, overwrite = true)
                    require(com.example.util.ReceiptManifestService.calculateSha256(staging) == item.getString("sha256")) { "Originaldatei konnte nicht vollständig kopiert werden." }
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
                if (raw.isNotBlank()) row.put(c, mapping[raw] ?: ReceiptOriginalChain.encodePaths(ReceiptOriginalChain.paths(raw).map { path -> requireNotNull(mapping[path]) { "Eine Dateireferenz fehlt im Sicherungspaket." } }))
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
                                require(columns.indices.all { c -> equivalent(value(cursor, cursor.getColumnIndexOrThrow(columns[c].name)), row.get(c)) }) {
                                    "Vorhandene Daten unterscheiden sich von der Sicherung. Es wurde nichts überschrieben. Bitte auf einem leeren App-Bestand wiederherstellen."
                                }
                            } else insertions += "INSERT INTO `$name` (${columns.joinToString { "`${it.name}`" }}) VALUES (${columns.joinToString { "?" }})" to Array(columns.size) { bound(row.get(it)) }
                        }
                    }
                }
                insertions.forEach { (sql, arguments) -> db.execSQL(sql, arguments) }
                db.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) { "Ungültige Beziehungen in der Sicherung." } }
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
