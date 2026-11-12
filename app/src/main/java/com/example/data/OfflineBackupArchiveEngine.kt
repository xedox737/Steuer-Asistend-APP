package com.example.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Prepares a complete private encrypted archive; publication and probe validation follow separately. */
internal class OfflineBackupArchiveEngine(private val context: Context, private val database: AppDatabase) {
    class Prepared internal constructor(val file: File, val summary: OfflineBackupSnapshot.Summary) : AutoCloseable {
        override fun close() {
            if (file.exists() && !file.delete()) throw OfflineBackupValidationException("Das private verschlüsselte Arbeitsarchiv konnte nicht entfernt werden.")
        }
    }

    suspend fun prepare(consent: OfflineBackupCredentialSection.Consent = OfflineBackupCredentialSection.Consent(),
        trigger: OfflineBackupCredentialSection.Trigger = OfflineBackupCredentialSection.Trigger.MANUAL,
        progress: (OfflineBackupSnapshot.Progress) -> Unit = {}): Prepared {
        var pending: Prepared? = null
        var delivered = false
        try {
            val prepared = withContext(Dispatchers.IO) {
                OfflineBackupKeyManager(context).withCurrentGeneration { generation ->
                    OfflineBackupSnapshot.capture(context, database, credentials =
                        OfflineBackupSnapshot.CredentialCapture(generation, consent, trigger), progress = progress).use { snapshot ->
                        val parent = File(context.filesDir, "offline-backup-staging")
                        OfflineBackupResourceBudget.checkPrepared(parent, snapshot)
                        val file = File(parent, "encrypted-${UUID.randomUUID()}.immopilot")
                        check(file.createNewFile()) { "Ein eindeutiger verschlüsselter Arbeitsplatz konnte nicht angelegt werden." }
                        val result = Prepared(file, snapshot.summary)
                        pending = result
                        FileOutputStream(file).use { output ->
                            val unclosed = object : FilterOutputStream(output) {
                                override fun write(bytes: ByteArray, offset: Int, length: Int) = out.write(bytes, offset, length)
                                override fun close() = flush()
                            }
                            OfflineBackupArchiveCrypto.encrypt(generation, unclosed) { snapshot.writeArchive(it) }
                            currentCoroutineContext().ensureActive()
                            output.fd.sync()
                        }
                        OfflineBackupArchiveValidator.verifyPrepared(context, file, generation, snapshot.summary)
                        result
                    }
                }
            }
            delivered = true
            return prepared
        } finally {
            // Also owns cleanup when prompt cancellation discards the IO dispatcher's return value.
            if (!delivered) pending?.close()
        }
    }
}
