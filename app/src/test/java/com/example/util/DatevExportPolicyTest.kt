package com.example.util

import com.example.data.BankTransactionClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatevExportPolicyTest {
    @Test
    fun refusesReceiptWithoutConfirmedAllocations() {
        val plan = DatevExportPolicy.plan(listOf(receipt(allocations = emptyList())))

        assertFalse(plan.exportable)
        assertTrue(plan.issues.any { it.code == "MISSING_CONFIRMED_ALLOCATION" })
    }

    @Test
    fun refusesUnconfirmedAndUnbalancedAllocations() {
        val plan = DatevExportPolicy.plan(
            listOf(
                receipt(
                    allocations = listOf(
                        allocation("a", 6_000, confirmed = true),
                        allocation("b", 3_000, confirmed = false)
                    )
                )
            )
        )

        assertFalse(plan.exportable)
        assertTrue(plan.issues.any { it.code == "UNCONFIRMED_ALLOCATION" })
        assertTrue(plan.issues.any { it.code == "ALLOCATION_TOTAL_MISMATCH" })
    }

    @Test
    fun createsOneAttachmentForSeveralBookingLines() {
        val plan = DatevExportPolicy.plan(
            listOf(
                receipt(
                    allocations = listOf(
                        allocation("a", 6_000),
                        allocation("b", 4_000)
                    )
                )
            )
        )

        assertTrue(plan.exportable)
        assertEquals(2, plan.bookingLines.size)
        assertEquals(1, plan.attachments.size)
        assertEquals(plan.bookingLines[0].receiptGuid, plan.bookingLines[1].receiptGuid)
    }

    @Test
    fun duplicateInternalIdBlocksExportInsteadOfDuplicatingAttachment() {
        val first = receipt()
        val plan = DatevExportPolicy.plan(listOf(first, first.copy(attachmentReference = "other.pdf")))

        assertFalse(plan.exportable)
        assertEquals(0, plan.attachments.size)
        assertTrue(plan.issues.all { it.code == "DUPLICATE_INTERNAL_ID" })
    }

    @Test
    fun stableGuidDependsOnInternalIdNotLocalRoomIdentity() {
        val first = DatevExportPolicy.stableReceiptGuid("receipt-42")
        val same = DatevExportPolicy.stableReceiptGuid("receipt-42")
        val other = DatevExportPolicy.stableReceiptGuid("receipt-43")

        assertEquals(first, same)
        assertNotEquals(first, other)
    }

    @Test
    fun contentIdIsStableAcrossInputOrder() {
        val one = receipt(
            internalId = "one",
            amountCent = 4_000,
            allocations = listOf(allocation("a", 4_000))
        )
        val two = receipt(
            internalId = "two",
            amountCent = 6_000,
            allocations = listOf(allocation("b", 6_000))
        )

        val forward = DatevExportPolicy.plan(listOf(one, two))
        val reverse = DatevExportPolicy.plan(listOf(two, one))

        assertTrue(forward.exportable)
        assertEquals(forward.contentId, reverse.contentId)
    }

    @Test
    fun supportsConfirmedCreditWithMatchingNegativeAllocation() {
        val plan = DatevExportPolicy.plan(
            listOf(
                receipt(
                    amountCent = -10_000,
                    allocations = listOf(allocation("credit", -10_000))
                )
            )
        )

        assertTrue(plan.exportable)
        assertEquals(-10_000L, plan.bookingLines.single().amountCent)
    }

    @Test
    fun privateBankMovementCanNeverBeExported() {
        val plan = DatevExportPolicy.plan(
            listOf(receipt(bankClassification = BankTransactionClassification.PRIVATE_IGNORED))
        )

        assertFalse(plan.exportable)
        assertTrue(plan.bookingLines.isEmpty())
        assertTrue(plan.issues.any { it.code == "BANK_PRIVATE_IGNORED" })
    }

    @Test
    fun transferCanNeverBeExportedAsNormalIncomeOrExpense() {
        val plan = DatevExportPolicy.plan(
            listOf(receipt(bankClassification = BankTransactionClassification.TRANSFER))
        )

        assertFalse(plan.exportable)
        assertTrue(plan.bookingLines.isEmpty())
        assertTrue(plan.issues.any { it.code == "BANK_TRANSFER" })
    }

    @Test
    fun previewSeparatesExportablePrivateTransferAndUnresolvedItems() {
        val preview = DatevExportPolicy.preview(
            listOf(
                receipt(internalId = "ok", bankTransactionId = "bank-ok"),
                receipt(
                    internalId = "private",
                    bankTransactionId = "bank-private",
                    bankClassification = BankTransactionClassification.PRIVATE_IGNORED
                ),
                receipt(
                    internalId = "transfer",
                    bankTransactionId = "bank-transfer",
                    bankClassification = BankTransactionClassification.TRANSFER
                ),
                receipt(
                    internalId = "open",
                    bankTransactionId = "bank-open",
                    allocations = emptyList(),
                    noReceiptRequired = true
                )
            )
        )

        assertEquals(4, preview.checked)
        assertEquals(1, preview.exportable)
        assertEquals(1, preview.privateIgnored)
        assertEquals(1, preview.transfers)
        assertEquals(1, preview.noReceiptRequired)
        assertEquals(1, preview.unresolved)
        assertEquals(1, preview.bookingLines)
        assertFalse(preview.canExport)
        assertTrue(preview.exclusions.any { it.bankTransactionId == "bank-private" && it.code == "BANK_PRIVATE_IGNORED" })
        assertTrue(preview.exclusions.any { it.bankTransactionId == "bank-transfer" && it.code == "BANK_TRANSFER" })
        assertTrue(preview.exclusions.any { it.bankTransactionId == "bank-open" && it.code == "MISSING_CONFIRMED_ALLOCATION" })
    }

    private fun receipt(
        internalId: String = "receipt-1",
        amountCent: Long = 10_000,
        attachmentReference: String? = "receipt.pdf",
        allocations: List<ConfirmedDatevAllocation> = listOf(allocation("a", amountCent)),
        bankTransactionId: String = "bank-1",
        bankClassification: String = BankTransactionClassification.NORMAL,
        noReceiptRequired: Boolean = false
    ) = DatevExportReceipt(
        internalId = internalId,
        amountCent = amountCent,
        bookingDate = "2026-08-01",
        attachmentReference = attachmentReference,
        allocations = allocations,
        bankTransactionId = bankTransactionId,
        bankClassification = bankClassification,
        noReceiptRequired = noReceiptRequired
    )

    private fun allocation(
        id: String,
        amountCent: Long,
        confirmed: Boolean = true
    ) = ConfirmedDatevAllocation(
        id = id,
        amountCent = amountCent,
        account = "4801",
        counterAccount = "70000",
        bookingText = "Bestätigte Buchung",
        confirmed = confirmed
    )
}
