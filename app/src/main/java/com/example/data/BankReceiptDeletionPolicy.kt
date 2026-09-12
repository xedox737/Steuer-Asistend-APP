package com.example.data

/**
 * Prevents deleting a receipt while bank transactions still reference it.
 * Links must be removed explicitly first so no dangling bank references remain.
 */
object BankReceiptDeletionPolicy {
    data class Decision(
        val allowed: Boolean,
        val linkedTransactionCount: Int,
        val reason: String?
    )

    fun decide(receiptId: Int, links: List<BankReceiptLink>): Decision {
        val count = links.count { it.receiptId == receiptId }
        return if (count == 0) {
            Decision(allowed = true, linkedTransactionCount = 0, reason = null)
        } else {
            Decision(
                allowed = false,
                linkedTransactionCount = count,
                reason = "Beleg ist mit $count Bankbuchung${if (count == 1) "" else "en"} verknüpft. Zuerst die Verknüpfung${if (count == 1) "" else "en"} lösen."
            )
        }
    }
}
