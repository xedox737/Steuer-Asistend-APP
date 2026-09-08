package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankLearningRulesAcceptanceTest {
    private lateinit var db: AppDatabase
    private lateinit var context: Context

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun close() = db.close()

    private fun tx(
        id: String = "t1", counterparty: String = "Hornbach GmbH", purpose: String = "Material Wohnung 2",
        amount: Double = -84.50, accountId: String = "konto-a", propertyId: String = "objekt-1", unitId: String = "WE2"
    ) = BankTransaction(id, accountId, "2026-09-07", amount = amount, counterparty = counterparty, purpose = purpose, propertyId = propertyId, unitId = unitId)

    private fun receipt(id: Int = 1) = Receipt(
        id = id, aussteller = "Hornbach", datum = "2026-09-07", uhrzeit = "12:00", bruttobetrag = 84.50,
        hauptkategorie = "Instandhaltung", unterkategorie = "Material", kontoNr = "", beschreibung = "Material",
        wohneinheit = "WE2", zahlungsart = "Überweisung", propertyId = "objekt-1"
    )

    private fun activeRule(
        id: String = "r1", enabled: Boolean = true, counterparty: String = "Hornbach", purpose: String = "material",
        accountId: String = "", propertyId: String = "", unitId: String = "", category: String = "Instandhaltung",
        amountMin: Double? = null, amountMax: Double? = null
    ) = BankLearningRule(
        ruleId = id, displayName = "Hornbach Material", enabled = enabled, state = BankRuleState.ACTIVE,
        transactionDirection = BankRuleDirection.EXPENSE, counterpartyPattern = counterparty, purposeTerms = purpose,
        amountMin = amountMin, amountMax = amountMax, accountId = accountId, propertyId = propertyId, unitId = unitId,
        receiptVendorTarget = "Hornbach", receiptCategoryTarget = category, receiptSubcategoryTarget = "Material",
        paymentMethodTarget = "Überweisung", evidenceCount = 3, successCount = 3, confidence = 80, source = BankRuleSource.USER_CREATED
    )

    @Test fun oneConfirmationDoesNotCreateRule() = runTest {
        val service = BankLearningService(db.bankLearningRuleDao()) { "2026-09-07T22:00:00Z" }
        assertNull(service.recordConfirmed(tx(), receipt()))
        assertEquals(1, db.bankLearningRuleDao().getAllEvidence().size)
        assertEquals(0, db.bankLearningRuleDao().getAllRules().size)
    }

    @Test fun threeConfirmationsCreateProposalButNotActiveRule() = runTest {
        val service = BankLearningService(db.bankLearningRuleDao()) { "2026-09-07T22:00:00Z" }
        service.recordConfirmed(tx("t1"), receipt(1))
        service.recordConfirmed(tx("t2"), receipt(2))
        val proposed = service.recordConfirmed(tx("t3"), receipt(3))
        assertNotNull(proposed)
        assertEquals(BankRuleState.PROPOSED, proposed!!.state)
        assertFalse(proposed.enabled)
        assertEquals(3, proposed.evidenceCount)
        assertEquals(BankRuleSource.LEARNED_FROM_CONFIRMATIONS, proposed.source)
    }

    @Test fun acceptedProposalBecomesActiveAndRejectionRemainsUserControlled() = runTest {
        val dao = db.bankLearningRuleDao()
        dao.upsertRule(activeRule().copy(enabled = false, state = BankRuleState.PROPOSED, source = BankRuleSource.LEARNED_FROM_CONFIRMATIONS))
        val service = BankLearningService(dao) { "2026-09-07T22:00:00Z" }
        service.accept("r1")
        assertTrue(dao.getRule("r1")!!.enabled)
        assertEquals(BankRuleState.ACTIVE, dao.getRule("r1")!!.state)
        service.recordRuleRejection("r1")
        assertEquals(1, dao.getRule("r1")!!.rejectionCount)
    }

    @Test fun counterpartyPurposeAmountAndDirectionAreDeterministic() {
        val rule = activeRule(amountMin = 80.0, amountMax = 90.0)
        assertNotNull(BankRuleEngine.score(tx(), rule))
        assertNull(BankRuleEngine.score(tx(amount = -120.0), rule))
        assertNull(BankRuleEngine.score(tx(amount = 84.5), rule))
        assertNull(BankRuleEngine.score(tx(counterparty = "IKEA"), rule))
        assertNull(BankRuleEngine.score(tx(purpose = "Lebensmittel"), rule))
    }

    @Test fun accountPropertyAndUnitScopeAreEnforced() {
        val rule = activeRule(accountId = "konto-a", propertyId = "objekt-1", unitId = "WE2")
        assertNotNull(BankRuleEngine.score(tx(), rule))
        assertNull(BankRuleEngine.score(tx(accountId = "konto-b"), rule))
        assertNull(BankRuleEngine.score(tx(propertyId = "objekt-2"), rule))
        assertNull(BankRuleEngine.score(tx(unitId = "WE3"), rule))
    }

    @Test fun disabledRuleHasNoEffect() {
        assertNull(BankRuleEngine.score(tx(), activeRule(enabled = false)))
        assertTrue(BankRuleEngine.evaluate(tx(), listOf(activeRule(enabled = false))).suggestions.isEmpty())
    }

    @Test fun equalStrongRulesWithDifferentTargetsCreateConflict() {
        val a = activeRule(id = "a", category = "Instandhaltung")
        val b = activeRule(id = "b", category = "Betriebskosten")
        val result = BankRuleEngine.evaluate(tx(), listOf(a, b))
        assertTrue(result.hasConflict)
        assertEquals("Mehrere Regeln passen", result.conflictReason)
    }

    @Test fun ruleBonusIsBoundedAndNeverRescuesImplausibleBaseMatch() {
        val base = BankMatchSuggestion("t1", 1, 70, "MITTEL", listOf("Basis"))
        val enhanced = BankRuleScoring.enhance(base, tx(), receipt(), listOf(activeRule()))
        assertTrue(enhanced.score > base.score)
        assertTrue(enhanced.score <= base.score + BankLearningThresholds.MAX_RULE_BONUS)
        val implausible = base.copy(score = 20)
        assertEquals(implausible, BankRuleScoring.enhance(implausible, tx(), receipt(), listOf(activeRule())))
    }

    @Test fun incompatibleReceiptTargetDoesNotReceiveRuleBonus() {
        val base = BankMatchSuggestion("t1", 1, 70, "MITTEL", listOf("Basis"))
        val wrongRule = activeRule(category = "Versicherung")
        assertEquals(base, BankRuleScoring.enhance(base, tx(), receipt(), listOf(wrongRule)))
    }

    @Test fun manualRuleWorksImmediatelyAndCanBeEdited() = runTest {
        val dao = db.bankLearningRuleDao()
        val manual = activeRule().copy(source = BankRuleSource.USER_CREATED)
        dao.upsertRule(manual)
        assertNotNull(BankRuleEngine.score(tx(), dao.getRule("r1")!!))
        dao.upsertRule(manual.copy(displayName = "Bearbeitet", source = BankRuleSource.USER_EDITED, enabled = false))
        assertEquals(BankRuleSource.USER_EDITED, dao.getRule("r1")!!.source)
        assertNull(BankRuleEngine.score(tx(), dao.getRule("r1")!!))
    }

    @Test fun rejectedLearnedProposalIsNotRecreated() = runTest {
        val dao = db.bankLearningRuleDao()
        val service = BankLearningService(dao) { "2026-09-07T22:00:00Z" }
        service.recordConfirmed(tx("t1"), receipt(1))
        service.recordConfirmed(tx("t2"), receipt(2))
        val proposal = service.recordConfirmed(tx("t3"), receipt(3))!!
        service.reject(proposal.ruleId)
        val again = service.recordConfirmed(tx("t4"), receipt(4))
        assertEquals(BankRuleState.REJECTED, again!!.state)
        assertFalse(again.enabled)
    }

    @Test fun confidenceChangesWithSuccessAndRejection() = runTest {
        val dao = db.bankLearningRuleDao()
        dao.upsertRule(activeRule().copy(evidenceCount = 3, successCount = 1, rejectionCount = 0, confidence = 50))
        val service = BankLearningService(dao) { "2026-09-07T22:00:00Z" }
        service.recordRuleSuccess("r1")
        val afterSuccess = dao.getRule("r1")!!
        assertEquals(4, afterSuccess.evidenceCount)
        assertEquals(2, afterSuccess.successCount)
        service.recordRuleRejection("r1")
        val afterRejection = dao.getRule("r1")!!
        assertEquals(1, afterRejection.rejectionCount)
        assertTrue(afterRejection.confidence in 0..100)
    }
}
