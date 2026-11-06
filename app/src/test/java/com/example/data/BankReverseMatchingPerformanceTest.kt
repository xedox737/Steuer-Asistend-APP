package com.example.data

import java.lang.management.ManagementFactory
import org.junit.Assert.assertEquals
import org.junit.Test

/** Diagnostic CPU/allocation workload for the reverse panel and its two pickers. */
class BankReverseMatchingPerformanceTest {
    @Test fun reportReversePanelAndPickerCostsFor1250And5000Rows() {
        listOf(1_250, 5_000).forEach { count ->
            val rows = transactions(count)
            val receipts = receipts()
            val links = (0 until 10).map { index ->
                BankReceiptLink("reverse-link-$index", rows[index].transactionId, receipts[index].id,
                    receiptInternalId = receipts[index].internalId, allocatedAmount = 25.0)
            }
            val snapshot = Triple(rows.toList(), receipts.toList(), links.toList())
            measure(count, "reverse_panel_30_receipts") {
                receipts.take(30).map { receipt ->
                    val receiptLinks = links.filter { it.receiptId == receipt.id ||
                        (receipt.internalId.isNotBlank() && it.receiptInternalId == receipt.internalId) }
                    val suggestion = BankReceiptMatcher.bestForReceipt(receipt, rows, links)
                    val selected = if (receiptLinks.isEmpty() && suggestion != null) {
                        rows.firstOrNull { it.transactionId == suggestion.transactionId }
                    } else null
                    Triple(receiptLinks, suggestion, selected)
                }
            }
            measure(count, "transaction_picker_rank") {
                BankReceiptMatcher.rankTransactionsForReceipt(receipts[11], rows, links)
            }
            measure(count, "receipt_picker_rank") {
                BankReceiptMatcher.rankReceipts(rows.last(), receipts, links)
            }
            assertEquals("Matching must preserve every source field and relation", snapshot, Triple(rows, receipts, links))
        }
    }

    private fun measure(count: Int, operation: String, compute: () -> Any) {
        val expected = compute()
        assertEquals(expected, compute())
        val bean = ManagementFactory.getThreadMXBean()
        if (bean.isCurrentThreadCpuTimeSupported) bean.isThreadCpuTimeEnabled = true
        val allocationBean = bean as? com.sun.management.ThreadMXBean
        if (allocationBean?.isThreadAllocatedMemorySupported == true) allocationBean.isThreadAllocatedMemoryEnabled = true
        val id = Thread.currentThread().id
        val wallMs = mutableListOf<Double>()
        val cpuMs = mutableListOf<Double>()
        val allocated = mutableListOf<Long>()
        repeat(5) {
            val allocationStart = allocationBean?.getThreadAllocatedBytes(id) ?: -1L
            val cpuStart = if (bean.isCurrentThreadCpuTimeSupported) bean.currentThreadCpuTime else -1L
            val start = System.nanoTime()
            val result = compute()
            wallMs += (System.nanoTime() - start) / 1_000_000.0
            cpuMs += if (cpuStart >= 0) (bean.currentThreadCpuTime - cpuStart) / 1_000_000.0 else -1.0
            allocated += if (allocationStart >= 0) (allocationBean?.getThreadAllocatedBytes(id) ?: allocationStart) - allocationStart else -1L
            assertEquals("operation=$operation count=$count", expected, result)
        }
        println("BANK_REVERSE runtime=jvm count=$count operation=$operation warmups=2 runs=5 " +
            "median_wall_ms=${wallMs.sorted()[2]} min_wall_ms=${wallMs.min()} max_wall_ms=${wallMs.max()} " +
            "median_cpu_ms=${cpuMs.sorted()[2]} median_allocated_bytes=${allocated.sorted()[2]}")
    }

    private fun transactions(count: Int): List<BankTransaction> = (0 until count).map { index ->
        val income = index % 5 == 0
        BankTransaction(transactionId = "reverse-${index.toString().padStart(5, '0')}", accountId = "reverse-account",
            bookingDate = "2026-10-08", amount = if (income) 600.0 else -(50.0 + index % 20),
            counterparty = "Synthetic viewport merchant ${index.toString().padStart(5, '0')}",
            purpose = if (income) "Miete Oktober WE 01" else "Synthetic repair $index",
            bankReference = "REVERSE-REF-$index", propertyId = "reverse-property", unitId = "reverse-unit",
            category = if (income) "Miete, Nebenkosten & Kaution" else "Instandhaltung",
            subcategory = if (income) "Kaltmiete" else "Reparatur", importRunId = "synthetic-reverse-import")
    }

    private fun receipts(): List<Receipt> = (0 until 40).map { index ->
        val income = index % 2 == 0
        Receipt(id = 20_000 + index, aussteller = "Synthetic viewport merchant $index", datum = "2026-10-08",
            uhrzeit = "", bruttobetrag = if (income) 600.0 else 50.0 + index % 20,
            hauptkategorie = if (income) "Miete, Nebenkosten & Kaution" else "Instandhaltung",
            unterkategorie = if (income) "Kaltmiete" else "Reparatur", kontoNr = if (income) "8100" else "4800",
            beschreibung = if (income) "Miete Oktober WE 01" else "Synthetic repair",
            propertyId = "reverse-property", unitId = "reverse-unit", wohneinheit = "WE 01",
            internalId = "reverse-receipt-$index")
    }
}
