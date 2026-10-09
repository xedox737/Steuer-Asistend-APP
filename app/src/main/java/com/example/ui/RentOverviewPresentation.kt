package com.example.ui

import android.content.Context
import com.example.data.BankReceiptLink
import com.example.data.BankRentAssignment
import com.example.data.BankSplitPaymentType
import com.example.data.BankTransaction
import com.example.data.PropertyMetadata
import com.example.data.Receipt
import com.example.data.BankRentSuggestion
import java.time.LocalDate

/** Read-only view of the existing scoped rent plans and tenant-history projection. */
internal data class RentPropertyUnits(val property: PropertyMetadata, val units: List<WohneinheitStatus>)
internal data class RentOverviewUnit(
    val property: PropertyMetadata,
    val projection: RentYearProjection,
    val nebenkosten: Double,
    val sonstige: Double
) {
    val unit get() = projection.unit
    val key get() = property.propertyId + ":" + PropertyUnitScopedData.stableUnitId(property.propertyId, unit)
    val tenants get() = projection.months.filter { it.expected > .01 }.flatMap { it.tenantNames.split(" → ") }.distinct()
        .joinToString(" → ").ifBlank { if (unit.status == "Vermietet") unit.mieter.ifBlank { "Mieter nicht hinterlegt" } else unit.status }
    val expected get() = projection.expected
    val actual get() = projection.actual
    // A surplus in one unit must never hide another unit's arrears.
    val missing get() = (expected - actual).coerceAtLeast(0.0)
    val monthly get() = if (unit.status == "Vermietet") unit.kaltmiete + nebenkosten + sonstige else 0.0
}
internal data class RentOverviewYear(val year: Int, val rows: List<RentOverviewUnit>, val unassigned: List<Receipt>, val utilities: Double, val reviews: List<RentReviewPayment> = emptyList()) {
    val actual get() = rows.sumOf { it.actual }
    val expected get() = rows.sumOf { it.expected }
    val missing get() = rows.sumOf { it.missing }
    val months get() = (0..11).map { index ->
        rows.sumOf { it.projection.months[index].expected } to rows.sumOf { it.projection.months[index].actual }
    }
}
internal object RentOverviewPresentation {
    fun date(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()
    fun scopedReceipts(groups: List<RentPropertyUnits>, receipts: List<Receipt>, propertyScoped: Boolean): List<Receipt> =
        if (!propertyScoped) receipts else receipts.filter { receipt -> groups.any { it.property.propertyId == receipt.propertyId } }

    fun years(
        context: Context,
        groups: List<RentPropertyUnits>,
        receipts: List<Receipt>,
        today: LocalDate = LocalDate.now(),
        bankAssignments: List<BankRentAssignment> = emptyList()
    ): List<Int> {
        val years = mutableSetOf<Int>()
        receipts.filter(::isRentalIncomeReceipt).mapNotNullTo(years) { date(it.datum)?.year }
        val propertyIds = groups.map { it.property.propertyId }.toSet()
        bankAssignments.asSequence()
            .filter { it.status == com.example.data.BankRentAssignmentStatus.CONFIRMED }
            .filter { it.propertyId in propertyIds }
            .filter { it.paymentType != BankSplitPaymentType.DEPOSIT && it.paymentType != RentPaymentType.KAUTION }
            .mapNotNull { assignment ->
                runCatching { java.time.YearMonth.parse(assignment.rentMonth).year }.getOrNull()
            }
            .forEach(years::add)
        groups.forEach { group -> group.units.forEach { unit ->
            date(unit.mietvertragsstart)?.year?.let(years::add)
            TenantHistoryStore.load(context, group.property.propertyId,
                PropertyUnitScopedData.stableUnitId(group.property.propertyId, unit), unit.name).forEach { period ->
                date(period.startDate)?.year?.let(years::add)
                date(period.endDate)?.year?.let(years::add)
            }
        } }
        if (years.isEmpty()) return listOf(today.year)
        // The current rent year remains available for ongoing contracts, even before the first receipt.
        return (years + today.year).sortedDescending()
    }

    fun year(
        context: Context,
        groups: List<RentPropertyUnits>,
        receipts: List<Receipt>,
        year: Int,
        bankAssignments: List<BankRentAssignment> = emptyList(),
        bankLinks: List<BankReceiptLink> = emptyList(),
        bankTransactions: List<BankTransaction> = emptyList(),
        bankSuggestions: Map<String, List<BankRentSuggestion>> = emptyMap()
    ): RentOverviewYear {
        val rental = receipts.filter { date(it.datum)?.year == year && isConfirmedRentalIncomeReceipt(it) }
        val rows = groups.flatMap { group ->
            val propertyId = group.property.propertyId
            val propertyReceipts = receipts.filter { it.propertyId == propertyId }
            RentTrackingLogic.year(
                context, propertyId, group.units, propertyReceipts, year,
                bankAssignments, bankLinks, bankTransactions
            ).map { projection ->
                RentOverviewUnit(group.property, projection,
                    PropertyUnitScopedData.rentValue(context, propertyId, projection.unit, "nk"),
                    PropertyUnitScopedData.rentValue(context, propertyId, projection.unit, "other"))
            }
        }
        val unassigned = rental.filter { receipt ->
            groups.none { group -> group.property.propertyId == receipt.propertyId &&
                group.units.any { it.name == receipt.wohneinheit && receipt.wohneinheit.isNotBlank() } }
        }
        // Only explicit receipt income categories. Warm/flat rent cannot reliably be split into utilities.
        val utilities = rental.filter { receipt -> receipt !in unassigned &&
            (receipt.unterkategorie.contains("Nebenkosten", true) || receipt.unterkategorie.contains("Betriebskosten", true))
        }.sumOf { it.bruttobetrag }
        val reviews = RentPaymentReview.build(groups, receipts, bankTransactions, bankAssignments, bankLinks, bankSuggestions)
            .filter { date(it.date)?.year == year }
        return RentOverviewYear(year, rows, unassigned, utilities, reviews)
    }
}

internal enum class RentPlanEditMode { CORRECT_EXISTING, CHANGE_FROM_DATE }
internal data class ValidRentPlan(
    val kalt: Double,
    val nk: Double,
    val other: Double,
    val date: String,
    val mode: RentPlanEditMode
)
internal object RentPlanInput {
    fun amount(value: String): Double? {
        if (value.isBlank()) return 0.0
        return GermanNumberInput.parseNonNegative(value)?.takeIf { it.toFloat().isFinite() }
    }
    fun error(
        kalt: String,
        nk: String,
        other: String,
        date: String,
        previousEnd: LocalDate? = null,
        mode: RentPlanEditMode = RentPlanEditMode.CORRECT_EXISTING
    ): String? = when {
        listOf(kalt, nk, other).any { amount(it) == null } ->
            "Bitte gültige Beträge ab 0 € eingeben (Komma oder Punkt)."
        mode == RentPlanEditMode.CHANGE_FROM_DATE && CalendarInput.parseIsoDate(date) == null ->
            "Bitte ein gültiges Datum für die Mietänderung im Format JJJJ-MM-TT eingeben."
        mode == RentPlanEditMode.CHANGE_FROM_DATE && CalendarInput.parseIsoDate(date)?.dayOfMonth != 1 ->
            "Mietänderungen sind derzeit nur zum Monatsersten möglich."
        mode == RentPlanEditMode.CORRECT_EXISTING && date.isNotBlank() && CalendarInput.parseIsoDate(date) == null ->
            "Bitte einen gültigen Mietbeginn im Format JJJJ-MM-TT eingeben."
        mode == RentPlanEditMode.CORRECT_EXISTING && previousEnd != null && !dateAfter(date, previousEnd) ->
            "Der Mietbeginn muss nach dem Ende des bisherigen Mietvertrags liegen."
        else -> null
    }

    fun needsLargeChangeConfirmation(previous: Double, updated: Double): Boolean {
        if (previous <= 0.0 || updated <= 0.0) return false
        val ratio = updated / previous
        return ratio >= 3.0 || ratio <= (1.0 / 3.0)
    }

    private fun dateAfter(start: String, previousEnd: LocalDate): Boolean =
        CalendarInput.parseIsoDate(start)?.isAfter(previousEnd) == true
}
