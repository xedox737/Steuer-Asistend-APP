package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingApprovalJsonTest {
    @Test
    fun allocationsRoundTripWithoutLosingCentValues() {
        val source = listOf(
            PersistedAllocation("a", "Dach", 60.0, 6_000),
            PersistedAllocation("b", "Wohnung", 40.0, 4_000)
        )

        assertEquals(source, AccountingApprovalJson.decodeAllocations(
            AccountingApprovalJson.encodeAllocations(source)
        ))
    }

    @Test
    fun bookingProposalsRoundTripWithoutInventingAccounts() {
        val source = listOf(
            PersistedBookingProposal("p1", "4801", "70000", 10_000, "9")
        )

        assertEquals(source, AccountingApprovalJson.decodeBookingProposals(
            AccountingApprovalJson.encodeBookingProposals(source)
        ))
    }

    @Test
    fun malformedLegacyValuesAreNotTreatedAsConfirmedData() {
        assertTrue(AccountingApprovalJson.decodeAllocations("not-json").isEmpty())
        assertTrue(AccountingApprovalJson.decodeBookingProposals("{broken").isEmpty())
    }
}
