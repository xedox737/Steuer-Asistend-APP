package com.example.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max

data class BankLastMonthAssignmentSuggestion(
    val transactionId: String,
    val sourceTransactionId: String,
    val sourceBookingDate: String,
    val suggestedPropertyId: String,
    val suggestedUnit: String,
    val suggestedVendor: String,
    val suggestedCategory: String,
    val suggestedSubcategory: String,
    val suggestedPaymentMethod: String,
    val confidence: Int,
    val reasons: List<String>
)

/**
 * Suggests the business assignment from the most recent comparable confirmed transaction.
 *
 * Deliberately does not expose a receiptId or receiptInternalId. The historical receipt is
 * evidence only and must never be re-linked to a new monthly transaction by this policy.
 */
object BankLastMonthAssignmentPolicy {
    private const val MIN_DAYS = 20L
    private const val MAX_DAYS = 45L

    fun suggest(
        target: BankTransaction,
        transactions: List<BankTransaction>,
        links: List<BankReceiptLink>,
        receipts: List<Receipt>
    ): BankLastMonthAssignmentSuggestion? {
        if (BankTransactionClassification.normalize(target.classification) != BankTransactionClassification.NORMAL) return null
        val targetDate = parseDate(target.bookingDate) ?: return null
        val targetCounterparty = BankRecurringPaymentDetector.normalizeCounterparty(target)
        val targetPurpose = BankRecurringPaymentDetector.purposeFingerprint(target.purpose)
        val linksByTransaction = links
            .filter { it.status == BankLinkStatus.CONFIRMED }
            .groupBy { it.transactionId }
        val receiptById = receipts.associateBy { it.id }

        return transactions.asSequence()
            .filter { it.transactionId != target.transactionId }
            .filter { it.accountId == target.accountId }
            .filter { (it.amount >= 0.0) == (target.amount >= 0.0) }
            .mapNotNull { source ->
                val sourceDate = parseDate(source.bookingDate) ?: return@mapNotNull null
                val days = ChronoUnit.DAYS.between(sourceDate, targetDate)
                if (days !in MIN_DAYS..MAX_DAYS) return@mapNotNull null
                if (BankRecurringPaymentDetector.normalizeCounterparty(source) != targetCounterparty) return@mapNotNull null

                val sourcePurpose = BankRecurringPaymentDetector.purposeFingerprint(source.purpose)
                if (targetPurpose.isNotBlank() && sourcePurpose.isNotBlank() && targetPurpose != sourcePurpose) return@mapNotNull null

                val amountTolerance = max(1.0, target.absoluteAmount * 0.10)
                if (abs(source.absoluteAmount - target.absoluteAmount) > amountTolerance) return@mapNotNull null

                // Multiple confirmed receipts can be a collective/combination case. Do not infer
                // a single "last month" assignment from an ambiguous historical transaction.
                val sourceLinks = linksByTransaction[source.transactionId].orEmpty()
                if (sourceLinks.size != 1) return@mapNotNull null
                val receipt = receiptById[sourceLinks.single().receiptId] ?: return@mapNotNull null

                val reasons = mutableListOf<String>()
                reasons += if (target.counterpartyIban.isNotBlank() && source.counterpartyIban.isNotBlank()) {
                    "Gleiche Gegenkonto-IBAN"
                } else {
                    "Gleicher Zahlungspartner"
                }
                if (targetPurpose.isNotBlank()) reasons += "Verwendungszweck-Muster passt"
                reasons += if (abs(source.absoluteAmount - target.absoluteAmount) <= 0.01) {
                    "Betrag stimmt"
                } else {
                    "Betrag liegt im üblichen Bereich"
                }
                reasons += "Letzte bestätigte Zuordnung vom ${source.bookingDate}"
                reasons += "Alter Monatsbeleg wird nicht übernommen"

                var confidence = 65
                if (target.counterpartyIban.isNotBlank() && source.counterpartyIban.isNotBlank()) confidence += 10
                if (targetPurpose.isNotBlank() && targetPurpose == sourcePurpose) confidence += 10
                if (abs(source.absoluteAmount - target.absoluteAmount) <= 0.01) confidence += 10
                if (days in 25L..35L) confidence += 5

                BankLastMonthAssignmentSuggestion(
                    transactionId = target.transactionId,
                    sourceTransactionId = source.transactionId,
                    sourceBookingDate = source.bookingDate,
                    suggestedPropertyId = receipt.propertyId,
                    suggestedUnit = receipt.wohneinheit,
                    suggestedVendor = receipt.aussteller,
                    suggestedCategory = receipt.hauptkategorie,
                    suggestedSubcategory = receipt.unterkategorie,
                    suggestedPaymentMethod = receipt.zahlungsart,
                    confidence = confidence.coerceIn(0, 100),
                    reasons = reasons
                )
            }
            .sortedWith(
                compareByDescending<BankLastMonthAssignmentSuggestion> { it.sourceBookingDate }
                    .thenByDescending { it.confidence }
            )
            .firstOrNull()
    }

    fun hasAssignmentConflict(target: BankTransaction, suggestion: BankLastMonthAssignmentSuggestion): Boolean {
        val propertyConflict = target.propertyId.isNotBlank() &&
            suggestion.suggestedPropertyId.isNotBlank() &&
            target.propertyId != suggestion.suggestedPropertyId
        val unitConflict = target.unitId.isNotBlank() &&
            suggestion.suggestedUnit.isNotBlank() &&
            target.unitId != suggestion.suggestedUnit
        return propertyConflict || unitConflict
    }

    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()
}
