package com.example.ui

import com.example.data.BankReconciliationStatus
import com.example.data.BankTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BankCompactUiPolicyTest {
    private fun tx(
        id: String,
        amount: Double,
        status: String,
        account: String = "a1",
        date: String = "2026-09-10",
        counterparty: String = "Synthetic Partner",
        purpose: String = "Synthetic purpose",
        reference: String = "REF-123"
    ) = BankTransaction(
        transactionId = id,
        accountId = account,
        bookingDate = date,
        amount = amount,
        counterparty = counterparty,
        purpose = purpose,
        bankReference = reference,
        reconciliationStatus = status
    )

    private val all = listOf(
        tx("open", -10.0, BankReconciliationStatus.OPEN),
        tx("matched", -20.0, BankReconciliationStatus.MATCHED),
        tx("partial", 30.0, BankReconciliationStatus.PARTIAL),
        tx("review", -40.0, BankReconciliationStatus.REVIEW),
        tx("no", 50.0, BankReconciliationStatus.NO_RECEIPT_REQUIRED)
    )

    @Test fun allStatusFiltersAndCountsUseExistingStatusSssot() {
        val counts = BankCompactUiPolicy.counts(all)
        assertEquals(5, counts.all)
        assertEquals(1, counts.open)
        assertEquals(1, counts.matched)
        assertEquals(1, counts.partial)
        assertEquals(1, counts.review)
        assertEquals(1, counts.noReceiptRequired)
        BankCompactFilter.entries.forEach { filter ->
            assertEquals(BankCompactUiPolicy.countFor(counts, filter), BankCompactUiPolicy.filter(all, filter).size)
        }
    }

    @Test fun searchCoversCounterpartyPurposeAmountAndReference() {
        val source = listOf(
            tx("x", -64.57, BankReconciliationStatus.OPEN, counterparty = "Synthetic Vendor", purpose = "Invoice September", reference = "INV-262919")
        )
        assertEquals(1, BankCompactUiPolicy.search(source, "vendor").size)
        assertEquals(1, BankCompactUiPolicy.search(source, "invoice").size)
        assertEquals(1, BankCompactUiPolicy.search(source, "64.57").size)
        assertEquals(1, BankCompactUiPolicy.search(source, "262919").size)
        assertTrue(BankCompactUiPolicy.search(source, "not-there").isEmpty())
    }

    @Test fun accountFilterSupportsMultipleAccountsAndAllAccounts() {
        val source = all + tx("other", -1.0, BankReconciliationStatus.OPEN, account = "a2")
        assertEquals(source.size, BankCompactUiPolicy.account(source, null).size)
        assertEquals(all.size, BankCompactUiPolicy.account(source, "a1").size)
        assertEquals(1, BankCompactUiPolicy.account(source, "a2").size)
    }

    @Test fun summaryCalculatesIncomingOutgoingAndBalance() {
        val summary = BankCompactUiPolicy.summary(all)
        assertEquals(80.0, summary.incoming, 0.001)
        assertEquals(-70.0, summary.outgoing, 0.001)
        assertEquals(10.0, summary.balance, 0.001)
    }

    @Test fun newestFirstGroupingHasTodayYesterdayAndNormalDateLabels() {
        val today = LocalDate.of(2026, 9, 10)
        val source = listOf(
            tx("old", -1.0, BankReconciliationStatus.OPEN, date = "2026-09-08"),
            tx("today", -1.0, BankReconciliationStatus.OPEN, date = "2026-09-10"),
            tx("yesterday", -1.0, BankReconciliationStatus.OPEN, date = "2026-09-09")
        )
        val groups = BankCompactUiPolicy.group(source, today)
        assertEquals(listOf("2026-09-10", "2026-09-09", "2026-09-08"), groups.map { it.key })
        assertEquals("Heute, 10.09.2026", groups[0].label)
        assertEquals("Gestern, 09.09.2026", groups[1].label)
        assertEquals("08.09.2026", groups[2].label)
    }

    @Test fun compactRowsExposeRequiredLabelInformation() {
        val source = tx("x", 1500.0, BankReconciliationStatus.PARTIAL, purpose = "Miete September")
        assertEquals("Miete September", BankCompactUiPolicy.compactSubtitle(source))
        assertEquals("Teilweise", BankCompactUiPolicy.statusLabel(source.reconciliationStatus))
    }

    @Test fun longTextSearchRemainsDeterministicAndEmptyListIsSupported() {
        val long = "Synthetic ".repeat(100) + "special-reference"
        val source = listOf(tx("x", -1.0, BankReconciliationStatus.OPEN, purpose = long))
        assertEquals(1, BankCompactUiPolicy.search(source, "special-reference").size)
        assertTrue(BankCompactUiPolicy.group(emptyList(), LocalDate.of(2026, 9, 10)).isEmpty())
    }
}
