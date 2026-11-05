package com.example.e2e

import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.os.Trace
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.MainActivity
import com.example.data.*
import com.example.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Run unchanged against base and head in a disposable emulator. Timings are warm-process
 * Activity relaunches, not process cold starts. Sampling is separate from timed runs.
 */
@RunWith(AndroidJUnit4::class)
class BankPerformanceEmulatorTest : EmulatorTestSupport() {
    private val baseline get() = InstrumentationRegistry.getArguments().getString("immopilotBankBaseline") == "true"
    private val listMatcher = SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)

    @Test fun imported1250RowsStayResponsiveAndPreserveAllData() = exerciseImport(1_250)

    @Test fun imported5000RowsStayResponsiveAndPreserveAllData() = exerciseImport(5_000)

    private fun exerciseImport(count: Int) {
        val property = seedProperty("bank-perf-property", "Synthetisches Miethaus", rented = true)
        val unit = vm.wohneinheitenStatus.value.single()
        val account = BankAccount("bank-perf-account", "Synthetisches Bankkonto")
        val transactions = (0 until count).map { index ->
            val income = index % 5 == 0
            BankTransaction(
                transactionId = "bank-perf-${index.toString().padStart(5, '0')}", accountId = account.accountId,
                bookingDate = "2026-10-08", amount = if (income) 600.0 else -(50.0 + index % 20),
                counterparty = "Synthetic viewport merchant ${index.toString().padStart(5, '0')}",
                purpose = if (income) "Miete Oktober WE 01" else "Synthetic repair $index",
                bankReference = "PERF-REF-$index", propertyId = property.propertyId, unitId = unit.unitId,
                category = if (income) "Miete, Nebenkosten & Kaution" else "Instandhaltung",
                subcategory = if (income) "Kaltmiete" else "Reparatur", importRunId = "synthetic-perf-import",
                importFileName = "synthetic-performance.csv", importedAt = "2026-10-08T12:00:00Z"
            )
        }
        val original = File(context.cacheDir, "synthetic-bank-perf-original.pdf")
            .apply { writeText("%PDF-1.4\nSynthetic performance fixture\n%%EOF") }
        val originalBytes = original.readBytes()
        runBlocking(Dispatchers.IO) {
            db.bankDao().upsertAccount(account)
            assertEquals(count, db.bankDao().insertTransactions(transactions).count { it != -1L })
            repeat(40) { index ->
                val rental = index % 2 == 0
                db.receiptDao().insertReceipt(Receipt(
                    id = 20_000 + index, aussteller = "Synthetic viewport merchant $index", datum = "2026-10-08",
                    uhrzeit = "", bruttobetrag = if (rental) 600.0 else 50.0 + index % 20,
                    hauptkategorie = if (rental) "Miete, Nebenkosten & Kaution" else "Instandhaltung",
                    unterkategorie = if (rental) "Kaltmiete" else "Reparatur", kontoNr = if (rental) "8100" else "4800",
                    beschreibung = if (rental) "Miete Oktober WE 01" else "Synthetic repair",
                    propertyId = property.propertyId, unitId = unit.unitId, wohneinheit = unit.name,
                    internalId = "bank-perf-receipt-$index", imageUrl = original.absolutePath,
                    originalMimeType = "application/pdf", fileSizeBytes = original.length()
                ))
            }
            repeat(10) { index ->
                db.bankDao().upsertLink(BankReceiptLink("bank-perf-link-$index", transactions[index].transactionId,
                    20_000 + index, "bank-perf-receipt-$index", 25.0, createdAt = "2026-10-08T12:00:00Z"))
            }
            repeat(4) { index ->
                db.bankRentAssignmentDao().upsert(BankRentAssignment("bank-perf-rent-$index",
                    transactions[index * 5].transactionId, property.propertyId, unit.unitId, "2026-10",
                    "bank-perf-tenant", 25.0, RentPaymentType.MIETE, receiptId = 20_000 + index * 2,
                    createdAt = "2026-10-08T12:00:00Z", updatedAt = "2026-10-08T12:00:00Z"))
            }
        }
        val initialModel = vm
        ui.waitUntil(10_000) { initialModel.bankTransactions.value.size == count && initialModel.receipts.value.size == 40 }
        val before = snapshot()
        val queryPlans = runBlocking(Dispatchers.IO) {
            listOf(
                "SELECT * FROM bank_transactions ORDER BY bookingDate DESC, transactionId DESC",
                "SELECT * FROM bank_receipt_links WHERE transactionId = 'bank-perf-00000'",
                "SELECT * FROM bank_rent_assignments WHERE propertyId = 'bank-perf-property' AND unitId = '${unit.unitId}' AND rentMonth = '2026-10' AND status = 'CONFIRMED'"
            ).joinToString("\n") { sql ->
                db.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN $sql").use { cursor ->
                    val steps = mutableListOf<String>()
                    while (cursor.moveToNext()) steps += cursor.getString(3)
                    "$sql\n${steps.joinToString("; ") }"
                }
            }
        }
        saveArtifact("bank-query-plans-$count.txt", queryPlans.toByteArray())
        val results = JSONArray()
        val report = JSONObject().put("count", count).put("baseline", baseline)
            .put("startup_kind", "warm_process_activity_relaunch").put("runs", results)
        val heartbeats = mutableListOf<Long>()
        val detailHeartbeats = mutableListOf<Long>()
        try {
            repeat(3) { run ->
                scenario.close()
                val startupHeartbeat = Heartbeat().apply { start() }
                val startupAt = SystemClock.uptimeMillis()
                val startupMs: Long
                val startupGap: Long
                try {
                    scenario = ActivityScenario.launch(MainActivity::class.java)
                    ui.onNodeWithText("Willkommen bei ImmoPilot").assertIsDisplayed()
                    val model = vm
                    ui.waitUntil(10_000) { model.bankTransactions.value.size == count && model.receipts.value.size == 40 }
                    startupMs = SystemClock.uptimeMillis() - startupAt
                } finally { startupGap = startupHeartbeat.stop() }
                prepareBankEntry()
                shell("dumpsys gfxinfo ${context.packageName} reset")
                val heartbeat = Heartbeat().apply { start() }
                val bankAt = SystemClock.uptimeMillis()
                val row = JSONObject().put("run", run + 1).put("activity_relaunch_ms", startupMs)
                    .put("startup_main_max_gap_ms", startupGap)
                results.put(row)
                val bankGap: Long
                try {
                    openBank(transactions.last().counterparty)
                    row.put("bank_ready_ms", SystemClock.uptimeMillis() - bankAt)
                    val composed = ui.onAllNodes(hasText("Synthetic viewport merchant", substring = true)).fetchSemanticsNodes().size
                    row.put("composed_rows", composed)
                    if (!baseline) assertTrue("Same-day import must stay lazy: count=$count composed=$composed", composed in 1..50)
                } finally {
                    bankGap = heartbeat.stop()
                    row.put("bank_main_max_gap_ms", bankGap)
                    heartbeats += bankGap
                }
                saveArtifact("bank-perf-$count-run-${run + 1}-meminfo.txt", shell("dumpsys meminfo ${context.packageName}").toByteArray())
                saveArtifact("bank-perf-$count-run-${run + 1}-gfxinfo.txt", shell("dumpsys gfxinfo ${context.packageName} framestats").toByteArray())
                if (run == 0) capture("bank-performance-$count-list")
                val detailHeartbeat = Heartbeat().apply { start() }
                val detailAt = SystemClock.uptimeMillis()
                try {
                    openDetailsAndAwaitSuggestions(transactions.last().counterparty)
                    row.put("detail_ready_ms", SystemClock.uptimeMillis() - detailAt)
                } finally {
                    val gap = detailHeartbeat.stop()
                    row.put("detail_main_max_gap_ms", gap)
                    detailHeartbeats += gap
                }
                saveArtifact("bank-perf-$count-run-${run + 1}-detail-meminfo.txt", shell("dumpsys meminfo ${context.packageName}").toByteArray())
                if (run == 0) capture("bank-performance-$count-detail")
                systemBack()
                // Indexed keys exercise the final imported ID without scanning thousands of viewports.
                if (!baseline) {
                    ui.onNode(listMatcher).performScrollToKey("transaction-${transactions.first().transactionId}")
                    ui.onNodeWithText(transactions.first().counterparty).assertIsDisplayed()
                    ui.onNodeWithText(transactions.first().counterparty).performClick()
                    ui.onNodeWithText("Buchungsdetails", substring = true).assertExists()
                    systemBack()
                }
                val recreateAt = SystemClock.uptimeMillis()
                scenario.recreate()
                ui.onNode(listMatcher).assertExists()
                row.put("activity_recreate_ms", SystemClock.uptimeMillis() - recreateAt)
                assertEquals("Every persisted field and relation must survive relaunch/navigation", before, snapshot())
                assertArrayEquals(originalBytes, original.readBytes())
            }
            if (!baseline) {
                val bankReady = (0 until results.length()).map { results.getJSONObject(it).getLong("bank_ready_ms") }
                assertTrue("Bank list must become ready within five seconds: $bankReady", bankReady.sorted()[1] < 5_000L)
                // Median avoids a single emulator scheduling outlier; two multi-second stalls fail.
                assertTrue("Bank navigation must not approach the five-second ANR threshold: $heartbeats",
                    heartbeats.sorted()[1] < 2_000L)
                assertTrue("Opening details and computing suggestions must remain responsive: $detailHeartbeats",
                    detailHeartbeats.sorted()[1] < 2_000L)
            }
            recordProfile(count, transactions, property, unit)
            assertEquals(before, snapshot())
        } finally {
            saveArtifact("bank-performance-$count.json", report.toString(2).toByteArray())
        }
    }

    private fun prepareBankEntry() {
        clickTab(AppScreen.MORE)
        ui.onNodeWithTag("more_menu").performScrollToNode(hasTestTag("more_item_Bank & Kontoauszüge"))
    }

    private fun openBank(firstCounterparty: String) {
        ui.onNodeWithTag("more_item_Bank & Kontoauszüge").performClick()
        ui.waitUntil(10_000) { ui.onAllNodes(hasText(firstCounterparty)).fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(listMatcher).performScrollToNode(hasText(firstCounterparty))
        ui.onNodeWithText(firstCounterparty).assertIsDisplayed()
    }

    private fun openDetailsAndAwaitSuggestions(counterparty: String, profileAlreadyActive: Boolean = false) {
        val diagnostic = !profileAlreadyActive && InstrumentationRegistry.getArguments()
            .getString("immopilotBankDiagnosticProfile") == "true"
        val count = vm.bankTransactions.value.size
        val trace = File(context.cacheDir, "bank-suggestion-diagnostic-$count.trace")
        if (diagnostic) Debug.startMethodTracingSampling(trace.absolutePath, 8 * 1024 * 1024, 1_000)
        try {
            ui.onNodeWithText(counterparty).performClick()
            ui.onNodeWithText("Buchungsdetails").assertIsDisplayed()
            val model = vm
            try {
                ui.waitUntil(10_000) { model.bankMatchSuggestions.value.isNotEmpty() && model.bankRentSuggestions.value.isNotEmpty() }
            } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
                throw AssertionError("Suggestion readiness: transactions=${model.bankTransactions.value.size}, " +
                    "receipts=${model.receipts.value.size}, properties=${model.properties.value.size}, " +
                    "receiptSuggestions=${model.bankMatchSuggestions.value.size}, " +
                    "rentSuggestions=${model.bankRentSuggestions.value.size}", failure)
            }
        } finally {
            if (diagnostic) {
                Debug.stopMethodTracing()
                saveArtifact("bank-suggestion-diagnostic-$count.trace", trace.readBytes())
            }
        }
    }

    private fun recordProfile(count: Int, transactions: List<BankTransaction>, property: PropertyMetadata, unit: WohneinheitStatus) {
        // A fresh ViewModel prevents the preceding detail runs' StateFlow values
        // from turning the sampled matching phase into a cache-hit profile.
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        ui.onNodeWithText("Willkommen bei ImmoPilot").assertIsDisplayed()
        val model = vm
        ui.waitUntil(10_000) { model.bankTransactions.value.size == count && model.receipts.value.size == 40 }
        prepareBankEntry()
        val trace = File(context.cacheDir, "bank-profile-$count.trace")
        val workloads = JSONObject()
        Debug.startMethodTracingSampling(trace.absolutePath, 16 * 1024 * 1024, 1_000)
        try {
            openBank(transactions.last().counterparty)
            openDetailsAndAwaitSuggestions(transactions.last().counterparty, profileAlreadyActive = true)
            val data = snapshot()
            fun workload(name: String, compute: () -> Unit) {
                val wall = SystemClock.elapsedRealtimeNanos()
                val cpu = Debug.threadCpuTimeNanos()
                Trace.beginSection("BankPerf.$name")
                try { compute() } finally {
                    Trace.endSection()
                    workloads.put(name, JSONObject().put("wall_ms", (SystemClock.elapsedRealtimeNanos() - wall) / 1_000_000.0)
                        .put("cpu_ms", (Debug.threadCpuTimeNanos() - cpu) / 1_000_000.0).put("thread", Thread.currentThread().name))
                }
            }
            workload("dao_transactions") { runBlocking(Dispatchers.IO) { assertEquals(count, db.bankDao().getAllTransactions().size) } }
            workload("dao_links_and_rent") { runBlocking(Dispatchers.IO) { db.bankDao().getAllLinks(); db.bankRentAssignmentDao().getAll() } }
            workload("counts") { BankCompactUiPolicy.counts(transactions) }
            workload("group") { BankCompactUiPolicy.group(transactions) }
            workload("search") { BankCompactUiPolicy.search(transactions, "Miete") }
            workload("matcher") { BankReceiptMatcher.bestSuggestions(transactions, data.receipts, data.links) }
            workload("rent") {
                // The same checked-in harness also runs on the unoptimized base commit.
                // Only absence is optional; a broken new API must fail this test.
                val batchClass = try { Class.forName("com.example.ui.BankRentCandidateFactory\$Batch") }
                    catch (_: ClassNotFoundException) { null }
                val batch = batchClass?.getDeclaredConstructor(android.content.Context::class.java,
                    List::class.java, Map::class.java, List::class.java, List::class.java, List::class.java, List::class.java)
                    ?.newInstance(context, listOf(property), mapOf(property.propertyId to listOf(unit)),
                        data.receipts, data.assignments, transactions, data.links)
                val batchBuild = batchClass?.getDeclaredMethod("build", BankTransaction::class.java)
                workloads.put("rent_uses_batch", batch != null)
                transactions.filter { it.isIncome }.forEach { transaction ->
                    @Suppress("UNCHECKED_CAST")
                    val candidates = if (batch != null) batchBuild!!.invoke(batch, transaction) as List<RentCandidateContext>
                    else BankRentCandidateFactory.build(context, transaction, listOf(property),
                        mapOf(property.propertyId to listOf(unit)), data.receipts, data.assignments, transactions, data.links)
                    BankRentMatcher.match(transaction, candidates)
                }
            }
        } finally {
            Debug.stopMethodTracing()
            saveArtifact("bank-profile-$count.trace", trace.readBytes())
            saveArtifact("bank-workloads-$count.json", workloads.toString(2).toByteArray())
        }
    }

    private data class Snapshot(val accounts: List<BankAccount>, val transactions: List<BankTransaction>,
        val links: List<BankReceiptLink>, val assignments: List<BankRentAssignment>, val receipts: List<Receipt>,
        val properties: List<PropertyMetadata>)

    private fun snapshot() = runBlocking(Dispatchers.IO) {
        Snapshot(db.bankDao().getAllAccounts(), db.bankDao().getAllTransactions(), db.bankDao().getAllLinks(),
            db.bankRentAssignmentDao().getAll(), db.receiptDao().getAllReceiptsIncludingDeletedList().sortedBy { it.id },
            db.propertyDao().getAllProperties().sortedBy { it.propertyId })
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

    private class Heartbeat : Runnable {
        private val handler = Handler(Looper.getMainLooper())
        @Volatile private var previous = 0L
        @Volatile private var maximum = 0L
        @Volatile private var running = false
        fun start() { previous = SystemClock.uptimeMillis(); running = true; handler.post(this) }
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            maximum = maxOf(maximum, now - previous)
            previous = now
            handler.postDelayed(this, 16)
        }
        fun stop(): Long {
            running = false
            handler.removeCallbacks(this)
            return maxOf(maximum, SystemClock.uptimeMillis() - previous)
        }
    }
}
