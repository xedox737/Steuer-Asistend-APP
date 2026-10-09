package com.example.ui

import com.example.data.*
import com.example.util.DatevReceiptEligibility
import java.time.LocalDate

internal enum class LedgerView(val label: String) {
    PAYMENTS("Zahlungsfluss"), APPROVED("Freigegebene Buchungen")
}

/** A read-only financial row; it is never a generated or persisted receipt. */
internal data class LedgerEntry(
    val key: String,
    val date: String,
    val amount: Double,
    val income: Boolean,
    val propertyId: String,
    val unitId: String,
    val category: String,
    val partner: String,
    val description: String,
    val receipt: Receipt? = null,
    val transaction: BankTransaction? = null
) {
    val tag get() = receipt?.let { "ledger_receipt_${it.id}" } ?: "ledger_bank_$key"
}

internal object LedgerPaymentPresentation {
    private fun entry(receipt: Receipt, amount: Double = receipt.bruttobetrag) = LedgerEntry(
        "receipt-${receipt.internalId.ifBlank { receipt.id.toString() }}", receipt.datum, amount,
        LedgerPresentation.isIncome(receipt), receipt.propertyId, receipt.unitId,
        LedgerPresentation.category(receipt), receipt.aussteller, receipt.beschreibung, receipt = receipt
    )

    fun entries(
        receipts: List<Receipt>, assignments: List<BankRentAssignment>,
        links: List<BankReceiptLink>, transactions: List<BankTransaction>,
        view: LedgerView = LedgerView.PAYMENTS
    ): List<LedgerEntry> {
        val active = receipts.filter { it.deletionStatus in setOf("", "ACTIVE") && it.bruttobetrag.isFinite() }
        if (view == LedgerView.APPROVED) return active.filter { DatevReceiptEligibility.isAccountingApproved(it) && BankLinkedReceiptDatevPolicy.exclusions(it, links, transactions).isEmpty() }.map { entry(it) }
        val sources = RentPaymentProjection.project(active, assignments, links, transactions)
        val txById = transactions.associateBy { it.transactionId }
        return active.filterNot(::isRentalIncomeReceipt).map { entry(it) } +
            sources.receiptPayments.map { entry(it.receipt, it.amount) } +
            sources.bankPayments.map { assignment ->
                val tx = txById.getValue(assignment.transactionId)
                LedgerEntry("bank-${assignment.assignmentId}", tx.bookingDate, assignment.allocatedAmount,
                    true, assignment.propertyId, assignment.unitId,
                    if (rentPaymentComponent(assignment.paymentType) == RentPaymentComponent.RENT) "Miete" else "Nebenkosten",
                    tx.counterparty, tx.purpose, transaction = tx)
            }
    }

    fun totals(entries: List<LedgerEntry>) = LedgerTotals(
        entries.filter { it.income }.sumOf { it.amount }, entries.filterNot { it.income }.sumOf { it.amount }, entries.size
    )

    fun filter(entries: List<LedgerEntry>, filters: LedgerFilters, today: LocalDate = LocalDate.now(),
        knownPropertyIds: Set<String>? = null): List<LedgerEntry> {
        val month = when (filters.period) {
            LedgerPeriod.YEAR -> null
            LedgerPeriod.CURRENT_MONTH -> today.withDayOfMonth(1)
            LedgerPeriod.LAST_MONTH -> today.minusMonths(1).withDayOfMonth(1)
        }
        return entries.filter { row ->
            val date = CalendarInput.parseIsoDate(row.date)
            date != null && date.year == filters.year &&
                (month == null || date.year == month.year && date.month == month.month) &&
                (filters.kind == LedgerKind.ALL || row.income == (filters.kind == LedgerKind.INCOME)) &&
                (filters.propertyId == null || if (filters.propertyId.isEmpty()) row.propertyId.isBlank() ||
                    (knownPropertyIds != null && row.propertyId !in knownPropertyIds) else row.propertyId == filters.propertyId) &&
                (filters.category == null || row.category == filters.category) &&
                (filters.query.isBlank() || listOf(row.partner, row.description, row.category, row.receipt?.hauptkategorie.orEmpty(),
                    row.receipt?.getEffectiveDisplayId().orEmpty()).any { it.contains(filters.query.trim(), true) })
        }.sortedWith(compareByDescending<LedgerEntry> { it.date }.thenBy { it.key })
    }
}
