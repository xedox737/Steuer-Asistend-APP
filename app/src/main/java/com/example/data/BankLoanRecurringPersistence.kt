package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

object BankLoanAssignmentStatus {
    const val CONFIRMED = "CONFIRMED"
    const val REVIEW = "REVIEW"
}

object BankLoanAssignmentSource {
    const val USER_CONFIRMED = "USER_CONFIRMED"
    const val MANUAL = "MANUAL"
}

object BankLoanSplitStatus {
    const val NONE = "NONE"
    const val PROPOSED = "PROPOSED"
    const val ACCEPTED = "ACCEPTED"
    const val EDITED = "EDITED"
    const val REJECTED = "REJECTED"
}

@Entity(
    tableName = "bank_loan_assignments",
    indices = [
        Index(value = ["transactionId"], name = "index_bank_loan_assignments_transactionId", unique = true),
        Index(value = ["loanId", "period"], name = "index_bank_loan_assignments_loan_period"),
        Index(value = ["propertyId"], name = "index_bank_loan_assignments_propertyId")
    ]
)
data class BankLoanAssignment(
    @PrimaryKey val assignmentId: String,
    val transactionId: String,
    val loanId: Int,
    val propertyId: String,
    val allocatedAmount: Double,
    val paymentType: String,
    val period: String,
    val status: String = BankLoanAssignmentStatus.CONFIRMED,
    val source: String = BankLoanAssignmentSource.USER_CONFIRMED,
    val splitStatus: String = BankLoanSplitStatus.NONE,
    val proposedInterest: Double? = null,
    val proposedPrincipal: Double? = null,
    val interestBasis: String = "",
    val createdAt: String,
    val updatedAt: String
)

@Dao
interface BankLoanAssignmentDao {
    @Query("SELECT * FROM bank_loan_assignments ORDER BY updatedAt DESC, assignmentId")
    fun observeAll(): Flow<List<BankLoanAssignment>>

    @Query("SELECT * FROM bank_loan_assignments ORDER BY updatedAt DESC, assignmentId")
    suspend fun getAll(): List<BankLoanAssignment>

    @Query("SELECT * FROM bank_loan_assignments WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getForTransaction(transactionId: String): BankLoanAssignment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(assignment: BankLoanAssignment)

    @Query("DELETE FROM bank_loan_assignments WHERE transactionId = :transactionId")
    suspend fun deleteForTransaction(transactionId: String)
}

@Entity(
    tableName = "bank_recurring_patterns",
    indices = [
        Index(value = ["accountId"], name = "index_bank_recurring_patterns_accountId"),
        Index(value = ["propertyId"], name = "index_bank_recurring_patterns_propertyId"),
        Index(value = ["enabled"], name = "index_bank_recurring_patterns_enabled")
    ]
)
data class BankRecurringPattern(
    @PrimaryKey val patternId: String,
    val enabled: Boolean = true,
    val direction: String,
    val normalizedCounterparty: String,
    val purposeFingerprint: String,
    val typicalAmount: Double,
    val amountTolerance: Double,
    val cadence: String,
    val typicalDay: Int,
    val accountId: String = "",
    val propertyId: String = "",
    val occurrenceCount: Int,
    val confidence: Int,
    val lastOccurrence: String,
    val nextExpectedStart: String,
    val nextExpectedEnd: String,
    val reasonsText: String = "",
    val createdAt: String,
    val updatedAt: String
)

@Dao
interface BankRecurringPatternDao {
    @Query("SELECT * FROM bank_recurring_patterns ORDER BY enabled DESC, confidence DESC, updatedAt DESC")
    fun observeAll(): Flow<List<BankRecurringPattern>>

    @Query("SELECT * FROM bank_recurring_patterns ORDER BY enabled DESC, confidence DESC, updatedAt DESC")
    suspend fun getAll(): List<BankRecurringPattern>

    @Query("SELECT * FROM bank_recurring_patterns WHERE patternId = :patternId LIMIT 1")
    suspend fun get(patternId: String): BankRecurringPattern?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pattern: BankRecurringPattern)

    @Query("UPDATE bank_recurring_patterns SET enabled = :enabled, updatedAt = :updatedAt WHERE patternId = :patternId")
    suspend fun setEnabled(patternId: String, enabled: Boolean, updatedAt: String)

    @Query("DELETE FROM bank_recurring_patterns WHERE patternId = :patternId")
    suspend fun delete(patternId: String)
}

object BankLoanAssignmentIdentity {
    fun id(transactionId: String): String =
        "loan-assignment-" + BankTransactionIdentity.sha256(transactionId).take(28)
}

data class LoanSplitProposal(
    val interest: Double,
    val principal: Double,
    val basis: String,
    val annualInterestRate: Double,
    val period: String
)

/**
 * Analysis-only split. It never changes Loan, Receipt or DATEV state.
 * A proposal is only emitted when existing Loan data contains a positive balance,
 * positive interest rate, a known period and a positive allocated amount.
 */
object BankLoanSplitProposer {
    fun propose(loan: Loan, allocatedAmount: Double, period: String): LoanSplitProposal? {
        if (loan.restschuld <= 0.0 || loan.sollzinsProzent <= 0.0 || allocatedAmount <= 0.0) return null
        if (runCatching { YearMonth.parse(period) }.isFailure) return null
        val monthlyInterest = loan.restschuld * (loan.sollzinsProzent / 100.0) / 12.0
        val interest = minOf(allocatedAmount, monthlyInterest).roundMoney()
        val principal = (allocatedAmount - interest).coerceAtLeast(0.0).roundMoney()
        if (abs((interest + principal) - allocatedAmount) > 0.02) return null
        return LoanSplitProposal(
            interest = interest,
            principal = principal,
            basis = "Restschuld ${loan.restschuld.roundMoney()} €, Sollzins ${loan.sollzinsProzent} %, Periode $period; rechnerischer Monatszins",
            annualInterestRate = loan.sollzinsProzent,
            period = period
        )
    }

    private fun Double.roundMoney(): Double = kotlin.math.round(this * 100.0) / 100.0
}

object BankPhase2CPriority {
    enum class Classification { RENT, LOAN, RECURRING, NONE }

    fun classify(
        transaction: BankTransaction,
        hasHighRentSuggestion: Boolean,
        loanSuggestions: List<BankLoanSuggestion>,
        recurringPatterns: List<RecurringPaymentPattern>,
        alreadyLoanAssigned: Boolean = false,
        alreadyRentAssigned: Boolean = false
    ): Classification {
        if (alreadyRentAssigned || (transaction.isIncome && hasHighRentSuggestion)) return Classification.RENT
        if (alreadyLoanAssigned || loanSuggestions.any { it.score >= BankLoanThresholds.MIN_SUGGESTION_SCORE }) return Classification.LOAN
        if (recurringPatterns.any { it.confidence >= BankRecurringThresholds.MIN_PATTERN_CONFIDENCE }) return Classification.RECURRING
        return Classification.NONE
    }
}

class BankLoanAssignmentService(
    private val dao: BankLoanAssignmentDao,
    private val bankDao: BankDao,
    private val now: () -> String = { Instant.now().toString() }
) {
    suspend fun confirm(
        transaction: BankTransaction,
        loan: Loan,
        suggestion: BankLoanSuggestion,
        paymentType: String = suggestion.paymentType,
        split: LoanSplitProposal? = null,
        source: String = BankLoanAssignmentSource.USER_CONFIRMED
    ): BankLoanAssignment {
        val existing = dao.getForTransaction(transaction.transactionId)
        val createdAt = existing?.createdAt?.ifBlank { now() } ?: now()
        val period = suggestion.period.ifBlank {
            runCatching { YearMonth.from(LocalDate.parse(transaction.bookingDate)).toString() }.getOrDefault("")
        }
        val assignment = BankLoanAssignment(
            assignmentId = BankLoanAssignmentIdentity.id(transaction.transactionId),
            transactionId = transaction.transactionId,
            loanId = loan.id,
            propertyId = loan.propertyId,
            allocatedAmount = transaction.absoluteAmount,
            paymentType = paymentType,
            period = period,
            status = if (suggestion.conflictState == BankLoanConflictState.NONE) BankLoanAssignmentStatus.CONFIRMED else BankLoanAssignmentStatus.REVIEW,
            source = source,
            splitStatus = if (split == null) BankLoanSplitStatus.NONE else BankLoanSplitStatus.PROPOSED,
            proposedInterest = split?.interest,
            proposedPrincipal = split?.principal,
            interestBasis = split?.basis.orEmpty(),
            createdAt = createdAt,
            updatedAt = now()
        )
        dao.upsert(assignment)
        // Keep the shared Phase-1 review/status world. Do not create a second transaction status.
        val status = if (assignment.status == BankLoanAssignmentStatus.REVIEW) BankReconciliationStatus.REVIEW else BankReconciliationStatus.MATCHED
        bankDao.updateTransactionStatus(transaction.transactionId, status, updatedAt = now())
        return assignment
    }

    suspend fun updateSplit(transactionId: String, interest: Double?, principal: Double?, accept: Boolean, edited: Boolean = false) {
        val current = dao.getForTransaction(transactionId) ?: return
        val amount = current.allocatedAmount
        val valid = interest != null && principal != null && interest >= 0.0 && principal >= 0.0 && abs(interest + principal - amount) <= 0.02
        val updated = when {
            !accept -> current.copy(splitStatus = BankLoanSplitStatus.REJECTED, proposedInterest = null, proposedPrincipal = null, updatedAt = now())
            valid -> current.copy(splitStatus = if (edited) BankLoanSplitStatus.EDITED else BankLoanSplitStatus.ACCEPTED, proposedInterest = interest, proposedPrincipal = principal, updatedAt = now())
            else -> current.copy(splitStatus = BankLoanSplitStatus.PROPOSED, updatedAt = now())
        }
        dao.upsert(updated)
    }

    suspend fun unlink(transactionId: String) {
        dao.deleteForTransaction(transactionId)
        // Do not touch receipt links. Re-open only the shared transaction status.
        bankDao.updateTransactionStatus(transactionId, BankReconciliationStatus.OPEN, updatedAt = now())
    }
}

fun RecurringPaymentPattern.toEntity(now: String = Instant.now().toString()): BankRecurringPattern = BankRecurringPattern(
    patternId = patternId,
    enabled = true,
    direction = direction,
    normalizedCounterparty = normalizedCounterparty,
    purposeFingerprint = purposeFingerprint,
    typicalAmount = typicalAmount,
    amountTolerance = amountTolerance,
    cadence = cadence,
    typicalDay = typicalDay,
    accountId = accountId.orEmpty(),
    propertyId = propertyId.orEmpty(),
    occurrenceCount = occurrenceCount,
    confidence = confidence,
    lastOccurrence = lastOccurrence,
    nextExpectedStart = nextExpectedWindow?.start.orEmpty(),
    nextExpectedEnd = nextExpectedWindow?.end.orEmpty(),
    reasonsText = reasons.joinToString(" | "),
    createdAt = now,
    updatedAt = now
)
