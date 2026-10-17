package com.example.data

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase10CPortfolioRelationRestoreTest : Phase10CRestoreFixture() {
    @Test fun explicitReplaceRemovesLocalDocumentAndPropertyAfaReferenceTogether() = runTest {
        seedProperty(); repository.insert(receipt(1)); backup()
        repository.insert(receipt(71))
        repository.updatePropertyMetadata(PropertyMetadata(id = 2, propertyId = "local-property",
            afaShorterDocumentId = "local-doc", afaShorterConfirmed = true))
        db.managedDocumentDao().upsert(ManagedDocument("local-doc", "local-property", receiptInternalId = "receipt-71"))
        repeat(2) {
            restore(RestoreMode.MERGE)
            assertNotNull(db.managedDocumentDao().getById("local-doc"))
            assertNotNull(repository.getPropertyByPropertyId("local-property"))
        }
        repeat(2) {
            restore(RestoreMode.REPLACE_FULL)
            assertNull(repository.getReceiptById(71))
            assertNull(db.managedDocumentDao().getById("local-doc"))
            assertNull(repository.getPropertyByPropertyId("local-property"))
            assertEquals(listOf("property-a"), repository.getAllProperties().map { it.propertyId })
        }
    }
    @Test fun preferenceRelationsAlreadyReplaceRenovationTenancyTasksAndUnitDataTogether() = runTest {
        seedProperty(); repository.insert(receipt(1)); backup()
        val renovation = RenovationReviewStore(context)
        repository.insert(receipt(71))
        renovation.saveMeasure(RenovationMeasure("local-measure", "property-a", "unit-a", "Bad"))
        renovation.saveRelation(RenovationReceiptRelation("receipt-71", "property-a", "local-measure"))
        context.getSharedPreferences("wohneinheiten_prefs", 0).edit().putFloat("property_property-a_unitid_local-unit_area", 41.25f).commit()
        val prefs = listOf("tenant_history_prefs", "property_tasks_prefs")
        prefs.forEach { context.getSharedPreferences(it, 0).edit().putString("local", "[]").commit() }
        restore(RestoreMode.REPLACE_FULL)
        renovation.refresh()
        assertTrue(renovation.snapshot.value.relations.isEmpty())
        assertFalse(context.getSharedPreferences("wohneinheiten_prefs", 0).contains("property_property-a_unitid_local-unit_area"))
        prefs.forEach { assertFalse(context.getSharedPreferences(it, 0).contains("local")) }
    }
    @Test fun absentLegacyArraysDoNotMeanEmptyAndMergeFlagCannotDeleteDocuments() = runTest {
        seedProperty()
        db.managedDocumentDao().upsert(ManagedDocument("local", "property-a"))
        db.loanDao().upsertLoan(Loan(id = 8))
        SupplementalDriveBackup.restorePayload(context, db, JSONObject("""{"schemaVersion":1}"""), restoreMode = RestoreMode.REPLACE_FULL)
        assertNotNull(db.managedDocumentDao().getById("local"))
        assertEquals(8, db.loanDao().getAllLoans().single().id)
        SupplementalDriveBackup.restorePayload(context, db, JSONObject("""{"schemaVersion":15,"managedDocuments":[]}"""),
            replaceManagedDocuments = true, restoreMode = RestoreMode.MERGE)
        assertNotNull(db.managedDocumentDao().getById("local"))
    }
}
