package com.example.data

data class BankAssignmentFavorite(
    val key: String,
    val vendor: String,
    val category: String,
    val subcategory: String,
    val propertyId: String,
    val unitName: String,
    val paymentMethod: String,
    val useCount: Int,
    val lastUsedDate: String,
    val manualFavorite: Boolean
) {
    val label: String
        get() = listOf(category, subcategory, unitName).filter { it.isNotBlank() }.joinToString(" • ")
            .ifBlank { vendor.ifBlank { "Zuordnung" } }
}

/**
 * Presentation-only comfort ranking. It never activates a bank rule or final tax posting.
 */
object BankAssignmentFavoritesPolicy {
    fun key(receipt: Receipt): String = listOf(
        receipt.propertyId.trim(),
        receipt.wohneinheit.trim().lowercase(),
        receipt.aussteller.trim().lowercase(),
        receipt.hauptkategorie.trim().lowercase(),
        receipt.unterkategorie.trim().lowercase(),
        receipt.zahlungsart.trim().lowercase()
    ).joinToString("|")

    fun derive(
        transactions: List<BankTransaction>,
        receipts: List<Receipt>,
        links: List<BankReceiptLink>,
        manualFavoriteKeys: Set<String>
    ): List<BankAssignmentFavorite> {
        val transactionById = transactions.associateBy { it.transactionId }
        val receiptById = receipts.associateBy { it.id }
        val evidence = links.asSequence()
            .filter { it.status == BankLinkStatus.CONFIRMED }
            .mapNotNull { link ->
                val transaction = transactionById[link.transactionId] ?: return@mapNotNull null
                val receipt = receiptById[link.receiptId] ?: return@mapNotNull null
                Triple(key(receipt), transaction, receipt)
            }
            .groupBy { it.first }

        return evidence.map { (key, rows) ->
            val latest = rows.maxByOrNull { it.second.bookingDate }!!
            val receipt = latest.third
            BankAssignmentFavorite(
                key = key,
                vendor = receipt.aussteller,
                category = receipt.hauptkategorie,
                subcategory = receipt.unterkategorie,
                propertyId = receipt.propertyId,
                unitName = receipt.wohneinheit,
                paymentMethod = receipt.zahlungsart,
                useCount = rows.size,
                lastUsedDate = rows.maxOf { it.second.bookingDate },
                manualFavorite = key in manualFavoriteKeys
            )
        }.sortedWith(
            compareByDescending<BankAssignmentFavorite> { it.manualFavorite }
                .thenByDescending { it.useCount }
                .thenByDescending { it.lastUsedDate }
                .thenBy { it.label }
        )
    }
}
