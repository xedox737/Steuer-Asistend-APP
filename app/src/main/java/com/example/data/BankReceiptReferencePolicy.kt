package com.example.data

/**
 * Verhindert verwaiste Bank-Beleg-Referenzen.
 * Ein Beleg darf erst gelöscht werden, wenn alle Bankverknüpfungen gelöst sind.
 */
data class BankReceiptDeletionDecision(
    val allowed: Boolean,
    val linkedTransactions: Int,
    val reason: String? = null
)

object BankReceiptReferencePolicy {
    fun deletionDecision(links: List<BankReceiptLink>): BankReceiptDeletionDecision {
        val count = links
            .map { it.transactionId }
            .distinct()
            .size
        return if (count == 0) {
            BankReceiptDeletionDecision(
                allowed = true,
                linkedTransactions = 0
            )
        } else {
            BankReceiptDeletionDecision(
                allowed = false,
                linkedTransactions = count,
                reason = if (count == 1) {
                    "Beleg ist noch mit 1 Bankbuchung verknüpft. Bitte zuerst die Verknüpfung lösen."
                } else {
                    "Beleg ist noch mit $count Bankbuchungen verknüpft. Bitte zuerst die Verknüpfungen lösen."
                }
            )
        }
    }
}
