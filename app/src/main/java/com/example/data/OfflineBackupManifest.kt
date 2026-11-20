package com.example.data

import android.util.JsonReader
import android.util.JsonToken
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Checks container declarations only; metadata/schema and isolated Room probe remain mandatory. */
internal object OfflineBackupManifest {
    data class Reference(val entry: String, val sha256: String, val size: Long)
    data class Version(val id: String, val portablePath: String?, val kind: OfflineBackupFileInventory.Kind, val entry: String)
    data class Checked(val rowCounts: Map<String, Long>, val aliases: Map<String, String>, val versions: List<Version>,
        val payloads: List<Reference>, val credentials: Reference?, val credentialProviders: Set<OfflineBackupCredentialSection.Provider>, val archiveInfo: OfflineBackupArchiveInfo)

    suspend fun verify(contents: OfflineBackupArchiveValidator.StagedContents,
        generation: OfflineBackupKeyEnvelope.Generation? = null): Checked = withContext(Dispatchers.IO) {
        try {
            val manifest = contents.parts.getValue("manifest.json")
            val file = requireNotNull(manifest.file)
            require(file.length() == manifest.bytes && file.length() > 0)
            val runtime = Runtime.getRuntime()
            if (file.length() > (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory() - 16 * 1024 * 1024) / 6)
                throw OfflineBackupValidationException("Für die sichere Manifestprüfung ist nicht genügend Arbeitsspeicher verfügbar. Es wurden keine Daten übernommen.")
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            JsonReader(InputStreamReader(file.inputStream(), decoder)).use { reader ->
                reader.isLenient = false
                Parser(reader, currentCoroutineContext()).read(contents, generation)
            }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: NoSuchElementException) { invalid() }
        catch (_: SecurityException) { invalid() }
    }

    private class Parser(private val reader: JsonReader, private val cancellation: CoroutineContext) {
        private fun text(): String {
            require(reader.peek() == JsonToken.STRING)
            return reader.nextString().also { require(it.length <= 8192 && '\u0000' !in it) }
        }
        private fun number(): Long {
            require(reader.peek() == JsonToken.NUMBER)
            val value = reader.nextString(); require(Regex("0|[1-9][0-9]{0,18}").matches(value))
            return requireNotNull(value.toLongOrNull())
        }
        private fun fields(required: Set<String>, optional: Set<String> = emptySet(), field: (String) -> Unit) {
            reader.beginObject(); val seen = hashSetOf<String>()
            while (reader.hasNext()) {
                cancellation.ensureActive()
                val name = reader.nextName(); require(name in required + optional && seen.add(name)); field(name)
            }
            reader.endObject(); require(seen.containsAll(required))
        }
        private fun <T> array(item: () -> T): List<T> {
            val result = mutableListOf<T>(); reader.beginArray()
            while (reader.hasNext()) { cancellation.ensureActive(); require(result.size < 10_000); result += item() }
            reader.endArray(); return result
        }
        private fun reference(): Reference {
            var entry: String? = null; var sha: String? = null; var size: Long? = null
            fields(setOf("entry", "sha256", "size")) { when (it) {
                "entry" -> entry = text(); "sha256" -> sha = text(); "size" -> size = number()
            } }
            require(Regex("[0-9a-f]{64}").matches(requireNotNull(sha)))
            return Reference(requireNotNull(entry), requireNotNull(sha), requireNotNull(size))
        }
        private fun version(): Version {
            var id: String? = null; var path: String? = null; var kind: String? = null; var entry: String? = null
            fields(setOf("id", "kind", "entry"), setOf("portablePath")) { when (it) {
                "id" -> id = text(); "kind" -> kind = text(); "entry" -> entry = text()
                "portablePath" -> if (reader.peek() == JsonToken.NULL) reader.nextNull() else path = text()
            } }
            return Version(requireNotNull(id), path, OfflineBackupFileInventory.Kind.valueOf(requireNotNull(kind)), requireNotNull(entry))
        }
        fun read(contents: OfflineBackupArchiveValidator.StagedContents, generation: OfflineBackupKeyEnvelope.Generation?): Checked {
            var format: String? = null; var version: Long? = null; var metadata: Reference? = null; var credentials: Reference? = null
            var payloads: List<Reference>? = null; var versions: List<Version>? = null
            var info: OfflineBackupArchiveInfo? = null
            val counts = linkedMapOf<String, Long>(); val aliases = linkedMapOf<String, String>()
            fields(setOf("format", "version", "archive", "metadata", "rowCounts", "aliases", "files", "payloads"), setOf("credentials")) { when (it) {
                "format" -> format = text(); "version" -> version = number(); "metadata" -> metadata = reference()
                "archive" -> {
                    var created: String? = null; var code: Long? = null; var name: String? = null
                    fields(setOf("createdAt", "appVersionCode", "appVersionName")) { key -> when (key) {
                        "createdAt" -> created = text(); "appVersionCode" -> code = number(); "appVersionName" -> name = text()
                    } }
                    info = OfflineBackupArchiveInfo(requireNotNull(created), requireNotNull(code), requireNotNull(name))
                }
                "credentials" -> if (reader.peek() == JsonToken.NULL) reader.nextNull() else credentials = reference()
                "payloads" -> payloads = array { reference() }; "files" -> versions = array { version() }
                "rowCounts" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        cancellation.ensureActive(); val name = reader.nextName()
                        require(counts.size < 128 && Regex("[A-Za-z_][A-Za-z0-9_]{0,127}").matches(name) && name !in counts)
                        counts[name] = number()
                    }
                    reader.endObject()
                }
                "aliases" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        cancellation.ensureActive(); val name = reader.nextName()
                        require(aliases.size < 10_000 && name !in aliases && Regex("original-(0|[1-9][0-9]{0,3})").matches(name))
                        aliases[name] = text()
                    }
                    reader.endObject()
                }
            } }
            require(reader.peek() == JsonToken.END_DOCUMENT && format == "ImmoPilotSnapshot" && counts.isNotEmpty())
            if (requireNotNull(version) > 2) throw OfflineBackupValidationException("Neuere Sicherung: Bitte die App aktualisieren.")
            require(version == 2L && metadata?.entry == "metadata.json")
            checkReference(requireNotNull(metadata), contents)
            require(counts.values.all { it <= requireNotNull(metadata).size } && counts.values.sum() <= requireNotNull(metadata).size)
            val fixedPayloads = requireNotNull(payloads)
            require(fixedPayloads.map { it.entry } == fixedPayloads.indices.map { "files/$it" })
            fixedPayloads.forEach { cancellation.ensureActive(); checkReference(it, contents) }
            val entries = fixedPayloads.map { it.entry }.toSet()
            require(aliases.keys == (0 until aliases.size).map { "original-$it" }.toSet() && aliases.values.all { it in entries })
            val fixedVersions = requireNotNull(versions)
            require(fixedVersions.map { it.id }.toSet().size == fixedVersions.size && fixedVersions.map { it.entry }.toSet() == entries)
            val paths = hashSetOf<String>()
            fixedVersions.forEach { file ->
                cancellation.ensureActive(); require(file.entry in entries)
                val path = file.portablePath
                if (path == null) require(file.kind == OfflineBackupFileInventory.Kind.ORIGINAL && file.id.startsWith("external/") && aliases[file.id.removePrefix("external/")] == file.entry)
                else require(file.id == path && paths.add(path) && portableKind(path) == file.kind)
            }
            val providers = credentials?.let { reference ->
                require(reference.entry == "credentials.bin"); checkReference(reference, contents)
                val sealed = requireNotNull(contents.parts.getValue(reference.entry).inMemory)
                OfflineBackupCredentialSection.open(requireNotNull(generation), sealed).use { it.values.keys.toSet() }
            } ?: emptySet()
            val expected = setOf("manifest.json", "metadata.json") + entries + listOfNotNull(credentials?.entry)
            require(contents.parts.keys == expected)
            return Checked(counts, aliases, fixedVersions, fixedPayloads, credentials, providers, requireNotNull(info))
        }
        private fun checkReference(reference: Reference, contents: OfflineBackupArchiveValidator.StagedContents) {
            val actual = contents.parts.getValue(reference.entry)
            require(actual.bytes == reference.size && actual.sha256 == reference.sha256)
        }
    }

    internal fun portableKind(path: String): OfflineBackupFileInventory.Kind? {
        if ('\\' in path || '\u0000' in path) return null
        val parts = path.split('/')
        if (parts.size < 2 || parts.any { it.isEmpty() || it in setOf(".", "..") }) return null
        val root = parts[1]
        if (parts[0] == "files") {
            if (parts.size == 2 && root.startsWith("receipt_page_")) return OfflineBackupFileInventory.Kind.ORIGINAL
            if (parts.size < 3 || root !in setOf("managed_documents", "property-images", "offline-originals", "restored_receipts", "receipt_documents")) return null
            if (parts.size == 3 && parts[2].endsWith(".tmp")) {
                val name = parts[2]
                if ((root == "managed_documents" && (name.startsWith("document-download-") || name.startsWith("receipt-import-"))) ||
                    (root == "property-images" && name.startsWith("restore-")) || (root == "offline-originals" && name.startsWith("original-"))) return null
            }
            return OfflineBackupFileInventory.Kind.ORIGINAL
        }
        if (parts[0] != "cache") return null
        if (parts.size >= 3 && root in setOf("exports", "reports")) return OfflineBackupFileInventory.Kind.SAVED_EXPORT
        if (parts.size != 2) return null
        if (Regex("temp_receipt_.*\\.jpg").matches(root)) return OfflineBackupFileInventory.Kind.ORIGINAL
        return if (listOf("EXTF_Buchungsstapel_.*\\.csv", "DATEV_Export_.*\\.zip", "Steuerbericht_(Finanzamt|Steuerberater)_.*\\.pdf",
                "document\\.xml", "datev_export\\.pdf").any { Regex(it).matches(root) }) OfflineBackupFileInventory.Kind.SAVED_EXPORT else null
    }
    private fun invalid(): Nothing = throw OfflineBackupValidationException("Das Sicherungsmanifest ist ungültig, unvollständig oder kann nicht sicher geprüft werden. Es wurden keine Daten übernommen.")
}
