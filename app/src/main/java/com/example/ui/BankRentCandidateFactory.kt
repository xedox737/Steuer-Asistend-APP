package com.example.ui

import android.content.Context
import com.example.data.BankReceiptLink
import com.example.data.BankRentAssignment
import com.example.data.BankRentAssignmentStatus
import com.example.data.BankTransaction
import com.example.data.PropertyMetadata
import com.example.data.Receipt
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/**
 * Adapts the existing rental truth (RentTrackingLogic + TenantHistoryStore) to the
 * bank-rent matcher. It does not calculate a second expected rent.
 */
internal object BankRentCandidateFactory {
    fun build(
        context: Context,
        transaction: BankTransaction,
        properties: List<PropertyMetadata>,
        unitsByProperty: Map<String, List<WohneinheitStatus>>,
        receipts: List<Receipt>,
        assignments: List<BankRentAssignment>,
        allTransactions: List<BankTransaction>,
        receiptLinks: List<BankReceiptLink>
    ): List<RentCandidateContext> = Batch(
        context, properties, unitsByProperty, receipts, assignments, allTransactions, receiptLinks
    ).build(transaction)

    /** Read-only indexes/caches for one source emission. Discard the batch whenever any source changes. */
    class Batch(
        private val context: Context,
        private val properties: List<PropertyMetadata>,
        private val unitsByProperty: Map<String, List<WohneinheitStatus>>,
        private val receipts: List<Receipt>,
        assignments: List<BankRentAssignment>,
        allTransactions: List<BankTransaction>,
        receiptLinks: List<BankReceiptLink>
    ) {
        private val receiptsById = receipts.associateBy { it.id }
        private val txById = allTransactions.associateBy { it.transactionId }
        private val linksByTransaction = receiptLinks.groupBy { it.transactionId }
        private val confirmedByUnit = assignments.filter { it.status == BankRentAssignmentStatus.CONFIRMED }
            .groupBy { it.propertyId to it.unitId }
        private val confirmedByMonth = assignments.asSequence()
            .filter { it.status == BankRentAssignmentStatus.CONFIRMED && it.paymentType == RentPaymentType.MIETE }
            .groupBy { Triple(it.propertyId, it.unitId, it.rentMonth) }
            .mapValues { (_, values) -> values.sumOf { it.allocatedAmount } }
        private val expectedByMonth = mutableMapOf<Triple<String, String, YearMonth>, Double>()
        private val periodsByMonth = mutableMapOf<Triple<String, String, YearMonth>, List<TenantPeriod>>()

        fun build(transaction: BankTransaction): List<RentCandidateContext> {
            if (!transaction.isIncome || properties.isEmpty()) return emptyList()
            val bookingMonth = runCatching { YearMonth.from(LocalDate.parse(transaction.bookingDate)) }.getOrNull()
            val detected = RentMonthParser.detect(transaction.purpose, transaction.bookingDate)
            val month = detected.month ?: bookingMonth ?: return emptyList()
            val linkedRentalReceipt = linksByTransaction[transaction.transactionId].orEmpty()
                .mapNotNull { receiptsById[it.receiptId] }
                .any(::isRentalIncomeReceipt)

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
                            val scope = property.propertyId to unitId
                            val monthKey = Triple(property.propertyId, unitId, month)
                            val expected = expectedByMonth.getOrPut(monthKey) {
                                RentTrackingLogic.month(context, property.propertyId, unit, receipts, month).expected
                            }
                            if (expected <= 0.01) return@flatMap emptySequence()
                            val confirmed = confirmedByMonth[Triple(property.propertyId, unitId, month.toString())] ?: 0.0
                            val periods = periodsByMonth.getOrPut(monthKey) {
                                val historical = TenantHistoryStore.load(context, property.propertyId, unitId, unit.name)
                                    .filter { period -> overlaps(period, month) }
                                historical.ifEmpty {
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
                            }
                            val prior = confirmedByUnit[scope].orEmpty()
                                .filter { it.transactionId != transaction.transactionId }
                                .mapNotNull { assignment -> txById[assignment.transactionId] }
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
                                    expectedAmount = expected,
                                    alreadyConfirmedAmount = confirmed,
                                    accountId = knownAccount,
                                    knownDayOfMonth = knownDay,
                                    existingRentalReceiptLink = linkedRentalReceipt
                                )
                            }
                        }
                }.toList()
        }
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
