package com.example.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import java.lang.management.ManagementFactory
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Repeatable CPU/allocation diagnostics; these are JVM measurements, not device latency. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BankComputationPerformanceTest {
    private lateinit var context: Context
    private val today = LocalDate.of(2026, 10, 8)
    private val property = PropertyMetadata(propertyId = "perf-property", name = "Synthetic house")
    private val units = (0..1).map { index ->
        WohneinheitStatus("WE $index", "WE $index", "Vermietet", "Müller $index", 690.0, 60.0,
            "2025-01-01", "perf-unit-$index")
    }

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
        units.forEachIndexed { index, unit ->
            TenantHistoryStore.save(context, property.propertyId, unit.unitId, unit.name,
                listOf(TenantPeriod(index + 1L, unit.name, unit.mieter, "2025-01-01", "", 690.0, 200.0, 0.0)))
        }
    }

    @Test fun groupingPreservesLegacyOrderLabelsInvalidDatesAndEveryIdentity() {
        val rows = transactions(1_250).mapIndexed { index, row ->
            row.copy(bookingDate = listOf("2026-10-08", "2026-10-07", "", "bad-date", "2026-02-30", "2026-10-01")[index % 6])
        }.reversed()
        assertEquals(legacyGroups(rows), BankCompactUiPolicy.group(rows, today))
        val grouped = BankCompactUiPolicy.group(rows, today).flatMap { it.transactions }
        assertEquals(rows.associateBy { it.transactionId }, grouped.associateBy { it.transactionId })
        assertEquals(rows.size, grouped.size)
    }

    @Test fun searchAndStatusCountsRetainGermanNormalizationAndLocaleBehavior() {
        val rows = transactions(1_250)
        val oldLocale = Locale.getDefault()
        try {
            listOf(Locale.GERMANY, Locale.US).forEach { locale ->
                Locale.setDefault(locale)
                listOf("MÜLLER", "mueller", "reparatur", "ref-00012", "64,57", "-64.57", "", "  ").forEach { query ->
                    assertEquals("locale=$locale query=$query", legacySearch(rows, query), BankCompactUiPolicy.search(rows, query))
                }
                val counts = BankCompactUiPolicy.counts(rows)
                BankCompactFilter.entries.forEach { filter ->
                    assertEquals(filter.name, BankCompactUiPolicy.filter(rows, filter).size, BankCompactUiPolicy.countFor(counts, filter))
                }
            }
        } finally { Locale.setDefault(oldLocale) }
    }

    @Test fun receiptBatchMatchingRetainsSingleTransactionResultsAndReadOnlyInputs() {
        val rows = transactions(1_250)
        val receipts = receipts()
        val links = links(rows)
        val beforeRows = rows.toList()
        val beforeReceipts = receipts.toList()
        val beforeLinks = links.toList()
        val confirmedIds = links.filter { it.status == BankLinkStatus.CONFIRMED }.map { it.transactionId }.toSet()
        val expected = rows.filter { BankClassificationPolicy.decision(it).eligibleForReceiptMatching }
            .filterNot { it.transactionId in confirmedIds && it.reconciliationStatus == BankReconciliationStatus.MATCHED }
            .mapNotNull { row -> BankReceiptMatcher.bestForTransaction(row, receipts, links)?.let { row.transactionId to it } }.toMap()
        assertEquals(expected, BankReceiptMatcher.bestSuggestions(rows, receipts, links))
        assertEquals(beforeRows, rows)
        assertEquals(beforeReceipts, receipts)
        assertEquals(beforeLinks, links)
    }

    @Test fun rentalCandidateRetainsConfirmedAmountsMonthLinkAndRhythmWithoutUsingItsOwnHistory() {
        val target = transactions(8)[4].copy(transactionId = "target-rent", accountId = "self-account",
            bookingDate = "2026-10-15", purpose = "Miete September 2026 WE 0")
        val prior = target.copy(transactionId = "prior-rent", accountId = "historical-account", bookingDate = "2026-09-03")
        val assignments = listOf(target, prior).mapIndexed { index, row ->
            BankRentAssignment("candidate-assignment-$index", row.transactionId, property.propertyId,
                units[0].unitId, "2026-09", "tenant-1", (index + 1) * 100.0, RentPaymentType.MIETE,
                createdAt = "2026-09-03T12:00:00Z", updatedAt = "2026-09-03T12:00:00Z")
        }
        val rentalReceipt = receipts().first { it.hauptkategorie == "Miete, Nebenkosten & Kaution" }
        val link = BankReceiptLink("candidate-link", target.transactionId, rentalReceipt.id,
            receiptInternalId = rentalReceipt.internalId, allocatedAmount = 100.0)
        val candidate = BankRentCandidateFactory.build(context, target, listOf(property),
            mapOf(property.propertyId to units), listOf(rentalReceipt), assignments, listOf(target, prior), listOf(link)).single()
        assertEquals(RentCandidateContext(property.propertyId, property.name, units[0].unitId, units[0].name,
            "tenant-1", units[0].mieter, "2025-01-01", "", java.time.YearMonth.of(2026, 9),
            890.0, 300.0, "historical-account", 3, true), candidate)
    }

    @Test fun report1250And5000ComputationAndAllocationBaselines() {
        listOf(1_250, 5_000).forEach { count ->
            val rows = transactions(count)
            val receipts = receipts()
            val links = links(rows)
            val assignments = assignments(rows)
            val preferencesBefore = PersistentPreferenceInventory.stores.associate { store ->
                store.name to context.getSharedPreferences(store.name, 0).all.toMap()
            }
            measure(count, "counts") { BankCompactUiPolicy.counts(rows) }
            measure(count, "search") { BankCompactUiPolicy.search(rows, "mueller") }
            measure(count, "group") { BankCompactUiPolicy.group(rows, today) }
            measure(count, "receipt_matching") { BankReceiptMatcher.bestSuggestions(rows, receipts, links) }
            measure(count, "rent_candidates") {
                val batch = BankRentCandidateFactory.Batch(context, listOf(property), mapOf(property.propertyId to units),
                    receipts, assignments, rows, links)
                rows.asSequence().filter { it.isIncome }.sumOf { row -> batch.build(row).size }
            }
            assertEquals(preferencesBefore, PersistentPreferenceInventory.stores.associate { store ->
                store.name to context.getSharedPreferences(store.name, 0).all.toMap()
            })
        }
    }

    private fun measure(count: Int, operation: String, compute: () -> Any) {
        // Two unreported warmups and three identical measured workloads. No time-based
        // assertion: deterministic viewport/worker tests enforce regression budgets.
        repeat(2) { compute() }
        val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        if (bean?.isThreadAllocatedMemorySupported == true) bean.isThreadAllocatedMemoryEnabled = true
        val id = Thread.currentThread().id
        val elapsed = mutableListOf<Double>()
        val allocated = mutableListOf<Long>()
        repeat(3) {
            val allocationStart = bean?.getThreadAllocatedBytes(id) ?: -1
            val start = System.nanoTime()
            val result = compute()
            elapsed += (System.nanoTime() - start) / 1_000_000.0
            allocated += if (allocationStart >= 0) (bean?.getThreadAllocatedBytes(id) ?: allocationStart) - allocationStart else -1
            assertNotNull(result)
        }
        println("BANK_COMPUTATION runtime=robolectric-jvm count=$count operation=$operation " +
            "runs=3 median_ms=${elapsed.sorted()[1]} min_ms=${elapsed.min()} max_ms=${elapsed.max()} " +
            "median_allocated_bytes=${allocated.sorted()[1]}")
    }

    private fun transactions(count: Int): List<BankTransaction> = (0 until count).map { index ->
        BankTransaction(transactionId = "perf-${index.toString().padStart(5, '0')}", accountId = "perf-account-${index % 2}",
            bookingDate = "2026-10-${(1 + index % 8).toString().padStart(2, '0')}",
            amount = if (index % 4 == 0) 890.0 else -64.57,
            counterparty = if (index % 4 == 0) "Müller ${index % 2}" else "Synthetic merchant ${index % 40}",
            purpose = if (index % 4 == 0) "Miete Oktober 2026 WE ${index % 2}" else "Reparatur Rechnung ${index % 40}",
            bankReference = "REF-${index.toString().padStart(5, '0')}",
            propertyId = property.propertyId, unitId = units[index % 2].unitId,
            category = "unveränderte Kategorie", subcategory = "unveränderte Unterkategorie",
            reconciliationStatus = when (index % 11) {
                0 -> BankReconciliationStatus.MATCHED
                1 -> BankReconciliationStatus.PARTIAL
                else -> BankReconciliationStatus.OPEN
            },
            classification = when (index % 13) {
                0 -> BankTransactionClassification.TRANSFER
                1 -> BankTransactionClassification.PRIVATE_IGNORED
                else -> BankTransactionClassification.NORMAL
            })
    }

    private fun receipts(): List<Receipt> = (1..40).map { index ->
        Receipt(index, if (index % 4 == 0) "Müller 0" else "Synthetic merchant $index", "2026-10-08", "",
            if (index % 4 == 0) 890.0 else 64.57,
            if (index % 4 == 0) "Miete, Nebenkosten & Kaution" else "Instandhaltung",
            if (index % 4 == 0) "Warmmiete" else "Reparatur", "4800", "Reparatur Rechnung $index",
            propertyId = property.propertyId, unitId = units[index % 2].unitId, internalId = "receipt-perf-$index",
            displayId = "BLG-PERF-$index", imageUrl = "content://synthetic/receipt/$index")
    }

    private fun links(rows: List<BankTransaction>): List<BankReceiptLink> = rows.filterIndexed { index, _ -> index % 11 == 0 }
        .mapIndexed { index, row -> BankReceiptLink("perf-link-$index", row.transactionId, index % 40 + 1,
            allocatedAmount = row.absoluteAmount / 2, createdAt = "2026-10-08T12:00:00Z") }

    private fun assignments(rows: List<BankTransaction>): List<BankRentAssignment> = rows.filter { it.isIncome }.take(16)
        .mapIndexed { index, row -> BankRentAssignment("perf-rent-$index", row.transactionId, property.propertyId,
            row.unitId, "2026-09", "tenant-${index % 2 + 1}", 100.0, RentPaymentType.MIETE,
            createdAt = "2026-09-08T12:00:00Z", updatedAt = "2026-09-08T12:00:00Z") }

    // Frozen pre-optimization reference, including stable ordering of invalid date groups.
    private fun legacyGroups(rows: List<BankTransaction>): List<BankDateGroup> = rows.sortedWith(
        compareByDescending<BankTransaction> { parseDate(it.bookingDate) ?: LocalDate.MIN }.thenByDescending { it.transactionId }
    ).groupBy { it.bookingDate }.map { (text, values) ->
        val date = parseDate(text)
        val formatted = date?.let { "%02d.%02d.%04d".format(it.dayOfMonth, it.monthValue, it.year) }
        BankDateGroup(text, when (date) {
            today -> "Heute, $formatted"
            today.minusDays(1) -> "Gestern, $formatted"
            null -> text.ifBlank { "Datum unbekannt" }
            else -> formatted!!
        }, values)
    }.sortedByDescending { parseDate(it.key) ?: LocalDate.MIN }

    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

    private fun legacySearch(rows: List<BankTransaction>, query: String): List<BankTransaction> {
        val needle = normalize(query)
        if (needle.isBlank()) return rows
        return rows.filter { row -> needle in normalize(listOf(row.counterparty, row.purpose, row.bankReference,
            row.amount.toString(), java.text.DecimalFormat("0.00").format(row.amount)).joinToString(" ")) }
    }

    private fun normalize(text: String): String = text.lowercase(Locale.GERMANY)
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace(',', '.').replace(Regex("\\s+"), " ").trim()
}
