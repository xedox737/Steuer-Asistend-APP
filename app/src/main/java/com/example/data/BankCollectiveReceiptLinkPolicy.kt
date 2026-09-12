package com.example.data

data class BankCollectiveReceiptCandidate(
    val transactionId: String,
    val allocatedAmount: Double
)

data class BankCollectiveReceiptConflict(
    val transactionId: String,
    val reason: String
)

data class BankCollectiveReceiptPreview(
    val candidates: List<BankCollectiveReceiptCandidate>,
    val conflicts: List<BankCollectiveReceiptConflict>
) {
    val candidateCount: Int get() = candidates.size
    val conflictCount: Int get() = conflicts.size
    val allocatedTotal: Double get() = candidates.sumOf { it.allocatedAmount }
}

/**
 * Simulates linking several bank transactions to one receipt in selection order.
 * Existing and simulated links share the same BankLinkPolicy, so the preview cannot
 * silently over-allocate the receipt or bypass transaction-level safeguards.
 */
object BankCollectiveReceiptLinkPolicy {
    fun preview(
        receipt: Receipt,
        selected: List<BankTransaction>,
        existingLinks: List<BankReceiptLink>
    ): BankCollectiveReceiptPreview {
        val workingLinks = existingLinks.toMutableList()
        val candidates = mutableListOf<BankCollectiveReceiptCandidate>()
        val conflicts = mutableListOf<BankCollectiveReceiptConflict>()

        selected.distinctBy { it.transactionId }.forEach { transaction ->
            val proposal = BankLinkPolicy.propose(transaction, receipt, workingLinks)
            if (!proposal.allowed || proposal.amount <= 0.0) {
                conflicts += BankCollectiveReceiptConflict(
                    transaction.transactionId,
                    proposal.reason.ifBlank { "Zuordnung ist mit dem aktuellen Beleg-/Buchungsstand nicht möglich" }
                )
            } else {
                candidates += BankCollectiveReceiptCandidate(transaction.transactionId, proposal.amount)
                workingLinks += BankReceiptLink(
                    linkId = "preview-${transaction.transactionId}-${receipt.id}",
                    transactionId = transaction.transactionId,
                    receiptId = receipt.id,
                    receiptInternalId = receipt.internalId,
                    allocatedAmount = proposal.amount,
                    status = BankLinkStatus.CONFIRMED,
                    source = BankLinkSource.NUTZER_BESTAETIGT
                )
            }
        }

        return BankCollectiveReceiptPreview(candidates, conflicts)
    }
}
