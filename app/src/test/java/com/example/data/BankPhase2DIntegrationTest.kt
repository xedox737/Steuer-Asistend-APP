package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankPhase2DIntegrationTest {
    private fun tx(id:String, amount:Double=-100.0, property:String="p1", status:String=BankReconciliationStatus.OPEN) = BankTransaction(
        transactionId=id, accountId="a1", bookingDate="2026-09-05", amount=amount,
        counterparty="Vendor", purpose="Invoice service", propertyId=property, reconciliationStatus=status
    )
    private fun receipt(id:Int, amount:Double=100.0, property:String="p1", vendor:String="Vendor") = Receipt(
        id=id, aussteller=vendor, datum="2026-09-04", uhrzeit="", bruttobetrag=amount,
        hauptkategorie="Kosten", unterkategorie="Material", kontoNr="", beschreibung="Invoice service", internalId="r$id", propertyId=property
    )

    @Test fun activePhase2ARuleBonusIsBoundedAndConflictDoesNotBoost() {
        val transaction = tx("t", -200.0)
        val receipts = listOf(receipt(1,100.0), receipt(2,100.0))
        val base = BankPhase2DEngine.analyze(listOf(transaction),receipts,emptyList(),emptyMap()).combinations.first()
        val rule = BankLearningRule(
            ruleId="r", displayName="Vendor", enabled=true, state=BankRuleState.ACTIVE,
            transactionDirection=BankRuleDirection.EXPENSE, counterpartyPattern="Vendor", purposeTerms="invoice",
            accountId="a1", propertyId="p1", confidence=90
        )
        val boosted = BankPhase2DEngine.analyze(listOf(transaction),receipts,emptyList(),emptyMap(),rules=listOf(rule)).combinations.first()
        assertTrue(boosted.score >= base.score)
        assertTrue(boosted.score - base.score <= BankPhase2DRulePolicy.MAX_RULE_BONUS)
        val disabled = rule.copy(enabled=false)
        val ignored = BankPhase2DEngine.analyze(listOf(transaction),receipts,emptyList(),emptyMap(),rules=listOf(disabled)).combinations.first()
        assertEquals(base.score, ignored.score)
    }

    @Test fun confirmedRentIsNotReclassifiedAsNormalCombination() {
        val transaction = tx("rent", 500.0)
        val rentReceipt = receipt(1,250.0).copy(hauptkategorie="Miete, Nebenkosten & Kaution")
        val rentReceipt2 = receipt(2,250.0).copy(hauptkategorie="Miete, Nebenkosten & Kaution")
        val assignment = BankRentAssignment(
            assignmentId="ra", transactionId="rent", propertyId="p1", unitId="u1", rentMonth="2026-09",
            tenantReference="tenant", allocatedAmount=500.0, paymentType="MIETE",
            status=BankRentAssignmentStatus.CONFIRMED, source=BankRentAssignmentSource.MANUAL,
            createdAt="c", updatedAt="u"
        )
        val analysis = BankPhase2DEngine.analyze(listOf(transaction),listOf(rentReceipt,rentReceipt2),emptyList(),emptyMap(),rentAssignments=listOf(assignment))
        assertTrue(analysis.combinations.none { "rent" in it.transactionIds })
        assertTrue(analysis.queue.none { "rent" in it.transactionIds && it.type !in setOf(BankReviewType.RENT_REVIEW) })
    }

    @Test fun confirmedLoanAndSpecialRepaymentAreNotNormalReceiptCombinations() {
        val transaction = tx("loan", -500.0)
        val loan = BankLoanAssignment("la","loan",7,"p1",500.0,BankLoanPaymentType.SONDERTILGUNG,"2026-09",BankLoanAssignmentStatus.REVIEW,"MANUAL",createdAt="c",updatedAt="u")
        val analysis = BankPhase2DEngine.analyze(listOf(transaction),listOf(receipt(1,250.0),receipt(2,250.0)),emptyList(),emptyMap(),loanAssignments=listOf(loan))
        assertTrue(analysis.combinations.none { "loan" in it.transactionIds })
        assertTrue(analysis.queue.any { it.type == BankReviewType.LOAN_REVIEW })
    }

    @Test fun propertyConflictBecomesHighPriorityQueueItem() {
        val transaction = tx("p", -100.0, property="p1")
        val r = receipt(1,100.0,property="p2")
        val one = mapOf("p" to BankMatchSuggestion("p",1,90,"HOCH",listOf("Text passt")))
        val queue = BankPhase2DReviewQueue.build(listOf(transaction),listOf(r),emptyList(),one,emptyList())
        assertEquals(BankReviewType.PROPERTY_CONFLICT, queue.single().type)
        assertTrue(queue.single().priority >= 100)
    }

    @Test fun amountConflictBecomesQueueItem() {
        val transaction = tx("a", -1000.0)
        val r = receipt(1,100.0)
        val one = mapOf("a" to BankMatchSuggestion("a",1,70,"MITTEL",listOf("Partner passt")))
        val queue = BankPhase2DReviewQueue.build(listOf(transaction),listOf(r),emptyList(),one,emptyList())
        assertEquals(BankReviewType.AMOUNT_CONFLICT, queue.single().type)
    }

    @Test fun possibleBankDuplicateGetsDedicatedQueueCategory() {
        val a = tx("a")
        val b = tx("b")
        val queue = BankPhase2DReviewQueue.build(listOf(a,b),emptyList(),emptyList(),emptyMap(),emptyList())
        assertEquals(2, queue.count { it.type == BankReviewType.POSSIBLE_DUPLICATE })
        assertTrue(queue.all { it.conflicts.isNotEmpty() })
    }

    @Test fun duplicateReceiptsDowngradeOtherwiseSafeSuggestion() {
        val transaction = tx("t")
        val r1 = receipt(1)
        val r2 = receipt(2)
        val one = mapOf("t" to BankMatchSuggestion("t",1,95,"HOCH",emptyList()))
        val queue = BankPhase2DReviewQueue.build(listOf(transaction),listOf(r1,r2),emptyList(),one,emptyList())
        assertEquals(BankReviewType.POSSIBLE_DUPLICATE, queue.single().type)
        assertEquals("NIEDRIG", queue.single().confidence)
        assertFalse(BankBatchEligibility.evaluate(queue.single()).eligible)
    }

    @Test fun noReceiptRequiredNeverReappearsInReviewQueue() {
        val transaction = tx("no", status=BankReconciliationStatus.NO_RECEIPT_REQUIRED)
        val queue = BankPhase2DReviewQueue.build(listOf(transaction),emptyList(),emptyList(),emptyMap(),emptyList())
        assertTrue(queue.isEmpty())
    }
}
