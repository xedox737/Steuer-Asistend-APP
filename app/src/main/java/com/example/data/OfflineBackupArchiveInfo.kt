package com.example.data

import com.example.BuildConfig
import java.time.DateTimeException
import java.time.Instant

/** Authenticated descriptive metadata; never grants ownership or deletion rights. */
internal data class OfflineBackupArchiveInfo(val createdAt: String, val appVersionCode: Long, val appVersionName: String) {
    init {
        require(createdAt.length in 1..64 && appVersionCode in 0..Int.MAX_VALUE && appVersionName.isNotBlank() &&
            appVersionName.length <= 128 && appVersionName.none(Char::isISOControl))
        val canonical = try { Instant.parse(createdAt).toString() } catch (_: DateTimeException) { null }
        require(canonical == createdAt) { "Ungültige Archivzeit." }
    }
    companion object {
        fun create() = OfflineBackupArchiveInfo(Instant.now().toString(), BuildConfig.VERSION_CODE.toLong(), BuildConfig.VERSION_NAME)
    }
}
