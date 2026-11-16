package com.example.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.util.Base64
import android.util.JsonWriter
import androidx.room.withTransaction
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.CodingErrorAction
import java.security.DigestOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A metadata draft alone is not a complete or consistent snapshot. */
internal class OfflineBackupMetadataWriter(
    private val context: Context,
    private val database: AppDatabase,
    private val maximumBytes: Long = MAX_BYTES,
    private val referenceMode: ReferenceMode = ReferenceMode.STRICT_BACKUP,
    private val referenceFileExists: (String) -> Boolean = { File(it).isFile }
) {
    enum class ReferenceMode { STRICT_BACKUP, ROLLBACK_INVENTORY }
    init { require(maximumBytes in 1..MAX_BYTES) }
    data class Reference(val key: String, val path: String, val owner: String,
        val expectedSha256: String?, val expectedSize: Long?, val verifiedCandidates: List<String>)
    data class Draft(val file: File, val sha256: String, val bytes: Long,
        val rowCounts: Map<String, Long>, val references: List<Reference>)
    private data class Column(val name: String, val type: String, val primary: Int, val notNull: Boolean)
    private data class Claim(val key: String, val path: String,
        val owners: MutableSet<String> = linkedSetOf(), val hashes: MutableSet<String> = linkedSetOf(),
        val sizes: MutableSet<Long> = linkedSetOf(), val candidates: MutableSet<String> = linkedSetOf())
    private data class ReceiptClaim(val id: String, val owner: String)

    suspend fun write(output: File): Draft {
        val cancellation = currentCoroutineContext()
        cancellation.ensureActive()
        var owned = false
        var complete = false
        try {
            if (!output.createNewFile()) invalid("Der Metadatenplatz ist bereits belegt. Es wurde nichts überschrieben.")
            owned = true
            val digest = MessageDigest.getInstance("SHA-256")
            val claims = linkedMapOf<String, Claim>()
            val receipts = mutableListOf<ReceiptClaim>()
            val documentsWithOriginal = mutableSetOf<String>()
            val availableDriveIds = mutableSetOf<String>()
            val attachmentDriveIds = mutableSetOf<String>()
            val rowCounts = linkedMapOf<String, Long>()
            database.withTransaction {
                val db = database.openHelper.writableDatabase
                val allTables = db.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").use { cursor ->
                    buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
                }
                val realTables = allTables.filterNot { it.startsWith("sqlite_") || it.startsWith("document_search_fts") || it in SYSTEM_TABLES }.toSet()
                if (realTables != BUSINESS_TABLES + LOCAL_TABLES) invalid("Die Datenbank enthält nicht klassifizierte oder fehlende Tabellen. Bitte die App prüfen.")
                FileOutputStream(output).use { fileOutput ->
                    val bounded = LimitedOutput(DigestOutputStream(fileOutput, digest), maximumBytes)
                    val encoder = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    JsonWriter(OutputStreamWriter(bounded, encoder)).use { writer ->
                        writer.beginObject().name("format").value("ImmoPilotOfflineBackup").name("version").value(2L)
                            .name("databaseVersion").value(db.version.toLong()).name("tables").beginObject()
                        BUSINESS_TABLES.sorted().forEach { name ->
                            cancellation.ensureActive()
                            val columns = db.query("PRAGMA table_info(`$name`)").use { cursor -> buildList {
                                while (cursor.moveToNext()) {
                                    val column = cursor.getString(1)
                                    if (!Regex("[A-Za-z_][A-Za-z0-9_]*").matches(column)) invalid("Unbekannte Tabellenspalte in der Datenbank.")
                                    add(Column(column, cursor.getString(2), cursor.getInt(5), cursor.getInt(3) != 0))
                                }
                            } }
                            if (columns.none { it.primary > 0 }) invalid("Eine fachliche Tabelle besitzt keinen unterstützten Primärschlüssel.")
                            writer.name(name).beginObject().name("columns").beginArray()
                            columns.forEach { column -> writer.beginObject().name("name").value(column.name).name("type").value(column.type)
                                .name("primary").value(column.primary.toLong()).name("notNull").value(column.notNull).endObject() }
                            writer.endArray().name("rows").beginArray()
                            var count = 0L
                            val order = columns.filter { it.primary > 0 }.sortedBy { it.primary }.joinToString(",") { "`${it.name}`" }
                            db.query("SELECT * FROM `$name` ORDER BY $order").use { cursor ->
                                while (cursor.moveToNext()) {
                                    cancellation.ensureActive()
                                    // One row at a time; no list/JSONObject of database tables is materialized.
                                    val row = columns.associate { it.name to value(cursor, cursor.getColumnIndexOrThrow(it.name)) }.toMutableMap()
                                    collectReferences(name, row, claims, receipts, documentsWithOriginal, availableDriveIds, attachmentDriveIds)
                                    writer.beginArray()
                                    columns.forEach { column -> writeValue(writer, row[column.name]) }
                                    writer.endArray()
                                    count++
                                }
                            }
                            rowCounts[name] = count
                            writer.endArray().endObject()
                        }
                        writer.endObject().name("preferences")
                        cancellation.ensureActive()
                        val preferences = JSONObject().also { PersistentPreferenceInventory.writePayload(context, it) }
                        writeValue(writer, preferences)
                        writer.name("sequences").beginObject()
                        if ("sqlite_sequence" in allTables) {
                            db.query("SELECT name, seq FROM sqlite_sequence ORDER BY name").use { cursor ->
                                while (cursor.moveToNext()) {
                                    if (cursor.getString(0) in BUSINESS_TABLES) {
                                        if (cursor.getType(1) != Cursor.FIELD_TYPE_INTEGER || cursor.getLong(1) < 0) invalid("Ein SQLite-Zählerstand ist ungültig.")
                                        writer.name(cursor.getString(0)).value(cursor.getLong(1))
                                    }
                                }
                            }
                        }
                        writer.endObject().endObject()
                    }
                    // JsonWriter/encoder is closed, but LimitedOutput keeps the caller-owned FD open.
                    fileOutput.fd.sync()
                }
            }
            fun unavailable(kind: String, identity: String, owner: String) {
                if (claims.size >= 10_000) invalid("Zu viele Originalverweise in den Rückfallmetadaten.")
                val path = "offline-missing-$kind:${StableDocumentIdentity.sha256(identity.toByteArray(Charsets.UTF_8))}"
                if (path in claims) invalid("Eine fehlende Originalzuordnung ist nicht eindeutig.")
                claims[path] = Claim("original-${claims.size}", path).also { it.owners += owner }
            }
            receipts.forEachIndexed { index, receipt ->
                if (receipt.id !in documentsWithOriginal) {
                    if (referenceMode == ReferenceMode.STRICT_BACKUP)
                        invalid("${receipt.owner}: Das Original liegt nur in der Cloud oder fehlt lokal. Bitte zuerst lokal herunterladen.")
                    unavailable("receipt", "${receipt.id}\u0000$index", receipt.owner)
                }
            }
            val missingAttachments = attachmentDriveIds - availableDriveIds
            if (missingAttachments.isNotEmpty() && referenceMode == ReferenceMode.STRICT_BACKUP)
                invalid("Ein zusätzlicher Beleganhang besitzt keine lokal auflösbare Originalzuordnung.")
            if (referenceMode == ReferenceMode.ROLLBACK_INVENTORY) missingAttachments.sorted().forEach { driveId ->
                unavailable("attachment", driveId, "Beleganhang mit nicht verfügbarer Originalzuordnung")
            }
            val hashes = claims.values.associate { claim ->
                if (referenceMode == ReferenceMode.STRICT_BACKUP && (claim.hashes.size > 1 || claim.sizes.size > 1))
                    invalid("${claim.owners.joinToString("; ")}: Widersprüchliche Originalmetadaten.")
                claim.path to if (referenceMode == ReferenceMode.STRICT_BACKUP) claim.hashes.singleOrNull() else null
            }
            val references = claims.values.map { claim ->
                val hash = hashes[claim.path]
                Reference(claim.key, claim.path, claim.owners.joinToString("; "), hash,
                    if (referenceMode == ReferenceMode.STRICT_BACKUP) claim.sizes.singleOrNull() else null,
                    if (hash == null) emptyList() else (claim.candidates + claims.values.filter { hashes[it.path] == hash }.map { it.path }).distinct())
            }
            cancellation.ensureActive()
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            cancellation.ensureActive()
            val result = Draft(output, sha, output.length(), rowCounts.toMap(), references)
            complete = true
            return result
        } catch (_: IOException) {
            invalid("Die Metadaten konnten nicht vollständig geschrieben werden. Bitte Speicher und Datenbestand prüfen.")
        } catch (_: SecurityException) {
            invalid("Der Zugriff auf die Sicherungsmetadaten wurde verweigert.")
        } catch (_: SQLiteException) {
            invalid("Die Datenbank konnte nicht vollständig gelesen werden. Es wurde keine Sicherung freigegeben.")
        } catch (_: JSONException) {
            invalid("Die gespeicherten Einstellungen oder Metadaten sind nicht vollständig darstellbar.")
        } finally {
            if (owned && !complete) {
                try { output.delete() } catch (_: SecurityException) {
                    invalid("Temporäre Metadaten konnten wegen fehlender Zugriffsrechte nicht entfernt werden.")
                }
            }
        }
    }

    private fun collectReferences(table: String, row: MutableMap<String, Any?>, claims: MutableMap<String, Claim>,
        receipts: MutableList<ReceiptClaim>, documentsWithOriginal: MutableSet<String>, availableDriveIds: MutableSet<String>,
        attachmentDriveIds: MutableSet<String>) {
        fun text(key: String) = row[key] as? String ?: ""
        val display = owner(table, row)
        if (table == "managed_documents" && text("localUri").isBlank()) {
            val metadataOnly = text("receiptInternalId").isNotBlank() && text("sha256").isBlank() &&
                (row["fileSizeBytes"] as? Number)?.toLong() == 0L && text("driveFileId").isBlank()
            if (!metadataOnly) {
                val id = text("documentId")
                if (id.isBlank()) invalid("Ein Dokumentoriginal besitzt keine stabile Dokument-ID.")
                row["localUri"] = "offline-original:${StableDocumentIdentity.sha256(id.toByteArray(Charsets.UTF_8))}"
            }
        }
        val receiptHasPath = text("imageUrl").isNotBlank()
        if (table == "receipts") {
            val needsOriginal = text("driveFileId").isNotBlank() || text("originalMimeType").isNotBlank() ||
                ((row["fileSizeBytes"] as? Number)?.toLong() ?: 0L) > 0
            if (needsOriginal && !receiptHasPath) {
                if (receipts.size >= 10_000) invalid("Zu viele erforderliche Originalzuordnungen in den Sicherungsmetadaten.")
                receipts += ReceiptClaim(text("internalId"), display)
            }
        }
        if (table == "receipt_documents") text("driveFileId").takeIf(String::isNotBlank)?.let(attachmentDriveIds::add)
        PATH_COLUMNS.forEach { column ->
            val raw = text(column)
            if (raw.isBlank()) return@forEach
            val direct = column == "localUri" || (table == "property_metadata" && column == "bildPfad")
            val paths = if (direct) listOf(raw) else ReceiptOriginalChain.paths(raw, referenceFileExists)
            val aliases = paths.map { path ->
                val claim = claims.getOrPut(path) {
                    if (claims.size >= 10_000) invalid("Zu viele Originalverweise in den Sicherungsmetadaten.")
                    Claim("original-${claims.size}", path)
                }
                claim.owners += display
                if (table == "managed_documents" && column == "localUri") {
                    val hash = text("sha256").lowercase(java.util.Locale.ROOT)
                    if (hash.isNotBlank() && referenceMode == ReferenceMode.STRICT_BACKUP) {
                        if (!Regex("[0-9a-f]{64}").matches(hash)) invalid("$display: Die gespeicherte Prüfsumme ist ungültig.")
                        claim.hashes += hash
                    }
                    if (referenceMode == ReferenceMode.STRICT_BACKUP)
                        (row["fileSizeBytes"] as? Number)?.toLong()?.takeIf { it > 0 }?.let(claim.sizes::add)
                    val filename = text("storedFilename")
                    if (filename.isNotBlank() && filename !in setOf(".", "..") && '/' !in filename && '\\' !in filename) {
                        val directory = File(context.filesDir, "managed_documents").canonicalFile
                        val candidate = File(directory, filename)
                        if (candidate.canonicalFile.parentFile == directory) claim.candidates += candidate.absolutePath
                    }
                    text("receiptInternalId").takeIf(String::isNotBlank)?.let(documentsWithOriginal::add)
                    text("driveFileId").takeIf(String::isNotBlank)?.let(availableDriveIds::add)
                }
                if (table == "receipts" && column == "imageUrl") text("driveFileId").takeIf(String::isNotBlank)?.let(availableDriveIds::add)
                claim.key
            }
            row[column] = if (direct || aliases.size == 1) aliases.single() else JSONArray(aliases).toString()
        }
    }

    private fun owner(table: String, row: Map<String, Any?>): String {
        fun field(name: String) = row[name] as? String ?: row[name]?.toString().orEmpty()
        val kind = when (table) { "managed_documents" -> "Dokument"; "property_metadata" -> "Immobilie"; else -> "Beleg" }
        val title = listOf("title", "aussteller", "name", "beschreibung").map(::field).firstOrNull(String::isNotBlank).orEmpty()
        val id = listOf("documentId", "internalId", "propertyId", "id").map(::field).firstOrNull(String::isNotBlank).orEmpty()
        val path = listOf("localUri", "imageUrl", "pdfPath", "bildPfad").map(::field).firstOrNull(String::isNotBlank).orEmpty()
        val filename = field("originalFilename").ifBlank { field("storedFilename") }.ifBlank { Uri.parse(path).lastPathSegment.orEmpty() }
            .substringAfterLast('/').substringAfterLast('\\')
        return "$kind „$title“ (ID: $id, Objekt: ${field("propertyId")}, Datei: $filename)"
    }

    private fun value(cursor: Cursor, index: Int): Any? = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
        Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index)
        else -> cursor.getString(index)
    }

    private fun writeValue(writer: JsonWriter, value: Any?) {
        when (value) {
            null, JSONObject.NULL -> writer.nullValue()
            is String -> writer.value(value)
            is Boolean -> writer.value(value)
            is Number -> writer.value(value)
            is ByteArray -> writer.beginObject().name("blob").value(Base64.encodeToString(value, Base64.NO_WRAP)).endObject()
            is JSONObject -> {
                writer.beginObject()
                value.keys().asSequence().toList().sorted().forEach { key -> writer.name(key); writeValue(writer, value.get(key)) }
                writer.endObject()
            }
            is JSONArray -> {
                writer.beginArray()
                for (index in 0 until value.length()) writeValue(writer, value.get(index))
                writer.endArray()
            }
            else -> invalid("Nicht unterstützter Wert in den Sicherungsmetadaten.")
        }
    }

    private class LimitedOutput(private val output: OutputStream, private val maximum: Long) : OutputStream() {
        private var count = 0L
        override fun write(value: Int) { capacity(1); output.write(value); count++ }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            capacity(length); output.write(bytes, offset, length); count += length
        }
        private fun capacity(length: Int) {
            if (length.toLong() > maximum - count) throw OfflineBackupValidationException("Sicherungsmetadaten überschreiten die Grenze von $maximum Byte.")
        }
        override fun flush() = output.flush()
        // The caller owns the file descriptor and fsyncs after the UTF-8 encoder has finished.
        override fun close() = flush()
    }

    private fun invalid(message: String): Nothing = throw OfflineBackupValidationException(message)

    internal companion object {
        const val MAX_BYTES = 64L * 1024 * 1024
        val PATH_COLUMNS = setOf("imageUrl", "pdfPath", "bildPfad", "localUri")
        val SYSTEM_TABLES = setOf("room_master_table", "android_metadata")
        val LOCAL_TABLES = setOf("document_migration_journal")
        val BUSINESS_TABLES = setOf("receipts", "receipt_entities", "belege", "property_metadata", "loans", "receipt_documents",
            "export_audit_runs", "logbook_trips", "standard_routes", "managed_documents", "bank_accounts", "bank_transactions",
            "bank_receipt_links", "bank_learning_rules", "bank_rule_evidence", "bank_rent_assignments", "bank_loan_assignments", "bank_recurring_patterns")
    }
}
