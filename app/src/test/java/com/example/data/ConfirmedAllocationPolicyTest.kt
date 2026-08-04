package com.example.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmedAllocationPolicyTest {
    @Test
    fun confirmedAllocationsMustEqualReceiptAmount() {
        val result = ConfirmedAllocationPolicy.validate(
            10_000,
            listOf(
                allocation("a", 60.0, 6_000),
                allocation("b", 40.0, 4_000)
            )
        )
        assertTrue(result.valid)
    }

    @Test
    fun oneCentRoundingDifferenceIsAccepted() {
        val result = ConfirmedAllocationPolicy.validate(
            10_000,
            listOf(
                allocation("a", 33.33, 3_333),
                allocation("b", 66.67, 6_666)
            )
        )
        assertTrue(result.valid)
    }

    @Test
    fun materialDifferenceIsRejected() {
        val result = ConfirmedAllocationPolicy.validate(
            10_000,
            listOf(
                allocation("a", 50.0, 4_000),
                allocation("b", 50.0, 4_000)
            )
        )
        assertFalse(result.valid)
    }

    @Test
    fun creditAllocationsSupportNegativeAmounts() {
        val result = ConfirmedAllocationPolicy.validate(
            -5_000,
            listOf(
                allocation("a", 80.0, -4_000),
                allocation("b", 20.0, -1_000)
            )
        )
        assertTrue(result.valid)
    }

    @Test
    fun oppositeSignsAndDuplicateIdsAreRejected() {
        val result = ConfirmedAllocationPolicy.validate(
            5_000,
            listOf(
                allocation("same", 50.0, 2_500),
                allocation("same", 50.0, -2_500)
            )
        )
        assertFalse(result.valid)
    }

    private fun allocation(
        id: String,
        percent: Double,
        amountCent: Long
    ) = PersistedAllocation(
        id = id,
        description = id,
        percent = percent,
        amountCent = amountCent
    )
}
