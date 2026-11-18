package com.example.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
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
            val history = OfflineBackupHistory(context)
            val operationId = UUID.randomUUID().toString()
            val origin = if (trigger == OfflineBackupCredentialSection.Trigger.MANUAL) OfflineBackupHistory.Origin.MANUAL
                else OfflineBackupHistory.Origin.AUTOMATIC
            fun event(outcome: OfflineBackupHistory.Outcome, published: OfflineBackupDestination.Published? = null,
                failure: OfflineBackupHistory.Failure? = null): OfflineBackupHistory.Event = OfflineBackupHistory.Event(
                operationId, OfflineBackupHistory.Kind.FULL, outcome, System.currentTimeMillis(), published?.bytes, failure, origin,
                published?.backupId, published?.uri?.toString(), published?.summary?.files?.toLong(),
                published?.summary?.rowCounts?.values?.fold(0L) { total, count -> Math.addExact(total, count) })
            history.record(event(OfflineBackupHistory.Outcome.STARTED))
            var published: OfflineBackupDestination.Published? = null
            try {
                destination.preflight()
                OfflineBackupKeyManager(context).withCurrentGeneration { generation ->
                    prepareHeld(generation, consent, trigger, snapshotProgress, destination).use { source ->
                        destination.publish(source, generation, destinationProgress).also { published = it }
                    }
                }
            } catch (error: Throwable) {
                val confirmed = published
                try { withContext(NonCancellable) {
                    history.record(if (confirmed != null) event(OfflineBackupHistory.Outcome.VERIFIED, confirmed)
                    else if (error is CancellationException) event(OfflineBackupHistory.Outcome.CANCELLED)
                    else event(OfflineBackupHistory.Outcome.FAILED, failure = when (error) {
                        is SecurityException -> OfflineBackupHistory.Failure.UNAVAILABLE
                        is java.io.IOException -> OfflineBackupHistory.Failure.STORAGE
                        is OfflineBackupValidationException -> OfflineBackupHistory.Failure.VALIDATION
                        else -> OfflineBackupHistory.Failure.UNKNOWN
                    }))
                } } catch (historyError: Exception) { error.addSuppressed(historyError) }
                if (confirmed != null && error !is CancellationException)
                    throw OfflineBackupValidationException("Die Sicherung wurde geprüft gespeichert; private Arbeitsdaten konnten nicht vollständig entfernt werden.")
                throw error
            }
            val confirmed = requireNotNull(published)
            try { history.record(event(OfflineBackupHistory.Outcome.VERIFIED, confirmed)) }
            catch (_: Exception) { throw OfflineBackupValidationException("Die Sicherung wurde geprüft gespeichert, aber der lokale Verlauf konnte nicht gespeichert werden.") }
            confirmed
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
