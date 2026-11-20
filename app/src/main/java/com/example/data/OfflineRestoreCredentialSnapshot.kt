package com.example.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import com.example.api.AiProviderSettings
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Local rollback of authenticated, already Keystore-encrypted credential records only. */
internal object OfflineRestoreCredentialSnapshot {
    data class Snapshot(val id: String, val file: File, val size: Long, val sha256: String,
        val providers: Set<OfflineBackupCredentialSection.Provider>)
    data class Checked(val id: String, val state: Map<OfflineBackupCredentialSection.Provider, AiProviderSettings.RollbackCiphertext?>)
    private const val MAX_BYTES = 128 * 1024
    private val hashPattern = Regex("[0-9a-f]{64}")

    suspend fun capture(context: Context, token: OfflineBackupCoordinator.Maintenance, id: String): Snapshot {
        var owned: File? = null; var delivered = false
        try {
            val result = withContext(Dispatchers.IO) {
                OfflineBackupCoordinator.shared.requireMaintenance(token)
                require(UUID.fromString(id).toString() == id)
                token.suspending {
                    currentCoroutineContext().ensureActive()
                    val state = AiProviderSettings.captureRollbackCiphertexts(context)
                    val buffer = ByteArrayOutputStream()
                    JsonWriter(OutputStreamWriter(buffer, Charsets.UTF_8)).use { writer ->
                        writer.beginObject().name("version").value(1L).name("owner").value("local-credential-rollback")
                            .name("id").value(id).name("providers").beginObject()
                        OfflineBackupCredentialSection.Provider.entries.forEach { provider ->
                            currentCoroutineContext().ensureActive()
                            writer.name(provider.name)
                            val pair = state.getValue(provider)
                            if (pair == null) writer.nullValue() else writer.beginObject().name("ciphertext").value(pair.ciphertext)
                                .name("iv").value(pair.iv).endObject()
                        }
                        writer.endObject().endObject()
                    }
                    val bytes = buffer.toByteArray(); require(bytes.size in 1..MAX_BYTES)
                    val directory = File(context.noBackupFilesDir, "offline-backup-state")
                    if (!directory.isDirectory && !directory.mkdirs()) invalid()
                    val available = directory.usableSpace; val required = bytes.size + OfflineBackupResourceBudget.RESERVE_BYTES
                    if (available < required) throw OfflineBackupValidationException("Zu wenig privater Speicher für den Schlüssel-Rückfallstand: benötigt $required Byte, verfügbar $available Byte.")
                    val file = File(directory, "rollback-$id.credentials.json")
                    if (!file.createNewFile()) invalid()
                    owned = file
                    FileOutputStream(file).use { output -> output.write(bytes); output.fd.sync() }
                    val sha = hash(bytes)
                    val checked = verify(context, id, bytes.size.toLong(), sha)
                    require(checked.state == state)
                    currentCoroutineContext().ensureActive()
                    Snapshot(id, file, bytes.size.toLong(), sha, state.filterValues { it != null }.keys.toSet())
                }
            }
            delivered = true
            return result
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: SecurityException) { invalid() }
        finally { if (!delivered) owned?.let { if (it.exists() && !it.delete()) invalid() } }
    }
    suspend fun verify(context: Context, id: String, expectedBytes: Long, expectedSha256: String): Checked = withContext(Dispatchers.IO) {
        try {
            require(UUID.fromString(id).toString() == id && expectedBytes in 1..MAX_BYTES.toLong() && hashPattern.matches(expectedSha256))
            val file = File(context.noBackupFilesDir, "offline-backup-state/rollback-$id.credentials.json")
            require(file.absoluteFile == file.canonicalFile && file.isFile && file.length() == expectedBytes)
            val output = ByteArrayOutputStream()
            FileInputStream(file).use { input -> val chunk = ByteArray(1024); while (true) {
                currentCoroutineContext().ensureActive(); val count = input.read(chunk); if (count < 0) break
                require(count.toLong() <= expectedBytes - output.size()); output.write(chunk, 0, count)
            } }
            val bytes = output.toByteArray()
            require(bytes.size.toLong() == expectedBytes && file.length() == expectedBytes && hash(bytes) == expectedSha256)
            val state = decode(bytes, id)
            AiProviderSettings.validateRollbackCiphertexts(state)
            currentCoroutineContext().ensureActive()
            Checked(id, state)
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: NoSuchElementException) { invalid() }
        catch (_: SecurityException) { invalid() }
    }
    private fun decode(bytes: ByteArray, id: String): Map<OfflineBackupCredentialSection.Provider, AiProviderSettings.RollbackCiphertext?> {
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return JsonReader(InputStreamReader(ByteArrayInputStream(bytes), decoder)).use { reader ->
            reader.isLenient = false; val seen = hashSetOf<String>(); var version: String? = null; var owner: String? = null; var foundId: String? = null
            val state = linkedMapOf<OfflineBackupCredentialSection.Provider, AiProviderSettings.RollbackCiphertext?>()
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName(); require(seen.add(name))
                when (name) {
                    "version" -> { require(reader.peek() == JsonToken.NUMBER); version = reader.nextString() }
                    "owner" -> { require(reader.peek() == JsonToken.STRING); owner = reader.nextString() }
                    "id" -> { require(reader.peek() == JsonToken.STRING); foundId = reader.nextString() }
                    "providers" -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val provider = OfflineBackupCredentialSection.Provider.valueOf(reader.nextName())
                            require(provider !in state)
                            state[provider] = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else {
                                val fields = hashSetOf<String>(); var ciphertext: String? = null; var iv: String? = null
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    val field = reader.nextName(); require(fields.add(field) && reader.peek() == JsonToken.STRING)
                                    when (field) {
                                        "ciphertext" -> ciphertext = reader.nextString().also { require(it.length in 1..24 * 1024) }
                                        "iv" -> iv = reader.nextString().also { require(it.length == 16) }
                                        else -> throw IllegalArgumentException()
                                    }
                                }
                                reader.endObject(); require(fields == setOf("ciphertext", "iv"))
                                AiProviderSettings.RollbackCiphertext(requireNotNull(ciphertext), requireNotNull(iv))
                            }
                        }
                        reader.endObject()
                    }
                    else -> throw IllegalArgumentException()
                }
            }
            reader.endObject()
            require(reader.peek() == JsonToken.END_DOCUMENT && seen == setOf("version", "owner", "id", "providers"))
            require(version == "1" && owner == "local-credential-rollback" && foundId == id && state.keys == OfflineBackupCredentialSection.Provider.entries.toSet())
            state.toMap()
        }
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun invalid(): Nothing = throw OfflineBackupValidationException("Der verschlüsselte Schlüssel-Rückfallstand ist ungültig oder nicht verfügbar. Die Schlüssel wurden nicht verändert.")
}
