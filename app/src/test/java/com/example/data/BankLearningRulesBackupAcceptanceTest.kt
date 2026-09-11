package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankLearningRulesBackupAcceptanceTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun close() = db.close()

    @Test fun currentSchemaBacksUpAndRestoresRulesAndEvidenceIdempotently() = runTest {
        val dao = db.bankLearningRuleDao()
        dao.upsertRule(BankLearningRule(
            ruleId = "r1", displayName = "Hornbach", enabled = true, state = BankRuleState.ACTIVE,
            counterpartyPattern = "Hornbach", purposeTerms = "material", accountId = "a", propertyId = "p", unitId = "WE2",
            receiptVendorTarget = "Hornbach", receiptCategoryTarget = "Instandhaltung", receiptSubcategoryTarget = "Material",
            paymentMethodTarget = "Überweisung", evidenceCount = 4, successCount = 3, rejectionCount = 1, confidence = 81,
            source = BankRuleSource.USER_EDITED, createdAt = "c", updatedAt = "u", lastMatchedAt = "m"
        ))
        dao.insertEvidence(BankRuleEvidence(
            evidenceId = "e1", candidateKey = "k", ruleId = "r1", transactionId = "t1", receiptId = 7,
            direction = BankRuleDirection.EXPENSE, counterparty = "Hornbach", purposeTerms = "material", accountId = "a",
            propertyId = "p", unitId = "WE2", vendorTarget = "Hornbach", categoryTarget = "Instandhaltung",
            subcategoryTarget = "Material", paymentMethodTarget = "Überweisung", createdAt = "c"
        ))
        val payload = SupplementalDriveBackup.createPayload(context, db)
        assertEquals(12, payload.getInt("schemaVersion"))
        assertEquals(1, payload.getJSONArray("bankLearningRules").length())
        assertEquals(1, payload.getJSONArray("bankRuleEvidence").length())

        dao.deleteRule("r1")
        dao.deleteEvidence("e1")
        SupplementalDriveBackup.restorePayload(context, db, payload)
        SupplementalDriveBackup.restorePayload(context, db, payload)

        val restored = dao.getRule("r1")!!
        assertEquals("Hornbach", restored.displayName)
        assertEquals(BankRuleSource.USER_EDITED, restored.source)
        assertEquals(81, restored.confidence)
        assertEquals(1, dao.getAllEvidence().count { it.evidenceId == "e1" })
    }

    @Test fun ruleBackupContainsNoSecretMaterial() = runTest {
        db.bankLearningRuleDao().upsertRule(BankLearningRule(ruleId = "r", displayName = "Regel", source = BankRuleSource.USER_CREATED))
        val text = SupplementalDriveBackup.createPayload(context, db).toString().lowercase()
        listOf("access_token", "refresh_token", "client_secret", "api_key", "apikey", "password", "fints_pin").forEach {
            assertFalse("secret-like field must not be backed up: $it", text.contains(it))
        }
    }
}
