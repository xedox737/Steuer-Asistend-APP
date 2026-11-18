package com.example.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import org.json.JSONObject

/** Conservative private-space estimates; never deletes data or substitutes a partial archive. */
internal object OfflineBackupResourceBudget {
    private const val MIB = 1024L * 1024
    private const val MAX_PAYLOAD = 2 * 1024 * MIB - 64 * MIB
    private const val MAX_FILE = 1024 * MIB
    private const val MAX_METADATA = 64 * MIB
    const val RESERVE_BYTES = 16 * MIB
    data class Estimate(val privateBytes: Long, val targetArchiveBytes: Long, val unknownOriginals: Int)

    /** ZIP/cipher expansion plus concurrent reread, extracted entries and SQLite/index/check space. */
    fun estimate(payloadBytes: Long, metadataBytes: Long, payloads: Int, pendingSnapshotBytes: Long = 0,
        unknownOriginals: Int = 0): Estimate {
        require(payloadBytes in 0..MAX_PAYLOAD && metadataBytes in 1..MAX_METADATA && payloads in 0..10_000 &&
            pendingSnapshotBytes in 0..(2 * 1024 * MIB) && unknownOriginals in 0..10_000)
        val raw = payloadBytes + metadataBytes
        val zip = raw + raw / 100 + (payloads + 3L) * 512 + 4 * MIB
        val encrypted = zip + zip / 100 + 64 * 1024
        val additional = pendingSnapshotBytes + encrypted + zip + raw + metadataBytes * 9 + RESERVE_BYTES
        return Estimate(additional, encrypted, unknownOriginals)
    }

    fun requireAvailable(estimate: Estimate, availableBytes: Long) {
        if (availableBytes < estimate.privateBytes) {
            val unknown = if (estimate.unknownOriginals == 0) "" else
                " ${estimate.unknownOriginals} externe Originalgrößen sind unbekannt und wurden konservativ abgeschätzt."
            throw OfflineBackupValidationException("Zu wenig privater Speicher: benötigt ${estimate.privateBytes} Byte einschließlich ${RESERVE_BYTES} Byte Reserve, verfügbar $availableBytes Byte.$unknown Es wurden keine Bestandsdaten verändert.")
        }
    }

    fun estimateCapture(metadata: OfflineBackupMetadataWriter.Draft, available: List<OfflineBackupFileInventory.Entry>,
        credentialBytes: Long = 0): Estimate {
        require(metadata.bytes in 1..MAX_METADATA && credentialBytes in 0..(128 * 1024L) && available.size <= 10_000)
        val owned = available.map { it.source.canonicalPath }.toSet()
        var payloadBytes = 0L; var unknown = 0; var external = 0
        fun add(bytes: Long) {
            if (bytes !in 0..MAX_FILE) throw OfflineBackupValidationException("Eine Originaldatei überschreitet die unterstützte Größe von $MAX_FILE Byte.")
            payloadBytes = minOf(MAX_PAYLOAD, payloadBytes + bytes)
        }
        available.forEach { add(it.source.length()) }
        metadata.references.forEach { reference ->
            val local = localFile(reference.path)
            if (local?.canonicalPath !in owned) {
                external++
                val bytes = reference.expectedSize ?: local?.takeIf { it.isFile && it.canRead() }?.length()
                    ?: reference.verifiedCandidates.asSequence().mapNotNull(::localFile)
                        .firstOrNull { it.isFile && it.canRead() }?.length()
                if (bytes == null) { unknown++; add(MAX_FILE) } else add(bytes)
            }
        }
        val count = minOf(10_000, available.size + external)
        // Version IDs repeat portable paths; quote sizes include escaped control/Unicode characters.
        val paths = available.sumOf { entry ->
            val path = entry.portablePath
            val quoted = JSONObject.quote(path).toByteArray(Charsets.UTF_8).size.toLong() +
                path.count { it in "<>&='\u2028\u2029" } * 5L
            quoted * 2 + 256
        }
        val manifest = 4096L + paths + metadata.references.size * 384L + count * 192L
        if (metadata.bytes + credentialBytes >= MAX_METADATA)
            throw OfflineBackupValidationException("Metadaten und Credential-Abschnitt lassen keinen Platz für das vollständige Manifest.")
        val manifestBytes = minOf(manifest, MAX_METADATA - metadata.bytes - credentialBytes)
        return estimate(payloadBytes, metadata.bytes + credentialBytes + manifestBytes, count,
            pendingSnapshotBytes = payloadBytes + manifestBytes, unknownOriginals = unknown)
    }

    fun checkCapture(directory: File, metadata: OfflineBackupMetadataWriter.Draft,
        available: List<OfflineBackupFileInventory.Entry>, credentialBytes: Long) {
        requireAvailable(estimateCapture(metadata, available, credentialBytes), directory.usableSpace)
    }

    fun checkPrepared(directory: File, snapshot: OfflineBackupSnapshot): Estimate {
        val estimate = estimate(snapshot.summary.bytes - snapshot.metadataBytes, snapshot.metadataBytes, snapshot.summary.payloads)
        requireAvailable(estimate, directory.usableSpace)
        return estimate
    }

    /** Remaining space after the checked archive and rollback copies already exist. */
    internal fun estimateRestore(metadataBytes: Long, rollbackDatabaseBytes: Long,
        importedFileBytes: List<Long>, rollbackFileBytes: List<Long>): Long {
        try {
            require(metadataBytes in 1..MAX_METADATA && rollbackDatabaseBytes > 0)
            require(importedFileBytes.size <= 10_000 && rollbackFileBytes.size <= 10_000)
            fun total(values: List<Long>): Long = values.fold(0L) { sum, value ->
                require(value >= 0); Math.addExact(sum, value)
            }
            // Include concurrent imported files/staging, rollback file copies, SQLite
            // verification/workspace and fresh probe/live transaction headroom.
            var needed = Math.addExact(RESERVE_BYTES, 80 * MIB)
            needed = Math.addExact(needed, Math.multiplyExact(metadataBytes, 16))
            needed = Math.addExact(needed, Math.multiplyExact(rollbackDatabaseBytes, 4))
            needed = Math.addExact(needed, Math.multiplyExact(total(importedFileBytes), 2))
            return Math.addExact(needed, total(rollbackFileBytes))
        } catch (_: ArithmeticException) {
            throw OfflineBackupValidationException("Der Speicherbedarf der Wiederherstellung ist zu groß. Es wurden keine Bestandsdaten verändert.")
        } catch (_: IllegalArgumentException) {
            throw OfflineBackupValidationException("Der Speicherbedarf der Wiederherstellung ist ungültig. Es wurden keine Bestandsdaten verändert.")
        }
    }

    /** Conservative per-volume check; archive and rollback already occupy the measured free space. */
    internal fun checkRestore(context: Context, archive: OfflineBackupRestoreInspector.Prepared,
        rollback: OfflineRestoreRollbackBundle.Prepared, plan: OfflineRestoreFilePlan.Plan): Long {
        val needed = estimateRestore(archive.contents.parts.getValue("metadata.json").bytes,
            rollback.database.size, plan.items.map { it.payload.size }, rollback.ownedFiles.versions.map { it.bytes })
        // Charging the whole estimate to each directory also handles distinct filesystems
        // conservatively, without assuming that Android's private directories share a mount.
        val directories = listOf(context.filesDir, context.cacheDir, context.noBackupFilesDir,
            requireNotNull(context.getDatabasePath("receipt_database").parentFile)).distinct()
        directories.forEach { directory ->
            if (!directory.isDirectory) throw OfflineBackupValidationException(
                "Der private Speicher für die Wiederherstellung ist nicht verfügbar. Es wurden keine Bestandsdaten verändert.")
            requireRestoreAvailable(needed, directory.usableSpace)
        }
        return needed
    }

    internal fun requireRestoreAvailable(needed: Long, available: Long) {
        require(needed >= RESERVE_BYTES && available >= 0)
        if (available < needed) throw OfflineBackupValidationException(
            "Zu wenig privater Speicher für die Wiederherstellung: benötigt $needed Byte einschließlich $RESERVE_BYTES Byte Reserve, verfügbar $available Byte. Es wurden keine Bestandsdaten verändert.")
    }

    private fun localFile(path: String): File? = try {
        val uri = Uri.parse(path)
        when (uri.scheme) {
            null -> File(path)
            "file" -> if (uri.authority.isNullOrEmpty() && uri.query == null && uri.fragment == null) uri.path?.let(::File) else null
            else -> null
        }
    } catch (_: IOException) { null } catch (_: SecurityException) { null }
}
