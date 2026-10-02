package com.example.ui

import com.example.data.Receipt
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Locale

/** Read-only projection of the existing receipt source. No accounting/export rules are changed. */
internal enum class LedgerKind(val label: String) { ALL("Alle"), INCOME("Einnahmen"), EXPENSE("Ausgaben") }
internal enum class LedgerPeriod(val label: String) {
    YEAR("Gesamtes Jahr"), CURRENT_MONTH("Aktueller Monat"), LAST_MONTH("Letzter Monat")
}
internal data class LedgerFilters(
    val year: Int,
    val kind: LedgerKind = LedgerKind.ALL,
    // null = all; empty string = unassigned. Property names are never identity keys.
    val propertyId: String? = null,
    val category: String? = null,
    val period: LedgerPeriod = LedgerPeriod.YEAR,
    val query: String = ""
)
internal data class LedgerTotals(val income: Double, val expense: Double, val count: Int) {
    val result: Double get() = income - expense
}
internal object LedgerPresentation {
    // Same classification as MonthlyIncomeExpenseChart and DatevExporter.
    fun isIncome(receipt: Receipt): Boolean = receipt.hauptkategorie == "Miete, Nebenkosten & Kaution" ||
        receipt.hauptkategorie == "Sonstige Einnahmen"

    fun date(receipt: Receipt): LocalDate? = runCatching { LocalDate.parse(receipt.datum) }.getOrNull()
    fun years(receipts: List<Receipt>, today: LocalDate = LocalDate.now()): List<Int> =
        receipts.mapNotNull { date(it)?.year }.distinct().sortedDescending().ifEmpty { listOf(today.year) }
    fun forYear(receipts: List<Receipt>, year: Int): List<Receipt> = receipts.filter { date(it)?.year == year }
    fun totals(receipts: List<Receipt>): LedgerTotals = LedgerTotals(
        receipts.filter(::isIncome).sumOf { it.bruttobetrag },
        receipts.filterNot(::isIncome).sumOf { it.bruttobetrag }, receipts.size
    )
    fun category(receipt: Receipt): String = receipt.unterkategorie.ifBlank {
        receipt.hauptkategorie.ifBlank { "Sonstige" }
    }
    fun filter(receipts: List<Receipt>, filters: LedgerFilters, today: LocalDate = LocalDate.now(),
        knownPropertyIds: Set<String>? = null): List<Receipt> {
        val month = when (filters.period) {
            LedgerPeriod.YEAR -> null
            LedgerPeriod.CURRENT_MONTH -> today.withDayOfMonth(1)
            LedgerPeriod.LAST_MONTH -> today.minusMonths(1).withDayOfMonth(1)
        }
        val query = filters.query.trim()
        return receipts.filter { receipt ->
            val date = date(receipt)
            date != null && date.year == filters.year &&
                (month == null || (date.year == month.year && date.month == month.month)) &&
                (filters.kind == LedgerKind.ALL || isIncome(receipt) == (filters.kind == LedgerKind.INCOME)) &&
                (filters.propertyId == null || if (filters.propertyId.isEmpty()) receipt.propertyId.isBlank() ||
                    (knownPropertyIds != null && receipt.propertyId !in knownPropertyIds)
                    else receipt.propertyId == filters.propertyId) &&
                (filters.category == null || category(receipt) == filters.category) &&
                (query.isEmpty() || listOf(receipt.aussteller, receipt.beschreibung, receipt.unterkategorie,
                    receipt.hauptkategorie, receipt.getEffectiveDisplayId()).any { it.contains(query, ignoreCase = true) })
        }.sortedWith(compareByDescending<Receipt> { it.datum }.thenByDescending { it.id })
    }
    fun money(value: Double): String = NumberFormat.getCurrencyInstance(Locale.GERMANY).format(value)
    fun signedMoney(receipt: Receipt): String {
        val signed = if (isIncome(receipt)) receipt.bruttobetrag else -receipt.bruttobetrag
        return (if (signed >= 0) "+" else "−") + money(kotlin.math.abs(signed))
    }
    fun displayDate(receipt: Receipt): String = date(receipt)?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: receipt.datum
    fun comparison(current: Double, previous: Double, year: Int): String? {
        if (previous <= 0.0) return null
        val percent = (current - previous) / previous * 100
        return String.format(Locale.GERMANY, "%+.0f %% zu %d", percent, year)
    }
}
