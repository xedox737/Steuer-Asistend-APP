package com.example.ui

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

class Phase8BPaymentHistoryTest {
    private val receipt = Receipt(801, "Vormieter", "2026-09-30", "", 980.0,
        "Miete, Nebenkosten & Kaution", "Miete", "", "September",
        propertyId = "a", unitId = "unit-a", wohneinheit = "WE 01", internalId = "rent-original",
        pruefstatus = "GEPRUEFT", exportStatus = "BEREIT")
    private val tx = BankTransaction("rent", "account", "2026-10-05", amount = 980.0,
        propertyId = "a", unitId = "unit-a", counterparty = "Vormieter", purpose = "Nachzahlung September",
        reconciliationStatus = BankReconciliationStatus.MATCHED)
    private val assignment = BankRentAssignment("rent-a", tx.transactionId, "a", "unit-a", "2026-09",
        "previous-tenancy", 980.0, BankSplitPaymentType.RENT, createdAt = "", updatedAt = "")
    private val link = BankReceiptLink("rent-link", tx.transactionId, receipt.id, receipt.internalId, 980.0)
    private fun last(receipts: List<Receipt> = emptyList(), assignments: List<BankRentAssignment> = emptyList(),
        transactions: List<BankTransaction> = emptyList(), links: List<BankReceiptLink> = emptyList(),
        property: String = "a", unit: String = "unit-a") = LedgerPaymentPresentation.lastRentalPayment(
        property, unit, receipts, assignments, links, transactions)

    @Test fun confirmedReceiptAloneIsVisibleByStableIdentityEvenAfterRename() {
        val row = last(listOf(receipt.copy(wohneinheit = "Alte Bezeichnung")))!!
        assertEquals(receipt.datum, row.date); assertEquals(980.0, row.amount, 0.0)
    }
    @Test fun confirmedBankAloneIsVisibleAfterLeaseEndsWithoutArtificialReceipt() {
        val row = last(assignments = listOf(assignment), transactions = listOf(tx))!!
        assertEquals("2026-10-05", row.date); assertEquals(980.0, row.amount, 0.0)
        assertNull(row.receipt)
        assertEquals("previous-tenancy", assignment.tenantReference)
        assertEquals("2026-09", assignment.rentMonth) // no remapping to new/current tenancy or payment month
    }
    @Test fun linkedBankAndReceiptAppearOnceOnActualBankDateBeforeMonthFilter() {
        val rows = LedgerPaymentPresentation.rentalEntries(listOf(receipt), listOf(assignment), listOf(link), listOf(tx))
        assertEquals(1, rows.size); assertEquals(980.0, rows.single().amount, 0.0)
        assertEquals("2026-10-05", last(listOf(receipt), listOf(assignment), listOf(tx), listOf(link))!!.date)
        val direct = assignment.copy(receiptId = receipt.id)
        assertEquals(1, LedgerPaymentPresentation.rentalEntries(listOf(receipt), listOf(direct), emptyList(), listOf(tx)).size)
    }
    @Test fun rentAndNkComponentsOfSameBankPaymentAreOneActualUnitPayment() {
        val rent = assignment.copy(allocatedAmount = 760.0)
        val nk = assignment.copy(assignmentId = "nk", allocatedAmount = 220.0, paymentType = BankSplitPaymentType.UTILITIES_PREPAYMENT)
        assertEquals(980.0, last(assignments = listOf(rent, nk), transactions = listOf(tx))!!.amount, 0.0)
        assertEquals(980.0, last(listOf(receipt.copy(unterkategorie = "Warmmiete")), listOf(rent, nk), listOf(tx), listOf(link))!!.amount, 0.0)
    }
    @Test fun pendingBankAndReceiptNeverBecomeConfirmedPayments() {
        assertNull(last(assignments = listOf(assignment.copy(status = BankRentAssignmentStatus.REVIEW)), transactions = listOf(tx)))
        assertNull(last(assignments = listOf(assignment.copy(source = "AUTOMATIC")), transactions = listOf(tx)))
        listOf(BankReconciliationStatus.OPEN, BankReconciliationStatus.REVIEW).forEach {
            assertNull(last(assignments = listOf(assignment), transactions = listOf(tx.copy(reconciliationStatus = it))))
        }
        assertNull(last(listOf(receipt.copy(pruefstatus = "ZU_PRUEFEN"))))
    }
    @Test fun depositAndOtherIncomeNeverReplaceRentButNkSettlementRemainsPayment() {
        listOf(BankSplitPaymentType.DEPOSIT, BankSplitPaymentType.OTHER_INCOME).forEach {
            assertNull(last(assignments = listOf(assignment.copy(paymentType = it)), transactions = listOf(tx)))
        }
        assertNull(last(listOf(receipt.copy(unterkategorie = "Kaution"))))
        assertEquals(980.0, last(assignments = listOf(assignment.copy(paymentType = BankSplitPaymentType.UTILITIES_SETTLEMENT)), transactions = listOf(tx))!!.amount, 0.0)
    }
    @Test fun privateBankIncludingLinkedReceiptIsExcluded() = excludedClassification(BankTransactionClassification.PRIVATE_IGNORED)
    @Test fun transferBankIncludingLinkedReceiptIsExcluded() = excludedClassification(BankTransactionClassification.TRANSFER)
    private fun excludedClassification(type: String) {
        val excluded = tx.copy(classification = type)
        assertNull(last(assignments = listOf(assignment), transactions = listOf(excluded)))
        assertNull(last(listOf(receipt), listOf(assignment), listOf(excluded), listOf(link)))
    }
    @Test fun newestRelevantPaymentUsesPaymentDateRatherThanRentMonthOrReceiptInsertion() {
        val newest = receipt.copy(id = 802, datum = "2026-10-06", internalId = "later-receipt", bruttobetrag = 490.0)
        assertEquals(490.0, last(listOf(newest, receipt), listOf(assignment), listOf(tx))!!.amount, 0.0)
        assertEquals(980.0, last(listOf(receipt), listOf(assignment), listOf(tx))!!.amount, 0.0)
        assertEquals("2026-10-05", last(listOf(receipt), listOf(assignment), listOf(tx))!!.date)
        assertNull(last(listOf(receipt.copy(datum = "invalid"))))
    }
    @Test fun identicallyNamedUnitsAndObjectsCannotLeakAndBlankIdsAreNotGuessed() {
        val b = receipt.copy(id = 803, propertyId = "b", unitId = "unit-b", datum = "2026-10-10")
        val samePropertyOtherUnit = receipt.copy(id = 804, unitId = "unit-other", datum = "2026-10-11")
        val a = last(listOf(receipt, b, samePropertyOtherUnit), listOf(assignment), listOf(tx))!!
        assertEquals("2026-10-05", a.date); assertEquals("a", a.propertyId); assertEquals("unit-a", a.unitId)
        assertEquals(b.datum, last(listOf(receipt, b), property = "b", unit = "unit-b")!!.date)
        assertNull(last(listOf(receipt.copy(unitId = ""))))
    }
    @Test fun partialAndOverpaymentPreserveExistingLedgerAmountsAndNoWrite() {
        listOf(490.0 to 490.0, 1100.0 to 980.0).forEach { (received, confirmed) ->
            val a = assignment.copy(allocatedAmount = confirmed)
            assertEquals(confirmed, last(assignments = listOf(a), transactions = listOf(tx.copy(amount = received)))!!.amount, 0.0)
        }
        assertNull(last(assignments = listOf(assignment.copy(allocatedAmount = 1100.0)), transactions = listOf(tx)))
    }
}
