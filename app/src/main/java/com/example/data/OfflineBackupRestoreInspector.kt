package com.example.data

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import java.security.GeneralSecurityException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Read-only portable restore preparation. Never installs keys or touches live Room/preferences. */
internal object OfflineBackupRestoreInspector {
    data class Preview(val backupId: String, val archiveInfo: OfflineBackupArchiveInfo,
        val rowCounts: Map<String, Long>, val fileVersions: Int, val originalBytes: Long,
        val credentialProviders: Set<OfflineBackupCredentialSection.Provider>)

    class Prepared internal constructor(private val resources: Resources, val preview: Preview) : AutoCloseable {
        internal val probe get() = checkNotNull(resources.probe)
        internal val contents get() = checkNotNull(resources.contents)
        internal val generation get() = checkNotNull(resources.generation)
        override fun close() = resources.close()
    }
    internal class Resources : AutoCloseable {
        var directory: File? = null
        var generation: OfflineBackupKeyEnvelope.Generation? = null
        var contents: OfflineBackupArchiveValidator.StagedContents? = null
        var probe: OfflineBackupRoomProbe.Prepared? = null
        private val closed = AtomicBoolean()
        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            try { probe?.close() } finally {
                try { contents?.close() } finally {
                    try { generation?.close() } finally {
                        directory?.let { if (it.exists() && !it.deleteRecursively())
                            invalid("Die privaten Wiederherstellungsdaten konnten nicht entfernt werden.") }
                    }
                }
            }
        }
    }

    /** Opens the provider only inside IO; consumes password even on pre-dispatch cancellation. */
    suspend fun inspect(context: Context, password: CharArray, openInput: () -> InputStream): Prepared {
        val resources = Resources()
        var delivered = false
        try {
            val prepared = withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                val parent = File(context.filesDir, "offline-backup-staging")
                if (!parent.isDirectory && !parent.mkdirs()) invalid("Der private Prüfbereich ist nicht verfügbar.")
                val reserve = OfflineBackupResourceBudget.RESERVE_BYTES
                val available = parent.usableSpace
                if (available <= reserve + 3) invalid("Zu wenig privater Speicher: benötigt mindestens ${reserve + 3} Byte, verfügbar $available Byte. Es wurden keine Bestandsdaten verändert.")
                // Keep space for extracted entries and further validation. Probe checks its
                // actual metadata/index budget separately before creating its private DB.
                val maximumPlaintext = minOf(2L * 1024 * 1024 * 1024, (available - reserve) / 3)
                val directory = File(parent, "restore-inspect-${UUID.randomUUID()}")
                if (!directory.mkdir()) invalid("Der private Prüfbereich konnte nicht angelegt werden.")
                resources.directory = directory
                val plaintext = File(directory, "archive.zip")
                val header = openInput().use { input ->
                    val parsed = OfflineBackupArchiveHeader.read(input)
                    val generation = OfflineBackupKeyEnvelope.recover(parsed.recovery, password)
                    resources.generation = generation
                    // Replay only the bounded header; the provider stream remains owned by use.
                    val borrowed = object : FilterInputStream(input) { override fun close() = Unit }
                    val complete = SequenceInputStream(ByteArrayInputStream(parsed.authenticatedBytes), borrowed)
                    OfflineBackupArchiveCrypto.decryptToPrivateStaging(complete, generation, plaintext, maximumPlaintext)
                    parsed
                }
                val contents = OfflineBackupArchiveValidator.stageZip(context, plaintext)
                resources.contents = contents
                val probe = OfflineBackupRoomProbe.prepare(context, contents, resources.generation)
                resources.probe = probe
                if (!plaintext.delete()) invalid("Die private Zwischenkopie konnte nicht entfernt werden.")
                val manifest = probe.manifest
                currentCoroutineContext().ensureActive()
                Prepared(resources, Preview(header.dataKey.archiveId, manifest.archiveInfo,
                    manifest.rowCounts.toMap(), manifest.versions.size, manifest.payloads.sumOf { it.size },
                    manifest.credentialProviders.toSet()))
            }
            delivered = true
            return prepared
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: GeneralSecurityException) { invalid("Passwort falsch oder Sicherung beschädigt. Es wurden keine Daten übernommen.") }
        catch (_: IOException) { invalid("Die Sicherung konnte nicht vollständig gelesen und geprüft werden. Es wurden keine Daten übernommen.") }
        catch (_: IllegalArgumentException) { invalid("Passwort oder Sicherungsheader ist ungültig. Es wurden keine Daten übernommen.") }
        catch (_: SecurityException) { invalid("Der Zugriff auf die Sicherung wurde verweigert. Es wurden keine Daten übernommen.") }
        finally {
            password.fill('\u0000')
            if (!delivered) resources.close()
        }
    }
    private fun invalid(message: String): Nothing = throw OfflineBackupValidationException(message)
}
