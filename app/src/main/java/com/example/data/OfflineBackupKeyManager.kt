package com.example.data

import android.content.Context
import java.io.DataInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Serializes key setup/rotation with complete archive encryption, including across instances. */
internal class OfflineBackupKeyManager(context: Context) {
    private val store = OfflineBackupGenerationStore(context)

    suspend fun initialize(password: CharArray, confirmation: CharArray) = configure(password, confirmation, null)

    suspend fun changePassword(expectedGenerationId: String, password: CharArray, confirmation: CharArray) =
        configure(password, confirmation, expectedGenerationId)

    private suspend fun configure(password: CharArray, confirmation: CharArray, expected: String?): Unit = try {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                require(password.size in 16..1024 && password.contentEquals(confirmation)) {
                    "Das Wiederherstellungspasswort muss mindestens 16 Zeichen haben und übereinstimmend bestätigt werden."
                }
                store.loadCurrent().use { previous ->
                    if (expected == null) check(previous == null) { "Die Sicherung ist bereits eingerichtet." }
                    else {
                        check(previous?.recovery?.generationId == expected) { "Die Schlüsselgeneration wurde inzwischen geändert." }
                        val probe = password.copyOf()
                        val unchanged = try {
                            OfflineBackupKeyEnvelope.recover(requireNotNull(previous).recovery, probe).use { true }
                        } catch (_: GeneralSecurityException) { false }
                        finally { probe.fill('\u0000') }
                        require(!unchanged) { "Bitte ein anderes Wiederherstellungspasswort wählen." }
                    }
                }
                OfflineBackupKeyEnvelope.create(password).use { generation ->
                    verifyTestArchive(generation, confirmation)
                    currentCoroutineContext().ensureActive()
                    if (expected == null) store.installFirst(generation)
                    else store.replaceCurrent(expected, generation)
                }
            }
        }
    } finally { password.fill('\u0000'); confirmation.fill('\u0000') }

    suspend fun encryptCurrent(output: OutputStream, writePlaintext: suspend (OutputStream) -> Unit): Unit =
        withCurrentGeneration { OfflineBackupArchiveCrypto.encrypt(it, output, writePlaintext) }

    internal suspend fun <T> withCurrentGeneration(block: suspend (OfflineBackupKeyEnvelope.Generation) -> T): T =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val generation = store.loadCurrent()
                    ?: throw OfflineBackupValidationException("Bitte zuerst ein Wiederherstellungspasswort einrichten.")
                generation.use { block(it) }
            }
        }

    private suspend fun verifyTestArchive(generation: OfflineBackupKeyEnvelope.Generation, password: CharArray) {
        val challenge = ByteArray(64).also(SecureRandom()::nextBytes)
        val archive = ByteArrayOutputStream()
        try {
            OfflineBackupArchiveCrypto.encrypt(generation, archive) { it.write(challenge) }
            archive.toByteArray().inputStream().use { input ->
                val header = OfflineBackupArchiveHeader.read(input)
                OfflineBackupKeyEnvelope.recover(header.recovery, password).use { recovered ->
                    val key = OfflineBackupKeyEnvelope.unwrapDataKey(recovered, header.dataKey)
                    try {
                        OfflineBackupStreamCipher.decrypt(key, header.authenticatedBytes, input).use { decrypted ->
                            // Read through authenticated EOF, bounded even for this internal challenge.
                            val actual = ByteArray(challenge.size)
                            try {
                                DataInputStream(decrypted).readFully(actual)
                                check(decrypted.read() == -1 && actual.contentEquals(challenge)) { "Die Wiederherstellungsprüfung ist fehlgeschlagen." }
                            }
                            finally { actual.fill(0) }
                        }
                    } finally { key.fill(0) }
                }
            }
        } finally { challenge.fill(0); password.fill('\u0000') }
    }

    private companion object { val mutex = Mutex() }
}
