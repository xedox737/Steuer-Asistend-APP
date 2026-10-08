package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.PropertyTask
import com.example.ui.PropertyTaskStore
import com.example.util.DatevProfileService
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PreferenceBackupCompletenessTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = newDatabase()
        clearPreferences()
    }
    @After fun tearDown() { database.close(); clearPreferences() }
    private fun newDatabase() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries().build()
    private fun clearPreferences() = PersistentPreferenceInventory.stores.forEach {
        context.getSharedPreferences(it.name, Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    @Test fun freshInstallRestoresAllBusinessPreferencesAndStableAssignmentsTwice() = runTest {
        val profile = DatevProfile.createDefaultSkr04().copy(
            beraterNummer = "1234567", mandantenNummer = "23456", mandantenName = "Testmandant",
            categoryKontoMapJson = "{\"Material\":\"6470\"}", lastModified = 123L
        )
        DatevProfileService.saveActiveProfile(context, profile)
        for (id in listOf("property-A", "property-B")) {
            PropertyTaskStore.upsert(context, PropertyTask("task-$id", id, "unit-$id", "Aufgabe $id",
                note = "Notiz $id", dueDate = "2026-12-01", createdAt = "now", updatedAt = "now"))
            prefs("unit_status_meta_prefs").edit()
                .putString("${id}_unit-${id}_status", if (id.endsWith("A")) "Leerstand" else "Vermietet")
                .putString("${id}_unit-${id}_effectiveDate", "2026-10-01").commit()
            prefs("unit_rental_detail_prefs").edit()
                .putString("${id}_unit-${id}_deposit", if (id.endsWith("A")) "1200" else "1500")
                .putString("${id}_unit-${id}_dueDate", "3")
                .putString("${id}_unit-${id}_rooms", if (id.endsWith("A")) "2" else "3")
                .putString("${id}_unit-${id}_paymentMethod", "Überweisung").commit()
        }
        prefs("bank_transaction_notes").edit().putString("transaction-A", "Notiz A")
            .putString("transaction-B", "Notiz B").commit()
        prefs("ki_learned_rules_prefs").edit()
            .putString("rule_vendor_a", "Vendor A|||Renovierung|||Material|||4801|||unit-A|||2")
            .putString("rule_vendor_b", "Vendor B|||Verwaltung|||Büro|||4950|||unit-B|||3").commit()
        prefs("ai_provider_settings").edit().putString("receipt_analysis_provider", "OPENAI")
            .putString("openai_model", "test-model").commit()
        prefs("google_drive_prefs").edit().putBoolean("auto_backup", false)
            .putString("selected_property_id", "property-B").commit()
        prefs("logbook_drafts").edit().putString("manual", "{\"purpose\":\"Besichtigung\"}").commit()
        prefs("rent_plan_prefs").edit().putFloat("v2_property-A_unit-A_nk", 125.5f).commit()
        prefs("tenant_history_prefs").edit().putString("history_v2_property-A_unit-A", "[]").commit()
        prefs("loan_interest_assignments").edit().putInt("receipt_1", 42).commit()
        prefs("annual_tax_approval_prefs").edit().putString("fingerprint_2026", "fingerprint").commit()
        prefs("wohneinheiten_prefs").edit().putString("property_property-A_unit_WE 1_id", "unit-A").commit()
        val expected = PersistentPreferenceInventory.stores.filter { it.kind == PersistentPreferenceInventory.Kind.BACKUP }
            .associate { it.name to prefs(it.name).all.toMap() }
        val serialized = SupplementalDriveBackup.createPayload(context, database).toString()
        clearPreferences()
        database.close()
        database = newDatabase()
        repeat(2) { SupplementalDriveBackup.restorePayload(context, database, JSONObject(serialized)) }
        expected.forEach { (name, values) -> assertEquals(name, values, prefs(name).all) }
        assertEquals(profile, DatevProfileService.getActiveProfile(context))
        for (id in listOf("property-A", "property-B")) {
            val task = PropertyTaskStore.load(context, id).single()
            assertEquals(id, task.propertyId)
            assertEquals("unit-$id", task.unitId)
            assertEquals("task-$id", task.id)
        }
        assertEquals(2, prefs("bank_transaction_notes").all.size)
        assertEquals(2, prefs("ki_learned_rules_prefs").all.size)
    }

    @Test fun confirmedAfaBackupRestoresAndMergeKeepsLocalVerifiedValues() = runTest {
        val propertyId = "confirmed-afa-property"
        val original = """{"cutoffDate":"2025-12-31","cumulativeAfa":40000.0,"remainingBookValue":360000.0,"source":"Steuerberater Jahresabschluss 2025"}"""
        val local = """{"cutoffDate":"2026-12-31","cumulativeAfa":50000.0,"remainingBookValue":350000.0,"source":"Lokaler Steuerbescheid 2026"}"""
        val store = prefs("afa_confirmed_values_prefs")
        store.edit().putString(propertyId, original).commit()
        val backup = SupplementalDriveBackup.createPayload(context, database)
        store.edit().clear().commit()
        SupplementalDriveBackup.restorePayload(context, database, backup)
        assertEquals(original, store.getString(propertyId, null))
        store.edit().putString(propertyId, local).commit()
        repeat(2) { SupplementalDriveBackup.restorePayload(context, database, backup) }
        assertEquals(local, store.getString(propertyId, null))
    }

    @Test fun mergingPropertySnapshotsUsesStableIdentityAndPreservesUnrelatedLocalObjects() = runTest {
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 1, propertyId = "remote-A", name = "Objekt A"))
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 2, propertyId = "remote-B", name = "Objekt B"))
        val payload = SupplementalDriveBackup.createPayload(context, database)
        database.close(); database = newDatabase()
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 1, propertyId = "local-C", name = "Lokales Objekt"))
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 7, propertyId = "remote-B", name = "Alter Name B"))
        repeat(2) { SupplementalDriveBackup.restorePayload(context, database, payload) }
        val properties = database.propertyDao().getAllProperties().associateBy { it.propertyId }
        assertEquals(setOf("remote-A", "remote-B", "local-C"), properties.keys)
        assertEquals(1, properties.getValue("local-C").id)
        assertEquals("Lokales Objekt", properties.getValue("local-C").name)
        assertEquals(7, properties.getValue("remote-B").id)
        assertEquals("Objekt B", properties.getValue("remote-B").name)
        assertNotEquals(1, properties.getValue("remote-A").id)
    }

    @Test fun credentialsNeverLeaveAnyStoreAndCannotBeInjectedOnRestore() = runTest {
        val secrets = mapOf("openai_api_key" to "OPENAI_SECRET_SENTINEL",
            "gemini_api_key" to "GEMINI_SECRET_SENTINEL", "google_routes_api_key" to "ROUTES_SECRET_SENTINEL",
            "pin" to "PIN_SENTINEL", "tan" to "TAN_SENTINEL", "access_token" to "ACCESS_TOKEN_SENTINEL",
            "refresh_token" to "REFRESH_SECRET_SENTINEL", "password" to "PASSWORD_SECRET_SENTINEL",
            "client_secret" to "CLIENT_SECRET_SENTINEL", "openai_key_ciphertext" to "CIPHER_SECRET_SENTINEL")
        PersistentPreferenceInventory.stores.forEach { store ->
            val editor = prefs(store.name).edit()
            secrets.forEach { (key, value) -> editor.putString(key, value) }
            editor.commit()
        }
        val root = SupplementalDriveBackup.createPayload(context, database)
        secrets.values.forEach { assertFalse(it, root.toString().contains(it)) }
        PersistentPreferenceInventory.stores.filter { it.payloadKey != null }.forEach { store ->
            root.getJSONObject(store.payloadKey!!).put("access_token",
                JSONObject().put("type", "string").put("value", "INJECTED_TOKEN"))
        }
        SupplementalDriveBackup.restorePayload(context, database, root)
        PersistentPreferenceInventory.stores.forEach { store ->
            assertEquals("ACCESS_TOKEN_SENTINEL", prefs(store.name).getString("access_token", null))
        }
    }

    @Test fun oldSchemaWithoutNewFieldsPreservesExistingDataAndMergesWithoutClearing() = runTest {
        prefs("property_tasks_prefs").edit().putString("tasks_local", "[]").commit()
        prefs("rent_plan_prefs").edit().putFloat("nk_local", 10f).commit()
        val old = JSONObject("""{"schemaVersion":13,"rentPlanPrefs":{"nk_old":{"type":"float","value":20.5}}}""")
        repeat(2) { SupplementalDriveBackup.restorePayload(context, database, old) }
        assertEquals("[]", prefs("property_tasks_prefs").getString("tasks_local", null))
        assertEquals(10f, prefs("rent_plan_prefs").getFloat("nk_local", 0f), 0f)
        assertEquals(20.5f, prefs("rent_plan_prefs").getFloat("nk_old", 0f), 0f)
    }

    @Test fun preferencePrimitiveTypesAndStringSetsRoundTrip() = runTest {
        prefs("rent_plan_prefs").edit().putString("string", "Text").putInt("int", 5)
            .putLong("long", Long.MAX_VALUE).putFloat("float", 1.5f).putBoolean("boolean", true)
            .putStringSet("set", setOf("A", "B")).commit()
        val expected = prefs("rent_plan_prefs").all.toMap()
        val payload = SupplementalDriveBackup.createPayload(context, database)
        clearPreferences()
        SupplementalDriveBackup.restorePayload(context, database, payload)
        assertEquals(expected, prefs("rent_plan_prefs").all)
    }

    @Test fun propertyImagesRestoreBytesWithoutDevicePathsAndRemainIdempotent() = runTest {
        val image = File(context.filesDir, "source-image.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 1, propertyId = "A", bildPfad = image.absolutePath))
        val payload = SupplementalDriveBackup.createPayload(context, database)
        assertFalse(payload.toString().contains(image.absolutePath))
        image.delete()
        database.close(); database = newDatabase()
        SupplementalDriveBackup.restorePayload(context, database, payload)
        val restoredPath = database.propertyDao().getPropertyByPropertyId("A")!!.bildPfad
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), File(restoredPath).readBytes())
        SupplementalDriveBackup.restorePayload(context, database, payload)
        assertEquals(restoredPath, database.propertyDao().getPropertyByPropertyId("A")!!.bildPfad)
        assertEquals(1, database.propertyDao().getAllProperties().size)
    }

    @Test fun futureSchemaIsRejectedBeforeAnyLocalDataChanges() = runTest {
        prefs("rent_plan_prefs").edit().putFloat("nk_local", 10f).commit()
        try {
            SupplementalDriveBackup.restorePayload(context, database, JSONObject().put("schemaVersion", 999))
            fail("Future schema accepted")
        } catch (_: IllegalArgumentException) { }
        assertEquals(10f, prefs("rent_plan_prefs").getFloat("nk_local", 0f), 0f)
    }

    @Test fun identicalPhotosBelongToSeparatePropertiesAndRestoreRepairsCorruptedLocalBytes() = runTest {
        val image = File(context.filesDir, "shared-source.jpg").apply { writeBytes(byteArrayOf(4, 3, 2, 1)) }
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 1, propertyId = "photo-A", bildPfad = image.absolutePath))
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 2, propertyId = "photo-B", bildPfad = image.absolutePath))
        val payload = SupplementalDriveBackup.createPayload(context, database)
        image.delete()
        SupplementalDriveBackup.restorePayload(context, database, payload)
        val a = File(database.propertyDao().getPropertyByPropertyId("photo-A")!!.bildPfad)
        val b = File(database.propertyDao().getPropertyByPropertyId("photo-B")!!.bildPfad)
        assertNotEquals(a.absolutePath, b.absolutePath)
        a.delete()
        assertTrue(b.isFile)
        b.writeBytes(byteArrayOf(0))
        SupplementalDriveBackup.restorePayload(context, database, payload)
        assertArrayEquals(byteArrayOf(4, 3, 2, 1), a.readBytes())
        assertArrayEquals(byteArrayOf(4, 3, 2, 1), b.readBytes())
    }

    @Test fun exportAuditHistoryRestoresCompletelyAndWithoutDuplicates() = runTest {
        val run = ExportAuditRun("export-A", timestamp = 123L, user = "Testnutzer", propertyName = "Objekt A",
            periodStart = "2026-01-01", periodEnd = "2026-12-31", filterSummary = "Objekt A",
            exportierteReceiptIdsJson = "[\"receipt-A\"]", ausgeschlosseneReceiptIdsJson = "[\"receipt-B\"]",
            kanzleiprofilNameVersion = "SKR04 v1", zipFileName = "export.zip", zipFileSizeBytes = 42L,
            zipSha256 = "abc", status = "SUCCESS", totalAmount = 123.5, bookingCount = 2,
            warningsCount = 1, logMessage = "Exportiert")
        database.exportAuditDao().insertRun(run)
        val payload = SupplementalDriveBackup.createPayload(context, database)
        database.close(); database = newDatabase()
        repeat(2) { SupplementalDriveBackup.restorePayload(context, database, payload) }
        assertEquals(listOf(run), database.exportAuditDao().getAllRunsFlow().first())
    }

    @Test fun invalidPreferencePayloadIsRejectedBeforeReplacingDocuments() = runTest {
        database.managedDocumentDao().upsert(ManagedDocument(documentId = "local-document", propertyId = "local-property"))
        val invalid = JSONObject("""{"schemaVersion":14,"managedDocuments":[],"rentPlanPrefs":{"nk":{"type":"unsupported","value":"bad"}}}""")
        try {
            SupplementalDriveBackup.restorePayload(context, database, invalid, replaceManagedDocuments = true)
            fail("Invalid payload accepted")
        } catch (_: IllegalStateException) { }
        assertEquals("local-document", database.managedDocumentDao().getAll().single().documentId)
    }

    @Test fun nullDocumentSnapshotCannotClearLocalDocumentsInReplaceMode() = runTest {
        database.managedDocumentDao().upsert(ManagedDocument(documentId = "local-document", propertyId = "local-property"))
        try {
            SupplementalDriveBackup.restorePayload(context, database,
                JSONObject().put("schemaVersion", 14).put("managedDocuments", JSONObject.NULL), replaceManagedDocuments = true)
            fail("Null snapshot accepted")
        } catch (_: org.json.JSONException) { }
        assertEquals("local-document", database.managedDocumentDao().getAll().single().documentId)
    }
}
