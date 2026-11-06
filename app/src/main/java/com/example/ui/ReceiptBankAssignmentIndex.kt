package com.example.ui

import com.example.data.BankReceiptLink
import com.example.data.BankLinkStatus
import com.example.data.Receipt

/** Read-only lookup of persisted bank assignments, shared by the overview's rows. */
internal class ReceiptBankAssignmentIndex(links: List<BankReceiptLink>) {
    private val receiptIds = HashSet<Int>()
    private val internalIds = HashSet<String>()

    init {
        links.forEach { link ->
            if (link.status == BankLinkStatus.CONFIRMED) {
                receiptIds.add(link.receiptId)
                if (link.receiptInternalId.isNotBlank()) internalIds.add(link.receiptInternalId)
            }
        }
    }

    fun isAssigned(receipt: Receipt): Boolean = receipt.id in receiptIds ||
        (receipt.internalId.isNotBlank() && receipt.internalId in internalIds)
}
