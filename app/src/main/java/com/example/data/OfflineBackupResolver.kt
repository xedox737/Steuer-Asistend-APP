package com.example.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Reads existing local references only; never grants access, downloads or changes originals. */
internal class OfflineBackupResolver(
    private val context: Context,
    private val maximumBytes: Long = MAX_ORIGINAL_BYTES
) {
    init {
        require(maximumBytes in 1L..MAX_ORIGINAL_BYTES) { "Die Originalgrößengrenze darf nur verringert werden." }
    }
    private class Unavailable(val detail: String, val mayTryCopy: Boolean = true) : Exception()
    private data class Fingerprint(val bytes: Long, val sha256: String)
    private val limitLabel: String get() = if (maximumBytes == MAX_ORIGINAL_BYTES) "1 GiB" else "$maximumBytes Byte"

    suspend fun copy(
        path: String,
        destination: File,
        owner: String,
        expectedSha256: String?,
        expectedSize: Long?,
        verifiedCandidates: List<String>
    ): String {
        fun invalid(detail: String): Nothing = throw OfflineBackupValidationException("$owner: $detail")
        if (expectedSha256 != null && !Regex("[0-9a-f]{64}").matches(expectedSha256)) {
            invalid("Die gespeicherte Original-Prüfsumme ist ungültig.")
        }
        check(!destination.exists()) { "Der temporäre Originalsicherungsplatz ist bereits belegt." }
        var complete = false
        var firstFailure: String? = null
        var mismatchingCopy = false
        val locations = (listOf(path) + if (expectedSha256 != null) verifiedCandidates else emptyList()).distinct()
        try {
            for ((index, location) in locations.withIndex()) {
                currentCoroutineContext().ensureActive()
                val input = try { open(location) } catch (error: Unavailable) {
                    if (!error.mayTryCopy) invalid(error.detail)
                    if (firstFailure == null) firstFailure = error.detail
                    continue
                }
                val copied = input.use { stream ->
                    FileOutputStream(destination).use { output ->
                        fingerprint(stream, output).also { output.fd.sync() }
                    }
                }
                if (copied.bytes == 0L) invalid("Originaldatei ist leer (0 Byte).")
                if (expectedSha256 != null && copied.sha256 != expectedSha256) {
                    if (index == 0) invalid("Originaldatei stimmt nicht mit ihrer gespeicherten Prüfsumme überein.")
                    mismatchingCopy = true
                    check(destination.delete()) { "Die verworfene Originalkopie konnte nicht entfernt werden." }
                    continue
                }
                if (expectedSize != null && copied.bytes != expectedSize) {
                    invalid("Originaldatei stimmt nicht mit ihrer gespeicherten Größe überein.")
                }
                val reread = try { open(location).use { fingerprint(it, null) } } catch (error: Unavailable) {
                    invalid("Originaldatei konnte nicht erneut geprüft werden. ${error.detail}")
                }
                if (copied != reread) invalid("Originaldatei wurde während der Sicherung geändert. Bitte erneut versuchen.")
                complete = true
                return copied.sha256
            }
            invalid(firstFailure.orEmpty().ifBlank { "Originaldatei fehlt lokal." } +
                if (mismatchingCopy) " Vorhandene Kopien stimmen nicht mit der gespeicherten Prüfsumme überein." else " Bitte zuerst die Originale wiederherstellen.")
        } catch (error: Unavailable) {
            invalid(error.detail)
        } catch (error: SecurityException) {
            invalid("Zugriff auf die Originaldatei wurde verweigert.")
        } catch (error: IOException) {
            invalid("Originaldatei konnte nicht vollständig gelesen oder zwischengespeichert werden. Bitte Datei, Zugriff und Speicher prüfen.")
        } finally {
            if (!complete) destination.delete()
        }
    }

    private fun open(location: String): InputStream {
        val uri = Uri.parse(location)
        if (uri.scheme == "content") {
            return try {
                context.contentResolver.openInputStream(uri)
                    ?: throw Unavailable("Originaldatei ist nicht lesbar.")
            } catch (error: SecurityException) {
                throw Unavailable("Zugriff auf die Originaldatei wurde verweigert.")
            } catch (error: IOException) {
                throw Unavailable("Originaldatei ist nicht lesbar oder nicht mehr verfügbar.")
            }
        }
        val source = when (uri.scheme) {
            null -> File(location)
            "file" -> {
                if (!uri.authority.isNullOrEmpty() || uri.query != null || uri.fragment != null || uri.path.isNullOrBlank()) {
                    throw Unavailable("Der lokale Originalverweis ist ungültig.")
                }
                File(requireNotNull(uri.path))
            }
            else -> throw Unavailable("Der Originalverweis ist nicht lokal lesbar. Bitte das Original zuerst lokal herunterladen.")
        }
        try {
            if (!source.exists()) throw Unavailable("Originaldatei fehlt lokal.")
            if (!source.isFile || !source.canRead()) throw Unavailable("Originaldatei ist nicht lesbar. Bitte den Zugriff prüfen.")
            if (source.length() > maximumBytes) throw Unavailable("Originaldatei überschreitet die Grenze von $limitLabel.", false)
            return source.inputStream()
        } catch (error: SecurityException) {
            throw Unavailable("Zugriff auf die Originaldatei wurde verweigert.")
        } catch (error: IOException) {
            throw Unavailable("Originaldatei ist nicht lesbar oder nicht mehr verfügbar.")
        }
    }

    private suspend fun fingerprint(input: InputStream, output: OutputStream?): Fingerprint {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var bytes = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            var count = input.read(buffer)
            if (count < 0) break
            if (count == 0) {
                val single = input.read()
                if (single < 0) break
                buffer[0] = single.toByte()
                count = 1
            }
            bytes += count
            if (bytes > maximumBytes) throw Unavailable("Originaldatei überschreitet die Grenze von $limitLabel.", false)
            digest.update(buffer, 0, count)
            output?.write(buffer, 0, count)
        }
        return Fingerprint(bytes, digest.digest().joinToString("") { "%02x".format(it) })
    }

    private companion object {
        const val MAX_ORIGINAL_BYTES = 1024L * 1024 * 1024
    }
}
