package com.example.data

import android.content.Context
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Local-only protected generations. Call on an IO dispatcher; never exports a device key. */
internal class OfflineBackupGenerationStore(context: Context) {
    private val root = File(context.noBackupFilesDir, "offline-backup-keys")
    private val current = File(root, "generation.bin")

    fun loadCurrent(): OfflineBackupKeyEnvelope.Generation? = synchronized(lock) {
        if (!current.exists()) null else read(current)
    }

    fun installFirst(generation: OfflineBackupKeyEnvelope.Generation): Unit = synchronized(lock) {
        check(!current.exists()) { "Eine Schlüsselgeneration ist bereits eingerichtet." }
        OfflineBackupDeviceKey.initialize()
        persist(generation)
    }

    fun replaceCurrent(expectedGenerationId: String, generation: OfflineBackupKeyEnvelope.Generation): Unit = synchronized(lock) {
        check(current.exists()) { "Die bisherige Schlüsselgeneration fehlt." }
        read(current).use { previous ->
            check(previous.recovery.generationId == expectedGenerationId) { "Die Schlüsselgeneration wurde inzwischen geändert." }
            check(generation.recovery.generationId != expectedGenerationId) { "Eine neue Schlüsselgeneration ist erforderlich." }
        }
        persist(generation)
    }

    private fun persist(generation: OfflineBackupKeyEnvelope.Generation) {
        val protected = OfflineBackupDeviceKey.protect(generation)
        check(root.isDirectory || root.mkdirs()) { "Der lokale Schlüsselspeicher ist nicht verfügbar." }
        val temporary = File.createTempFile("generation-", ".tmp", root)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(header(protected.recovery)); output.write(protected.iv); output.write(protected.ciphertext)
                output.fd.sync()
            }
            read(temporary).use { reopened ->
                val expected = generation.masterCopy(); val actual = reopened.masterCopy()
                try { check(MessageDigest.isEqual(expected, actual)) { "Die Schlüsselprüfung ist fehlgeschlagen." } }
                finally { expected.fill(0); actual.fill(0) }
            }
            // Same-directory rename publishes only a complete, synced and authenticated record.
            check(temporary.renameTo(current)) { "Die Schlüsselgeneration konnte nicht gespeichert werden." }
        } finally { temporary.delete() }
    }

    private fun read(file: File): OfflineBackupKeyEnvelope.Generation {
        if (file.length() !in 1..(64 * 1024 + 60).toLong()) invalid()
        return DataInputStream(file.inputStream().buffered()).use { input ->
            val parsed = OfflineBackupArchiveHeader.read(input)
            if (!parsed.authenticatedBytes.contentEquals(header(parsed.recovery))) invalid()
            val iv = ByteArray(12).also(input::readFully)
            val ciphertext = ByteArray(48).also(input::readFully)
            if (input.read() != -1) invalid()
            OfflineBackupDeviceKey.open(OfflineBackupDeviceKey.Protected(parsed.recovery, iv, ciphertext))
        }
    }

    private fun header(recovery: OfflineBackupKeyEnvelope.Recovery) = OfflineBackupArchiveHeader.encode(recovery,
        OfflineBackupKeyEnvelope.WrappedDataKey(recovery.generationId, "00000000-0000-0000-0000-000000000000",
            ByteArray(12), ByteArray(48)))
    private fun invalid(): Nothing = throw OfflineBackupValidationException("Ungültiger lokaler Schlüsselspeicher. Es wurde kein Ersatz erzeugt.")
    private companion object { val lock = Any() }
}
