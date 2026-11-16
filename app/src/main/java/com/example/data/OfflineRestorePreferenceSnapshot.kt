package com.example.data

import android.content.Context
import android.util.JsonWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.nio.charset.CodingErrorAction
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Captures only classified business preferences; credentials remain in their device-bound stores. */
internal object OfflineRestorePreferenceSnapshot {
    data class Snapshot(val id: String, val file: File, val size: Long, val sha256: String)
    private const val MAX_BYTES = 64L * 1024 * 1024
    suspend fun capture(context: Context, token: OfflineBackupCoordinator.Maintenance, id: String): Snapshot {
        var owned: File? = null; var delivered = false
        try {
            val result = withContext(Dispatchers.IO) {
                OfflineBackupCoordinator.shared.requireMaintenance(token)
                require(UUID.fromString(id).toString() == id)
                val cancellation = currentCoroutineContext()
                token.writing {
                    val directory = File(context.noBackupFilesDir, "offline-backup-state")
                    if (!directory.isDirectory && !directory.mkdirs()) invalid("Der private Rückfallbereich ist nicht verfügbar.")
                    val available = directory.usableSpace
                    val reserve = OfflineBackupResourceBudget.RESERVE_BYTES
                    if (available <= reserve) invalid("Zu wenig privater Speicher: benötigt mehr als $reserve Byte, verfügbar $available Byte.")
                    val file = File(directory, "rollback-$id.preferences.json")
                    if (!file.createNewFile()) invalid("Der private Rückfallplatz ist bereits belegt.")
                    owned = file
                    val digest = MessageDigest.getInstance("SHA-256")
                    val maximum = minOf(MAX_BYTES, available - reserve)
                    var count = 0L
                    FileOutputStream(file).use { stream ->
                        val output = object : OutputStream() {
                            private val target = DigestOutputStream(stream, digest)
                            override fun write(value: Int) { if (count >= maximum) invalid("Der Einstellungs-Rückfallstand überschreitet das verfügbare Speicherbudget."); target.write(value); count++ }
                            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                                if (length.toLong() > maximum - count) invalid("Der Einstellungs-Rückfallstand überschreitet das verfügbare Speicherbudget.")
                                target.write(bytes, offset, length); count += length
                            }
                            override fun flush() = target.flush()
                        }
                        val writer = JsonWriter(OutputStreamWriter(output, Charsets.UTF_8.newEncoder()
                            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)))
                        PersistentPreferenceInventory.writePayload(context, writer) { cancellation.ensureActive() }
                        writer.flush(); stream.fd.sync()
                    }
                    val expected = digest.digest()
                    val actual = MessageDigest.getInstance("SHA-256")
                    file.inputStream().use { input -> val buffer = ByteArray(8192); while (true) {
                        cancellation.ensureActive(); val size = input.read(buffer); if (size < 0) break; actual.update(buffer, 0, size)
                    } }
                    if (file.length() != count || !actual.digest().contentEquals(expected)) invalid("Der Einstellungs-Rückfallstand konnte nicht vollständig rückgelesen werden.")
                    Snapshot(id, file, count, expected.joinToString("") { "%02x".format(it.toInt() and 255) })
                }
            }
            delivered = true
            return result
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid("Der Einstellungs-Rückfallstand konnte nicht vollständig gesichert werden.") }
        catch (_: IllegalArgumentException) { invalid("Der Einstellungs-Rückfallstand ist ungültig.") }
        catch (_: IllegalStateException) { invalid("Der Einstellungs-Rückfallstand konnte nicht sicher geprüft werden.") }
        finally { if (!delivered) owned?.let { if (it.exists() && !it.delete()) invalid("Unvollständige private Rückfalldaten konnten nicht entfernt werden.") } }
    }
    private fun invalid(message: String): Nothing = throw OfflineBackupValidationException(message)
}
