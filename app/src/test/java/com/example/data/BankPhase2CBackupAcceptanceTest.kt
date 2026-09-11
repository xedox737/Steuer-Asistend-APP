package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankPhase2CBackupAcceptanceTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    @Test fun loanAssignmentsAndRecurringPatternsRestoreIdempotently() = runTest {
        val assignment = BankLoanAssignment(
            assignmentId="loan-assignment-1", transactionId="tx-1", loanId=7, propertyId="property-1",
            allocatedAmount=445.0, paymentType=BankLoanPaymentType.REGULAERE_RATE, period="2026-09",
            splitStatus=BankLoanSplitStatus.PROPOSED, proposedInterest=210.0, proposedPrincipal=235.0,
            interestBasis="Restschuld/Zins/Periode", createdAt="created", updatedAt="updated"
        )
        val pattern = BankRecurringPattern(
            patternId="rec-1", enabled=true, direction=RecurringDirection.EXPENSE,
            normalizedCounterparty="iban:de123", purposeFingerprint="darlehen haus",
            typicalAmount=445.0, amountTolerance=5.0, cadence=RecurringCadence.MONTHLY,
            typicalDay=5, accountId="account-1", propertyId="property-1", occurrenceCount=5,
            confidence=90, lastOccurrence="2026-09-05", nextExpectedStart="2026-10-01",
            nextExpectedEnd="2026-10-10", reasonsText="monatlicher Rhythmus",
            createdAt="created", updatedAt="updated"
        )
        database.bankLoanAssignmentDao().upsert(assignment)
        database.bankRecurringPatternDao().upsert(pattern)

        val payload = SupplementalDriveBackup.createPayload(context, database)
        assertEquals(12, payload.getInt("schemaVersion"))
        assertEquals(1, payload.getJSONArray("bankLoanAssignments").length())
        assertEquals(1, payload.getJSONArray("bankRecurringPatterns").length())
        database.bankLoanAssignmentDao().deleteForTransaction("tx-1")
        database.bankRecurringPatternDao().delete("rec-1")

        SupplementalDriveBackup.restorePayload(context, database, payload)
        SupplementalDriveBackup.restorePayload(context, database, payload)
        val restoredAssignment = database.bankLoanAssignmentDao().getAll().single()
        val restoredPattern = database.bankRecurringPatternDao().getAll().single()
        assertEquals(445.0, restoredAssignment.allocatedAmount, 0.001)
        assertEquals(210.0, restoredAssignment.proposedInterest!!, 0.001)
        assertEquals("property-1", restoredAssignment.propertyId)
        assertEquals(RecurringCadence.MONTHLY, restoredPattern.cadence)
        assertEquals(90, restoredPattern.confidence)
        assertEquals("2026-10-10", restoredPattern.nextExpectedEnd)
    }

    @Test fun oldSchemaNineWithoutPhase2CDataStillRestores() = runTest {
        val old = JSONObject("""{"schemaVersion":9,"loans":[],"bankAccounts":[],"bankTransactions":[],"bankReceiptLinks":[],"bankLearningRules":[],"bankRuleEvidence":[],"bankRentAssignments":[]}""")
        SupplementalDriveBackup.restorePayload(context, database, old)
        assertTrue(database.bankLoanAssignmentDao().getAll().isEmpty())
        assertTrue(database.bankRecurringPatternDao().getAll().isEmpty())
    }

    @Test fun backupContainsNoBankingCredentialsOrSecrets() = runTest {
        context.getSharedPreferences("ai_provider_settings", Context.MODE_PRIVATE).edit()
            .putString("pin", "PIN_SENTINEL")
            .putString("tan", "TAN_SENTINEL")
            .putString("fints_password", "FINTS_SECRET_SENTINEL")
            .putString("oauth_access_token", "OAUTH_SECRET_SENTINEL")
            .putString("api_key", "API_KEY_SENTINEL")
            .apply()
        val serialized = SupplementalDriveBackup.createPayload(context, database).toString()
        listOf("PIN_SENTINEL","TAN_SENTINEL","FINTS_SECRET_SENTINEL","OAUTH_SECRET_SENTINEL","API_KEY_SENTINEL")
            .forEach { assertFalse(serialized.contains(it)) }
    }
}
