package com.example.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Phase10AIdentityTest {
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var vm: ReceiptViewModel
    private val store = ViewModelStore()
    private val instance = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
    private var previous: AppDatabase? = null
    private val propertyId = "phase10a"
    private val entries = listOf(UnitIdentity.Entry("U1", "WE 01, EG links"), UnitIdentity.Entry("U2", "WE 02"))
    private val property get() = PropertyMetadata(id = 101, propertyId = propertyId, name = "Kommahaus", wohneinheiten = UnitIdentity.encode(entries))
    private val prefs get() = app.getSharedPreferences("wohneinheiten_prefs", 0)
    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        PersistentPreferenceInventory.stores.forEach { app.getSharedPreferences(it.name, 0).edit().clear().commit() }
        app.getSharedPreferences("google_drive_prefs", 0).edit().putBoolean("user_disconnected", true).commit()
        previous = instance.get(null) as AppDatabase?
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        instance.set(null, db)
        entries.forEachIndexed { index, entry ->
            prefs.edit().putFloat("property_${propertyId}_unitid_${entry.unitId}_area", if (index == 0) 41.25f else 55.5f).commit()
        }
        vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[ReceiptViewModel::class.java]
    }
    @After fun cleanup() {
        store.clear(); instance.set(null, previous); db.close()
        PersistentPreferenceInventory.stores.forEach { app.getSharedPreferences(it.name, 0).edit().clear().commit() }
    }
    @Test fun commaNamesAndAreasReloadAsExactlyTwoStableUnitsDespitePoisonedIndexes() {
        prefs.edit().putString("property_${propertyId}_unit_id_index_0", "wrong")
            .putString("property_${propertyId}_unit_id_index_1", "U1").commit()
        repeat(2) {
            val units = vm.getWohneinheitenForProperty(property)
            assertEquals(entries.map { it.unitId }, units.map { it.unitId })
            assertEquals(entries.map { it.name }, units.map { it.name })
            assertEquals(listOf(41.25, 55.5), units.map { it.wohnflaeche })
        }
        assertEquals("wrong", prefs.getString("property_${propertyId}_unit_id_index_0", null))
    }
    @Test fun separatorsWhitespaceAndReorderingRemainLabels() {
        listOf("WE 01, EG links, Altbau", "WE; 01|EG/links", " WE  01, EG links ", "WE\n01").forEach { name ->
            val renamed = entries.map { if (it.unitId == "U1") it.copy(name = name) else it }.reversed()
            val result = vm.getWohneinheitenForProperty(property.copy(wohneinheiten = UnitIdentity.encode(renamed)))
            assertEquals(renamed.map { it.unitId to it.name }, result.map { it.unitId to it.name })
            assertEquals(41.25, result.single { it.unitId == "U1" }.wohnflaeche, 0.0)
        }
    }
    @Test fun duplicateNamePolicyAndDuplicateIdsRejectBeforePersistence() {
        assertTrue(runCatching { UnitIdentity.encode(listOf(entries[0], entries[1].copy(name = entries[0].name))) }.isFailure)
        assertTrue(runCatching { UnitIdentity.encode(listOf(entries[0], entries[1].copy(unitId = "U1"))) }.isFailure)
    }
    @Test fun ambiguousLegacyDoesNotReadOrWriteAnyGuessedUnit() {
        val raw = "WE 01, EG links, WE 02"
        prefs.edit().putString("property_${propertyId}_unit_id_index_0", "U1")
            .putString("property_${propertyId}_unit_id_index_1", "U2").commit()
        val before = prefs.all.toMap()
        assertTrue(vm.getWohneinheitenForProperty(property.copy(wohneinheiten = raw)).isEmpty())
        assertEquals(UnitIdentity.ERROR, vm.unitIdentityError(property.copy(wohneinheiten = raw)))
        assertEquals(before, prefs.all)
        assertEquals(raw, property.copy(wohneinheiten = raw).wohneinheiten)
    }
    @Test fun legacyCanOnlyBeReconstructedFromCompleteScopedNamesAndUniqueIds() {
        entries.forEach { prefs.edit().putString("property_${propertyId}_unit_${it.name}_id", it.unitId).commit() }
        val raw = "WE 01, EG links, WE 02"
        val before = prefs.all.toMap()
        assertEquals(entries, UnitIdentity.read(raw, propertyId, prefs).entries)
        assertEquals(before, prefs.all)
        assertNotNull(UnitIdentity.read(raw, "another-property", prefs).error)
        prefs.edit().putString("property_${propertyId}_unit_WE 01_id", "X")
            .putString("property_${propertyId}_unit_EG links_id", "Y").commit()
        assertNotNull(UnitIdentity.read(raw, propertyId, prefs).error)
    }
    @Test fun backupTwiceAndFullReplaceKeepNamesIdsAreasHistoryBankDocumentAndOriginal() = runBlocking {
        db.propertyDao().insertPropertyMetadata(property)
        val period = TenantPeriod(1001, entries[0].name, "Mieter", "2020-01-01", "", 600.0, 100.0, 0.0)
        TenantHistoryStore.save(app, propertyId, "U1", entries[0].name, listOf(period))
        db.bankDao().upsertAccount(BankAccount("account", "Synthetisch"))
        val transaction = BankTransaction("tx", "account", "2026-10-01", amount = 700.0, propertyId = propertyId, unitId = "U1")
        val assignment = BankRentAssignment("assignment", "tx", propertyId, "U1", "2026-10", "1001", 700.0,
            BankSplitPaymentType.RENT, createdAt = "", updatedAt = "")
        db.bankDao().upsertTransaction(transaction); db.bankRentAssignmentDao().upsert(assignment)
        val original = File(app.filesDir, "phase10a-original.pdf").apply { writeText("original bytes") }
        val document = ManagedDocument(documentId = "D1", propertyId = propertyId, unitId = "U1",
            originalFilename = "Original, unverändert.pdf", localUri = original.absolutePath,
            storedFilename = original.name, mimeType = "application/pdf", sha256 = StableDocumentIdentity.sha256(original.readBytes()))
        db.managedDocumentDao().upsert(document)
        val payload = SupplementalDriveBackup.createPayload(app, db)
        db.clearAllTables()
        PersistentPreferenceInventory.stores.forEach { app.getSharedPreferences(it.name, 0).edit().clear().commit() }
        repeat(2) {
            SupplementalDriveBackup.restorePayload(app, db, payload)
            val restored = db.propertyDao().getPropertyByPropertyId(propertyId)!!
            assertEquals(property, restored)
            assertEquals(entries.map { it.unitId to it.name }, vm.getWohneinheitenForProperty(restored).map { it.unitId to it.name })
            assertEquals(41.25, vm.getWohneinheitenForProperty(restored).first().wohnflaeche, 0.0)
            assertEquals(listOf(period), TenantHistoryStore.load(app, propertyId, "U1", entries[0].name))
            assertEquals(listOf(assignment), db.bankRentAssignmentDao().getAll())
            assertEquals(listOf(transaction), db.bankDao().getAllTransactions())
            assertEquals("U1", db.managedDocumentDao().getById("D1")!!.unitId)
            assertEquals("original bytes", original.readText())
        }
        val renamed = property.copy(wohneinheiten = UnitIdentity.encode(entries.map { if (it.unitId == "U1") it.copy(name = "Lokal, umbenannt") else it }))
        db.propertyDao().insertPropertyMetadata(renamed)
        repeat(2) { SupplementalDriveBackup.restorePayload(app, db, payload) }
        assertEquals(renamed, db.propertyDao().getPropertyByPropertyId(propertyId))
        SupplementalDriveBackup.restorePayload(app, db, payload, restoreMode = RestoreMode.REPLACE_FULL)
        assertEquals(property, db.propertyDao().getPropertyByPropertyId(propertyId))
        original.delete()
        Unit
    }
    @Test fun firestoreDocumentKeyUsesPropertyAndStableUnitIdInsteadOfRenameableName() {
        assertEquals(FirestoreService.unitDocumentKey("P1", "U1"), FirestoreService.unitDocumentKey("P1", "U1"))
        assertNotEquals(FirestoreService.unitDocumentKey("P1", "U1"), FirestoreService.unitDocumentKey("P2", "U1"))
        assertNotEquals(FirestoreService.unitDocumentKey("P1", "U1"), FirestoreService.unitDocumentKey("P1", "U2"))
    }
    @Test fun stableReceiptIdentityWinsOverRenamedOrMisleadingLabels() {
        val units = vm.getWohneinheitenForProperty(property)
        val old = Receipt(1010, "Handel", "2026-10-01", "", 119.0, "Werbungskosten", "Reparatur", "4801", "Reparatur",
            propertyId = propertyId, unitId = "U1", wohneinheit = "Alter Name")
        assertTrue(ReceiptUnitResolver.belongs(old, propertyId, units[0], units))
        assertFalse(ReceiptUnitResolver.belongs(old.copy(wohneinheit = units[1].name), propertyId, units[1], units))
        assertFalse(ReceiptUnitResolver.belongs(old.copy(propertyId = "foreign"), propertyId, units[0], units))
        val ambiguousLabels = units.map { it.copy(label = "Wohnung") }
        assertFalse(ReceiptUnitResolver.belongs(old.copy(unitId = "", wohneinheit = "Wohnung"), propertyId, ambiguousLabels[0], ambiguousLabels))
    }
    @Test fun commonQuantityFormatterPreservesStoredDecimalsAndFloatWidening() {
        listOf(41.25 to "41,25", 41.99 to "41,99", 41.0 to "41", 41.5 to "41,5", 2.33 to "2,33", 1.25 to "1,25").forEach { (value, expected) ->
            assertEquals(expected, GermanNumberInput.formatQuantity(value))
            assertEquals(expected, GermanNumberInput.formatQuantity(value.toFloat().toDouble()))
        }
    }
    @Test fun learnedRuleLabelsRoundTripWithoutDelimiterFieldsOrCountShift() {
        val rule = LearnedVendorRule("Handel|||Nord", "Werbungskosten", "Reparatur", "4801", "WE|||01, EG", 4)
        assertEquals(rule, LearnedVendorRuleCodec.decode(LearnedVendorRuleCodec.encode(rule)))
        assertTrue(runCatching { LearnedVendorRuleCodec.decode("A|||B|||C|||D|||WE|||01|||4") }.isFailure)
        assertEquals(LearnedVendorRule("A", "B", "C", "D", "WE", 4), LearnedVendorRuleCodec.decode("A|||B|||C|||D|||WE|||4"))
    }
    @Test fun originalCommaFilenameRemainsOneOriginalInEveryReader() {
        val file = File(app.filesDir, "original, EG.pdf").apply { writeText("original") }
        try {
            assertEquals(listOf(file.absolutePath), ReceiptOriginalChain.paths(file.absolutePath))
            val paths = listOf(file.absolutePath, "/another.pdf")
            assertEquals(paths, ReceiptOriginalChain.paths(ReceiptOriginalChain.encodePaths(paths)))
            val second = File(app.filesDir, "second.pdf").apply { writeText("second") }
            try {
                assertEquals(listOf(file.absolutePath, second.absolutePath), ReceiptOriginalChain.paths(file.absolutePath + "," + second.absolutePath))
            } finally { second.delete() }
            assertEquals(listOf("unresolved, original.pdf"), ReceiptOriginalChain.paths("unresolved, original.pdf"))
        } finally { file.delete() }
    }
    @Test fun tenantLabelArrowDoesNotBecomeAnotherTenantOnAnnualProjection() {
        val unit = vm.getWohneinheitenForProperty(property).first()
        val month = RentMonthProjection(unit, 1.0, 0.0, "Martin → Weber", false)
        val row = RentOverviewUnit(property, RentYearProjection(unit, listOf(month)), 0.0, 0.0)
        assertEquals("Martin → Weber", row.tenants)
        assertEquals(listOf("Martin → Weber"), month.tenantLabels)
    }
}
