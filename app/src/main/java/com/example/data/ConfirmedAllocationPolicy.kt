package com.example.data

data class AllocationValidationResult(
    val valid: Boolean,
    val errors: List<String>,
    val roundingDifferenceCent: Long
)

/**
 * Validates user-confirmed accounting allocations independently from receipt line items.
 * Receipt positions remain document content and are never treated as booking rows by this policy.
 */
object ConfirmedAllocationPolicy {
    fun validate(
        receiptAmountCent: Long,
        allocations: List<PersistedAllocation>,
        roundingToleranceCent: Long = 1L
    ): AllocationValidationResult {
        val errors = mutableListOf<String>()
        if (allocations.isEmpty()) {
            errors += "Keine fachlich bestätigte Aufteilung vorhanden."
            return AllocationValidationResult(false, errors, receiptAmountCent)
        }
        if (allocations.map { it.id }.any(String::isBlank) ||
            allocations.map { it.id }.distinct().size != allocations.size
        ) {
            errors += "Aufteilungen benötigen eindeutige IDs."
        }
        allocations.forEach {
            if (it.percent < 0.0 || it.percent > 100.0) {
                errors += "Ungültiger Prozentwert bei '${it.description}'."
            }
            if (receiptAmountCent > 0L && it.amountCent < 0L ||
                receiptAmountCent < 0L && it.amountCent > 0L
            ) {
                errors += "Vorzeichen der Aufteilung passt nicht zum Belegbetrag."
            }
        }

        val percentDifference = kotlin.math.abs(100.0 - allocations.sumOf { it.percent })
        if (percentDifference > 0.01) {
            errors += "Summe der Aufteilungsprozente ist nicht 100 %."
        }

        val difference = receiptAmountCent - allocations.sumOf { it.amountCent }
        if (kotlin.math.abs(difference) > roundingToleranceCent) {
            errors += "Summe der Aufteilungen weicht um $difference Cent vom Belegbetrag ab."
        }

        return AllocationValidationResult(errors.isEmpty(), errors, difference)
    }
}
