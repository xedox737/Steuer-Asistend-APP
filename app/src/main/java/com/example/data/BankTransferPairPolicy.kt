package com.example.data

/**
 * Pure business policy for explicitly confirmed own-account transfer pairs.
 * It never links transactions automatically; callers must pass the exact pair the user confirmed.
 */
object BankTransferPairPolicy {
    data class PairUpdate(
        val first: BankTransactionClassificationRecord,
        val second: BankTransactionClassificationRecord
    )

    data class UnlinkUpdate(
        val selected: BankTransactionClassificationRecord,
        val counterpart: BankTransactionClassificationRecord?
    )

    fun canConfirm(first: BankTransaction, second: BankTransaction): Boolean {
        if (first.transactionId == second.transactionId) return false
        if (first.accountId == second.accountId) return false
        if (BankTransactionClassification.normalize(first.classification) == BankTransactionClassification.PRIVATE_IGNORED) return false
        if (BankTransactionClassification.normalize(second.classification) == BankTransactionClassification.PRIVATE_IGNORED) return false
        return BankTransferMatcher.suggestions(first, listOf(second))
            .any { it.counterTransactionId == second.transactionId }
    }

    fun confirm(first: BankTransaction, second: BankTransaction, now: String): PairUpdate {
        require(canConfirm(first, second)) { "Buchungen bilden kein bestätigbares Umbuchungspaar." }
        return PairUpdate(
            first = BankClassificationPolicy.classify(
                transactionId = first.transactionId,
                classification = BankTransactionClassification.TRANSFER,
                transferCounterAccountId = second.accountId,
                linkedTransferTransactionId = second.transactionId,
                now = now
            ),
            second = BankClassificationPolicy.classify(
                transactionId = second.transactionId,
                classification = BankTransactionClassification.TRANSFER,
                transferCounterAccountId = first.accountId,
                linkedTransferTransactionId = first.transactionId,
                now = now
            )
        )
    }

    /**
     * Removes only the reciprocal link. Both movements remain TRANSFER until the user explicitly
     * removes that classification as a separate action.
     */
    fun unlink(selected: BankTransaction, counterpart: BankTransaction?, now: String): UnlinkUpdate {
        val selectedUpdate = BankClassificationPolicy.classify(
            transactionId = selected.transactionId,
            classification = BankTransactionClassification.TRANSFER,
            now = now
        )
        val counterpartUpdate = counterpart
            ?.takeIf { it.linkedTransferTransactionId == selected.transactionId }
            ?.let {
                BankClassificationPolicy.classify(
                    transactionId = it.transactionId,
                    classification = BankTransactionClassification.TRANSFER,
                    now = now
                )
            }
        return UnlinkUpdate(selectedUpdate, counterpartUpdate)
    }
}
