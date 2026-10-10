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
        "receipt-${receipt.id}", receipt.datum, amount,
        LedgerPresentation.isIncome(receipt), receipt.propertyId, receipt.unitId,
        LedgerPresentation.category(receipt), receipt.aussteller, receipt.beschreibung, receipt = receipt
    )

    fun entries(
        receipts: List<Receipt>, assignments: List<BankRentAssignment>,
        links: List<BankReceiptLink>, transactions: List<BankTransaction>,
        view: LedgerView = LedgerView.PAYMENTS,
        loanAssignments: List<BankLoanAssignment> = emptyList()
    ): List<LedgerEntry> {
        val active = receipts.filter { it.deletionStatus in setOf("", "ACTIVE") && it.bruttobetrag.isFinite() }
        if (view == LedgerView.APPROVED) return active.filter { DatevReceiptEligibility.isAccountingApproved(it) && BankLinkedReceiptDatevPolicy.exclusions(it, links, transactions).isEmpty() }.map { entry(it) }
        val txById = transactions.associateBy { it.transactionId }
        val rentalReceipts = active.filter(::isRentalIncomeReceipt).mapNotNull { receipt ->
            val excluded = BankLinkedReceiptDatevPolicy.linksForReceipt(receipt, links).filter {
                it.status == BankLinkStatus.CONFIRMED && it.allocatedAmount.isFinite() && it.allocatedAmount > 0.0 &&
                    txById[it.transactionId]?.let { tx -> !BankClassificationPolicy.decision(tx).eligibleForPropertyPayments } == true
            }.distinctBy { it.linkId }.sumOf { it.allocatedAmount }
            val remaining = (receipt.bruttobetrag - excluded).coerceAtLeast(0.0)
            receipt.copy(bruttobetrag = remaining).takeIf { remaining > 0.0 }
        }
        val sources = RentPaymentProjection.project(rentalReceipts, assignments, links, transactions)
        return nonRentalEntries(active.filterNot(::isRentalIncomeReceipt), assignments, links, transactions, loanAssignments) +
            sources.receiptPayments.map { entry(it.receipt, it.amount) } +
            sources.bankPayments.map { assignment ->
                val tx = txById.getValue(assignment.transactionId)
                LedgerEntry("bank-${assignment.assignmentId}", tx.bookingDate, assignment.allocatedAmount,
                    true, assignment.propertyId, assignment.unitId,
                    if (rentPaymentComponent(assignment.paymentType) == RentPaymentComponent.RENT) "Miete" else "Nebenkosten",
                    tx.counterparty, tx.purpose, transaction = tx)
            }
    }

    /** Existing links and split assignments cover the same cents before any UI filter. */
    private fun nonRentalEntries(
        receipts: List<Receipt>, assignments: List<BankRentAssignment>, links: List<BankReceiptLink>,
        transactions: List<BankTransaction>, loanAssignments: List<BankLoanAssignment>
    ): List<LedgerEntry> {
        val rows = mutableListOf<LedgerEntry>()
        val txById = transactions.associateBy { it.transactionId }
        val confirmedLinks = links.filter { it.status == BankLinkStatus.CONFIRMED && it.allocatedAmount.isFinite() && it.allocatedAmount > 0.0 }
            .distinctBy { it.linkId }
        val confirmedSplits = assignments.filter { it.status == BankRentAssignmentStatus.CONFIRMED &&
            it.source in setOf(BankRentAssignmentSource.USER_CONFIRMED, BankRentAssignmentSource.MANUAL) &&
            it.allocatedAmount.isFinite() && it.allocatedAmount > 0.0 }.distinctBy { it.assignmentId }
        val splitsByTx = confirmedSplits.groupBy { it.transactionId }
        val remainingCoverage = confirmedSplits.associate { it.assignmentId to it.allocatedAmount }.toMutableMap()
        val loanTxIds = loanAssignments.map { it.transactionId }.toSet()
        val remainingTransaction = transactions.associate { tx -> tx.transactionId to
            (tx.absoluteAmount - splitsByTx[tx.transactionId].orEmpty().sumOf { it.allocatedAmount }).coerceAtLeast(0.0) }.toMutableMap()
        fun confirmed(tx: BankTransaction): Boolean = tx.amount.isFinite() && tx.amount != 0.0 &&
            BankClassificationPolicy.decision(tx).eligibleForPropertyPayments &&
            tx.reconciliationStatus in setOf(BankReconciliationStatus.MATCHED, BankReconciliationStatus.PARTIAL, BankReconciliationStatus.NO_RECEIPT_REQUIRED) &&
            splitsByTx[tx.transactionId].orEmpty().sumOf { it.allocatedAmount } <= tx.absoluteAmount + BankAllocationPolicy.MONEY_TOLERANCE &&
            confirmedLinks.filter { it.transactionId == tx.transactionId }.sumOf { it.allocatedAmount } <= tx.absoluteAmount + BankAllocationPolicy.MONEY_TOLERANCE
        fun bankEntry(key: String, tx: BankTransaction, amount: Double, propertyId: String, unitId: String,
            receipt: Receipt? = null) = LedgerEntry(key, tx.bookingDate, amount, tx.isIncome, propertyId, unitId,
            receipt?.let(LedgerPresentation::category) ?: tx.subcategory.ifBlank { tx.category.ifBlank { "Sonstige Ausgabe" } },
            tx.counterparty, tx.purpose, receipt = receipt, transaction = tx)

        confirmedSplits.forEach { split ->
            val tx = txById[split.transactionId] ?: return@forEach
            if (!confirmed(tx) || split.propertyId.isBlank() ||
                !BankSplitPaymentType.directionAllowed(split.paymentType, tx) ||
                split.paymentType !in setOf(BankSplitPaymentType.OTHER_EXPENSE, BankSplitPaymentType.OTHER_INCOME)) return@forEach
            val receipt = receipts.singleOrNull { it.id > 0 && it.id == split.receiptId }
            rows += bankEntry("bank-${split.assignmentId}", tx, split.allocatedAmount, split.propertyId, split.unitId, receipt)
        }
        receipts.forEach { receipt ->
            val receiptLinks = BankLinkedReceiptDatevPolicy.linksForReceipt(receipt, confirmedLinks)
                .filter { it.transactionId in txById }.sortedBy { it.linkId }
            if (receiptLinks.isEmpty()) {
                // A confirmed split may reference the receipt directly, without a BankReceiptLink.
                if (confirmedSplits.none { it.receiptId == receipt.id && it.transactionId in txById }) rows += entry(receipt)
                return@forEach
            }
            // A linked receipt is evidence for allocated payments, not a second full payment.
            // Unlinked receipts retain the existing receipt-based financial view.
            var receiptCapacity = kotlin.math.abs(receipt.bruttobetrag)
            receiptLinks.forEach { link ->
                val tx = txById.getValue(link.transactionId)
                var amount = minOf(link.allocatedAmount, receiptCapacity, tx.absoluteAmount)
                receiptCapacity -= amount
                splitsByTx[tx.transactionId].orEmpty().filter { split ->
                    split.receiptId == receipt.id || (split.receiptId == null &&
                        (split.propertyId.isBlank() || split.propertyId == receipt.propertyId) &&
                        (split.unitId.isBlank() || split.unitId == receipt.unitId))
                }.forEach { split ->
                    val overlap = minOf(amount, remainingCoverage.getValue(split.assignmentId))
                    amount -= overlap
                    remainingCoverage[split.assignmentId] = remainingCoverage.getValue(split.assignmentId) - overlap
                }
                amount = minOf(amount, remainingTransaction.getValue(tx.transactionId))
                if (amount > 0.0 && confirmed(tx)) {
                    rows += bankEntry("bank-link-${link.linkId}", tx, BankAllocationPolicy.roundMoney(amount),
                        receipt.propertyId, receipt.unitId, receipt)
                    remainingTransaction[tx.transactionId] = remainingTransaction.getValue(tx.transactionId) - amount
                }
            }
        }
        transactions.distinctBy { it.transactionId }.forEach { tx ->
            if (tx.isIncome || !confirmed(tx) || tx.propertyId.isBlank() || tx.transactionId in loanTxIds ||
                confirmedLinks.any { it.transactionId == tx.transactionId } || assignments.any { it.transactionId == tx.transactionId } ||
                !(tx.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED ||
                    tx.reconciliationStatus == BankReconciliationStatus.MATCHED && tx.reviewState == BankReviewState.DONE) ||
                tx.category in setOf(BankSplitPaymentType.DEPOSIT, "Kaution", "Tilgung", BankLoanPaymentType.SONDERTILGUNG,
                    BankLoanPaymentType.REGULAERE_RATE, BankLoanPaymentType.SONSTIGE_DARLEHENSZAHLUNG) ||
                tx.subcategory in setOf("Kaution", "Tilgung", BankLoanPaymentType.SONDERTILGUNG)) return@forEach
            rows += bankEntry("bank-${tx.transactionId}", tx, tx.absoluteAmount, tx.propertyId, tx.unitId)
        }
        return rows
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
