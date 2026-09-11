package com.example.data

import java.security.MessageDigest
import kotlin.math.max

/** Builds an existing BankLearningRule from one explicitly confirmed bank/receipt assignment.
 * The rule is deliberately PROPOSED + disabled and therefore never posts or classifies by itself.
 */
object BankExplicitRuleProposal {
    fun create(transaction: BankTransaction, receipt: Receipt, now: String): BankLearningRule {
        val direction = if (transaction.amount >= 0.0) BankRuleDirection.INCOME else BankRuleDirection.EXPENSE
        val tolerance = max(1.0, transaction.absoluteAmount * 0.05)
        val purposeTerms = BankRuleEngine.norm(transaction.purpose)
            .split(' ')
            .filter { it.length >= 4 }
            .take(6)
            .joinToString("|")
        val canonical = listOf(
            BankRuleEngine.norm(transaction.counterparty),
            BankRuleEngine.norm(transaction.counterpartyIban),
            direction,
            transaction.accountId,
            receipt.propertyId,
            transaction.unitId,
            BankRuleEngine.norm(receipt.aussteller),
            BankRuleEngine.norm(receipt.hauptkategorie),
            BankRuleEngine.norm(receipt.unterkategorie)
        ).joinToString("|")
        val id = "rule-manual-" + sha256(canonical).take(24)
        val label = transaction.counterparty.ifBlank { receipt.aussteller.ifBlank { "Bankbuchung" } }
        val target = receipt.hauptkategorie.ifBlank { receipt.aussteller.ifBlank { "Zuordnung" } }
        return BankLearningRule(
            ruleId = id,
            displayName = "$label → $target",
            enabled = false,
            state = BankRuleState.PROPOSED,
            ruleType = BankRuleType.COMBINED,
            transactionDirection = direction,
            counterpartyPattern = transaction.counterparty.trim(),
            counterpartyIbanPattern = transaction.counterpartyIban.trim(),
            purposeTerms = purposeTerms,
            amountMin = (transaction.absoluteAmount - tolerance).coerceAtLeast(0.0),
            amountMax = transaction.absoluteAmount + tolerance,
            currency = transaction.currency,
            accountId = transaction.accountId,
            propertyId = receipt.propertyId,
            unitId = transaction.unitId,
            receiptVendorTarget = receipt.aussteller,
            receiptCategoryTarget = receipt.hauptkategorie,
            receiptSubcategoryTarget = receipt.unterkategorie,
            paymentMethodTarget = receipt.zahlungsart,
            evidenceCount = 1,
            confidence = 60,
            source = BankRuleSource.USER_CREATED,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
