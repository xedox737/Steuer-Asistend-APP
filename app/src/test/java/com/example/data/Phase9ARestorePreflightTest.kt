package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase9ARestorePreflightTest {
    private lateinit var db: AppDatabase
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() = db.close()

    @Test fun badPresentLoanValuesBlockWholeRestoreBeforeAnyRoomOrPreferenceMutation() = runTest {
        val existing = Loan(id = 77, restschuld = 80000.0, propertyId = "local")
        db.loanDao().upsertLoan(existing)
        val prefs = context.getSharedPreferences("rent_plan_prefs", 0)
        prefs.edit().putFloat("phase9a-preserve", 12.5f).commit()
        try {
            for (field in listOf("restschuld", "vermietungsanteilProzent", "monatlicheRate")) {
                val good = JSONObject().put("id", 81).put("propertyId", "a").put("darlehensbetrag", 100000).put("restschuld", 80000).put("sollzinsProzent", 3)
                val bad = JSONObject(good.toString()).put("id", 82).put(field, "kaputt")
                val payload = JSONObject().put("schemaVersion", 15).put("loans", JSONArray().put(good).put(bad))
                assertTrue(runCatching { SupplementalDriveBackup.restorePayload(context, db, payload) }.isFailure)
                assertEquals(listOf(existing), db.loanDao().getAllLoans())
                assertEquals(12.5f, prefs.getFloat("phase9a-preserve", 0f))
            }
        } finally { prefs.edit().remove("phase9a-preserve").commit() }
    }

    @Test fun oldMissingOptionalFieldsRetainDefinedDefaultsAndMergeIsIdempotent() = runTest {
        val loan = JSONObject().put("id", 81).put("propertyId", "a").put("darlehensbetrag", 100000).put("restschuld", 80000).put("sollzinsProzent", 3)
        val payload = JSONObject().put("schemaVersion", 1).put("loans", JSONArray().put(loan))
        repeat(2) { SupplementalDriveBackup.restorePayload(context, db, payload) }
        val restored = db.loanDao().getAllLoans().single()
        assertEquals(81, restored.id); assertEquals("a", restored.propertyId)
        assertEquals(100.0, restored.vermietungsanteilProzent, 0.0); assertEquals(0.0, restored.monatlicheRate, 0.0)
    }

    @Test fun malformedPropertyUnitAndLoanChronologyArePreflightErrors() = runTest {
        val repository = ReceiptRepository(db.receiptDao(), db.propertyDao())
        val drive = DrivePersistenceRepository(context, repository)
        assertTrue(runCatching { drive.parsePropertyMetadata("""{"id":1,"wohnflaeche":"kaputt"}""") }.isFailure)
        assertTrue(runCatching { drive.parseWohneinheiten("""{"units":[{"name":"A","wohnflaeche":"kaputt"}]}""") }.isFailure)
        val loan = JSONObject().put("id", 81).put("darlehensbetrag", 100000).put("restschuld", 80000).put("sollzinsProzent", 3)
            .put("startDatum", "2026-10-01").put("laufzeitBis", "2026-09-01")
        assertTrue(runCatching { SupplementalDriveBackup.restorePayload(context, db, JSONObject().put("schemaVersion", 15).put("loans", JSONArray().put(loan))) }.isFailure)
        assertTrue(db.loanDao().getAllLoans().isEmpty()); assertTrue(db.propertyDao().getAllProperties().isEmpty())
        val property = JSONObject().put("id", 1).put("propertyId", "a").put("wohnflaeche", "kaputt")
        assertTrue(runCatching { SupplementalDriveBackup.restorePayload(context, db, JSONObject().put("schemaVersion", 15).put("properties", JSONArray().put(property))) }.isFailure)
        assertTrue(db.propertyDao().getAllProperties().isEmpty())
    }

    @Test fun coordinatorPreflightFailureNeverInvokesEitherMutatingRestore() = runTest {
        var calls = 0
        val outcome = FullRestoreCoordinator.execute(
            coreRestore = { calls++; DriveRestoreReport("test", isSuccess = true) },
            supplementalRestore = { calls++; SupplementalDriveBackup.Result(true, "ok") },
            preflight = { SupplementalDriveBackup.validatePayload(JSONObject().put("schemaVersion", 15)
                .put("loans", JSONArray().put(JSONObject().put("id", 81).put("restschuld", "kaputt")))) })
        assertFalse(outcome.isSuccess); assertEquals(0, calls); assertFalse(outcome.supplementalAttempted)
        assertFalse(outcome.message.contains("kaputt"))
        assertTrue(outcome.message.contains("81 (Darlehen)"))
    }
}
