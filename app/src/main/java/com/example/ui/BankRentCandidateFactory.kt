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
