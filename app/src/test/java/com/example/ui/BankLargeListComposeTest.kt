package com.example.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.BankAccount
import com.example.data.BankTransaction
import com.example.data.PersistentPreferenceInventory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A large import on one day must not defeat LazyColumn's viewport bound. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-420dpi")
class BankLargeListComposeTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private lateinit var database: AppDatabase
    private val models = ViewModelStore()
    private val instance = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
    private var previousDatabase: AppDatabase? = null

    @Before fun setUp() {
        val app = ui.activity.application as Application
        PersistentPreferenceInventory.stores.forEach { app.getSharedPreferences(it.name, 0).edit().clear().commit() }
        app.getSharedPreferences("google_drive_prefs", 0).edit().putBoolean("user_disconnected", true).commit()
        previousDatabase = instance.get(null) as AppDatabase?
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        instance.set(null, database)
    }

    @After fun tearDown() {
        ui.runOnUiThread { models.clear() }
        instance.set(null, previousDatabase)
        if (::database.isInitialized) database.close()
    }

    @Test fun sameDayImportOf1250RowsComposesOnlyViewportAndKeepsLastRowReachable() = checkLargeImport(1_250)

    @Test fun sameDayImportOf5000RowsComposesOnlyViewportAndKeepsLastRowReachable() = checkLargeImport(5_000)

    private fun checkLargeImport(count: Int) {
        val transactions = (0 until count).map { index ->
            BankTransaction(
                transactionId = "large-ui-${index.toString().padStart(5, '0')}",
                accountId = "large-ui-account",
                bookingDate = "2026-10-08",
                amount = -(index + 1).toDouble(),
                counterparty = "Synthetic viewport merchant ${index.toString().padStart(5, '0')}",
                purpose = "Synthetic bank import $index",
                bankReference = "REF-$index",
                category = "Instandhaltung",
                subcategory = "Reparatur",
                propertyId = "stable-property-id",
                unitId = "stable-unit-id",
                importRunId = "synthetic-large-ui-run",
                importedAt = "2026-10-08T12:00:00Z"
            )
        }
        val account = BankAccount("large-ui-account", "Synthetic account")
        runBlocking(Dispatchers.IO) {
            database.bankDao().upsertAccount(account)
            database.bankDao().insertTransactions(transactions)
        }
        val before = runBlocking(Dispatchers.IO) { database.bankDao().getAllTransactions() }
        val heapBefore = usedHeap()
        val started = System.nanoTime()
        lateinit var model: ReceiptViewModel
        ui.runOnUiThread {
            model = ViewModelProvider(models, ViewModelProvider.AndroidViewModelFactory(ui.activity.application))[ReceiptViewModel::class.java]
        }
        ui.setContent { MaterialTheme { BankScreen(model) } }
        ui.waitUntil(10_000) {
            ui.waitForIdle()
            model.bankTransactions.value.size == count
        }
        // Descending IDs put the last imported transaction at the top of the date group.
        val first = transactions.last().counterparty
        val last = transactions.first().counterparty
        ui.waitUntil(10_000) { ui.onAllNodes(hasText(first)).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("bank_transaction_list").performScrollToNode(hasText(first))
        ui.onNodeWithText(first).assertIsDisplayed()
        val firstVisibleNanos = System.nanoTime() - started
        val composedCount = ui.onAllNodes(hasText("Synthetic viewport merchant", substring = true)).fetchSemanticsNodes().size
        val heapAfter = usedHeap()
        println("BANK_LARGE_LIST count=$count first_visible_ms=${firstVisibleNanos / 1_000_000.0} " +
            "composed_rows=$composedCount jvm_used_heap_before=$heapBefore jvm_used_heap_after=$heapAfter")
        assertTrue("A viewport must not compose all $count same-day rows: composed=$composedCount", composedCount in 1..50)
        val scrollStarted = System.nanoTime()
        ui.onNodeWithTag("bank_transaction_list").performScrollToKey("transaction-${transactions.first().transactionId}")
        ui.onNodeWithText(last).assertIsDisplayed()
        println("BANK_LARGE_LIST count=$count scroll_to_end_ms=${(System.nanoTime() - scrollStarted) / 1_000_000.0}")
        runBlocking(Dispatchers.IO) {
            assertEquals("Reading and scrolling must preserve every imported field and ID", before, database.bankDao().getAllTransactions())
            assertEquals(listOf(account), database.bankDao().getAllAccounts())
        }
    }

    private fun usedHeap(): Long = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
}
