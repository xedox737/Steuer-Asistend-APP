package com.example.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** First recovery gate: bound index and four direct artifact hashes, no application of data. */
internal object OfflineRestoreRollbackVerifier {
    data class Index(val rollbackId: String, val database: File, val preferences: File,
        val ownedManifest: File, val referenceManifest: File, val fileCount: Int,
        val referenceCount: Int, val unavailableCount: Int)
    private data class Description(val id: String, val databaseBytes: Long, val databaseHash: String,
        val preferencesBytes: Long, val preferencesHash: String, val filesBytes: Long, val filesHash: String,
        val fileCount: Int, val referencesBytes: Long, val referencesHash: String, val referenceCount: Int, val unavailableCount: Int)
    private const val MAX_BUNDLE = 16L * 1024
    private const val MAX_METADATA = 64L * 1024 * 1024
    private val hashPattern = Regex("[0-9a-f]{64}")

    suspend fun verifyIndex(context: Context, expected: OfflineRestoreJournal.Entry): Index = withContext(Dispatchers.IO) {
        try {
            if (OfflineRestoreJournal(context).load() != expected) invalid()
            val parent = File(context.noBackupFilesDir, "offline-backup-state")
            val id = expected.rollbackId
            val bundle = checkedFile(File(parent, "rollback-$id.bundle.json"))
            require(expected.rollbackManifestBytes in 1..MAX_BUNDLE)
            val bytes = readBounded(bundle, expected.rollbackManifestBytes)
            require(hash(bytes) == expected.rollbackManifestSha256)
            val description = decode(bytes)
            require(description.id == id)
            val database = checkedFile(File(parent, "rollback-$id.db"))
            val preferences = checkedFile(File(parent, "rollback-$id.preferences.json"))
            val owned = checkedFile(File(parent, "rollback-$id.files/manifest.json"))
            val references = checkedFile(File(parent, "rollback-$id.references/manifest.json"))
            checkFile(database, description.databaseBytes, description.databaseHash)
            checkFile(preferences, description.preferencesBytes, description.preferencesHash)
            checkFile(owned, description.filesBytes, description.filesHash)
            checkFile(references, description.referencesBytes, description.referencesHash)
            if (OfflineRestoreJournal(context).load() != expected) invalid()
            currentCoroutineContext().ensureActive()
            Index(id, database, preferences, owned, references, description.fileCount, description.referenceCount, description.unavailableCount)
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: SecurityException) { invalid() }
        catch (_: NoSuchElementException) { invalid() }
    }
    private fun checkedFile(file: File): File {
        require(file.absoluteFile == file.canonicalFile && file.isFile && file.canRead())
        return file
    }
    private fun readBounded(file: File, expected: Long): ByteArray {
        require(file.length() == expected)
        return FileInputStream(file).use { input ->
            val buffer = ByteArrayOutputStream(); val chunk = ByteArray(1024)
            while (true) { val count = input.read(chunk); if (count < 0) break
                require(count.toLong() <= expected - buffer.size()); buffer.write(chunk, 0, count) }
            require(buffer.size().toLong() == expected && file.length() == expected)
            buffer.toByteArray()
        }
    }
    private suspend fun checkFile(file: File, expected: Long, expectedHash: String) {
        require(file.length() == expected && expected > 0 && hashPattern.matches(expectedHash))
        val digest = MessageDigest.getInstance("SHA-256"); var total = 0L
        FileInputStream(file).use { input -> val buffer = ByteArray(8192); while (true) {
            currentCoroutineContext().ensureActive(); val size = input.read(buffer); if (size < 0) break
            require(size.toLong() <= expected - total); digest.update(buffer, 0, size); total += size
        } }
        require(total == expected && file.length() == expected && hex(digest.digest()) == expectedHash)
    }
    private fun decode(bytes: ByteArray): Description {
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return JsonReader(InputStreamReader(ByteArrayInputStream(bytes), decoder)).use { reader ->
            val numbers = setOf("version", "databaseBytes", "preferencesBytes", "filesManifestBytes", "fileCount",
                "referencesManifestBytes", "referenceCount", "unavailableCount")
            val strings = setOf("owner", "id", "databaseSha256", "preferencesSha256", "filesManifestSha256", "referencesManifestSha256")
            val values = mutableMapOf<String, String>(); reader.isLenient = false; reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName(); require(name !in values && (name in numbers || name in strings))
                require(reader.peek() == if (name in numbers) JsonToken.NUMBER else JsonToken.STRING)
                values[name] = reader.nextString()
            }
            reader.endObject(); require(reader.peek() == JsonToken.END_DOCUMENT && values.keys == numbers + strings)
            require(values["version"] == "1" && values["owner"] == "local-full-restore-rollback")
            fun number(name: String, maximum: Long): Long {
                val raw = values.getValue(name); val value = raw.toLong(); require(value.toString() == raw && value in 0..maximum); return value
            }
            fun sha(name: String): String = values.getValue(name).also { require(hashPattern.matches(it)) }
            val files = number("fileCount", 10_000).toInt()
            val refs = number("referenceCount", 10_000).toInt()
            val unavailable = number("unavailableCount", refs.toLong()).toInt()
            Description(values.getValue("id"), number("databaseBytes", Long.MAX_VALUE), sha("databaseSha256"),
                number("preferencesBytes", MAX_METADATA), sha("preferencesSha256"), number("filesManifestBytes", MAX_METADATA),
                sha("filesManifestSha256"), files, number("referencesManifestBytes", MAX_METADATA), sha("referencesManifestSha256"), refs, unavailable)
        }
    }
    private fun hash(bytes: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun invalid(): Nothing = throw OfflineBackupValidationException("Der lokale Rückfallstand ist unvollständig oder beschädigt. Der Bestand bleibt gesperrt.")
}
