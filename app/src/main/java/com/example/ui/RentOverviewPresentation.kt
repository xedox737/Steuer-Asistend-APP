package com.example.ui

import android.content.Context
import com.example.data.PropertyMetadata
import com.example.data.Receipt
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
internal data class RentOverviewYear(val year: Int, val rows: List<RentOverviewUnit>, val unassigned: List<Receipt>, val utilities: Double) {
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

    fun years(context: Context, groups: List<RentPropertyUnits>, receipts: List<Receipt>, today: LocalDate = LocalDate.now()): List<Int> {
        val years = mutableSetOf<Int>()
        receipts.filter(::isRentalIncomeReceipt).mapNotNullTo(years) { date(it.datum)?.year }
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

    fun year(context: Context, groups: List<RentPropertyUnits>, receipts: List<Receipt>, year: Int): RentOverviewYear {
        val rental = receipts.filter { date(it.datum)?.year == year && isRentalIncomeReceipt(it) }
        val rows = groups.flatMap { group ->
            val propertyId = group.property.propertyId
            val propertyReceipts = rental.filter { it.propertyId == propertyId }
            RentTrackingLogic.year(context, propertyId, group.units, propertyReceipts, year).map { projection ->
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
        return RentOverviewYear(year, rows, unassigned, utilities)
    }
}

internal data class ValidRentPlan(val kalt: Double, val nk: Double, val other: Double, val start: String)
internal object RentPlanInput {
    fun amount(value: String): Double? {
        if (value.isBlank()) return 0.0
        return GermanNumberInput.parseNonNegative(value)?.takeIf { it.toFloat().isFinite() }
    }
    fun error(kalt: String, nk: String, other: String, start: String, previousEnd: LocalDate? = null): String? = when {
        listOf(kalt, nk, other).any { amount(it) == null } -> "Bitte gültige Beträge ab 0 € eingeben (Komma oder Punkt)."
        start.isNotBlank() && (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(start.trim()) || RentOverviewPresentation.date(start.trim()) == null) ->
            "Bitte einen gültigen Mietbeginn im Format JJJJ-MM-TT eingeben."
        previousEnd != null && (dateAfter(start, previousEnd).not()) ->
            "Der Mietbeginn muss nach dem Ende des bisherigen Mietvertrags liegen."
        else -> null
    }
    private fun dateAfter(start: String, previousEnd: LocalDate): Boolean =
        RentOverviewPresentation.date(start.trim())?.isAfter(previousEnd) == true
}
