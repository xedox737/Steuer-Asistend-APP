package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankLastMonthAssignmentPolicyTest {
    private fun tx(id: String, date: String, classification: String = BankTransactionClassification.NORMAL, propertyId: String = "") = BankTransaction(
        transactionId = id,
        accountId = "acc-1",
        bookingDate = date,
        amount = -120.0,
        counterparty = "Stadtwerke Musterstadt",
        counterpartyIban = "DE00123456789012345678",
        purpose = "Abschlag Strom September 2026",
        classification = classification,
        propertyId = propertyId
    )

    private fun receipt(id: Int = 77) = Receipt(
        id = id,
        aussteller = "Stadtwerke Musterstadt",
        datum = "2026-08-10",
        uhrzeit = "",
        bruttobetrag = 120.0,
        hauptkategorie = "Nebenkosten",
        unterkategorie = "Strom",
        kontoNr = "",
        beschreibung = "Stromabschlag",
        wohneinheit = "EG links",
        zahlungsart = "Überweisung",
        propertyId = "property-1"
    )

    private fun link(id: String = "link-old", receiptId: Int = 77) = BankReceiptLink(
        linkId = id,
        transactionId = "old",
        receiptId = receiptId,
        allocatedAmount = 120.0,
        status = BankLinkStatus.CONFIRMED
    )

    @Test fun copiesOnlyBusinessTargetsFromPreviousConfirmedMonth() {
        val target = tx("new", "2026-09-10")
        val suggestion = BankLastMonthAssignmentPolicy.suggest(
            target, listOf(tx("old", "2026-08-10"), target), listOf(link()), listOf(receipt())
        )!!
        assertEquals("old", suggestion.sourceTransactionId)
        assertEquals("property-1", suggestion.suggestedPropertyId)
        assertEquals("EG links", suggestion.suggestedUnit)
        assertEquals("Nebenkosten", suggestion.suggestedCategory)
        assertEquals("Strom", suggestion.suggestedSubcategory)
        assertTrue(suggestion.reasons.any { it.contains("nicht übernommen") })
    }

    @Test fun specialTransactionsDoNotGetSuggestion() {
        val old = tx("old", "2026-08-10")
        val target = tx("new", "2026-09-10", BankTransactionClassification.TRANSFER)
        assertNull(BankLastMonthAssignmentPolicy.suggest(target, listOf(old, target), listOf(link()), listOf(receipt())))
    }

    @Test fun ambiguousPreviousMultiReceiptCaseIsNotSuggested() {
        val target = tx("new", "2026-09-10")
        assertNull(BankLastMonthAssignmentPolicy.suggest(
            target,
            listOf(tx("old", "2026-08-10"), target),
            listOf(link(), link("link-2", 88)),
            listOf(receipt(), receipt(88))
        ))
    }

    @Test fun existingDifferentPropertyIsAConflict() {
        val target = tx("new", "2026-09-10", propertyId = "other")
        val suggestion = BankLastMonthAssignmentPolicy.suggest(
            target, listOf(tx("old", "2026-08-10"), target), listOf(link()), listOf(receipt())
        )!!
        assertTrue(BankLastMonthAssignmentPolicy.hasAssignmentConflict(target, suggestion))
    }
}
