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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankBackup7AcceptanceTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun close() = database.close()

    @Test fun schema8PreservesAuditNoReceiptReasonAndIsIdempotent() = runTest {
        database.bankDao().upsertAccount(BankAccount("a", "Haus", bankName = "Sparkasse", accountHolder = "Sergej"))
        database.bankDao().upsertTransaction(BankTransaction(
            "t", "a", "2026-09-04", amount = -5.0, reconciliationStatus = BankReconciliationStatus.NO_RECEIPT_REQUIRED,
            noReceiptReason = "Bankgebühr", importedAt = "import", updatedAt = "status-change"
        ))
        val payload = SupplementalDriveBackup.createPayload(context, database)
        assertEquals(8, payload.getInt("schemaVersion"))
        database.bankDao().upsertTransaction(database.bankDao().getTransaction("t")!!.copy(reconciliationStatus = BankReconciliationStatus.OPEN, noReceiptReason = "", updatedAt = "other"))
        SupplementalDriveBackup.restorePayload(context, database, payload)
        SupplementalDriveBackup.restorePayload(context, database, payload)
        val restored = database.bankDao().getTransaction("t")!!
        assertEquals(BankReconciliationStatus.NO_RECEIPT_REQUIRED, restored.reconciliationStatus)
        assertEquals("Bankgebühr", restored.noReceiptReason)
        assertEquals("status-change", restored.updatedAt)
        assertEquals(1, database.bankDao().getAllTransactions().count { it.transactionId == "t" })
    }

    @Test fun noReceiptRequiredSurvivesDatabaseRestart() = runTest {
        val databaseName = "bank-no-receipt-restart-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        var first: AppDatabase? = null
        var reopened: AppDatabase? = null
        try {
            first = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
                .allowMainThreadQueries()
                .build()
            first.bankDao().upsertAccount(BankAccount("restart-account", "Hauskonto", bankName = "Sparkasse"))
            first.bankDao().upsertTransaction(
                BankTransaction(
                    transactionId = "restart-transaction",
                    accountId = "restart-account",
                    bookingDate = "2026-09-07",
                    amount = -7.50,
                    reconciliationStatus = BankReconciliationStatus.NO_RECEIPT_REQUIRED,
                    noReceiptReason = "Bankgebühr",
                    importedAt = "2026-09-07T20:00:00Z",
                    updatedAt = "2026-09-07T20:01:00Z"
                )
            )
            first.close()
            first = null

            reopened = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
                .allowMainThreadQueries()
                .build()
            val restored = reopened.bankDao().getTransaction("restart-transaction")!!
            assertEquals(BankReconciliationStatus.NO_RECEIPT_REQUIRED, restored.reconciliationStatus)
            assertEquals("Bankgebühr", restored.noReceiptReason)
            assertEquals("2026-09-07T20:01:00Z", restored.updatedAt)
        } finally {
            first?.close()
            reopened?.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test fun schema7WithoutRuleArraysStillRestores() = runTest {
        val old = JSONObject("""
            {"schemaVersion":7,
             "bankAccounts":[{"accountId":"a","displayName":"Alt","bankName":"Sparkasse","accountHolder":"","iban":"DE","currency":"EUR","source":"CSV","active":true,"createdAt":"","updatedAt":""}],
             "bankTransactions":[{"transactionId":"t","accountId":"a","bookingDate":"2026-09-01","valueDate":"","amount":-10.0,"currency":"EUR","counterparty":"Alt","counterpartyIban":"","purpose":"","bankReference":"","source":"CSV","propertyId":"p","unitId":"","importFileName":"old.csv","importRunId":"run","reconciliationStatus":"OPEN","noReceiptReason":"","importedAt":"import","updatedAt":""}],
             "bankReceiptLinks":[]}
        """.trimIndent())
        SupplementalDriveBackup.restorePayload(context, database, old)
        assertEquals("", database.bankDao().getTransaction("t")?.updatedAt)
        assertEquals("old.csv", database.bankDao().getTransaction("t")?.importFileName)
        assertEquals(0, database.bankLearningRuleDao().getAllRules().size)
        assertEquals(0, database.bankLearningRuleDao().getAllEvidence().size)
    }

    @Test fun schema6WithoutUpdatedAtStillRestores() = runTest {
        val old = JSONObject("""
            {"schemaVersion":6,
             "bankAccounts":[{"accountId":"a","displayName":"Alt","bankName":"Sparkasse","accountHolder":"","iban":"DE","currency":"EUR","source":"CSV","active":true,"createdAt":"","updatedAt":""}],
             "bankTransactions":[{"transactionId":"t","accountId":"a","bookingDate":"2026-09-01","valueDate":"","amount":-10.0,"currency":"EUR","counterparty":"Alt","counterpartyIban":"","purpose":"","bankReference":"","source":"CSV","propertyId":"p","unitId":"","importFileName":"old.csv","importRunId":"run","reconciliationStatus":"OPEN","noReceiptReason":"","importedAt":"import"}],
             "bankReceiptLinks":[]}
        """.trimIndent())
        SupplementalDriveBackup.restorePayload(context, database, old)
        assertEquals("", database.bankDao().getTransaction("t")?.updatedAt)
        assertEquals("old.csv", database.bankDao().getTransaction("t")?.importFileName)
    }

    @Test fun bankBackupContainsNoCredentialLikeFields() = runTest {
        val serialized = SupplementalDriveBackup.createPayload(context, database).toString().lowercase()
        listOf("pin", "fints", "access_token", "oauth", "api_key", "apikey", "openai", "gemini_api_key").forEach {
            assertFalse("unexpected secret field marker $it", serialized.contains(it))
        }
    }
}
