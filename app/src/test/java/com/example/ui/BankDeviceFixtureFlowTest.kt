package com.example.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Reproduces the device benchmark's exact matching inputs in a fresh ViewModel. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BankDeviceFixtureFlowTest {
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private val models = ViewModelStore()
    private val instance = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
    private var previous: AppDatabase? = null

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        PersistentPreferenceInventory.stores.forEach { app.getSharedPreferences(it.name, 0).edit().clear().commit() }
        app.getSharedPreferences("google_drive_prefs", 0).edit().putBoolean("user_disconnected", true).commit()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        previous = instance.get(null) as AppDatabase?
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        instance.set(null, db)
    }

    @After fun tearDown() {
        models.clear()
        instance.set(null, previous)
        db.close()
        Dispatchers.resetMain()
    }

    @Test fun fresh1250FixtureProducesBothSuggestionMaps() = reproduce(1_250)
    @Test fun fresh5000FixtureProducesBothSuggestionMaps() = reproduce(5_000)

    private fun reproduce(count: Int) = runBlocking {
        val property = PropertyMetadata(id = 1, propertyId = "bank-perf-property", name = "Synthetisches Miethaus",
            adresse = "Beispielstraße 1", wohneinheiten = "WE 01")
        val unit = WohneinheitStatus("WE 01", "Testwohnung", "Vermietet", "Testmieter Alt", 600.0, 41.25,
            "2021-04-01", "bank-perf-property-unit")
        val prefix = "property_${property.propertyId}_unit_${unit.name}_"
        check(app.getSharedPreferences("wohneinheiten_prefs", 0).edit()
            .putString(prefix + "id", unit.unitId).putString(prefix + "label", unit.label)
            .putString(prefix + "status", unit.status).putString(prefix + "mieter", unit.mieter)
            .putFloat(prefix + "rent", 600f).putFloat(prefix + "area", 41.25f).putString(prefix + "start", unit.mietvertragsstart)
            .putString("property_${property.propertyId}_unit_id_index_0", unit.unitId).commit())
        val transactions = (0 until count).map { index ->
            val income = index % 5 == 0
            BankTransaction("bank-perf-${index.toString().padStart(5, '0')}", "bank-perf-account", "2026-10-08",
                amount = if (income) 600.0 else -(50.0 + index % 20),
                counterparty = "Synthetic viewport merchant ${index.toString().padStart(5, '0')}",
                purpose = if (income) "Miete Oktober WE 01" else "Synthetic repair $index", bankReference = "PERF-REF-$index",
                propertyId = property.propertyId, unitId = unit.unitId,
                category = if (income) "Miete, Nebenkosten & Kaution" else "Instandhaltung",
                subcategory = if (income) "Kaltmiete" else "Reparatur", importRunId = "synthetic-perf-import",
                importFileName = "synthetic-performance.csv", importedAt = "2026-10-08T12:00:00Z")
        }
        val receipts = (0 until 40).map { index ->
            val rental = index % 2 == 0
            Receipt(20_000 + index, "Synthetic viewport merchant $index", "2026-10-08", "",
                if (rental) 600.0 else 50.0 + index % 20, if (rental) "Miete, Nebenkosten & Kaution" else "Instandhaltung",
                if (rental) "Kaltmiete" else "Reparatur", if (rental) "8100" else "4800",
                if (rental) "Miete Oktober WE 01" else "Synthetic repair", propertyId = property.propertyId,
                unitId = unit.unitId, wohneinheit = unit.name, internalId = "bank-perf-receipt-$index")
        }
        val links = (0 until 10).map { BankReceiptLink("bank-perf-link-$it", transactions[it].transactionId,
            20_000 + it, "bank-perf-receipt-$it", 25.0, createdAt = "2026-10-08T12:00:00Z") }
        val assignments = (0 until 4).map { BankRentAssignment("bank-perf-rent-$it", transactions[it * 5].transactionId,
            property.propertyId, unit.unitId, "2026-10", "bank-perf-tenant", 25.0, RentPaymentType.MIETE,
            receiptId = 20_000 + it * 2, createdAt = "2026-10-08T12:00:00Z", updatedAt = "2026-10-08T12:00:00Z") }
        withContext(Dispatchers.IO) {
            db.propertyDao().insertPropertyMetadata(property)
            db.bankDao().upsertAccount(BankAccount("bank-perf-account", "Synthetisches Bankkonto"))
            db.bankDao().insertTransactions(transactions)
            receipts.forEach { db.receiptDao().insertReceipt(it) }
            links.forEach { db.bankDao().upsertLink(it) }
            assignments.forEach { db.bankRentAssignmentDao().upsert(it) }
        }
        val model = ViewModelProvider(models, ViewModelProvider.AndroidViewModelFactory(app))[ReceiptViewModel::class.java]
        withTimeout(10_000) {
            model.bankTransactions.first { it.size == count }
            model.receipts.first { it.size == 40 }
            model.properties.first { it.singleOrNull()?.propertyId == property.propertyId }
            model.bankReceiptLinks.first { it.size == 10 }
            model.bankRentAssignments.first { it.size == 4 }
        }
        val loadedUnits = model.getWohneinheitenForProperty(property)
        assertEquals(listOf(unit), loadedUnits)
        val candidates = BankRentCandidateFactory.Batch(app, listOf(property), mapOf(property.propertyId to loadedUnits),
            receipts, assignments, transactions, links).build(transactions.first())
        println("BANK_FIXTURE count=$count candidate=$candidates")
        assertEquals(600.0, candidates.single().expectedAmount, .001)
        assertEquals(100.0, candidates.single().alreadyConfirmedAmount, .001)
        assertFalse(BankRentMatcher.match(transactions.first(), candidates).isEmpty())
        val start = System.nanoTime()
        val receiptResult = async {
            withTimeout(10_000) { model.bankMatchSuggestions.first { it.isNotEmpty() } }.also {
                println("BANK_FIXTURE count=$count receipt_map_size=${it.size} receipt_ready_ms=${(System.nanoTime() - start) / 1_000_000.0}")
            }
        }
        val rentResult = async {
            withTimeout(10_000) { model.bankRentSuggestions.first { it.isNotEmpty() } }.also {
                println("BANK_FIXTURE count=$count rent_map_size=${it.size} rent_ready_ms=${(System.nanoTime() - start) / 1_000_000.0}")
            }
        }
        try {
            assertTrue(receiptResult.await().isNotEmpty())
            assertEquals(count / 5, rentResult.await().size)
        } finally {
            println("BANK_FIXTURE count=$count final_receipt_map=${model.bankMatchSuggestions.value.size} final_rent_map=${model.bankRentSuggestions.value.size}")
        }
    }
}
