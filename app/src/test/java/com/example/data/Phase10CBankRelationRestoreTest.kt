package com.example.data

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase10CBankRelationRestoreTest : Phase10CRestoreFixture() {
    private suspend fun setupSnapshot() {
        seedProperty()
        repository.insert(receipt(1))
        db.loanDao().upsertLoan(Loan(id = 1, propertyId = "property-a"))
        db.bankDao().upsertAccount(BankAccount("a", "Snapshot"))
        db.bankDao().upsertTransaction(BankTransaction("snapshot", "a", "2026-10-17", amount = 3.0, updatedAt = now))
        db.bankDao().upsertLink(BankReceiptLink("snapshot-link", "snapshot", 1, "receipt-1", 3.0))
        backup()
    }
    private suspend fun localReceiptAndTransaction() {
        repository.insert(receipt(71))
        db.bankDao().upsertTransaction(BankTransaction("local", "a", "2026-10-17", amount = 3.0))
    }
    @Test fun localBankReceiptLinkCannotOutliveReceiptOrTransaction() = runTest {
        setupSnapshot(); localReceiptAndTransaction()
        db.bankDao().upsertLink(BankReceiptLink("local-link", "local", 71, "receipt-71", 3.0))
        repeat(2) { restore(RestoreMode.MERGE); assertNotNull(db.bankDao().getLink("local-link")) }
        repeat(2) {
            restore(RestoreMode.REPLACE_FULL)
            assertNull(repository.getReceiptById(71))
            assertNull(db.bankDao().getLink("local-link"))
            assertEquals(listOf("snapshot-link"), db.bankDao().getAllLinks().map { it.linkId })
            assertTrue(db.bankDao().getAllLinks().all {
                db.bankDao().getTransaction(it.transactionId) != null && repository.getReceiptById(it.receiptId) != null
            })
        }
    }
    @Test fun localRentReceiptAssignmentCannotOutliveReceiptOrSnapshotUnit() = runTest {
        setupSnapshot(); localReceiptAndTransaction()
        db.bankRentAssignmentDao().upsert(BankRentAssignment("local-rent", "local", "property-a", "unit-a",
            "2026-10", "Test", 3.0, "MIETE", receiptId = 71, createdAt = now, updatedAt = now))
        repeat(2) { restore(RestoreMode.MERGE); assertEquals(71, db.bankRentAssignmentDao().getById("local-rent")!!.receiptId) }
        repeat(2) { restore(RestoreMode.REPLACE_FULL); assertTrue(db.bankRentAssignmentDao().getAll().isEmpty()) }
    }
    @Test fun localRuleEvidenceCannotOutliveReceipt() = runTest {
        setupSnapshot(); localReceiptAndTransaction()
        db.bankLearningRuleDao().insertEvidence(BankRuleEvidence("local-evidence", "candidate", transactionId = "local",
            receiptId = 71, direction = BankRuleDirection.EXPENSE))
        repeat(2) { restore(RestoreMode.MERGE); assertEquals(1, db.bankLearningRuleDao().getAllEvidence().size) }
        repeat(2) { restore(RestoreMode.REPLACE_FULL); assertTrue(db.bankLearningRuleDao().getAllEvidence().isEmpty()) }
    }
    @Test fun bankSnapshotReplacesAllLocalDomainsAndMergeKeepsClassificationAndRemovedLink() = runTest {
        setupSnapshot()
        val newer = db.bankDao().getTransaction("snapshot")!!.copy(category = "local", updatedAt = "2026-10-17T12:00:00Z")
        db.bankDao().upsertTransaction(newer)
        db.bankDao().deleteLink("snapshot-link")
        localReceiptAndTransaction()
        db.bankDao().upsertAccount(BankAccount("local-account", "Lokal"))
        val property = repository.getPropertyByPropertyId("property-a")!!
        repository.updatePropertyMetadata(property.copy(wohneinheiten = UnitIdentity.encode(listOf(
            UnitIdentity.Entry("unit-a", "WE 01, EG links"), UnitIdentity.Entry("local-unit", "Lokal")))))
        context.getSharedPreferences("wohneinheiten_prefs", 0).edit()
            .putFloat("property_property-a_unitid_local-unit_area", 41.25f).commit()
        db.bankDao().upsertTransaction(db.bankDao().getTransaction("local")!!.copy(propertyId = "property-a", unitId = "local-unit"))
        db.bankLearningRuleDao().upsertRule(BankLearningRule("local-rule", "Lokal", propertyId = "property-a", unitId = "local-unit"))
        db.loanDao().upsertLoan(Loan(id = 2, propertyId = "property-a"))
        db.bankLoanAssignmentDao().upsert(BankLoanAssignment("local-loan", "local", 2, "property-a", 3.0,
            "RATE", "2026-10", createdAt = now, updatedAt = now))
        db.bankRecurringPatternDao().upsert(BankRecurringPattern("local-pattern", direction = "EXPENSE",
            normalizedCounterparty = "Test", purposeFingerprint = "Miete", typicalAmount = 3.0, amountTolerance = 0.0,
            cadence = "MONTHLY", typicalDay = 1, accountId = "local-account", occurrenceCount = 3, confidence = 90,
            lastOccurrence = "2026-10-01", nextExpectedStart = "2026-11-01", nextExpectedEnd = "2026-11-02",
            createdAt = now, updatedAt = now))
        repeat(2) {
            restore(RestoreMode.MERGE)
            assertEquals(newer, db.bankDao().getTransaction("snapshot"))
            assertNull(db.bankDao().getLink("snapshot-link"))
            assertNotNull(db.bankDao().getTransaction("local"))
            assertEquals(1, db.bankLoanAssignmentDao().getAll().size)
        }
        repeat(2) {
            restore(RestoreMode.REPLACE_FULL)
            val restoredProperty = repository.getPropertyByPropertyId("property-a")!!
            val unitIds = UnitIdentity.read(restoredProperty.wohneinheiten, restoredProperty.propertyId,
                context.getSharedPreferences("wohneinheiten_prefs", 0)).entries.map { it.unitId }.toSet()
            assertTrue("orphan bank unitId", db.bankDao().getAllTransactions().none {
                it.propertyId == "property-a" && it.unitId.isNotBlank() && it.unitId !in unitIds
            })
            assertEquals(listOf("snapshot"), db.bankDao().getAllTransactions().map { it.transactionId })
            assertEquals(listOf("a"), db.bankDao().getAllAccounts().map { it.accountId })
            assertEquals("", db.bankDao().getTransaction("snapshot")!!.category)
            assertNotNull(db.bankDao().getLink("snapshot-link"))
            assertTrue(db.bankLoanAssignmentDao().getAll().isEmpty())
            assertTrue(db.bankLearningRuleDao().getAllRules().isEmpty())
            assertTrue(db.bankRecurringPatternDao().getAll().isEmpty())
            assertEquals(listOf(1), db.loanDao().getAllLoans().map { it.id })
        }
    }
}
