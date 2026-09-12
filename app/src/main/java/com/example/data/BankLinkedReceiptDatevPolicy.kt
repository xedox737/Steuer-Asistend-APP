package com.example.data

data class BankLinkedReceiptDatevExclusion(
    val transactionId: String,
    val code: String,
    val message: String
)

/**
 * Export-layer guard for receipts that are linked to bank movements with a special classification.
 * This policy is intentionally independent of Compose/UI filters and can be reused by every DATEV path.
 */
object BankLinkedReceiptDatevPolicy {
    fun exclusions(
        receipt: Receipt,
        links: List<BankReceiptLink>,
        transactions: List<BankTransaction>
    ): List<BankLinkedReceiptDatevExclusion> {
        val relevantLinks = links.filter { link ->
            link.receiptId == receipt.id ||
                (receipt.internalId.isNotBlank() && link.receiptInternalId == receipt.internalId)
        }
        if (relevantLinks.isEmpty()) return emptyList()

        val txById = transactions.associateBy { it.transactionId }
        return relevantLinks.mapNotNull { link ->
            val transaction = txById[link.transactionId] ?: return@mapNotNull null
            when (BankTransactionClassification.normalize(transaction.classification)) {
                BankTransactionClassification.PRIVATE_IGNORED -> BankLinkedReceiptDatevExclusion(
                    transactionId = transaction.transactionId,
                    code = "BANK_PRIVATE_IGNORED",
                    message = "Mit privater/ignorierter Bankbuchung verknüpft – kein DATEV-Export."
                )
                BankTransactionClassification.TRANSFER -> BankLinkedReceiptDatevExclusion(
                    transactionId = transaction.transactionId,
                    code = "BANK_TRANSFER",
                    message = "Mit Umbuchung verknüpft – nicht als normale Einnahme/Ausgabe nach DATEV exportieren."
                )
                else -> null
            }
        }.distinctBy { it.transactionId to it.code }
    }

    fun isExportable(
        receipt: Receipt,
        links: List<BankReceiptLink>,
        transactions: List<BankTransaction>
    ): Boolean = exclusions(receipt, links, transactions).isEmpty()
}
