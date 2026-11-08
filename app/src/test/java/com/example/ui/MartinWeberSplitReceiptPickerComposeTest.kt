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
import com.example.data.BankReconciliationStatus
import com.example.data.BankTransaction
import com.example.data.PersistentPreferenceInventory
import com.example.data.PropertyMetadata
import com.example.data.Receipt
import java.time.LocalDate
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

/** MW-02: a normal yearly receipt stock must remain selectable in the split workflow. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-420dpi")
class MartinWeberSplitReceiptPickerComposeTest {
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

    @Test fun invoice41Of55CanBeSearchedSelectedAndConfirmedWithoutChangingOriginals() {
        val property = PropertyMetadata(propertyId = "picker-property", name = "MFH Lindenstraße")
        val transaction = BankTransaction(
            transactionId = "picker-payment", accountId = "picker-account", bookingDate = "2026-11-06",
            amount = -100.0, counterparty = "Synthetic vendor", purpose = "Invoice 41",
            propertyId = property.propertyId
        )
        val receipts = (1..55).map { index ->
            Receipt(
                id = index, aussteller = "MW invoice $index",
                datum = LocalDate.of(2026, 11, 1).minusDays(index.toLong()).toString(),
                uhrzeit = "", bruttobetrag = 100.0, hauptkategorie = "Kosten",
                unterkategorie = "Reparatur", kontoNr = "4800", beschreibung = "Synthetic invoice $index",
                internalId = "picker-receipt-$index", propertyId = property.propertyId
            )
        }
        runBlocking(Dispatchers.IO) {
            database.propertyDao().insertPropertyMetadata(property)
            database.bankDao().upsertAccount(BankAccount("picker-account", "Synthetic account"))
            database.bankDao().insertTransactions(listOf(transaction))
            database.receiptDao().insertAll(receipts)
        }
        lateinit var model: ReceiptViewModel
        ui.runOnUiThread {
            model = ViewModelProvider(models,
                ViewModelProvider.AndroidViewModelFactory(ui.activity.application))[ReceiptViewModel::class.java]
        }
        ui.setContent { MaterialTheme { BankTransactionSplitActions(model, transaction) } }
        ui.waitUntil(10_000) { ui.waitForIdle(); model.receipts.value.size == 55 && model.properties.value.isNotEmpty() }
        // Verify the target really falls outside the previous first-30 picker boundary.
        assertEquals(41, model.receipts.value[40].id)
        ui.onNodeWithText("Buchung aufteilen").performClick()
        boundLatestDialog()
        ui.onNode(hasSetTextAction() and hasText("Betrag")).performScrollTo().performTextReplacement("100,00")
        ui.onNodeWithText("Kategorie: Miete").performScrollTo().performClick()
        ui.onNodeWithText("Beleg zuordnen").performClick()
        ui.onNodeWithText("Beleg: auswählen").performScrollTo().performClick()
        boundLatestDialog()
        ui.onNode(hasSetTextAction() and hasText("Belege suchen")).performTextReplacement("MW invoice 41")
        ui.onNode(hasText(" • MW invoice 41 • ", substring = true) and hasClickAction()).performClick()
        ui.onNodeWithText("Aufteilung prüfen").assertIsEnabled().performClick()
        ui.onNodeWithText("Aufteilung bestätigen").assertIsEnabled().performClick()
        try {
            ui.waitUntil(10_000) { ui.waitForIdle(); model.bankReceiptLinks.value.any { it.receiptId == 41 } }
        } catch (error: ComposeTimeoutException) {
            val stored = runBlocking(Dispatchers.IO) { database.bankDao().getLinksForTransaction(transaction.transactionId) }
            throw AssertionError("Confirmation did not publish receipt 41; persisted links=$stored\n${ui.onRoot().printToString()}", error)
        }
        runBlocking(Dispatchers.IO) {
            val link = database.bankDao().getLinksForTransaction(transaction.transactionId).single()
            assertEquals(41, link.receiptId)
            assertEquals("picker-receipt-41", link.receiptInternalId)
            assertEquals(100.0, link.allocatedAmount, 0.0)
            val saved = database.bankDao().getTransaction(transaction.transactionId)!!
            assertEquals(BankReconciliationStatus.MATCHED, saved.reconciliationStatus)
            assertEquals(transaction.amount, saved.amount, 0.0)
            assertEquals(transaction.propertyId, saved.propertyId)
            assertEquals(receipts.sortedBy { it.id }, database.receiptDao().getAllReceiptsList().sortedBy { it.id })
            assertTrue(database.bankRentAssignmentDao().getForTransaction(transaction.transactionId).isEmpty())
        }
    }

    private fun boundLatestDialog() {
        // Existing native Robolectric picker tests bound wrap-content text-input dialogs too.
        ui.mainClock.advanceTimeByFrame()
        ui.runOnUiThread {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            assertTrue(dialog.isShowing)
            val density = ui.activity.resources.displayMetrics.density
            dialog.window!!.setLayout((360 * density).toInt(), (700 * density).toInt())
        }
    }
}
