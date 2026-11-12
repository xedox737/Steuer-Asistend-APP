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

/** One verified pipeline for manual/scheduled archives; holds its generation through publication. */
internal class OfflineBackupArchiveEngine(private val context: Context, private val database: AppDatabase) {
    class Prepared internal constructor(val file: File, val summary: OfflineBackupSnapshot.Summary,
        internal val resources: OfflineBackupResourceBudget.Estimate, val archiveInfo: OfflineBackupArchiveInfo) : AutoCloseable {
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
                    prepareHeld(generation, consent, trigger, progress).also { pending = it }
                }
            }
            delivered = true
            return prepared
        } finally {
            // Also owns cleanup when prompt cancellation discards the IO dispatcher's return value.
            if (!delivered) pending?.close()
        }
    }
    suspend fun publish(destination: OfflineBackupDestination,
        consent: OfflineBackupCredentialSection.Consent = OfflineBackupCredentialSection.Consent(),
        trigger: OfflineBackupCredentialSection.Trigger = OfflineBackupCredentialSection.Trigger.MANUAL,
        snapshotProgress: (OfflineBackupSnapshot.Progress) -> Unit = {},
        destinationProgress: (OfflineBackupDestination.Stage) -> Unit = {}): OfflineBackupDestination.Published =
        withContext(Dispatchers.IO) {
            destination.preflight()
            OfflineBackupKeyManager(context).withCurrentGeneration { generation ->
                prepareHeld(generation, consent, trigger, snapshotProgress, destination).use { source ->
                    destination.publish(source, generation, destinationProgress)
                }
            }
        }

    private suspend fun prepareHeld(generation: OfflineBackupKeyEnvelope.Generation,
        consent: OfflineBackupCredentialSection.Consent, trigger: OfflineBackupCredentialSection.Trigger,
        progress: (OfflineBackupSnapshot.Progress) -> Unit, destination: OfflineBackupDestination? = null): Prepared {
        var pending: Prepared? = null; var handedOver = false
        try {
            val result = OfflineBackupSnapshot.capture(context, database, credentials =
                OfflineBackupSnapshot.CredentialCapture(generation, consent, trigger), progress = progress).use { snapshot ->
                val parent = File(context.filesDir, "offline-backup-staging")
                val resources = OfflineBackupResourceBudget.checkPrepared(parent, snapshot)
                destination?.preflight(resources.targetArchiveBytes)
                val file = File(parent, "encrypted-${UUID.randomUUID()}.immopilot")
                check(file.createNewFile()) { "Ein eindeutiger verschlüsselter Arbeitsplatz konnte nicht angelegt werden." }
                val prepared = Prepared(file, snapshot.summary, resources, snapshot.archiveInfo)
                pending = prepared
                FileOutputStream(file).use { output ->
                    val unclosed = object : FilterOutputStream(output) {
                        override fun write(bytes: ByteArray, offset: Int, length: Int) = out.write(bytes, offset, length)
                        override fun close() = flush()
                    }
                    OfflineBackupArchiveCrypto.encrypt(generation, unclosed) { snapshot.writeArchive(it) }
                    currentCoroutineContext().ensureActive(); output.fd.sync()
                }
                OfflineBackupArchiveValidator.verifyPrepared(context, file, generation, snapshot.summary)
                prepared
            }
            handedOver = true
            return result
        } finally { if (!handedOver) pending?.close() }
    }

}
