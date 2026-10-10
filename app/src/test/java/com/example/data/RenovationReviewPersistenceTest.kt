package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RenovationReviewPersistenceTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var store: RenovationReviewStore
    private val measure = RenovationMeasure(id = "bath", propertyId = "a", unitId = "a-unit", name = "Bad",
        advisorNote = "Bitte zusammen prüfen", evidence = listOf(RenovationEvidence("doc", RenovationEvidenceRole.ANGEBOT)))
    private val relation = RenovationReceiptRelation("internal-r", "a", measure.id, RenovationTaxStatus.FUER_15_PROZENT_PRUEFUNG, advisorMarked = true, confirmedNetAmount = 100.0)
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        store = RenovationReviewStore(context)
    }
    @After fun close() { db.close(); PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() } }
    private suspend fun populate() { store.saveMeasure(measure); store.saveRelation(relation) }

    @Test fun reloadPreservesIdNoteTaxStatusAdvisorMarkAndDocumentReference() = runTest {
        populate(); val reloaded = RenovationReviewStore(context); reloaded.refresh()
        val result = reloaded.snapshot.value
        assertEquals(measure.id, result.measures.single().id); assertEquals(measure.advisorNote, result.measures.single().advisorNote)
        assertEquals(measure.evidence, result.measures.single().evidence); assertEquals(relation.copy(updatedAt = result.relations.single().updatedAt), result.relations.single())
    }
    @Test fun backupRestoreTwicePreservesAllRelationsIncludingCredit() = runTest {
        populate(); store.saveRelation(relation.copy(receiptInternalId = "credit-r", confirmedNetAmount = 20.0))
        val payload = SupplementalDriveBackup.createPayload(context, db)
        assertEquals(15, payload.getInt("schemaVersion"))
        context.getSharedPreferences(RenovationReviewStore.PREFS, 0).edit().clear().commit()
        repeat(2) { SupplementalDriveBackup.restorePayload(context, db, payload, restoreMode = RestoreMode.MERGE) }
        store.refresh(); assertEquals(1, store.snapshot.value.measures.size); assertEquals(2, store.snapshot.value.relations.size)
        assertEquals("Bitte zusammen prüfen", store.snapshot.value.measures.single().advisorNote)
        assertEquals(20.0, store.snapshot.value.relations.single { it.receiptInternalId == "credit-r" }.confirmedNetAmount!!, .01)
    }
    @Test fun olderBackupCannotUndoNewNoteStatusOrMovedReceipt() = runTest {
        populate(); val payload = SupplementalDriveBackup.createPayload(context, db)
        store.saveMeasure(measure.copy(advisorNote = "Neu bestätigt", status = RenovationStatus.ABGESCHLOSSEN, taxStatus = RenovationTaxStatus.STEUERBERATER_BESTAETIGT))
        store.saveMeasure(measure.copy(id = "window", name = "Fenster"))
        store.saveRelation(relation.copy(renovationMeasureId = "window"))
        repeat(2) { SupplementalDriveBackup.restorePayload(context, db, payload) }
        store.refresh(); val current = store.snapshot.value
        assertEquals(2, current.measures.size); assertEquals("Neu bestätigt", current.measures.single { it.id == measure.id }.advisorNote)
        assertEquals(RenovationStatus.ABGESCHLOSSEN, current.measures.single { it.id == measure.id }.status)
        assertEquals("window", current.relations.single().renovationMeasureId)
    }
    @Test fun explicitRemovalCannotBeResurrectedByMerge() = runTest {
        populate(); val payload = SupplementalDriveBackup.createPayload(context, db)
        store.saveRelation(relation.copy(renovationMeasureId = "", taxStatus = RenovationTaxStatus.NICHT_GEPRUEFT, advisorMarked = false))
        repeat(2) { SupplementalDriveBackup.restorePayload(context, db, payload) }; store.refresh()
        assertEquals("", store.snapshot.value.relations.single().renovationMeasureId)
    }
    @Test fun replaceRestoresSnapshotWhileMergeImportsMissingMeasureIds() = runTest {
        populate(); val payload = SupplementalDriveBackup.createPayload(context, db)
        store.saveMeasure(measure.copy(id = "new-local", name = "Lokal"))
        SupplementalDriveBackup.restorePayload(context, db, payload, restoreMode = RestoreMode.REPLACE_FULL); store.refresh()
        assertEquals(1, store.snapshot.value.measures.size)
    }
    @Test fun oldSchemaWithoutNewStoreDoesNotEraseExistingReview() = runTest {
        populate(); val payload = SupplementalDriveBackup.createPayload(context, db).put("schemaVersion", 14)
        payload.remove(RenovationReviewStore.PAYLOAD_KEY)
        SupplementalDriveBackup.restorePayload(context, db, payload, restoreMode = RestoreMode.REPLACE_FULL); store.refresh()
        assertEquals(1, store.snapshot.value.measures.size)
    }
    @Test fun invalidSchemaOrDamagedRelationshipFailsBeforeRestoreMutation() = runTest {
        populate(); val payload = SupplementalDriveBackup.createPayload(context, db)
        payload.getJSONObject(RenovationReviewStore.PAYLOAD_KEY).getJSONObject("receipt_internal-r").put("value", "{broken")
        assertTrue(runCatching { SupplementalDriveBackup.restorePayload(context, db, payload) }.isFailure)
        store.refresh(); assertEquals(measure.advisorNote, store.snapshot.value.measures.single().advisorNote)
        val future = JSONObject().put("schemaVersion", 16)
        assertTrue(runCatching { SupplementalDriveBackup.restorePayload(context, db, future) }.isFailure)
    }
    @Test fun damagedLocalJsonRemainsStoredAndIsReported() = runTest {
        context.getSharedPreferences(RenovationReviewStore.PREFS, 0).edit().putString("measure_broken", "broken").commit()
        store.refresh(); assertEquals(1, store.snapshot.value.errors.size)
        assertEquals("broken", context.getSharedPreferences(RenovationReviewStore.PREFS, 0).getString("measure_broken", ""))
    }
    @Test fun measureValidationRejectsBlankNamesAndInvalidChronology() {
        listOf(measure.copy(name = " "), measure.copy(startDate = "2026-02-30"),
            measure.copy(startDate = "2026-10-10", endDate = "2026-10-09"), measure.copy(endDate = "2026-10-10")).forEach {
            assertTrue(runCatching { RenovationReviewStore.validateMeasure(it) }.isFailure)
        }
        RenovationReviewStore.validateMeasure(measure.copy(startDate = "2026-10-10", endDate = "2026-10-10"))
    }
    @Test fun unitAndObjectWideMeasuresUseStableIds() = runTest {
        store.saveMeasure(measure); store.saveMeasure(measure.copy(id = "general", unitId = ""))
        assertEquals(setOf("", "a-unit"), store.snapshot.value.measures.map { it.unitId }.toSet())
    }
    @Test fun relationRejectsForeignMeasureAndInvalidNetSigns() = runTest {
        store.saveMeasure(measure)
        assertTrue(runCatching { store.saveRelation(relation.copy(propertyId = "b")) }.isFailure)
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertTrue(runCatching { store.saveRelation(relation.copy(confirmedNetAmount = it)) }.isFailure)
        }
        assertTrue(store.snapshot.value.relations.isEmpty())
    }
}
