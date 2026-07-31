package com.example.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreEligibilityPolicyTest {
    @Test
    fun activeReceiptWithoutTombstoneIsRestored() {
        assertTrue(RestoreEligibilityPolicy.shouldRestore("SYNCED", null))
    }

    @Test
    fun deletedIndexEntryIsNeverRestored() {
        assertFalse(RestoreEligibilityPolicy.shouldRestore("DELETED", null))
    }

    @Test
    fun tombstonePreventsRestoreEvenWhenIndexStillSaysSynced() {
        assertFalse(RestoreEligibilityPolicy.shouldRestore("SYNCED", "DELETED"))
    }
}
