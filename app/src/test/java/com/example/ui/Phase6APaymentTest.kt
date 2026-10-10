package com.example.ui

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class Phase6APaymentTest {
    private fun tx(id: String = "insurance", amount: Double = -1200.0) = BankTransaction(id, "account", "2026-10-03", amount = amount,
        propertyId = "a", category = "Gebäudeversicherung", purpose = "Jahresprämie", counterparty = "Testversicherung",
        reconciliationStatus = BankReconciliationStatus.NO_RECEIPT_REQUIRED, noReceiptReason = "Sonstiges")
    private fun receipt(amount: Double = 1200.0) = Receipt(1, "Testversicherung", "2025-12-31", "", amount, "Werbungskosten",
        "Gebäudeversicherung", "4360", "Jahresprämie", propertyId = "a", internalId = "stable-receipt")
    private fun link(tx: String = "insurance", amount: Double = 1200.0) = BankReceiptLink("link-$tx", tx, 1, "stable-receipt", amount)
    private fun split(amount: Double = 700.0, property: String = "a") = BankRentAssignment("split-$property", "insurance", property, "",
        "2026-10", "", amount, BankSplitPaymentType.OTHER_EXPENSE, createdAt = "", updatedAt = "", source = BankRentAssignmentSource.MANUAL)
    private fun rows(receipts: List<Receipt> = emptyList(), splits: List<BankRentAssignment> = emptyList(), links: List<BankReceiptLink> = emptyList(),
        txs: List<BankTransaction> = listOf(tx())) = LedgerPaymentPresentation.entries(receipts, splits, links, txs)
    private fun expense(rows: List<LedgerEntry>) = LedgerPaymentPresentation.totals(rows).expense

    @Test fun confirmedBankExpenseWithoutReceiptComplementsConfirmedRent() {
        val rent = tx("rent", 980.0).copy(reconciliationStatus = BankReconciliationStatus.MATCHED)
        val assignment = split(980.0).copy(transactionId = "rent", unitId = "a-unit", paymentType = BankSplitPaymentType.RENT)
        val result = rows(splits = listOf(assignment), txs = listOf(tx(), rent))
        assertEquals(980.0, LedgerPaymentPresentation.totals(result).income, .001)
        assertEquals(1200.0, expense(result), .001)
        assertTrue(result.all { it.receipt == null })
        assertTrue(LedgerPaymentPresentation.entries(emptyList(), listOf(assignment), emptyList(), listOf(tx(), rent), LedgerView.APPROVED).isEmpty())
    }
    @Test fun confirmedPropertyPaymentDoesNotRequireAnAccountingCategory() {
        val result = rows(txs = listOf(tx().copy(category = "", subcategory = "", noReceiptReason = "Bankgebühr")))
        assertEquals(1200.0, expense(result), .001)
        assertEquals("Sonstige Ausgabe", result.single().category)
    }
    @Test fun linkedExpenseCountsOnceOnBankDateBeforeYearFilter() {
        val result = rows(listOf(receipt()), links = listOf(link()))
        assertEquals(1200.0, expense(result), .001)
        assertEquals(1, result.size)
        assertEquals("2026-10-03", result.single().date)
        assertEquals(1200.0, expense(LedgerPaymentPresentation.filter(result, LedgerFilters(2026))), .001)
        assertTrue(LedgerPaymentPresentation.filter(result, LedgerFilters(2025)).isEmpty())
    }
    @Test fun equalAmountDateAndVendorWithoutStableLinkAreNotDeduplicated() {
        assertEquals(2400.0, expense(rows(listOf(receipt().copy(datum = "2026-10-03")))), .001)
    }
    @Test fun openReviewPrivateAndTransferExpensesAreExcluded() {
        listOf(tx().copy(reconciliationStatus = BankReconciliationStatus.OPEN), tx().copy(reconciliationStatus = BankReconciliationStatus.REVIEW),
            tx().copy(classification = BankTransactionClassification.PRIVATE_IGNORED), tx().copy(classification = BankTransactionClassification.TRANSFER))
            .forEach { assertTrue(rows(txs = listOf(it)).isEmpty()) }
    }
    @Test fun privateLinkedReceiptIsRemovedAndResetRestoresItExactlyOnce() {
        val receipt = receipt(119.0)
        val link = link(amount = 119.0)
        val tx = tx(amount = -119.0).copy(reconciliationStatus = BankReconciliationStatus.MATCHED)
        assertTrue(rows(listOf(receipt), links = listOf(link), txs = listOf(tx.copy(classification = BankTransactionClassification.PRIVATE_IGNORED))).isEmpty())
        assertEquals(119.0, expense(rows(listOf(receipt), links = listOf(link), txs = listOf(tx))), .001)
    }
    @Test fun transferLinkedReceiptAlsoCannotReappearAsBusinessExpense() {
        assertTrue(rows(listOf(receipt()), links = listOf(link()), txs = listOf(tx().copy(classification = BankTransactionClassification.TRANSFER))).isEmpty())
    }
    @Test fun onlyConfirmedBusinessSplitCountsAndRemainderStaysExcluded() {
        val result = rows(splits = listOf(split()), txs = listOf(tx(amount = -1000.0).copy(reconciliationStatus = BankReconciliationStatus.PARTIAL)))
        assertEquals(700.0, expense(result), .001)
    }
    @Test fun objectlessExcludedSplitDoesNotBecomeBusinessExpense() {
        val result = rows(splits = listOf(split(), split(300.0, "")), txs = listOf(tx(amount = -1000.0)))
        assertEquals(700.0, expense(result), .001)
    }
    @Test fun mixedSplitWithFullReceiptLinkIsCoveredOnlyOnce() {
        val result = rows(listOf(receipt(1000.0)), listOf(split(), split(300.0, "")), listOf(link(amount = 1000.0)), listOf(tx(amount = -1000.0)))
        assertEquals(700.0, expense(result), .001)
        assertEquals(1, result.size)
    }
    @Test fun directSplitReceiptRelationshipAlsoCountsOnlyTheConfirmedBusinessPart() {
        val result = rows(listOf(receipt(1000.0)), listOf(split().copy(receiptId = 1)), txs = listOf(tx(amount = -1000.0)))
        assertEquals(700.0, expense(result), .001)
        assertEquals(1, result.size)
    }
    @Test fun directSplitReceiptRelationshipRespectsPrivateCorrection() {
        val result = rows(listOf(receipt(1000.0)), listOf(split().copy(receiptId = 1)),
            txs = listOf(tx(amount = -1000.0).copy(classification = BankTransactionClassification.PRIVATE_IGNORED)))
        assertTrue(result.isEmpty())
    }
    @Test fun sameReceiptWithBusinessAndPrivateBankPartsCountsOnlyBusinessPart() {
        val result = rows(listOf(receipt(1000.0)), links = listOf(link(amount = 700.0), link("private", 300.0)),
            txs = listOf(tx(amount = -700.0), tx("private", -300.0).copy(classification = BankTransactionClassification.PRIVATE_IGNORED)))
        assertEquals(700.0, expense(result), .001)
    }
    @Test fun splitPropertiesFilterBeforeComputingMetrics() {
        val result = rows(splits = listOf(split(), split(300.0, "b")), txs = listOf(tx(amount = -1000.0)))
        assertEquals(700.0, expense(LedgerPaymentPresentation.filter(result, LedgerFilters(2026, propertyId = "a"))), .001)
        assertEquals(300.0, expense(LedgerPaymentPresentation.filter(result, LedgerFilters(2026, propertyId = "b"))), .001)
    }
    @Test fun yearKindCategorySearchAndPeriodUseTheSameRows() {
        val result = rows()
        assertEquals(1200.0, expense(LedgerPaymentPresentation.filter(result, LedgerFilters(2026, category = "Gebäudeversicherung", query = "Jahresprämie", kind = LedgerKind.EXPENSE))), .001)
        assertTrue(LedgerPaymentPresentation.filter(result, LedgerFilters(2026, query = "Unbekannt")).isEmpty())
        assertTrue(LedgerPaymentPresentation.filter(result, LedgerFilters(2026, kind = LedgerKind.INCOME)).isEmpty())
        assertTrue(LedgerPaymentPresentation.filter(result, LedgerFilters(2026, period = LedgerPeriod.CURRENT_MONTH), LocalDate.of(2026, 9, 1)).isEmpty())
    }
    @Test fun depositPrincipalAndSpecialNoReceiptReasonsAreExcluded() {
        listOf(tx().copy(category = "Kaution"), tx().copy(category = "Tilgung"), tx().copy(noReceiptReason = BankNoReceiptReason.PRIVATE),
            tx().copy(noReceiptReason = BankNoReceiptReason.TRANSFER), tx().copy(noReceiptReason = BankNoReceiptReason.LOAN))
            .forEach { assertTrue(rows(txs = listOf(it)).isEmpty()) }
    }
    @Test fun bankLoanRelationshipExcludesUnseparatedRate() {
        val loan = BankLoanAssignment("loan", "insurance", 1, "a", 1200.0, BankLoanPaymentType.REGULAERE_RATE,
            "2026-10", createdAt = "", updatedAt = "")
        assertTrue(LedgerPaymentPresentation.entries(emptyList(), emptyList(), emptyList(), listOf(tx()), loanAssignments = listOf(loan)).isEmpty())
    }
    @Test fun explicitLinkedInterestReceiptStillCountsAlongsideLoanRelationship() {
        val loan = BankLoanAssignment("loan", "insurance", 1, "a", 1200.0, BankLoanPaymentType.REGULAERE_RATE,
            "2026-10", createdAt = "", updatedAt = "")
        val interest = receipt(119.0).copy(unterkategorie = "Zinsen", beschreibung = "Bestätigter Zinsanteil")
        val result = LedgerPaymentPresentation.entries(listOf(interest), emptyList(), listOf(link(amount = 119.0)),
            listOf(tx().copy(reconciliationStatus = BankReconciliationStatus.MATCHED)), loanAssignments = listOf(loan))
        assertEquals(119.0, expense(result), .001)
        assertEquals(1, result.size)
    }
    @Test fun internalIdWinsOverReusedRoomId() {
        val other = receipt().copy(internalId = "other-stable-id")
        assertEquals(1200.0, expense(rows(listOf(other), links = listOf(link()), txs = listOf(tx().copy(classification = BankTransactionClassification.PRIVATE_IGNORED)))), .001)
    }
    @Test fun overallocatedAndUnconfirmedSplitsNeverCreateAdditionalMoney() {
        listOf(split(1500.0), split().copy(status = BankRentAssignmentStatus.REVIEW), split().copy(source = "KI_VORSCHLAG"))
            .forEach { assertTrue(rows(splits = listOf(it)).isEmpty()) }
    }
    @Test fun duplicateInputRelationshipsAreIdempotent() {
        val result = rows(listOf(receipt()), links = listOf(link(), link()), txs = listOf(tx(), tx()))
        assertEquals(1200.0, expense(result), .001)
        assertEquals(1, result.size)
    }
}
