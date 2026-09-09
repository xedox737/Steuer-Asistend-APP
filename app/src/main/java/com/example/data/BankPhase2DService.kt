package com.example.data

import androidx.room.withTransaction
import java.time.Instant

data class BankCombinationApplyResult(
    val success: Boolean,
    val appliedLinkIds: List<String> = emptyList(),
    val message: String,
    val affectedTransactionIds: List<String> = emptyList()
)

class BankPhase2DService(
    private val database: AppDatabase,
    private val now: () -> String = { Instant.now().toString() }
) {
    suspend fun confirmCombination(
        suggestion: BankCombinationSuggestion,
        explicitlyConfirmed: Boolean
    ): BankCombinationApplyResult {
        if (!explicitlyConfirmed) return BankCombinationApplyResult(false, message = "Explizite Nutzerbestätigung fehlt.")
        if (suggestion.conflicts.isNotEmpty()) return BankCombinationApplyResult(false, message = "Konfliktbehaftete Kombination muss zuerst manuell aufgelöst werden.")
        if (suggestion.allocations.isEmpty()) return BankCombinationApplyResult(false, message = "Keine Allocations vorhanden.")
        return applyAllocations(suggestion.allocations)
    }

    suspend fun confirmManualAllocations(
        allocations: List<BankProposedAllocation>,
        explicitlyConfirmed: Boolean
    ): BankCombinationApplyResult {
        if (!explicitlyConfirmed) return BankCombinationApplyResult(false, message = "Explizite Nutzerbestätigung fehlt.")
        return applyAllocations(allocations)
    }

    suspend fun executeSafeBatch(
        selected: List<BankReviewItem>,
        explicitlyConfirmed: Boolean
    ): BankCombinationApplyResult {
        if (!explicitlyConfirmed) return BankCombinationApplyResult(false, message = "Batch-Vorschau wurde nicht final bestätigt.")
        if (selected.isEmpty()) return BankCombinationApplyResult(false, message = "Keine Fälle ausgewählt.")

        // Stable keys are the batch identity. Recomposition/repeated selection must never duplicate work.
        val unique = selected.distinctBy { it.stableKey }
        val invalid = unique.filterNot { BankBatchEligibility.evaluate(it).eligible }
        if (invalid.isNotEmpty()) {
            return BankCombinationApplyResult(false, message = "Batch enthält nicht sichere oder konfliktbehaftete Fälle.")
        }
        if (unique.any { it.transactionIds.size != 1 || it.receiptIds.size != 1 }) {
            return BankCombinationApplyResult(false, message = "Batch enthält keinen eindeutigen 1:1-Fall.")
        }
        val transactionIds = unique.map { it.transactionIds.single() }
        val receiptIds = unique.map { it.receiptIds.single() }
        if (transactionIds.distinct().size != transactionIds.size) {
            return BankCombinationApplyResult(false, message = "Eine Banktransaktion darf im selben Batch nicht mehrfach verwendet werden.")
        }
        if (receiptIds.distinct().size != receiptIds.size) {
            return BankCombinationApplyResult(false, message = "Ein Beleg darf im selben Batch nicht widersprüchlich mehrfach verwendet werden.")
        }

        val allocations = unique.map { item ->
            BankProposedAllocation(
                transactionId = item.transactionIds.single(),
                receiptId = item.receiptIds.single(),
                amount = minOf(item.remainingAmount, item.amount)
            )
        }
        // applyAllocations runs in one Room transaction and re-checks current DB state, property,
        // NO_RECEIPT_REQUIRED, existing user links and over-allocation immediately before write.
        return applyAllocations(allocations)
    }

    suspend fun changeAllocation(
        linkId: String,
        newAmount: Double,
        explicitlyConfirmed: Boolean
    ): BankCombinationApplyResult {
        if (!explicitlyConfirmed) return BankCombinationApplyResult(false, message = "Explizite Nutzerbestätigung fehlt.")
        return database.withTransaction {
            val bankDao = database.bankDao()
            val existing = bankDao.getLink(linkId) ?: return@withTransaction BankCombinationApplyResult(false, message = "Link nicht gefunden.")
            val transaction = bankDao.getTransaction(existing.transactionId) ?: return@withTransaction BankCombinationApplyResult(false, message = "Bankbuchung nicht gefunden.")
            val receipt = database.receiptDao().getReceiptById(existing.receiptId) ?: return@withTransaction BankCombinationApplyResult(false, message = "Beleg nicht gefunden.")
            val linksWithoutCurrent = bankDao.getAllLinks().filterNot { it.linkId == linkId }
            val guard = BankAllocationPolicy.guardAllocation(transaction, receipt, newAmount, linksWithoutCurrent)
            if (!guard.allowed) return@withTransaction BankCombinationApplyResult(false, message = guard.reason)
            bankDao.upsertLink(existing.copy(allocatedAmount = guard.normalizedAmount))
            refreshStatus(bankDao, transaction.transactionId)
            BankCombinationApplyResult(true, listOf(linkId), "Allocation wurde geändert und Restbetrag neu berechnet.", listOf(transaction.transactionId))
        }
    }

    suspend fun unlink(linkId: String): BankCombinationApplyResult = database.withTransaction {
        val bankDao = database.bankDao()
        val existing = bankDao.getLink(linkId) ?: return@withTransaction BankCombinationApplyResult(false, message = "Link nicht gefunden.")
        bankDao.deleteLink(linkId)
        refreshStatus(bankDao, existing.transactionId)
        BankCombinationApplyResult(true, message = "Link entfernt; Status und Restbetrag wurden neu berechnet.", affectedTransactionIds = listOf(existing.transactionId))
    }

    private suspend fun applyAllocations(allocations: List<BankProposedAllocation>): BankCombinationApplyResult = database.withTransaction {
        val bankDao = database.bankDao()
        val allLinks = bankDao.getAllLinks().toMutableList()
        val planned = mutableListOf<BankReceiptLink>()
        val affected = linkedSetOf<String>()

        for (allocation in allocations.sortedWith(compareBy<BankProposedAllocation> { it.transactionId }.thenBy { it.receiptId })) {
            val transaction = bankDao.getTransaction(allocation.transactionId)
                ?: return@withTransaction BankCombinationApplyResult(false, message = "Bankbuchung ${allocation.transactionId} fehlt.")
            if (transaction.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED) {
                return@withTransaction BankCombinationApplyResult(false, message = "NO_RECEIPT_REQUIRED darf nicht durch Batch/Kombination überschrieben werden.")
            }
            val receipt = database.receiptDao().getReceiptById(allocation.receiptId)
                ?: return@withTransaction BankCombinationApplyResult(false, message = "Beleg ${allocation.receiptId} fehlt.")
            if (transaction.propertyId.isNotBlank() && receipt.propertyId.isNotBlank() && transaction.propertyId != receipt.propertyId) {
                return@withTransaction BankCombinationApplyResult(false, message = "Immobilienkonflikt verhindert die Zuordnung.")
            }
            val linkId = BankLinkPolicy.linkId(transaction.transactionId, receipt.id, receipt.internalId)
            val existingSame = allLinks.firstOrNull { it.linkId == linkId }
            if (existingSame != null) {
                if (kotlin.math.abs(existingSame.allocatedAmount - BankAllocationPolicy.roundMoney(allocation.amount)) <= BankAllocationPolicy.MONEY_TOLERANCE) {
                    affected += transaction.transactionId
                    continue
                }
                return@withTransaction BankCombinationApplyResult(false, message = "Bestehender Nutzerlink hat einen anderen Betrag und wird nicht überschrieben.")
            }
            val guard = BankAllocationPolicy.guardAllocation(transaction, receipt, allocation.amount, allLinks + planned)
            if (!guard.allowed) return@withTransaction BankCombinationApplyResult(false, message = guard.reason)
            val link = BankReceiptLink(
                linkId = linkId,
                transactionId = transaction.transactionId,
                receiptId = receipt.id,
                receiptInternalId = receipt.internalId,
                allocatedAmount = guard.normalizedAmount,
                status = BankLinkStatus.CONFIRMED,
                source = BankLinkSource.NUTZER_BESTAETIGT,
                createdAt = now()
            )
            planned += link
            affected += transaction.transactionId
        }

        // No write happens before every allocation has passed all guards. A failure therefore rolls
        // the Room transaction back without a partially applied batch.
        planned.forEach { bankDao.upsertLink(it) }
        affected.forEach { refreshStatus(bankDao, it) }
        BankCombinationApplyResult(
            success = true,
            appliedLinkIds = (planned.map { it.linkId } + allLinks.filter { it.transactionId in affected }.map { it.linkId }).distinct(),
            message = if (planned.isEmpty()) "Bereits bestätigt; keine Doppelverknüpfung erzeugt." else "${planned.size} Zuordnung(en) bestätigt.",
            affectedTransactionIds = affected.toList()
        )
    }

    private suspend fun refreshStatus(bankDao: BankDao, transactionId: String) {
        val transaction = bankDao.getTransaction(transactionId) ?: return
        val links = bankDao.getLinksForTransaction(transactionId)
        val status = BankLinkPolicy.statusFor(transaction, links)
        bankDao.updateTransactionStatus(transactionId, status, "", now())
    }
}
