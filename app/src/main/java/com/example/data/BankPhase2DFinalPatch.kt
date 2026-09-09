package com.example.data

/**
 * Final Phase-2D patch helpers. These types do not persist new state and deliberately
 * reuse the existing BankReviewItem, BankBatchEligibility and BankAllocationPolicy SSOT.
 */
data class BankBatchCasePreview(
    val stableKey: String,
    val transactionId: String,
    val bookingDate: String,
    val counterpartyOrPurpose: String,
    val transactionAmount: Double,
    val transactionRemaining: Double,
    val receiptId: Int,
    val receiptIssuer: String,
    val receiptAmount: Double,
    val receiptRemaining: Double,
    val allocationAmount: Double,
    val score: Int,
    val confidence: String
)

data class BankBatchExcludedCasePreview(
    val stableKey: String,
    val transactionIds: List<String>,
    val receiptIds: List<Int>,
    val reason: String,
    val conflicts: List<String>,
    val score: Int,
    val confidence: String
)

data class BankDetailedBatchPreview(
    val eligibleKeys: List<String>,
    val cases: List<BankBatchCasePreview>,
    val excludedCases: List<BankBatchExcludedCasePreview>,
    val totalAmount: Double,
    val caseCount: Int,
    val transactionCount: Int,
    val receiptCount: Int
)

object BankBatchPreviewBuilder {
    fun build(
        items: List<BankReviewItem>,
        transactions: List<BankTransaction>,
        receipts: List<Receipt>,
        links: List<BankReceiptLink>
    ): BankDetailedBatchPreview {
        val txById = transactions.associateBy { it.transactionId }
        val receiptById = receipts.associateBy { it.id }
        val uniqueItems = items.distinctBy { it.stableKey }
        val accepted = mutableListOf<BankBatchCasePreview>()
        val excluded = mutableListOf<BankBatchExcludedCasePreview>()
        val usedTransactions = mutableSetOf<String>()
        val usedReceipts = mutableSetOf<Int>()

        fun exclude(item: BankReviewItem, reasons: List<String>) {
            excluded += BankBatchExcludedCasePreview(
                stableKey = item.stableKey,
                transactionIds = item.transactionIds.sorted(),
                receiptIds = item.receiptIds.sorted(),
                reason = reasons.filter { it.isNotBlank() }.distinct().joinToString(" • ").ifBlank { "Nicht batchfähig." },
                conflicts = item.conflicts,
                score = item.score,
                confidence = item.confidence
            )
        }

        uniqueItems.sortedBy { it.stableKey }.forEach { item ->
            val reasons = BankBatchEligibility.evaluate(item).reasons.toMutableList()
            val transactionId = item.transactionIds.singleOrNull()
            val receiptId = item.receiptIds.singleOrNull()
            if (transactionId == null || receiptId == null) reasons += "Keine eindeutige 1:1-Zuordnung."
            val transaction = transactionId?.let(txById::get)
            val receipt = receiptId?.let(receiptById::get)
            if (transactionId != null && transaction == null) reasons += "Banktransaktion ist nicht mehr verfügbar."
            if (receiptId != null && receipt == null) reasons += "Beleg ist nicht mehr verfügbar."
            if (transactionId != null && transactionId in usedTransactions) reasons += "Transaktion wird im Batch bereits verwendet."
            if (receiptId != null && receiptId in usedReceipts) reasons += "Beleg wird im Batch bereits verwendet."
            if (transaction?.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED) reasons += "NO_RECEIPT_REQUIRED ist geschützt."

            if (transaction != null && receipt != null) {
                if (transaction.propertyId.isNotBlank() && receipt.propertyId.isNotBlank() && transaction.propertyId != receipt.propertyId) {
                    reasons += "Property-Konflikt."
                }
                val txRemaining = BankAllocationPolicy.transactionRemaining(transaction, links).remainingAmount
                val receiptRemaining = BankAllocationPolicy.receiptRemaining(receipt, links).remainingAmount
                val allocation = BankAllocationPolicy.roundMoney(minOf(txRemaining, receiptRemaining, item.remainingAmount))
                if (allocation <= BankAllocationPolicy.MONEY_TOLERANCE) reasons += "Kein Restbetrag."
                val guard = BankAllocationPolicy.guardAllocation(transaction, receipt, allocation, links)
                if (!guard.allowed) reasons += guard.reason

                if (reasons.isEmpty()) {
                    accepted += BankBatchCasePreview(
                        stableKey = item.stableKey,
                        transactionId = transaction.transactionId,
                        bookingDate = transaction.bookingDate,
                        counterpartyOrPurpose = transaction.counterparty.ifBlank { transaction.purpose },
                        transactionAmount = BankAllocationPolicy.roundMoney(transaction.amount),
                        transactionRemaining = txRemaining,
                        receiptId = receipt.id,
                        receiptIssuer = receipt.aussteller,
                        receiptAmount = BankAllocationPolicy.roundMoney(receipt.bruttobetrag),
                        receiptRemaining = receiptRemaining,
                        allocationAmount = guard.normalizedAmount,
                        score = item.score,
                        confidence = item.confidence
                    )
                    usedTransactions += transaction.transactionId
                    usedReceipts += receipt.id
                    return@forEach
                }
            }
            exclude(item, reasons)
        }

        return BankDetailedBatchPreview(
            eligibleKeys = accepted.map { it.stableKey },
            cases = accepted,
            excludedCases = excluded,
            totalAmount = BankAllocationPolicy.roundMoney(accepted.sumOf { it.allocationAmount }),
            caseCount = accepted.size,
            transactionCount = accepted.map { it.transactionId }.distinct().size,
            receiptCount = accepted.map { it.receiptId }.distinct().size
        )
    }
}

object BankPhase2DReviewAction {
    const val CONFIRM_SAFE = "CONFIRM_SAFE"
    const val OPEN_RECEIPT_PICKER = "OPEN_RECEIPT_PICKER"
    const val CREATE_RECEIPT = "CREATE_RECEIPT"
    const val NO_RECEIPT_REQUIRED = "NO_RECEIPT_REQUIRED"
    const val CONFIRM_COMBINATION = "CONFIRM_COMBINATION"
    const val EDIT_ALLOCATION = "EDIT_ALLOCATION"
    const val UNLINK = "UNLINK"
    const val OPEN_RENT_WORKFLOW = "OPEN_RENT_WORKFLOW"
    const val OPEN_LOAN_WORKFLOW = "OPEN_LOAN_WORKFLOW"
    const val OPEN_RECURRING_WORKFLOW = "OPEN_RECURRING_WORKFLOW"
    const val MARK_MANUAL_REVIEW = "MARK_MANUAL_REVIEW"
    const val DETAILS_ONLY = "DETAILS_ONLY"
}

/** Small testable routing policy; it points to existing workflows rather than duplicating them. */
object BankPhase2DReviewActionPolicy {
    fun actionsFor(type: String, hasExistingLinks: Boolean): List<String> {
        val base = when (type) {
            BankReviewType.SAFE_SUGGESTION -> listOf(BankPhase2DReviewAction.CONFIRM_SAFE, BankPhase2DReviewAction.OPEN_RECEIPT_PICKER, BankPhase2DReviewAction.MARK_MANUAL_REVIEW)
            BankReviewType.MISSING_RECEIPT -> listOf(BankPhase2DReviewAction.CREATE_RECEIPT, BankPhase2DReviewAction.OPEN_RECEIPT_PICKER, BankPhase2DReviewAction.NO_RECEIPT_REQUIRED, BankPhase2DReviewAction.MARK_MANUAL_REVIEW)
            BankReviewType.MULTIPLE_CANDIDATES -> listOf(BankPhase2DReviewAction.OPEN_RECEIPT_PICKER, BankPhase2DReviewAction.MARK_MANUAL_REVIEW)
            BankReviewType.COMBINATION_SUGGESTION -> listOf(BankPhase2DReviewAction.CONFIRM_COMBINATION, BankPhase2DReviewAction.OPEN_RECEIPT_PICKER, BankPhase2DReviewAction.MARK_MANUAL_REVIEW)
            BankReviewType.PARTIAL_PAYMENT -> listOf(BankPhase2DReviewAction.OPEN_RECEIPT_PICKER, BankPhase2DReviewAction.EDIT_ALLOCATION, BankPhase2DReviewAction.MARK_MANUAL_REVIEW)
            BankReviewType.RENT_REVIEW -> listOf(BankPhase2DReviewAction.OPEN_RENT_WORKFLOW, BankPhase2DReviewAction.MARK_MANUAL_REVIEW)
            BankReviewType.LOAN_REVIEW -> listOf(BankPhase2DReviewAction.OPEN_LOAN_WORKFLOW, BankPhase2DReviewAction.MARK_MANUAL_REVIEW)
            BankReviewType.RECURRING_REVIEW -> listOf(BankPhase2DReviewAction.OPEN_RECURRING_WORKFLOW)
            BankReviewType.POSSIBLE_DUPLICATE -> listOf(BankPhase2DReviewAction.MARK_MANUAL_REVIEW, BankPhase2DReviewAction.DETAILS_ONLY)
            BankReviewType.AMOUNT_CONFLICT -> listOf(BankPhase2DReviewAction.OPEN_RECEIPT_PICKER, BankPhase2DReviewAction.MARK_MANUAL_REVIEW)
            BankReviewType.PROPERTY_CONFLICT -> listOf(BankPhase2DReviewAction.OPEN_RECEIPT_PICKER, BankPhase2DReviewAction.MARK_MANUAL_REVIEW)
            BankReviewType.MANUAL_REVIEW -> listOf(BankPhase2DReviewAction.OPEN_RECEIPT_PICKER, BankPhase2DReviewAction.MARK_MANUAL_REVIEW)
            else -> listOf(BankPhase2DReviewAction.DETAILS_ONLY)
        }.toMutableList()
        if (hasExistingLinks && type in setOf(BankReviewType.PARTIAL_PAYMENT, BankReviewType.COMBINATION_SUGGESTION, BankReviewType.MANUAL_REVIEW)) {
            base += BankPhase2DReviewAction.UNLINK
        }
        return base.distinct()
    }
}
