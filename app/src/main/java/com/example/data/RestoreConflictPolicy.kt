package com.example.data

import java.time.Instant

/** Unknown/equal versions are never evidence that a backup supersedes local work. */
internal object RestoreConflictPolicy {
    fun backupIsNewer(localUpdatedAt: String, backupUpdatedAt: String): Boolean {
        val local = runCatching { Instant.parse(localUpdatedAt) }.getOrNull() ?: return false
        val backup = runCatching { Instant.parse(backupUpdatedAt) }.getOrNull() ?: return false
        return backup.isAfter(local)
    }

    fun shouldImport(
        mode: RestoreMode,
        localExists: Boolean,
        localUpdatedAt: String = "",
        backupUpdatedAt: String = ""
    ): Boolean = mode != RestoreMode.MERGE || !localExists || backupIsNewer(localUpdatedAt, backupUpdatedAt)
}
