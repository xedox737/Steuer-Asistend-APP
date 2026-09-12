package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankExplicitRuleProposalTest {
    @Test
    fun proposalUsesConfirmedAssignmentTargetsButStaysDisabled() {
        val tx = BankTransaction(
            transactionId = "tx-1",
            accountId = "account-1",
            bookingDate = "2026-09-10",
            amount = -100.0,
            counterparty = "Stadtwerke",
            counterpartyIban = "DE00123456789012345678",
            purpose = "Strom Abschlag September",
            propertyId = "property-1",
            unitId = "unit-eg"
        )
        val receipt = Receipt(
            id = 7,
            aussteller = "Stadtwerke",
            datum = "2026-09-10",
            uhrzeit = "",
            bruttobetrag = 100.0,
            hauptkategorie = "Nebenkosten",
            unterkategorie = "Strom",
            kontoNr = "",
            beschreibung = "Stromabschlag",
            wohneinheit = "EG links",
            zahlungsart = "Überweisung",
            propertyId = "property-1"
        )

        val rule = BankExplicitRuleProposal.create(tx, receipt, "2026-09-11T18:00:00Z")

        assertFalse(rule.enabled)
        assertEquals(BankRuleState.PROPOSED, rule.state)
        assertEquals(BankRuleSource.USER_CREATED, rule.source)
        assertEquals(BankRuleDirection.EXPENSE, rule.transactionDirection)
        assertEquals("account-1", rule.accountId)
        assertEquals("property-1", rule.propertyId)
        assertEquals("unit-eg", rule.unitId)
        assertEquals("Nebenkosten", rule.receiptCategoryTarget)
        assertEquals("Strom", rule.receiptSubcategoryTarget)
        assertTrue(rule.amountMin!! < 100.0)
        assertTrue(rule.amountMax!! > 100.0)
        assertTrue(rule.purposeTerms.contains("strom"))
    }

    @Test
    fun sameAssignmentCreatesStableRuleId() {
        val tx = BankTransaction("tx-a", "account-1", "2026-09-10", amount = -50.0, counterparty = "Versorger", purpose = "Wasser Abschlag")
        val receipt = Receipt(
            id = 1, aussteller = "Versorger", datum = "2026-09-10", uhrzeit = "", bruttobetrag = 50.0,
            hauptkategorie = "Nebenkosten", unterkategorie = "Wasser", kontoNr = "", beschreibung = "",
            wohneinheit = "", zahlungsart = "Überweisung", propertyId = "property-1"
        )
        val a = BankExplicitRuleProposal.create(tx, receipt, "one")
        val b = BankExplicitRuleProposal.create(tx.copy(transactionId = "tx-b"), receipt.copy(id = 2), "two")
        assertEquals(a.ruleId, b.ruleId)
    }
}
