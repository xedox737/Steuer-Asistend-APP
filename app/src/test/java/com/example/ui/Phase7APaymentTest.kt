package com.example.ui

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

/** Martin Weber main 92a795a: the unsplit 200 EUR reimbursement is the mandatory regression. */
class Phase7APaymentTest {
    private fun tx(id: String = "refund", amount: Double = 200.0) = BankTransaction(id, "account", "2026-10-11",
        amount = amount, propertyId = "a", category = "Sonstige Einnahmen", purpose = "200 EUR Erstattung",
        classification = BankTransactionClassification.NORMAL, reconciliationStatus = BankReconciliationStatus.NO_RECEIPT_REQUIRED,
        noReceiptReason = "Sonstiges")
    private fun split(type: String = BankSplitPaymentType.OTHER_INCOME, amount: Double = 200.0) =
        BankRentAssignment("split-$type", "refund", "a", "a-unit", "2026-10", "tenant", amount, type,
            createdAt = "", updatedAt = "", source = BankRentAssignmentSource.MANUAL)
    private fun rows(transactions: List<BankTransaction> = listOf(tx()), receipts: List<Receipt> = emptyList(),
        assignments: List<BankRentAssignment> = emptyList(), links: List<BankReceiptLink> = emptyList(),
        loans: List<BankLoanAssignment> = emptyList()) =
        LedgerPaymentPresentation.entries(receipts, assignments, links, transactions, loanAssignments = loans)
    private fun assertIncome(expected: Double, entries: List<LedgerEntry>, count: Int) {
        val totals = LedgerPaymentPresentation.totals(entries)
        assertEquals(count, entries.size)
        assertEquals(expected, totals.income, .001)
        assertEquals(expected - totals.expense, totals.result, .001)
    }
    @Test fun martinWeberUnsplitNormalConfirmed200EuroReimbursementCountsExactlyOnce() {
        assertTrue(BankClassificationPolicy.decision(tx()).eligibleForPropertyPayments)
        val entries = rows()
        assertIncome(200.0, entries, 1)
        assertEquals("a", entries.single().propertyId)
        assertEquals("refund", entries.single().transaction!!.transactionId)
        assertNull(entries.single().receipt)
        assertTrue(LedgerPaymentPresentation.entries(emptyList(), emptyList(), emptyList(), listOf(tx()), LedgerView.APPROVED).isEmpty())
    }
    @Test fun openReviewAndUnconfirmedMatchedIncomeDoNotCount() {
        listOf(BankReconciliationStatus.OPEN, BankReconciliationStatus.REVIEW, BankReconciliationStatus.PARTIAL,
            BankReconciliationStatus.MATCHED).forEach { assertIncome(0.0, rows(listOf(tx().copy(reconciliationStatus = it))), 0) }
    }
    @Test fun explicitlyReviewedMatchedIncomeCounts() = assertIncome(200.0,
        rows(listOf(tx().copy(reconciliationStatus = BankReconciliationStatus.MATCHED, reviewState = BankReviewState.DONE))), 1)
    @Test fun privateAndIgnoredIncomeStayExcluded() = assertIncome(0.0,
        rows(listOf(tx().copy(classification = BankTransactionClassification.PRIVATE_IGNORED))), 0)
    @Test fun transferIncomeStaysExcluded() = assertIncome(0.0,
        rows(listOf(tx().copy(classification = BankTransactionClassification.TRANSFER))), 0)
    @Test fun specialNoReceiptReasonsStayExcluded() {
        listOf(BankNoReceiptReason.PRIVATE, BankNoReceiptReason.TRANSFER, BankNoReceiptReason.LOAN, BankNoReceiptReason.RENT)
            .forEach { assertIncome(0.0, rows(listOf(tx().copy(noReceiptReason = it))), 0) }
    }
    @Test fun receiptCoverageCountsOnceOnBankDate() {
        val receipt = Receipt(71, "Versicherung", "2025-12-31", "", 200.0, "Sonstige Einnahmen", "Erstattung", "", "Erstattung",
            propertyId = "a", internalId = "income-original")
        val link = BankReceiptLink("refund-link", "refund", 71, "income-original", 200.0)
        val entries = rows(receipts = listOf(receipt), links = listOf(link))
        assertIncome(200.0, entries, 1)
        assertEquals("2026-10-11", entries.single().date)
        assertIncome(200.0, LedgerPaymentPresentation.filter(entries, LedgerFilters(2026)), 1)
    }
    @Test fun otherIncomeSplitCountsExactlyOnce() = assertIncome(200.0, rows(assignments = listOf(split())), 1)
    @Test fun mixedOtherIncomeAndNkSplitIsAuthoritative() {
        val entries = rows(listOf(tx().copy(reconciliationStatus = BankReconciliationStatus.MATCHED)),
            assignments = listOf(split(amount = 120.0), split(BankSplitPaymentType.UTILITIES_SETTLEMENT, 80.0)))
        assertIncome(200.0, entries, 2)
        assertEquals(setOf(120.0, 80.0), entries.map { it.amount }.toSet())
    }
    @Test fun rentPartialAndOverpaymentRemainOnlyOnRentPath() {
        listOf(490.0 to 490.0, 980.0 to 980.0, 1100.0 to 980.0).forEach { (bank, allocated) ->
            val entries = rows(listOf(tx(amount = bank).copy(reconciliationStatus = BankReconciliationStatus.MATCHED)),
                assignments = listOf(split(BankSplitPaymentType.RENT, allocated)))
            assertIncome(allocated, entries, 1)
            assertEquals("Miete", entries.single().category)
        }
    }
    @Test fun openRentCandidateNeverBecomesGenericIncome() = assertIncome(0.0,
        rows(listOf(tx().copy(reconciliationStatus = BankReconciliationStatus.OPEN)), assignments = listOf(split(BankSplitPaymentType.RENT))), 0)
    @Test fun bothNkComponentsRetainTheirExistingPath() {
        listOf(BankSplitPaymentType.UTILITIES_PREPAYMENT, BankSplitPaymentType.UTILITIES_SETTLEMENT).forEach {
            val entries = rows(listOf(tx().copy(reconciliationStatus = BankReconciliationStatus.MATCHED)), assignments = listOf(split(it)))
            assertIncome(200.0, entries, 1)
            assertEquals("Nebenkosten", entries.single().category)
        }
    }
    @Test fun depositSplitAndDirectDepositStayExcluded() {
        assertIncome(0.0, rows(assignments = listOf(split(BankSplitPaymentType.DEPOSIT))), 0)
        listOf("Kaution", BankSplitPaymentType.DEPOSIT, "Tilgung", "Darlehen", "Darlehensauszahlung",
            BankLoanPaymentType.REGULAERE_RATE, BankLoanPaymentType.SONSTIGE_DARLEHENSZAHLUNG).forEach {
            assertIncome(0.0, rows(listOf(tx().copy(category = it))), 0)
            assertIncome(0.0, rows(listOf(tx().copy(category = "", subcategory = it))), 0)
        }
    }
    @Test fun positiveLoanAssignmentExcludesDisbursementAndCorrections() {
        val loan = BankLoanAssignment("loan", "refund", 1, "a", 200.0, BankLoanPaymentType.SONSTIGE_DARLEHENSZAHLUNG,
            "2026-10", createdAt = "", updatedAt = "")
        assertIncome(0.0, rows(loans = listOf(loan)), 0)
    }
    @Test fun directConfirmedExpenseRetainsPhase6AResult() {
        val entries = rows(listOf(tx(amount = -200.0).copy(category = "Reparatur")))
        assertEquals(200.0, LedgerPaymentPresentation.totals(entries).expense, .001)
        assertIncome(0.0, entries, 1)
    }
    @Test fun privateLinkedExpenseRetainsPhase6AExclusion() {
        val receipt = Receipt(71, "Handwerker", "2026-10-11", "", 200.0, "Werbungskosten", "Reparatur", "", "Reparatur",
            propertyId = "a", internalId = "expense-original")
        assertIncome(0.0, rows(listOf(tx(amount = -200.0).copy(classification = BankTransactionClassification.PRIVATE_IGNORED)),
            listOf(receipt), links = listOf(BankReceiptLink("private", "refund", 71, "expense-original", 200.0))), 0)
    }
    @Test fun sameFilteredRowsSupplyListIncomeAndBalanceForTwoObjectsAndYears() {
        val entries = rows(listOf(tx(), tx("b", 300.0).copy(propertyId = "b"), tx("prior", 150.0).copy(bookingDate = "2025-10-11")))
        assertIncome(200.0, LedgerPaymentPresentation.filter(entries, LedgerFilters(2026, propertyId = "a")), 1)
        assertIncome(300.0, LedgerPaymentPresentation.filter(entries, LedgerFilters(2026, propertyId = "b")), 1)
        assertIncome(500.0, LedgerPaymentPresentation.filter(entries, LedgerFilters(2026)), 2)
        assertIncome(150.0, LedgerPaymentPresentation.filter(entries, LedgerFilters(2025)), 1)
    }
    @Test fun duplicateTransactionsAndUnconfirmedSplitsNeverAddGenericMoney() {
        assertIncome(200.0, rows(listOf(tx(), tx())), 1)
        assertIncome(0.0, rows(assignments = listOf(split().copy(status = BankRentAssignmentStatus.REVIEW))), 0)
        assertIncome(0.0, rows(listOf(tx().copy(propertyId = ""))), 0)
    }
}
