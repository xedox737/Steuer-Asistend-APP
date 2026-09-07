package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankPhase1FinalAcceptanceTest {
    @Test fun csvSollOnlyIsNegative() {
        val tx = BankImportParser.parseCsv("Buchungstag;Soll\n04.09.2026;84,50").transactions.single()
        assertEquals(-84.50, tx.amount, 0.001)
    }

    @Test fun csvHabenOnlyIsPositive() {
        val tx = BankImportParser.parseCsv("Buchungstag;Haben\n05.09.2026;950,00").transactions.single()
        assertEquals(950.0, tx.amount, 0.001)
    }

    @Test fun csvSollAndHabenInSameFileSupportEmptyCellsAndGermanDecimals() {
        val csv = "Buchungstag;Belastung;Gutschrift\n04.09.2026;1.234,56;\n05.09.2026;;950,00"
        val tx = BankImportParser.parseCsv(csv).transactions
        assertEquals(-1234.56, tx[0].amount, 0.001)
        assertEquals(950.0, tx[1].amount, 0.001)
    }

    @Test fun normalAmountColumnWinsWithoutDoubleBooking() {
        val csv = "Buchungstag;Betrag;Soll;Haben\n04.09.2026;-84,50;999,00;"
        val tx = BankImportParser.parseCsv(csv).transactions
        assertEquals(1, tx.size)
        assertEquals(-84.50, tx.single().amount, 0.001)
    }

    @Test fun malformedSollHabenRowIsSkipped() {
        val csv = "Buchungstag;Debit;Credit\n04.09.2026;abc;\n05.09.2026;;10,00"
        val batch = BankImportParser.parseCsv(csv)
        assertEquals(1, batch.transactions.size)
        assertEquals(1, batch.errorRows)
    }

    @Test fun ambiguousSollAndHabenOnOneRowIsSkippedRatherThanDuplicated() {
        val csv = "Buchungstag;Soll;Haben\n04.09.2026;10,00;10,00\n05.09.2026;;5,00"
        val batch = BankImportParser.parseCsv(csv)
        assertEquals(1, batch.transactions.size)
        assertEquals(1, batch.errorRows)
    }

    @Test fun identicalRowsRemainTwoRealOccurrencesButAreStableAcrossReimport() {
        val csv = "Buchungstag;Auftraggeber;Verwendungszweck;Betrag;Referenz\n04.09.2026;A;X;-10,00;R\n04.09.2026;A;X;-10,00;R"
        val first = BankImportParser.parseCsv(csv).transactions
        val second = BankImportParser.parseCsv(csv).transactions
        assertEquals(2, first.size)
        assertNotEquals(first[0].transactionId, first[1].transactionId)
        assertEquals(first.map { it.transactionId }, second.map { it.transactionId })
    }

    @Test fun sameCounterpartyDateAmountButDifferentPurposeRemainDistinct() {
        val csv = "Buchungstag;Auftraggeber;Verwendungszweck;Betrag\n04.09.2026;A;Zweck 1;-10,00\n04.09.2026;A;Zweck 2;-10,00"
        val tx = BankImportParser.parseCsv(csv).transactions
        assertNotEquals(tx[0].transactionId, tx[1].transactionId)
    }

    @Test fun paymentMethodTransferImprovesMatchingButCannotCreateMatchAlone() {
        val tx = BankTransaction("t", "a", "2026-09-04", amount = -84.5, counterparty = "Hornbach", purpose = "SEPA Überweisung Material")
        val unknown = receipt(1, 84.5, "Unbekannt")
        val transfer = unknown.copy(zahlungsart = "Überweisung")
        assertTrue(BankReceiptMatcher.score(tx, transfer).score > BankReceiptMatcher.score(tx, unknown).score)
        val unrelated = transfer.copy(id = 2, bruttobetrag = 999.0, aussteller = "Andere Firma", beschreibung = "fremd")
        assertNull(BankReceiptMatcher.bestForTransaction(tx, listOf(unrelated)))
    }

    @Test fun paymentMethodDirectDebitImprovesMatching() {
        val tx = BankTransaction("t", "a", "2026-09-04", amount = -84.5, counterparty = "Hornbach", purpose = "SEPA Lastschrift")
        val unknown = receipt(1, 84.5, "Unbekannt")
        val debit = unknown.copy(zahlungsart = "Lastschrift")
        assertTrue(BankReceiptMatcher.score(tx, debit).score > BankReceiptMatcher.score(tx, unknown).score)
    }

    @Test fun cashClearlyReducesBankMatchAndUnknownIsNeutral() {
        val tx = BankTransaction("t", "a", "2026-09-04", amount = -84.5, counterparty = "Hornbach", purpose = "Material")
        val unknown = receipt(1, 84.5, "Unbekannt")
        val cash = unknown.copy(zahlungsart = "Bar")
        assertTrue(BankReceiptMatcher.score(tx, cash).score <= BankReceiptMatcher.score(tx, unknown).score - 30)
        assertEquals(BankReceiptMatcher.score(tx, unknown).score, BankReceiptMatcher.score(tx, unknown.copy(zahlungsart = "Unbekannt")).score)
    }

    @Test fun loanExactRateAndBankProducesSuggestionForDebitAndCredit() {
        val account = BankAccount("a", "Hauskonto", bankName = "Sparkasse")
        val loan = Loan(id = 1, bezeichnung = "Sanierungskredit", bank = "Sparkasse", monatlicheRate = 445.0, propertyId = "p")
        listOf(-445.0, 445.0).forEach { amount ->
            val tx = BankTransaction("t$amount", "a", "2026-09-04", amount = amount, purpose = "Sanierungskredit Rate", propertyId = "p")
            assertEquals(1, BankLoanMatcher.suggestions(tx, listOf(loan), account, listOf(tx, tx.copy(transactionId = "old"))).single().loanId)
        }
    }

    @Test fun loanSimilarRateCanStillBeSuggested() {
        val account = BankAccount("a", "Hauskonto", bankName = "Sparkasse")
        val loan = Loan(id = 2, bezeichnung = "Kredit", bank = "Sparkasse", monatlicheRate = 445.0)
        val tx = BankTransaction("t", "a", "2026-09-04", amount = -442.0, purpose = "Kredit Rate")
        assertTrue(BankLoanMatcher.suggestions(tx, listOf(loan), account).isNotEmpty())
    }

    @Test fun wrongBankSuppressesLoanSuggestion() {
        val account = BankAccount("a", "Hauskonto", bankName = "Volksbank")
        val loan = Loan(id = 3, bezeichnung = "Kredit", bank = "Sparkasse", monatlicheRate = 445.0)
        val tx = BankTransaction("t", "a", "2026-09-04", amount = -445.0)
        assertTrue(BankLoanMatcher.suggestions(tx, listOf(loan), account).isEmpty())
    }

    @Test fun twoPossibleLoansAreBothReturnedWithoutAutomaticChoice() {
        val account = BankAccount("a", "Hauskonto", bankName = "Sparkasse")
        val loans = listOf(
            Loan(id = 1, bezeichnung = "Kredit A", bank = "Sparkasse", monatlicheRate = 445.0),
            Loan(id = 2, bezeichnung = "Kredit B", bank = "Sparkasse", monatlicheRate = 445.0)
        )
        val tx = BankTransaction("t", "a", "2026-09-04", amount = -445.0, purpose = "Kredit Rate")
        assertEquals(setOf(1, 2), BankLoanMatcher.suggestions(tx, loans, account).map { it.loanId }.toSet())
    }

    @Test fun loanNoMatchAndInactiveLoanAreNotSuggested() {
        val account = BankAccount("a", "Hauskonto", bankName = "Sparkasse")
        val tx = BankTransaction("t", "a", "2026-09-04", amount = -100.0)
        assertTrue(BankLoanMatcher.suggestions(tx, listOf(Loan(id = 1, bank = "Sparkasse", monatlicheRate = 445.0)), account).isEmpty())
        assertTrue(BankLoanMatcher.suggestions(tx.copy(amount = -445.0), listOf(Loan(id = 2, bank = "Sparkasse", monatlicheRate = 445.0, aktiv = false)), account).isEmpty())
    }

    @Test fun splitOneBankToManyMovesPartialMatchedPartialOpen() {
        val tx = BankTransaction("t", "a", "2026-09-04", amount = -150.0)
        val r100 = receipt(1, 100.0)
        val r50 = receipt(2, 50.0)
        val firstAmount = BankLinkPolicy.propose(tx, r100, emptyList()).amount
        val first = BankReceiptLink("l1", "t", 1, r100.internalId, firstAmount)
        assertEquals(BankReconciliationStatus.PARTIAL, BankLinkPolicy.statusFor(tx, listOf(first)))
        val secondAmount = BankLinkPolicy.propose(tx, r50, listOf(first)).amount
        val second = BankReceiptLink("l2", "t", 2, r50.internalId, secondAmount)
        assertEquals(BankReconciliationStatus.MATCHED, BankLinkPolicy.statusFor(tx, listOf(first, second)))
        assertEquals(BankReconciliationStatus.PARTIAL, BankLinkPolicy.statusFor(tx, listOf(first)))
        assertEquals(BankReconciliationStatus.OPEN, BankLinkPolicy.statusFor(tx, emptyList()))
    }

    @Test fun collectionManyBanksToOneCompletesAndPreventsOverAllocation() {
        val receipt = receipt(9, 200.0)
        val t1 = BankTransaction("t1", "a", "2026-09-01", amount = -100.0)
        val t2 = BankTransaction("t2", "a", "2026-09-02", amount = -100.0)
        val t3 = BankTransaction("t3", "a", "2026-09-03", amount = -100.0)
        val first = BankReceiptLink("l1", "t1", 9, receipt.internalId, BankLinkPolicy.propose(t1, receipt, emptyList()).amount)
        assertEquals(100.0, BankLinkPolicy.propose(t2, receipt, listOf(first)).amount, 0.001)
        val second = BankReceiptLink("l2", "t2", 9, receipt.internalId, 100.0)
        assertFalse(BankLinkPolicy.propose(t3, receipt, listOf(first, second)).allowed)
    }

    @Test fun reverseRankingRespectsDirectionAndExistingAllocation() {
        val receipt = receipt(5, 100.0)
        val expense = BankTransaction("expense", "a", "2026-09-04", amount = -100.0, counterparty = "Hornbach")
        val income = BankTransaction("income", "a", "2026-09-04", amount = 100.0, counterparty = "Hornbach")
        val ranked = BankReceiptMatcher.rankTransactionsForReceipt(receipt, listOf(income, expense), emptyList())
        assertEquals(listOf("expense"), ranked.map { it.transactionId })
        val fullLink = BankReceiptLink("l", "expense", receipt.id, receipt.internalId, 100.0)
        assertTrue(BankReceiptMatcher.rankTransactionsForReceipt(receipt, listOf(expense), listOf(fullLink)).isEmpty())
    }

    private fun receipt(id: Int, amount: Double, payment: String = "Unbekannt") = Receipt(
        id = id, aussteller = "Hornbach", datum = "2026-09-04", uhrzeit = "", bruttobetrag = amount,
        hauptkategorie = "Renovierung", unterkategorie = "", kontoNr = "", beschreibung = "Material",
        zahlungsart = payment, internalId = "receipt-$id"
    )
}
