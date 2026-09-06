package com.example.ui

import android.content.Context
import com.example.data.Receipt
import com.example.data.StableDocumentIdentity
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Backward-compatible property/unit namespace used by Immobilien-Manager Phase 2.
 * Legacy values are never deleted. For property-1 they are copied lazily to the
 * stable namespace on first read; other properties never fall back to name-only keys.
 */
internal object PropertyUnitScopedData {
    private const val RENT_PREFS = "rent_plan_prefs"
    private const val DRIVE_PREFS = "google_drive_prefs"

    fun selectedPropertyId(context: Context): String =
        context.getSharedPreferences(DRIVE_PREFS, Context.MODE_PRIVATE)
            .getString("selected_property_id", "")
            ?.takeIf { it.isNotBlank() }
            ?: StableDocumentIdentity.LEGACY_PROPERTY_ID

    fun stableUnitId(propertyId: String, unit: WohneinheitStatus): String =
        unit.unitId.ifBlank { StableDocumentIdentity.legacyUnitId(propertyId, unit.name) }

    private fun scopedRentKey(propertyId: String, unitId: String, field: String): String =
        "v2_${propertyId}_${unitId}_$field"

    private fun legacyRentKey(unitName: String, field: String): String = when (field) {
        "nk" -> "nk_$unitName"
        else -> "other_$unitName"
    }

    fun rentValue(context: Context, propertyId: String, unit: WohneinheitStatus, field: String): Double {
        val prefs = context.getSharedPreferences(RENT_PREFS, Context.MODE_PRIVATE)
        val unitId = stableUnitId(propertyId, unit)
        val scoped = scopedRentKey(propertyId, unitId, field)
        if (prefs.contains(scoped)) return prefs.getFloat(scoped, 0f).toDouble()
        if (propertyId == StableDocumentIdentity.LEGACY_PROPERTY_ID) {
            val legacy = legacyRentKey(unit.name, field)
            if (prefs.contains(legacy)) {
                val value = prefs.getFloat(legacy, 0f)
                prefs.edit().putFloat(scoped, value).apply()
                return value.toDouble()
            }
        }
        return 0.0
    }

    fun setRentValues(context: Context, propertyId: String, unit: WohneinheitStatus, nebenkosten: Double, sonstige: Double) {
        val prefs = context.getSharedPreferences(RENT_PREFS, Context.MODE_PRIVATE)
        val unitId = stableUnitId(propertyId, unit)
        prefs.edit()
            .putFloat(scopedRentKey(propertyId, unitId, "nk"), nebenkosten.toFloat())
            .putFloat(scopedRentKey(propertyId, unitId, "other"), sonstige.toFloat())
            .apply()
        // Keep historical property-1 consumers compatible without ever writing
        // name-only values for a second property.
        if (propertyId == StableDocumentIdentity.LEGACY_PROPERTY_ID) {
            prefs.edit()
                .putFloat(legacyRentKey(unit.name, "nk"), nebenkosten.toFloat())
                .putFloat(legacyRentKey(unit.name, "other"), sonstige.toFloat())
                .apply()
        }
    }
}

internal data class RentMonthProjection(
    val unit: WohneinheitStatus,
    val expected: Double,
    val actual: Double,
    val tenantNames: String,
    val tenantChangeInMonth: Boolean
) {
    val missing: Double get() = (expected - actual).coerceAtLeast(0.0)
    val status: RentPaymentStatus get() = when {
        expected <= 0.01 -> RentPaymentStatus.NO_EXPECTATION
        actual + 0.01 >= expected -> RentPaymentStatus.PAID
        actual <= 0.01 -> RentPaymentStatus.MISSING
        else -> RentPaymentStatus.PARTIAL
    }
}

internal enum class RentPaymentStatus { NO_EXPECTATION, PAID, PARTIAL, MISSING }

internal data class RentYearProjection(
    val unit: WohneinheitStatus,
    val months: List<RentMonthProjection>
) {
    val expected: Double get() = months.sumOf { it.expected }
    val actual: Double get() = months.sumOf { it.actual }
    val missing: Double get() = months.sumOf { it.missing }
    val suspiciousMonths: Int get() = months.count { it.status == RentPaymentStatus.MISSING || it.status == RentPaymentStatus.PARTIAL }
}

internal object RentTrackingLogic {
    private fun receiptMonth(receipt: Receipt): YearMonth? =
        runCatching { YearMonth.from(LocalDate.parse(receipt.datum)) }.getOrNull()

    private fun expectedInMonth(period: TenantPeriod, month: YearMonth): Double {
        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()
        val start = runCatching { LocalDate.parse(period.startDate) }.getOrNull() ?: monthStart
        val end = runCatching { LocalDate.parse(period.endDate) }.getOrNull() ?: monthEnd
        val from = if (start.isAfter(monthStart)) start else monthStart
        val to = if (end.isBefore(monthEnd)) end else monthEnd
        if (to.isBefore(from)) return 0.0
        val days = ChronoUnit.DAYS.between(from, to).toDouble() + 1.0
        return period.monatSoll * days / month.lengthOfMonth().toDouble()
    }

    private fun fallbackPeriod(context: Context, propertyId: String, unit: WohneinheitStatus): TenantPeriod? {
        val nk = PropertyUnitScopedData.rentValue(context, propertyId, unit, "nk")
        val other = PropertyUnitScopedData.rentValue(context, propertyId, unit, "other")
        if (unit.status != "Vermietet") return null
        if (unit.mieter.isBlank() && unit.kaltmiete <= 0.0 && nk <= 0.0 && other <= 0.0) return null
        return TenantPeriod(
            id = -PropertyUnitScopedData.stableUnitId(propertyId, unit).hashCode().toLong(),
            unitName = unit.name,
            tenantName = unit.mieter,
            startDate = unit.mietvertragsstart,
            endDate = "",
            kaltmiete = unit.kaltmiete,
            nebenkosten = nk,
            sonstige = other
        )
    }

    fun month(
        context: Context,
        propertyId: String,
        unit: WohneinheitStatus,
        receipts: List<Receipt>,
        month: YearMonth
    ): RentMonthProjection {
        val stored = TenantHistoryStore.load(
            context = context,
            propertyId = propertyId,
            unitId = PropertyUnitScopedData.stableUnitId(propertyId, unit),
            unitName = unit.name
        )
        val periods = if (stored.isNotEmpty()) stored else listOfNotNull(fallbackPeriod(context, propertyId, unit))
        val relevant = periods.filter { expectedInMonth(it, month) > 0.0 }
        val expected = relevant.sumOf { expectedInMonth(it, month) }
        val actual = receipts.filter {
            it.wohneinheit == unit.name && receiptMonth(it) == month && isRentalIncomeReceipt(it)
        }.sumOf { it.bruttobetrag }
        val tenants = relevant.map { it.tenantName.ifBlank { "Mieter nicht hinterlegt" } }
            .distinct().joinToString(" → ").ifBlank {
                if (unit.status == "Vermietet") unit.mieter.ifBlank { "Mieter nicht hinterlegt" } else unit.status
            }
        return RentMonthProjection(unit, expected, actual, tenants, relevant.size > 1)
    }

    fun year(
        context: Context,
        propertyId: String,
        units: List<WohneinheitStatus>,
        receipts: List<Receipt>,
        year: Int
    ): List<RentYearProjection> = units.map { unit ->
        RentYearProjection(
            unit = unit,
            months = (1..12).map { month(context, propertyId, unit, receipts, YearMonth.of(year, it)) }
        )
    }
}
