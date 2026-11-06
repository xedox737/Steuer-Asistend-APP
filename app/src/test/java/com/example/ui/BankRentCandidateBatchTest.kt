package com.example.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BankRentCandidateBatchTest {
    private lateinit var context: Context
    private val property = PropertyMetadata(propertyId = "batch-property", name = "Synthetic house")
    private val units = (0..1).map { index ->
        WohneinheitStatus("WE $index", "WE $index", "Vermietet", "Müller $index", 690.0, 60.0,
            "2025-01-01", "batch-unit-$index")
    }
    private val unitsByProperty = mapOf(property.propertyId to units)
    private val transactions = (0..9).map { index ->
        BankTransaction(transactionId = "batch-tx-$index", accountId = "batch-account-${index % 3}",
            bookingDate = when (index) { 8 -> "invalid"; else -> "2026-${if (index % 2 == 0) "09" else "10"}-03" },
            amount = if (index == 9) -890.0 else 890.0,
            counterparty = "Müller ${index % 2}",
            purpose = when (index) { 6 -> "Miete September 2026"; 7 -> "Miete Oktober 2026"; else -> "Mietzahlung" },
            propertyId = if (index == 4) "other-property" else property.propertyId,
            unitId = if (index == 5) "" else units[index % 2].unitId)
    }
    private val receipt = Receipt(1, "Müller 0", "2026-09-03", "", 890.0,
        "Miete, Nebenkosten & Kaution", "Warmmiete", "8100", "Miete September",
        propertyId = property.propertyId, unitId = units[0].unitId, internalId = "batch-receipt-id")
    private val assignments = listOf(0, 1, 2, 3).map { index ->
        BankRentAssignment("batch-assignment-$index", transactions[index].transactionId, property.propertyId,
            units[index % 2].unitId, if (index % 2 == 0) "2026-09" else "2026-10", "tenant-${index % 2 + 1}",
            100.0, if (index == 3) RentPaymentType.NEBENKOSTEN else RentPaymentType.MIETE,
            createdAt = "2026-09-03T12:00:00Z", updatedAt = "2026-09-03T12:00:00Z")
    }
    private val links = listOf(BankReceiptLink("batch-link", transactions[0].transactionId, receipt.id,
        receiptInternalId = receipt.internalId, allocatedAmount = 100.0))

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
        saveHistory(690.0)
    }

    @Test fun oneBatchPreservesAllLegacyCandidatesAcrossMonthsScopesHistoryAndSelfAssignments() {
        val batch = batch()
        val before = preferences()
        transactions.forEach { tx ->
            assertEquals("transaction=${tx.transactionId}", legacy(tx), batch.build(tx))
        }
        // Reversed order catches month/unit cache keys and transaction-dependent rhythm.
        transactions.reversed().forEach { tx ->
            assertEquals("revisited transaction=${tx.transactionId}", legacy(tx), batch.build(tx))
        }
        assertEquals(before, preferences())
    }

    @Test fun aNewBatchSeesUpdatedHistoriesAssignmentsReceiptsAndTransactionSources() {
        val oldCandidate = batch().build(transactions[0]).single()
        saveHistory(990.0)
        val changedReceipt = receipt.copy(hauptkategorie = "Instandhaltung")
        val changedTransactions = transactions.map { tx -> tx.copy(accountId = "updated-account", bookingDate = "2026-09-14") }
        val changedAssignments = assignments.map { it.copy(allocatedAmount = 250.0) }
        val updated = BankRentCandidateFactory.Batch(context = context, properties = listOf(property),
            unitsByProperty = unitsByProperty, receipts = listOf(changedReceipt), assignments = changedAssignments,
            allTransactions = changedTransactions, receiptLinks = links)
        val target = changedTransactions[0]
        val expected = LegacyBankRentCandidateFactory.build(context, target, listOf(property), unitsByProperty,
            listOf(changedReceipt), changedAssignments, changedTransactions, links)
        assertEquals(expected, updated.build(target))
        val candidate = updated.build(target).single()
        assertTrue(candidate.expectedAmount > oldCandidate.expectedAmount)
        assertEquals(500.0, candidate.alreadyConfirmedAmount, .001)
        assertEquals("updated-account", candidate.accountId)
        assertEquals(14, candidate.knownDayOfMonth)
        assertFalse(candidate.existingRentalReceiptLink)
    }

    private fun batch() = BankRentCandidateFactory.Batch(context = context, properties = listOf(property),
        unitsByProperty = unitsByProperty, receipts = listOf(receipt), assignments = assignments,
        allTransactions = transactions, receiptLinks = links)

    private fun legacy(tx: BankTransaction) = LegacyBankRentCandidateFactory.build(context, tx, listOf(property),
        unitsByProperty, listOf(receipt), assignments, transactions, links)

    private fun saveHistory(rent: Double) {
        units.forEachIndexed { index, unit ->
            TenantHistoryStore.save(context, property.propertyId, unit.unitId, unit.name,
                listOf(TenantPeriod(index + 1L, unit.name, unit.mieter, "2025-01-01", "", rent, 200.0, 0.0)))
        }
    }

    private fun preferences() = PersistentPreferenceInventory.stores.associate { store ->
        store.name to context.getSharedPreferences(store.name, 0).all.toMap()
    }
}

// Frozen main implementation: independent behavioral oracle for the batch optimization.
private object LegacyBankRentCandidateFactory {
    fun build(
        context: Context,
        transaction: BankTransaction,
        properties: List<PropertyMetadata>,
        unitsByProperty: Map<String, List<WohneinheitStatus>>,
        receipts: List<Receipt>,
        assignments: List<BankRentAssignment>,
        allTransactions: List<BankTransaction>,
        receiptLinks: List<BankReceiptLink>
    ): List<RentCandidateContext> {
        if (!transaction.isIncome) return emptyList()
        val bookingMonth = runCatching { YearMonth.from(LocalDate.parse(transaction.bookingDate)) }.getOrNull()
        val detected = RentMonthParser.detect(transaction.purpose, transaction.bookingDate)
        val month = detected.month ?: bookingMonth ?: return emptyList()
        val receiptsById = receipts.associateBy { it.id }
        val linkedRentalReceipt = receiptLinks.filter { it.transactionId == transaction.transactionId }
            .mapNotNull { receiptsById[it.receiptId] }
            .any(::isRentalIncomeReceipt)
        val txById = allTransactions.associateBy { it.transactionId }

        return properties.asSequence()
            .filter { property -> transaction.propertyId.isBlank() || transaction.propertyId == property.propertyId }
            .flatMap { property ->
                unitsByProperty[property.propertyId].orEmpty().asSequence()
                    .filter { unit ->
                        val stableUnitId = PropertyUnitScopedData.stableUnitId(property.propertyId, unit)
                        transaction.unitId.isBlank() || transaction.unitId == stableUnitId
                    }
                    .flatMap { unit ->
                        val unitId = PropertyUnitScopedData.stableUnitId(property.propertyId, unit)
                        val projection = RentTrackingLogic.month(context, property.propertyId, unit, receipts, month)
                        if (projection.expected <= 0.01) return@flatMap emptySequence()
                        val confirmed = assignments.filter {
                            it.propertyId == property.propertyId && it.unitId == unitId && it.rentMonth == month.toString() &&
                                it.status == BankRentAssignmentStatus.CONFIRMED && it.paymentType == RentPaymentType.MIETE
                        }.sumOf { it.allocatedAmount }
                        val historical = TenantHistoryStore.load(context, property.propertyId, unitId, unit.name)
                            .filter { period -> overlaps(period, month) }
                        val periods = historical.ifEmpty {
                            if (unit.status == "Vermietet") listOf(
                                TenantPeriod(
                                    id = -unitId.hashCode().toLong(),
                                    unitName = unit.name,
                                    tenantName = unit.mieter,
                                    startDate = unit.mietvertragsstart,
                                    endDate = "",
                                    kaltmiete = unit.kaltmiete,
                                    nebenkosten = PropertyUnitScopedData.rentValue(context, property.propertyId, unit, "nk"),
                                    sonstige = PropertyUnitScopedData.rentValue(context, property.propertyId, unit, "other")
                                )
                            ) else emptyList()
                        }
                        val prior = assignments.filter {
                            it.propertyId == property.propertyId && it.unitId == unitId &&
                                it.status == BankRentAssignmentStatus.CONFIRMED && it.transactionId != transaction.transactionId
                        }.mapNotNull { assignment -> txById[assignment.transactionId] }
                        val knownAccount = prior.groupingBy { it.accountId }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
                        val knownDay = prior.mapNotNull { runCatching { LocalDate.parse(it.bookingDate).dayOfMonth }.getOrNull() }
                            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

                        periods.asSequence().map { period ->
                            RentCandidateContext(
                                propertyId = property.propertyId,
                                propertyLabel = property.name.ifBlank { property.adresse },
                                unitId = unitId,
                                unitName = unit.name,
                                tenantReference = "tenant-${period.id}",
                                tenantName = period.tenantName,
                                tenantStart = period.startDate,
                                tenantEnd = period.endDate,
                                rentMonth = month,
                                expectedAmount = projection.expected,
                                alreadyConfirmedAmount = confirmed,
                                accountId = knownAccount,
                                knownDayOfMonth = knownDay,
                                existingRentalReceiptLink = linkedRentalReceipt
                            )
                        }
                    }
            }.toList()
    }

    private fun overlaps(period: TenantPeriod, month: YearMonth): Boolean {
        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()
        val start = runCatching { LocalDate.parse(period.startDate) }.getOrNull() ?: monthStart
        val end = runCatching { LocalDate.parse(period.endDate) }.getOrNull() ?: monthEnd
        return !start.isAfter(monthEnd) && !end.isBefore(monthStart)
    }

    internal fun rhythmDistanceDays(transaction: BankTransaction, candidate: RentCandidateContext): Int? {
        val known = candidate.knownDayOfMonth ?: return null
        val actual = runCatching { LocalDate.parse(transaction.bookingDate).dayOfMonth }.getOrNull() ?: return null
        return abs(actual - known)
    }
}
