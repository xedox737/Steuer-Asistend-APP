package com.example.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase9BWriteBoundaryTest {
    @get:Rule val isolation = IsolatedAppDatabaseRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val db get() = AppDatabase.getDatabase(app, kotlinx.coroutines.CoroutineScope(Dispatchers.IO))
    private val models = androidx.lifecycle.ViewModelStore()
    private fun model(): ReceiptViewModel = androidx.lifecycle.ViewModelProvider(models,
        androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(app))[ReceiptViewModel::class.java]
    @Before fun clear() { PersistentPreferenceInventory.stores.forEach { app.getSharedPreferences(it.name, 0).edit().clear().commit() } }
    @After fun cleanup() { models.clear(); clear() }

    @Test fun invalidRuleCannotReplaceExistingAndValidRuleCanBeBackedUp() = runBlocking {
        val rule = BankLearningRule("9b-rule", "Bestand", amountMin = 98.0, amountMax = 1234.56)
        db.bankLearningRuleDao().upsertRule(rule)
        val vm = model()
        for (bad in listOf(rule.copy(amountMin = Double.POSITIVE_INFINITY), rule.copy(amountMax = Double.NaN),
            rule.copy(amountMin = -1.0), rule.copy(amountMin = 2000.0))) {
            assertFalse(vm.saveBankRule(bad))
            assertEquals(rule, db.bankLearningRuleDao().getRule(rule.ruleId))
        }
        assertTrue(SupplementalDriveBackup.createPayload(app, db).getJSONArray("bankLearningRules").length() == 1)
        val malformed = JSONObject().put("schemaVersion", 15).put("bankLearningRules", JSONArray().put(JSONObject()
            .put("ruleId", rule.ruleId).put("amountMin", 2000).put("amountMax", 1000)))
        assertTrue(runCatching { SupplementalDriveBackup.restorePayload(app, db, malformed) }.isFailure)
        assertEquals(rule, db.bankLearningRuleDao().getRule(rule.ruleId))
    }

    @Test fun sharedRentWriterIsAtomicAndOldInfinityIsRetainedUntilExplicitRepair() = runBlocking {
        val unit = WohneinheitStatus("WE 01", "WE 01", "Vermietet", "Mieter", 600.0, 72.5, "", "unit-9b")
        val prefs = app.getSharedPreferences("rent_plan_prefs", 0)
        PropertyUnitScopedData.setRentValues(app, "property-1", unit, 220.0, 10.0)
        val before = prefs.all.toMap()
        assertTrue(runCatching { PropertyUnitScopedData.setRentValues(app, "property-1", unit, 230.0, 1e200) }.isFailure)
        assertEquals(before, prefs.all)
        prefs.edit().putFloat("nk_WE 01", Float.POSITIVE_INFINITY).remove("v2_property-1_unit-9b_nk").commit()
        assertTrue(PropertyUnitScopedData.rentValue(app, "property-1", unit, "nk").isInfinite())
        assertFalse(prefs.contains("v2_property-1_unit-9b_nk"))
        assertTrue(runCatching { SupplementalDriveBackup.createPayload(app, db) }.isFailure)
        PropertyUnitScopedData.setRentValues(app, "property-1", unit, 220.0, 10.0)
        assertNotNull(SupplementalDriveBackup.createPayload(app, db))
        val period = TenantPeriod(10, unit.name, "Mieter", "2026-01-01", "", 600.0, 220.0, 10.0)
        assertTrue(TenantHistoryStore.save(app, "property-1", unit.unitId, unit.name, listOf(period)))
        val history = app.getSharedPreferences("tenant_history_prefs", 0).all.toMap()
        assertFalse(TenantHistoryStore.save(app, "property-1", unit.unitId, unit.name, listOf(period.copy(kaltmiete = 1e200))))
        assertEquals(history, app.getSharedPreferences("tenant_history_prefs", 0).all)
    }

    @Test fun restoreUnitWriterRejectsEntireBatchAndRouteWriterRejectsInvalidDistance() = runBlocking {
        val repo = ReceiptRepository(db.receiptDao(), db.propertyDao())
        val drive = DrivePersistenceRepository(app, repo)
        val unit = WohneinheitStatus("WE 01", "WE 01", "Leerstand", "", 0.0, 72.5, "", "unit-9b")
        drive.saveWohneinheitenToPrefs(listOf(unit), RestoreMode.REPLACE_FULL)
        val prefs = app.getSharedPreferences("wohneinheiten_prefs", 0); val before = prefs.all.toMap()
        assertTrue(runCatching { drive.saveWohneinheitenToPrefs(listOf(unit.copy(label = "Neu"), unit.copy(name = "WE 02", wohnflaeche = 1e200)), RestoreMode.REPLACE_FULL) }.isFailure)
        assertEquals(before, prefs.all)
        val vm = model()
        for (distance in listOf(-12.5, Double.NaN, Double.POSITIVE_INFINITY, 0.0)) {
            assertTrue(runCatching { vm.saveStandardRoute(StandardRoute(name = "9B", startAddress = "A", destinationAddress = "B", distanceKm = distance, routeSignature = "9b", createdAt = "2026-10-14", updatedAt = "2026-10-14")) }.isFailure)
        }
        assertNull(db.logbookDao().findStandardRoute("9b"))
        vm.saveStandardRoute(StandardRoute(name = "9B", startAddress = "A", destinationAddress = "B", distanceKm = 20.0, routeSignature = "9b", createdAt = "2026-10-14", updatedAt = "2026-10-14"))
        assertEquals(20.0, db.logbookDao().findStandardRoute("9b")!!.distanceKm, 0.0)
    }

    @Test fun unitEditAndWizardRejectFloatOverflowBeforeAnyOtherFieldWrite() = runBlocking {
        val unit = WohneinheitStatus("WE 01", "Bestand", "Leerstand", "", 600.0, 72.5, "", "unit-9b")
        val property = PropertyMetadata(id = 1, propertyId = "p-9b", name = "Bestand", wohneinheiten = unit.name)
        db.propertyDao().insertPropertyMetadata(property)
        val prefs = app.getSharedPreferences("wohneinheiten_prefs", 0)
        prefs.edit().putString("property_p-9b_unit_WE 01_id", unit.unitId).putFloat("unit_area_WE 01", 72.5f).commit()
        val before = prefs.all.toMap()
        val vm = model()
        vm.updateWohneinheit(unit.copy(label = "Darf nicht geschrieben werden", wohnflaeche = 1e200), correctCurrentContract = true)
        vm.createProperty(property.copy(propertyId = "new"), listOf(unit.copy(wohnflaeche = 1e200)))
        vm.createProperty(property.copy(propertyId = "new"), listOf(unit), Loan(darlehensbetrag = 1e200))
        assertEquals(before, prefs.all)
        assertEquals(listOf(property), db.propertyDao().getAllProperties())
        assertTrue(db.loanDao().getAllLoans().isEmpty())
    }

    @Test fun supplementalFloatPreflightUsesSameTargetBoundaryBeforeAnyPreferenceMutation() = runBlocking {
        val root = SupplementalDriveBackup.createPayload(app, db)
        val prefs = app.getSharedPreferences("wohneinheiten_prefs", 0)
        prefs.edit().putFloat("unit_area_WE 01", 72.5f).commit()
        val payload = SupplementalDriveBackup.createPayload(app, db)
        val raw = payload.toString()
        // Replace only the numeric value of the known typed preference entry.
        for (value in listOf("1e200", "1e-100", "-72.5")) {
            val malformed = JSONObject(raw.replace("72.5", value))
            assertTrue(runCatching { SupplementalDriveBackup.restorePayload(app, db, malformed) }.isFailure)
            assertEquals(72.5f, prefs.getFloat("unit_area_WE 01", 0f))
        }
        assertNotNull(root)
    }
}
