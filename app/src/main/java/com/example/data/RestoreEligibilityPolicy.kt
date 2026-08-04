package com.example.data

/**
 * Pure restore eligibility policy shared by snapshot validation and tests.
 * A deletion marker in either the index or its tombstone always wins.
 */
object RestoreEligibilityPolicy {
    fun shouldRestore(indexSyncStatus: String?, tombstoneStatus: String?): Boolean {
        return indexSyncStatus != "DELETED" && tombstoneStatus != "DELETED"
    }
}
