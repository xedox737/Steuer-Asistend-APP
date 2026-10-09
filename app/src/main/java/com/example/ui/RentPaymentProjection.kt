package com.example.ui

import com.example.data.*
import java.time.YearMonth

internal data class RentReceiptPayment(val receipt: Receipt, val amount: Double)
internal data class RentPaymentSources(
    val bankPayments: List<BankRentAssignment>,
    val receiptPayments: List<RentReceiptPayment>
)

/** Shared Phase-2 coverage policy. Resolve stable links before applying date or UI filters. */
internal object RentPaymentProjection {
    private fun sameScope(receipt: Receipt, assignment: BankRentAssignment): Boolean =
        (receipt.propertyId.isBlank() || assignment.propertyId == receipt.propertyId) &&
            (receipt.unitId.isBlank() || assignment.unitId == receipt.unitId)

    private fun covers(receipt: Receipt, assignment: BankRentAssignment, linkedTransactions: Set<String>): Boolean =
        sameScope(receipt, assignment) && ((receipt.id > 0 && assignment.receiptId == receipt.id) ||
            (assignment.receiptId == null && assignment.transactionId in linkedTransactions &&
                (rentPaymentComponent(receipt) == RentPaymentComponent.COMBINED ||
                    rentPaymentComponent(assignment.paymentType) == rentPaymentComponent(receipt))))

    /** Review remainder: a stable receipt link and its rent assignment cover the same euros once. */
    fun remainingAmount(transaction: BankTransaction, receipts: List<Receipt>, links: List<BankReceiptLink>,
        assignments: List<BankRentAssignment>): Double {
        val confirmed = assignments.filter { it.transactionId == transaction.transactionId &&
            it.status == BankRentAssignmentStatus.CONFIRMED && it.allocatedAmount.isFinite() && it.allocatedAmount > 0.0 }
            .distinctBy { it.assignmentId }
        val coverage = confirmed.associate { it.assignmentId to it.allocatedAmount }.toMutableMap()
        var allocated = confirmed.sumOf { it.allocatedAmount }
        links.filter { it.transactionId == transaction.transactionId && it.status == BankLinkStatus.CONFIRMED &&
            it.allocatedAmount.isFinite() && it.allocatedAmount > 0.0 }.distinctBy { it.linkId }.sortedBy { it.linkId }.forEach { link ->
            val receipt = receipts.firstOrNull {
                if (link.receiptInternalId.isNotBlank()) it.internalId == link.receiptInternalId else it.id == link.receiptId
            } ?: return@forEach
            var residual = link.allocatedAmount
            confirmed.filter { covers(receipt, it, setOf(transaction.transactionId)) }.forEach { assignment ->
                val overlap = minOf(residual, coverage.getValue(assignment.assignmentId))
                residual -= overlap
                coverage[assignment.assignmentId] = coverage.getValue(assignment.assignmentId) - overlap
            }
            allocated += residual
        }
        return BankAllocationPolicy.roundMoney((transaction.absoluteAmount - allocated).coerceAtLeast(0.0))
    }

    fun project(
        receipts: List<Receipt>,
        bankAssignments: List<BankRentAssignment>,
        bankLinks: List<BankReceiptLink>,
        bankTransactions: List<BankTransaction>,
        assignmentScope: (BankRentAssignment) -> Boolean = { true },
        receiptScope: (Receipt) -> Boolean = { true }
    ): RentPaymentSources {
        val transactions = bankTransactions.filter {
            it.amount.isFinite() && it.isIncome && it.classification == BankTransactionClassification.NORMAL &&
                it.reconciliationStatus in setOf(BankReconciliationStatus.MATCHED, BankReconciliationStatus.PARTIAL)
        }.associateBy { it.transactionId }
        val confirmed = bankAssignments.filter {
            it.status == BankRentAssignmentStatus.CONFIRMED && it.allocatedAmount.isFinite() && it.allocatedAmount > 0.0 &&
                it.source in setOf(com.example.data.BankRentAssignmentSource.USER_CONFIRMED, com.example.data.BankRentAssignmentSource.MANUAL)
        }.distinctBy { it.assignmentId }
        val allocatedByTransaction = confirmed.groupBy { it.transactionId }.mapValues { (_, assignments) -> assignments.sumOf { it.allocatedAmount } }
        val validTransactions = transactions.filter { (id, transaction) ->
            // Conflicting allocations must not turn more money than the bank received into rent.
            (allocatedByTransaction[id] ?: 0.0) <= transaction.amount + 0.01
        }
        val paymentTypes = setOf(BankSplitPaymentType.RENT, BankSplitPaymentType.UTILITIES_PREPAYMENT,
            BankSplitPaymentType.UTILITIES_SETTLEMENT, RentPaymentType.NEBENKOSTEN)
        val assignments = confirmed.filter {
            it.propertyId.isNotBlank() && it.unitId.isNotBlank() && it.transactionId.isNotBlank() &&
                assignmentScope(it) && it.paymentType in paymentTypes &&
                it.transactionId in validTransactions && runCatching { YearMonth.parse(it.rentMonth) }.isSuccess
        }
        val remainingCoverage = assignments.associate { it.assignmentId to it.allocatedAmount }.toMutableMap()
        val receiptCapacity = validTransactions.mapValues { (id, transaction) ->
            (transaction.amount - (allocatedByTransaction[id] ?: 0.0)).coerceAtLeast(0.0)
        }.toMutableMap()
        val confirmedLinks = bankLinks.filter { it.status == BankLinkStatus.CONFIRMED }
        val linksByInternalId = confirmedLinks.filter { it.receiptInternalId.isNotBlank() }.groupBy { it.receiptInternalId }
        val linksByLocalId = confirmedLinks.filter { it.receiptInternalId.isBlank() }.groupBy { it.receiptId }
        val scopedReceipts = receipts.filter { receipt ->
            receiptScope(receipt) && isConfirmedRentalIncomeReceipt(receipt)
        }.sortedBy { it.internalId.ifBlank { it.id.toString() } }
        val receiptPayments = mutableListOf<RentReceiptPayment>()
        // Resolve linked source overlap before month/year filtering. Confirmed bank
        // allocations keep their explicit rentMonth; only uncovered receipts keep datum.
        for (receipt in scopedReceipts) {
            val linkedTransactions = (linksByInternalId[receipt.internalId].orEmpty() + linksByLocalId[receipt.id].orEmpty())
                .map { it.transactionId }.toSet()
            val scopedAssignments = assignments.filter { sameScope(receipt, it) }
            val direct = scopedAssignments.filter { receipt.id > 0 && it.receiptId == receipt.id }
            val mirrored = linkedTransactions.intersect(scopedAssignments.map { it.transactionId }.toSet()) + direct.map { it.transactionId }
            var residual = receipt.bruttobetrag
            if (residual > 0.0 && mirrored.isNotEmpty()) {
                val matching = assignments.filter { covers(receipt, it, linkedTransactions) }
                for (assignment in matching) {
                    val remaining = remainingCoverage.getValue(assignment.assignmentId)
                    val covered = minOf(residual, remaining)
                    residual -= covered
                    remainingCoverage[assignment.assignmentId] = remaining - covered
                }
                var uncovered = 0.0
                for (transactionId in (linkedTransactions + mirrored).sorted()) {
                    val available = receiptCapacity[transactionId] ?: continue
                    val amount = minOf(residual, available)
                    uncovered += amount
                    residual -= amount
                    receiptCapacity[transactionId] = available - amount
                }
                residual = uncovered
            }
            if (residual != 0.0) receiptPayments += RentReceiptPayment(receipt, residual)
        }
        return RentPaymentSources(assignments, receiptPayments)
    }
}
