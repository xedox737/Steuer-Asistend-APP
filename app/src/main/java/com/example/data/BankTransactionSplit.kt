package com.example.data

object BankSplitPaymentType {
    const val RENT = "MIETE"
    const val UTILITIES_PREPAYMENT = "NEBENKOSTENVORAUSZAHLUNG"
    const val DEPOSIT = "KAUTION"
    const val UTILITIES_SETTLEMENT = "NEBENKOSTENNACHZAHLUNG"
    const val OTHER_INCOME = "SONSTIGE_EINNAHME"
    const val RECEIPT = "BELEG"
    const val OTHER_EXPENSE = "SONSTIGE_AUSGABE"

    val all = listOf(RENT, UTILITIES_PREPAYMENT, DEPOSIT, UTILITIES_SETTLEMENT, OTHER_INCOME, RECEIPT, OTHER_EXPENSE)
    private val incomeTypes = setOf(RENT, UTILITIES_PREPAYMENT, DEPOSIT, UTILITIES_SETTLEMENT, OTHER_INCOME)
    val rentScoped = setOf(RENT, UTILITIES_PREPAYMENT, DEPOSIT, UTILITIES_SETTLEMENT)

    fun label(type: String): String = when (type) {
        RENT -> "Miete"
        UTILITIES_PREPAYMENT -> "Nebenkostenvorauszahlung"
        DEPOSIT -> "Kaution"
        UTILITIES_SETTLEMENT -> "Betriebskosten-/Nebenkostennachzahlung"
        OTHER_INCOME -> "Sonstige Einnahme"
        RECEIPT -> "Beleg zuordnen"
        OTHER_EXPENSE -> "Sonstige Ausgabe"
        else -> type
    }

    fun directionAllowed(type: String, transaction: BankTransaction): Boolean = when (type) {
        RECEIPT -> true
        OTHER_EXPENSE -> !transaction.isIncome
        in incomeTypes -> transaction.isIncome
        else -> false
    }
}

data class BankManualSplitPosition(
    val transactionId: String,
    val amount: Double,
    val paymentType: String,
    val propertyId: String = "",
    val unitId: String = "",
    val tenantReference: String = "",
    val rentMonth: String = "",
    val note: String = "",
    val receiptId: Int? = null
)

data class BankSplitPositionValidation(val allowed: Boolean, val amount: Double = 0.0, val reason: String = "")

data class BankTransactionSplitPreview(
    val originalAmount: Double,
    val alreadyAllocated: Double,
    val newAllocations: Double,
    val remainingAmount: Double,
    val statusAfter: String,
    val valid: Boolean,
    val message: String = ""
)

/** Adapter over the existing receipt-link and rent/payment-assignment SSOT; it does not create a parallel ledger. */
object BankTransactionSplitPolicy {
    fun allocatedAmount(transactionId: String, links: List<BankReceiptLink>, assignments: List<BankRentAssignment>): Double =
        BankAllocationPolicy.roundMoney(
            links.asSequence().filter { it.transactionId == transactionId && it.status == BankLinkStatus.CONFIRMED }.sumOf { it.allocatedAmount } +
                assignments.asSequence().filter { it.transactionId == transactionId && it.status == BankRentAssignmentStatus.CONFIRMED }.sumOf { it.allocatedAmount }
        )

    fun remainingAmount(transaction: BankTransaction, links: List<BankReceiptLink>, assignments: List<BankRentAssignment>): Double =
        BankAllocationPolicy.roundMoney((transaction.absoluteAmount - allocatedAmount(transaction.transactionId, links, assignments)).coerceAtLeast(0.0))

    fun statusFor(transaction: BankTransaction, links: List<BankReceiptLink>, assignments: List<BankRentAssignment>): String {
        if (transaction.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED) return BankReconciliationStatus.NO_RECEIPT_REQUIRED
        val allocated = allocatedAmount(transaction.transactionId, links, assignments)
        return when {
            allocated <= BankAllocationPolicy.MONEY_TOLERANCE -> BankReconciliationStatus.OPEN
            transaction.absoluteAmount - allocated <= BankAllocationPolicy.MONEY_TOLERANCE -> BankReconciliationStatus.MATCHED
            else -> BankReconciliationStatus.PARTIAL
        }
    }

    fun validatePosition(transaction: BankTransaction, position: BankManualSplitPosition): BankSplitPositionValidation {
        if (position.transactionId != transaction.transactionId) return BankSplitPositionValidation(false, reason = "Teilposition gehört nicht zur Bankbuchung.")
        if (position.paymentType !in BankSplitPaymentType.all) return BankSplitPositionValidation(false, reason = "Unbekannte Aufteilungskategorie.")
        if (!position.amount.isFinite() || position.amount <= 0.0) return BankSplitPositionValidation(false, reason = "Teilbetrag muss positiv und endlich sein.")
        val amount = BankAllocationPolicy.roundMoney(position.amount)
        if (amount <= BankAllocationPolicy.MONEY_TOLERANCE) return BankSplitPositionValidation(false, reason = "Teilbetrag muss größer als 0,00 € sein.")
        if (!BankSplitPaymentType.directionAllowed(position.paymentType, transaction)) return BankSplitPositionValidation(false, reason = "Kategorie passt nicht zur Zahlungsrichtung der Bankbuchung.")
        if (transaction.propertyId.isNotBlank() && position.propertyId.isNotBlank() && transaction.propertyId != position.propertyId) return BankSplitPositionValidation(false, reason = "propertyId-Konflikt verhindert die Aufteilung.")
        if (position.paymentType in BankSplitPaymentType.rentScoped && (position.propertyId.isBlank() || position.unitId.isBlank())) return BankSplitPositionValidation(false, reason = "Für Miete, Nebenkosten oder Kaution sind Immobilie und Wohneinheit erforderlich.")
        if (position.paymentType == BankSplitPaymentType.RECEIPT && position.receiptId == null) return BankSplitPositionValidation(false, reason = "Für die Belegzuordnung muss ein Beleg gewählt werden.")
        return BankSplitPositionValidation(true, amount)
    }

    fun preview(
        transaction: BankTransaction,
        links: List<BankReceiptLink>,
        assignments: List<BankRentAssignment>,
        positions: List<BankManualSplitPosition>
    ): BankTransactionSplitPreview {
        val original = BankAllocationPolicy.roundMoney(transaction.absoluteAmount)
        val already = allocatedAmount(transaction.transactionId, links, assignments)
        if (positions.isEmpty()) return BankTransactionSplitPreview(original, already, 0.0, remainingAmount(transaction, links, assignments), transaction.reconciliationStatus, false, "Mindestens eine Position ist erforderlich.")
        val validations = positions.map { validatePosition(transaction, it) }
        val invalid = validations.firstOrNull { !it.allowed }
        if (invalid != null) return BankTransactionSplitPreview(original, already, 0.0, remainingAmount(transaction, links, assignments), transaction.reconciliationStatus, false, invalid.reason)
        val added = BankAllocationPolicy.roundMoney(validations.sumOf { it.amount })
        val available = BankAllocationPolicy.roundMoney((original - already).coerceAtLeast(0.0))
        if (added > available + BankAllocationPolicy.MONEY_TOLERANCE) return BankTransactionSplitPreview(original, already, added, BankAllocationPolicy.roundMoney(available - added), transaction.reconciliationStatus, false, "Neue Zuordnungen überschreiten den verfügbaren Restbetrag.")
        val remaining = BankAllocationPolicy.roundMoney((available - added).coerceAtLeast(0.0))
        val status = if (remaining <= BankAllocationPolicy.MONEY_TOLERANCE) BankReconciliationStatus.MATCHED else BankReconciliationStatus.PARTIAL
        return BankTransactionSplitPreview(original, already, added, remaining, status, true)
    }
}
