package com.example.ui

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real screen/tool/picker, with a deterministic guard against matching on Android's main thread. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-420dpi")
class BankReverseMatchingComposeTest {
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

    @Test fun reverseToolAndBankPickerKeep1250MatchesOffMainAndPreserveAllIds() = exercise(1_250)
    @Test fun reverseToolAndBankPickerKeep5000MatchesOffMainAndPreserveAllIds() = exercise(5_000)

    private fun exercise(count: Int) {
        val transactions = (0 until count).map { index ->
            BankTransaction("reverse-${index.toString().padStart(5, '0')}", "reverse-account", "2026-10-08",
                amount = -(100.0 + index % 30),
                counterparty = "Reverse Merchant ${index % 30} Tx${index.toString().padStart(5, '0')}",
                purpose = "Reverse Repair $index", bankReference = "REVERSE-$index",
                category = "Instandhaltung", subcategory = "Reparatur", propertyId = "reverse-property",
                unitId = "reverse-unit", importRunId = "reverse-import")
        }
        val receipts = (0 until 30).map { index ->
            Receipt(30_000 + index, "Reverse Merchant $index", "2026-10-08", "", 100.0 + index,
                "Instandhaltung", "Reparatur", "4800", "Reverse Repair $index",
                internalId = "reverse-receipt-$index", propertyId = "reverse-property", unitId = "reverse-unit")
        }
        val links = listOf(BankReceiptLink("reverse-existing-link", transactions.first().transactionId,
            receipts.last().id, receipts.last().internalId, 25.0))
        val account = BankAccount("reverse-account", "Reverse synthetic account")
        runBlocking(Dispatchers.IO) {
            database.bankDao().upsertAccount(account)
            database.bankDao().insertTransactions(transactions)
            receipts.forEach { database.receiptDao().insertReceipt(it) }
            links.forEach { database.bankDao().upsertLink(it) }
        }
        val before = snapshot()
        // Independent ranking orchestration on a worker: public score/eligibility stay canonical,
        // while tie ordering, linked-ID exclusion and UI resolution are asserted here explicitly.
        val expected = runBlocking(Dispatchers.Default) {
            receipts.associate { receipt ->
                val linkedIds = links.filter { it.receiptId == receipt.id || it.receiptInternalId == receipt.internalId }
                    .map { it.transactionId }.toSet()
                receipt.id to transactions.asSequence().filterNot { it.transactionId in linkedIds }
                    .map { BankReceiptMatcher.score(it, receipt) }.filter { it.score >= 45 }
                    .maxWithOrNull(compareBy<BankMatchSuggestion> { it.score }.thenBy { it.transactionId })!!
            }
        }
        val expectedPicker = runBlocking(Dispatchers.Default) {
            transactions.filter { BankLinkPolicy.propose(it, receipts.first(), links).allowed }
                .map { BankReceiptMatcher.score(it, receipts.first()) }
                .sortedWith(compareByDescending<BankMatchSuggestion> { it.score }.thenBy { it.transactionId }).take(50)
        }
        val transactionsById = transactions.associateBy { it.transactionId }
        fun suggestionText(receipt: Receipt): String = expected.getValue(receipt.id).let { match ->
            "Vorschlag: ${transactionsById.getValue(match.transactionId).counterparty} • ${match.score}%"
        }
        val guardedTransactions = MatcherThreadGuardList(transactions)
        lateinit var model: ReceiptViewModel
        ui.runOnUiThread {
            model = ViewModelProvider(models, ViewModelProvider.AndroidViewModelFactory(ui.activity.application))[ReceiptViewModel::class.java]
            // Test-only source seam, analogous to the existing in-memory database seam.
            // Use identical persisted values; production ViewModel/BankScreen are unchanged.
            // Guards inspect the stack once per iteration, never once per transaction access.
            replaceFlow(model, "bankTransactions", guardedTransactions)
            replaceFlow(model, "receipts", MatcherThreadGuardList(receipts))
        }
        ui.setContent { MaterialTheme { BankScreen(model) } }
        ui.waitUntil(10_000) { model.bankReceiptLinks.value == links }
        ui.waitUntil(10_000) { ui.onAllNodesWithText(transactions.last().counterparty).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithContentDescription("Weitere Funktionen").performClick()
        ui.onNodeWithText("Beleg → Bank-Zuordnung").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText(suggestionText(receipts.first())).fetchSemanticsNodes().isNotEmpty() }
        receipts.dropLast(1).forEach { receipt -> ui.onNodeWithText(suggestionText(receipt)).assertExists() }
        ui.onNodeWithText("${NumberFormatter.format(25.0)} bereits zugeordnet").assertExists()

        // Ordinary list search must keep the tool's account-scoped suggestions unchanged.
        ui.onNode(hasSetTextAction() and hasText("Buchungen durchsuchen …")).performTextReplacement("Reverse")
        receipts.dropLast(1).forEach { receipt -> ui.onNodeWithText(suggestionText(receipt)).assertExists() }
        ui.onAllNodesWithText("Andere Buchung auswählen").onFirst().performScrollTo().performClick()
        // Native Robolectric does not settle this wrap-content text-input dialog.
        // Match the bounded device window setup in RentOverviewComposeTest.
        ui.mainClock.advanceTimeByFrame()
        ui.runOnUiThread {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            assertTrue(dialog.isShowing)
            val density = ui.activity.resources.displayMetrics.density
            dialog.window!!.setLayout((360 * density).toInt(), (700 * density).toInt())
        }
        val firstRanked = transactionsById.getValue(expectedPicker.first().transactionId)
        val lastRanked = transactionsById.getValue(expectedPicker.last().transactionId)
        ui.waitUntil(10_000) {
            ui.onAllNodes(hasText(firstRanked.counterparty) and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty()
        }
        val pickerList = ui.onNode(hasScrollToIndexAction() and hasAnyAncestor(isDialog()))
        pickerList.performScrollToNode(hasText(firstRanked.counterparty))
        ui.onNode(hasText(firstRanked.counterparty) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        pickerList.performScrollToNode(hasText(lastRanked.counterparty))
        ui.onNode(hasText(lastRanked.counterparty) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        assertEquals("Search/recomposition must not repeat reverse matching", 31, guardedTransactions.matchingIterations.get())
        ui.onNode(hasText("Schließen") and hasAnyAncestor(isDialog())).performClick()
        assertEquals("Matching and browsing must preserve every persisted field and ID", before, snapshot())
    }

    private fun <T> replaceFlow(model: ReceiptViewModel, name: String, values: List<T>) {
        ReceiptViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }
            .set(model, MutableStateFlow(values))
    }

    private class MatcherThreadGuardList<T>(private val values: List<T>) : AbstractList<T>() {
        val matchingIterations = java.util.concurrent.atomic.AtomicInteger()
        override val size: Int get() = values.size
        override fun get(index: Int): T = values[index]
        override fun iterator(): Iterator<T> { checkMatchingThread(); return values.iterator() }
        override fun listIterator(): ListIterator<T> { checkMatchingThread(); return values.listIterator() }
        override fun listIterator(index: Int): ListIterator<T> { checkMatchingThread(); return values.listIterator(index) }
        private fun checkMatchingThread() {
            if (Throwable().stackTrace.any { it.className.startsWith(BankReceiptMatcher::class.java.name) }) {
                check(Looper.myLooper() != Looper.getMainLooper()) {
                    "BankReceiptMatcher must consume large inputs on a worker, never on Android's main thread"
                }
                matchingIterations.incrementAndGet()
            }
        }
    }

    private data class Snapshot(val transactions: List<BankTransaction>, val receipts: List<Receipt>, val links: List<BankReceiptLink>)
    private fun snapshot() = runBlocking(Dispatchers.IO) {
        Snapshot(database.bankDao().getAllTransactions(), database.receiptDao().getAllReceiptsIncludingDeletedList().sortedBy { it.id },
            database.bankDao().getAllLinks())
    }
}
