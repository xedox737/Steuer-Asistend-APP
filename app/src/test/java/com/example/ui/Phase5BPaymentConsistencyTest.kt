package com.example.ui

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

class Phase5BPaymentConsistencyTest {
    private val property = PropertyMetadata(propertyId = "a", name = "Haus A")
    private val unit = WohneinheitStatus("WE 01", "WE 01", "Vermietet", "Müller", 760.0, 70.0, "2021-04-01", "u-a")
    private val groups = listOf(RentPropertyUnits(property, listOf(unit)))
    private fun tx(amount: Double = 980.0) = BankTransaction("tx", "account", "2026-10-03", amount = amount,
        counterparty = "Müller", purpose = "Miete Oktober", reconciliationStatus = BankReconciliationStatus.MATCHED)
    private fun assignment(amount: Double = 980.0, type: String = BankSplitPaymentType.RENT) = BankRentAssignment(
        "assignment-$type", "tx", "a", "u-a", "2026-10", "tenant", amount, type, createdAt = "2026-10-03", updatedAt = "2026-10-03")
    private fun receipt(amount: Double = 980.0) = Receipt(1, "Müller", "2026-10-03", "", amount,
        "Miete, Nebenkosten & Kaution", "Warmmiete", "8100", "Miete", propertyId = "a", unitId = "u-a", internalId = "r-a")
    private fun link() = BankReceiptLink("link", "tx", 1, "r-a", 980.0)

    @Test fun bankRentWithoutReceiptIsPaymentIncomeWithoutAccountingRelease() {
        val payment = LedgerPaymentPresentation.entries(emptyList(), listOf(assignment()), emptyList(), listOf(tx()))
        assertEquals(980.0, LedgerPaymentPresentation.totals(payment).income, .001)
        assertNull(payment.single().receipt)
        assertEquals("2026-10-03", payment.single().date)
        assertTrue(LedgerPaymentPresentation.entries(emptyList(), listOf(assignment()), emptyList(), listOf(tx()), LedgerView.APPROVED).isEmpty())
    }

    @Test fun stableLinkPreventsDuplicateEvenAcrossReceiptDates() {
        val receipt = receipt().copy(datum = "2025-12-31")
        val entries = LedgerPaymentPresentation.entries(listOf(receipt), listOf(assignment()), listOf(link()), listOf(tx()))
        assertEquals(980.0, LedgerPaymentPresentation.totals(entries).income, .001)
        assertEquals(980.0, LedgerPaymentPresentation.totals(LedgerPaymentPresentation.filter(entries, LedgerFilters(2026))).income, .001)
        assertTrue(LedgerPaymentPresentation.filter(entries, LedgerFilters(2025)).isEmpty())
        assertEquals("OFFEN", receipt.freigabestatus)
    }

    @Test fun equalValuesWithoutStableRelationshipRemainSeparate() {
        assertEquals(1960.0, LedgerPaymentPresentation.totals(LedgerPaymentPresentation.entries(
            listOf(receipt()), listOf(assignment()), emptyList(), listOf(tx()))).income, .001)
    }

    @Test fun reviewProposalsPrivateTransfersAndOpenTransactionsAreNotConfirmedPayments() {
        val invalid = listOf(tx().copy(reconciliationStatus = BankReconciliationStatus.REVIEW),
            tx().copy(reconciliationStatus = BankReconciliationStatus.OPEN),
            tx().copy(classification = BankTransactionClassification.PRIVATE_IGNORED),
            tx().copy(classification = BankTransactionClassification.TRANSFER))
        invalid.forEach { assertTrue(LedgerPaymentPresentation.entries(emptyList(), listOf(assignment()), emptyList(), listOf(it)).isEmpty()) }
        listOf(assignment().copy(status = BankRentAssignmentStatus.REVIEW), assignment().copy(source = "KI_VORSCHLAG")).forEach {
            assertTrue(LedgerPaymentPresentation.entries(emptyList(), listOf(it), emptyList(), listOf(tx())).isEmpty())
        }
    }

    @Test fun splitRentUtilitiesAndDepositKeepRentalTotalAndUnitIsolation() {
        val assignments = listOf(assignment(760.0), assignment(220.0, BankSplitPaymentType.UTILITIES_PREPAYMENT),
            assignment(320.0, BankSplitPaymentType.DEPOSIT))
        val entries = LedgerPaymentPresentation.entries(listOf(receipt(760.0).copy(unterkategorie = "Kaltmiete")), assignments, listOf(link()), listOf(tx(1300.0)))
        assertEquals(980.0, LedgerPaymentPresentation.totals(entries).income, .001)
        assertTrue(entries.all { it.propertyId == "a" && it.unitId == "u-a" })
        assertTrue(LedgerPaymentPresentation.filter(entries, LedgerFilters(2026, propertyId = "b")).isEmpty())
    }

    @Test fun objectFilterChangesTotalsAndOtherFiltersShareTheSameBasis() {
        val expenses = listOf(receipt(1000.0).copy(hauptkategorie = "Werbungskosten", unterkategorie = "Reparatur"),
            receipt(2000.0).copy(id = 2, internalId = "r-b", propertyId = "b", hauptkategorie = "Werbungskosten", unterkategorie = "Versicherung"))
        val entries = LedgerPaymentPresentation.entries(expenses, emptyList(), emptyList(), emptyList())
        assertEquals(3000.0, LedgerPaymentPresentation.totals(entries).expense, .001)
        val filtered = LedgerPaymentPresentation.filter(entries, LedgerFilters(2026, propertyId = "a"))
        assertEquals(1000.0, LedgerPaymentPresentation.totals(filtered).expense, .001)
        assertEquals(1, filtered.size)
        assertTrue(LedgerPaymentPresentation.filter(entries, LedgerFilters(2026, kind = LedgerKind.INCOME)).isEmpty())
        assertEquals(2000.0, LedgerPaymentPresentation.totals(LedgerPaymentPresentation.filter(entries,
            LedgerFilters(2026, category = "Versicherung"))).expense, .001)
        assertTrue(LedgerPaymentPresentation.filter(entries, LedgerFilters(2026, period = LedgerPeriod.CURRENT_MONTH), java.time.LocalDate.of(2026, 9, 1)).isEmpty())
    }

    @Test fun unresolvedBankCandidateAppearsInReviewAndNeverCountsAsPaid() {
        val open = tx(1020.0).copy(reconciliationStatus = BankReconciliationStatus.OPEN)
        val reviews = RentPaymentReview.build(groups, emptyList(), listOf(open), emptyList(), emptyList())
        assertEquals(1020.0, reviews.single().amount, .001)
        assertEquals("Bankzahlung prüfen", reviews.single().kind)
        assertTrue(LedgerPaymentPresentation.entries(emptyList(), emptyList(), emptyList(), listOf(open)).isEmpty())
    }

    @Test fun partialCandidateShowsOnlyUnassignedRemainder() {
        val partial = tx(1020.0).copy(reconciliationStatus = BankReconciliationStatus.PARTIAL)
        val reviews = RentPaymentReview.build(groups, emptyList(), listOf(partial), listOf(assignment(500.0)), emptyList())
        assertEquals(520.0, reviews.single().amount, .001)
        assertEquals("Teilzugeordnete Bankzahlung", reviews.single().kind)
    }

    @Test fun linkedReceiptAndPartialAssignmentDoNotHideOpenRemainder() {
        val partial = tx(1020.0).copy(reconciliationStatus = BankReconciliationStatus.PARTIAL)
        val reviews = RentPaymentReview.build(groups, listOf(receipt(500.0)), listOf(partial),
            listOf(assignment(500.0)), listOf(link().copy(allocatedAmount = 500.0)))
        assertEquals(520.0, reviews.single().amount, .001)
        assertEquals("Teilzugeordnete Bankzahlung", reviews.single().kind)
    }

    @Test fun privateRefundAndUnrelatedCreditsDoNotBecomeRentalCandidates() {
        val open = tx().copy(reconciliationStatus = BankReconciliationStatus.OPEN)
        val transactions = listOf(open.copy(transactionId = "private", classification = BankTransactionClassification.PRIVATE_IGNORED),
            open.copy(transactionId = "transfer", classification = BankTransactionClassification.TRANSFER),
            open.copy(transactionId = "refund", category = "Erstattung", purpose = "Miete erstattet"),
            open.copy(transactionId = "salary", purpose = "Gehalt", counterparty = "Arbeitgeber"))
        assertTrue(RentPaymentReview.build(groups, emptyList(), transactions, emptyList(), emptyList()).isEmpty())
    }

    @Test fun reviewIncludesUnreviewedReceiptsAndBankReviewAssignments() {
        val pending = receipt().copy(pruefstatus = "UNGEPRUEFT")
        val tx = tx().copy(reconciliationStatus = BankReconciliationStatus.REVIEW)
        val reviews = RentPaymentReview.build(groups, listOf(pending), listOf(tx), listOf(assignment().copy(status = BankRentAssignmentStatus.REVIEW)), emptyList())
        assertEquals(setOf("Beleg prüfen", "Bankzahlung prüfen"), reviews.map { it.kind }.toSet())
    }

    @Test fun dashboardUsesLiveReviewAndRelationshipState() {
        val pending = receipt().copy(pruefstatus = "UNGEPRUEFT")
        val expense = tx().copy(amount = -980.0, category = "Reparatur", reconciliationStatus = BankReconciliationStatus.OPEN)
        val unknown = expense.copy(transactionId = "unknown", category = "")
        val counts = DashboardReviewPresentation.counts(listOf(pending), listOf(expense, unknown), emptyList(), emptyList())
        assertEquals(1, counts.receiptsToReview)
        assertEquals(1, counts.missingBankReceipts)
        assertEquals(1, counts.uncheckedBankEntries)
        val reviewed = DashboardReviewPresentation.counts(listOf(pending.copy(pruefstatus = "GEPRUEFT")), listOf(expense), listOf(link()), emptyList())
        assertEquals(0, reviewed.receiptsToReview)
        assertEquals(0, reviewed.missingBankReceipts)
    }
}
