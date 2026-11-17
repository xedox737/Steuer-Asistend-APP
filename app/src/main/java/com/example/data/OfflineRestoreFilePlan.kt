package com.example.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.UUID

/** Read-only destination plan; it creates, changes and deletes no files. */
internal object OfflineRestoreFilePlan {
    data class Item(val version: OfflineBackupManifest.Version, val payload: OfflineBackupManifest.Reference, val destination: File)
    data class Plan(val operationId: String, val items: List<Item>, val aliases: Map<String, File>, val ownedDirectories: Set<File>)

    fun prepare(context: Context, operationId: String, manifest: OfflineBackupManifest.Checked): Plan {
        try {
            require(UUID.fromString(operationId).toString() == operationId)
            val payloads = manifest.payloads.associateBy { it.entry }
            require(payloads.size == manifest.payloads.size && manifest.versions.size <= 10_000)
            require(manifest.versions.map { it.id }.toSet().size == manifest.versions.size)
            val files = context.filesDir.canonicalFile; val cache = context.cacheDir.canonicalFile
            require(files.isDirectory && cache.isDirectory)
            val roots = mapOf(
                OfflineBackupFileInventory.Kind.ORIGINAL to File(files, "offline-originals/offline-restore-$operationId"),
                OfflineBackupFileInventory.Kind.SAVED_EXPORT to File(cache, "exports/offline-restore-$operationId")
            )
            val directories = manifest.versions.map { roots.getValue(it.kind) }.toSet()
            directories.forEach { require(it.absoluteFile == it.canonicalFile && !it.exists()) }
            val items = manifest.versions.mapIndexed { index, version ->
                val payload = requireNotNull(payloads[version.entry])
                require(payload.size >= 0 && payload.sha256.matches(Regex("[0-9a-f]{64}")))
                val path = version.portablePath
                if (path != null) require(version.id == path && OfflineBackupManifest.portableKind(path) == version.kind)
                else require(version.kind == OfflineBackupFileInventory.Kind.ORIGINAL && version.id.startsWith("external/"))
                val name = path?.substringAfterLast('/') ?: "original"
                val destination = File(roots.getValue(version.kind), "file-$index/$name").absoluteFile
                require(destination == destination.canonicalFile && !destination.exists())
                Item(version, payload, destination)
            }
            require(items.map { it.destination }.toSet().size == items.size)
            val originalByEntry = items.filter { it.version.kind == OfflineBackupFileInventory.Kind.ORIGINAL }
                .groupBy { it.version.entry }
            val aliases = manifest.aliases.mapValues { (_, entry) -> requireNotNull(originalByEntry[entry]?.firstOrNull()).destination }
            return Plan(operationId, items, aliases, directories)
        } catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: NoSuchElementException) { invalid() }
        catch (_: SecurityException) { invalid() }
    }
    private fun invalid(): Nothing = throw OfflineBackupValidationException(
        "Die Wiederherstellungsdateien können nicht eindeutig und sicher zugeordnet werden. Der Bestand wurde nicht verändert."
    )
}
