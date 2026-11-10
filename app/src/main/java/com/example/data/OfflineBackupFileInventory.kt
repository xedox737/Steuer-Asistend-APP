package com.example.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

/** Available business files, including retained versions without a current row reference. */
internal class OfflineBackupFileInventory(context: Context, private val maximumFiles: Int = MAX_FILES) {
    init { require(maximumFiles in 1..MAX_FILES) }
    enum class Kind { ORIGINAL, SAVED_EXPORT }
    data class Entry(val source: File, val portablePath: String, val kind: Kind)

    private val scanPrefix = checkedAccess("files") { File(context.filesDir, "receipt_page_") }
    private val files = checkedAccess("files") { scanPrefix.parentFile!!.canonicalFile }
    private val cache = checkedAccess("cache") { context.cacheDir.canonicalFile }
    private val originalRoots = listOf("managed_documents", "property-images", "offline-originals",
        "restored_receipts", "receipt_documents")
    private val exportRoots = listOf("exports", "reports")
    private val rootExports = listOf(
        Regex("EXTF_Buchungsstapel_.*\\.csv"), Regex("DATEV_Export_.*\\.zip"),
        Regex("Steuerbericht_(Finanzamt|Steuerberater)_.*\\.pdf"),
        Regex("document\\.xml"), Regex("datev_export\\.pdf")
    )

    fun available(): List<Entry> = checkedAccess("Dateibestand") {
        val result = mutableListOf<Entry>()
        fun add(file: File, key: String, kind: Kind) {
            if (!file.isFile || !file.canRead()) invalid(key, "ist nicht lesbar")
            if (result.size >= maximumFiles) throw OfflineBackupValidationException("Zu viele Dateien im Sicherungsbestand (maximal $maximumFiles).")
            result += Entry(file, key, kind)
        }
        fun visit(directory: File, key: String, kind: Kind) {
            checked(directory, key)
            if (!directory.isDirectory) invalid(key, "ist kein Dateiverzeichnis")
            children(directory, key).forEach { child ->
                val childKey = "$key/${child.name}"
                checked(child, childKey)
                if (kind == Kind.ORIGINAL && staging(key, child.name)) return@forEach
                if (child.isDirectory) visit(child, childKey, kind) else add(child, childKey, kind)
            }
        }
        originalRoots.forEach { name ->
            val root = File(files, name)
            if (root.exists()) visit(root, "files/$name", Kind.ORIGINAL)
        }
        // Older scan pages live directly below filesDir.
        children(files, "files").filter { it.name.startsWith("receipt_page_") }.forEach { file ->
            checked(file, "files/${file.name}")
            add(file, "files/${file.name}", Kind.ORIGINAL)
        }
        exportRoots.forEach { name ->
            val root = File(cache, name)
            if (root.exists()) visit(root, "cache/$name", Kind.SAVED_EXPORT)
        }
        children(cache, "cache").filter { file -> Regex("temp_receipt_.*\\.jpg").matches(file.name) }.forEach { file ->
            checked(file, "cache/${file.name}")
            add(file, "cache/${file.name}", Kind.ORIGINAL)
        }
        children(cache, "cache").filter { file -> rootExports.any { it.matches(file.name) } }.forEach { file ->
            checked(file, "cache/${file.name}")
            add(file, "cache/${file.name}", Kind.SAVED_EXPORT)
        }
        result.sortedBy { it.portablePath }
    }

    /** CameraX callback publication is still pending; camera sources deliberately return false. */
    fun isCoordinated(path: String): Boolean {
        return checkedAccess("Originalverweis") {
            val uri = Uri.parse(path)
            val candidate = when (uri.scheme) {
                null -> File(path)
                "file" -> if (uri.authority.isNullOrEmpty() && uri.query == null && uri.fragment == null && !uri.path.isNullOrBlank()) File(uri.path!!) else return@checkedAccess false
                else -> return@checkedAccess false
            }
            val absolute = candidate.absoluteFile
            val canonical = candidate.canonicalFile
            if (absolute != canonical) return@checkedAccess false
            val filesRelative = relative(canonical, files)
            if (filesRelative != null) {
                val root = filesRelative.substringBefore('/')
                return@checkedAccess root in originalRoots || ('/' !in filesRelative && root.startsWith("receipt_page_"))
            }
            val cacheRelative = relative(canonical, cache) ?: return@checkedAccess false
            return@checkedAccess cacheRelative.substringBefore('/') in exportRoots ||
                ('/' !in cacheRelative && rootExports.any { it.matches(cacheRelative) })
        }
    }

    private fun relative(file: File, root: File): String? =
        file.path.takeIf { it.startsWith(root.path + File.separator) }?.removePrefix(root.path + File.separator)

    private fun checked(file: File, key: String) {
        if (file.canonicalFile != file.absoluteFile || '\\' in file.name || file.name in setOf(".", "..")) {
            invalid(key, "ist kein sicher zuordenbarer lokaler Dateipfad")
        }
    }

    private fun children(directory: File, key: String): List<File> =
        directory.listFiles()?.sortedBy { it.name } ?: invalid(key, "konnte nicht vollständig gelesen werden")

    private fun staging(directoryKey: String, filename: String): Boolean = when (directoryKey) {
        "files/managed_documents" -> filename.endsWith(".tmp") &&
            (filename.startsWith("document-download-") || filename.startsWith("receipt-import-"))
        "files/property-images" -> filename.startsWith("restore-") && filename.endsWith(".tmp")
        "files/offline-originals" -> filename.startsWith("original-") && filename.endsWith(".tmp")
        else -> false
    }

    private inline fun <T> checkedAccess(key: String, action: () -> T): T = try {
        action()
    } catch (_: IOException) {
        invalid(key, "konnte nicht vollständig geprüft werden")
    } catch (_: SecurityException) {
        invalid(key, "ist nicht zugänglich")
    }

    private fun invalid(key: String, detail: String): Nothing =
        throw OfflineBackupValidationException("Sicherungsdatei „$key“ $detail. Bitte Datei und Zugriff prüfen.")

    private companion object { const val MAX_FILES = 10_000 }
}
