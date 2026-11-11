package com.example.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Wire encryption. Authenticated plaintext still needs manifest/schema/probe validation. */
internal object OfflineBackupArchiveCrypto {
    private const val MAX_PLAINTEXT = 2L * 1024 * 1024 * 1024

    suspend fun encrypt(generation: OfflineBackupKeyEnvelope.Generation, output: OutputStream,
        writePlaintext: suspend (OutputStream) -> Unit): Unit = withContext(Dispatchers.IO) {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            val wrapped = OfflineBackupKeyEnvelope.wrapDataKey(generation, UUID.randomUUID().toString(), key)
            val header = OfflineBackupArchiveHeader.encode(generation.recovery, wrapped)
            currentCoroutineContext().ensureActive()
            output.write(header)
            OfflineBackupStreamCipher.encrypt(key, header, output).use { encrypted -> writePlaintext(encrypted) }
        } finally { key.fill(0) }
    }

    /** New caller-owned PRIVATE staging only. Never exposes a result before authenticated EOF. */
    suspend fun decryptToPrivateStaging(input: InputStream, password: CharArray, output: File,
        maximumPlaintextBytes: Long = MAX_PLAINTEXT): Unit = try { withContext(Dispatchers.IO) {
        var owned = false
        var complete = false
        var key: ByteArray? = null
        try {
            require(maximumPlaintextBytes in 1..MAX_PLAINTEXT)
            currentCoroutineContext().ensureActive()
            val header = OfflineBackupArchiveHeader.read(input)
            OfflineBackupKeyEnvelope.recover(header.recovery, password).use { generation ->
                key = OfflineBackupKeyEnvelope.unwrapDataKey(generation, header.dataKey)
            }
            currentCoroutineContext().ensureActive()
            if (!output.createNewFile()) throw OfflineBackupValidationException("Der private Entschlüsselungsplatz ist bereits belegt.")
            owned = true
            FileOutputStream(output).use { destination ->
                OfflineBackupStreamCipher.decrypt(requireNotNull(key), header.authenticatedBytes, input).use { plaintext ->
                    val buffer = ByteArray(8192)
                    var count = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val size = plaintext.read(buffer)
                        if (size < 0) break // Includes Tink's authenticated final-segment check.
                        if (size == 0) continue
                        if (size.toLong() > maximumPlaintextBytes - count) throw OfflineBackupValidationException("Entschlüsselte Sicherung überschreitet die erlaubte Größe.")
                        destination.write(buffer, 0, size)
                        count += size
                    }
                }
                destination.fd.sync()
            }
            currentCoroutineContext().ensureActive()
            complete = true
        } catch (error: OfflineBackupValidationException) {
            throw error
        } catch (_: GeneralSecurityException) {
            throw OfflineBackupValidationException("Passwort falsch oder Sicherung beschädigt. Es wurden keine Daten übernommen.")
        } catch (_: IOException) {
            throw OfflineBackupValidationException("Die Sicherung konnte nicht vollständig entschlüsselt und authentifiziert werden.")
        } catch (_: IllegalArgumentException) {
            throw OfflineBackupValidationException("Passwort oder Sicherungsheader ist ungültig.")
        } catch (_: SecurityException) {
            throw OfflineBackupValidationException("Der Zugriff auf den privaten Entschlüsselungsplatz wurde verweigert.")
        } finally {
            key?.fill(0)
            password.fill('\u0000')
            if (owned && !complete) {
                try { output.delete() } catch (_: SecurityException) {
                    throw OfflineBackupValidationException("Unvollständige private Entschlüsselungsdaten konnten nicht entfernt werden.")
                }
            }
        }
    } } finally { password.fill('\u0000') }
}
