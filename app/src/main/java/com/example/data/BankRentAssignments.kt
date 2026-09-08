package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

object BankRentAssignmentStatus {
    const val CONFIRMED = "CONFIRMED"
    const val REVIEW = "REVIEW"
}

object BankRentAssignmentSource {
    const val USER_CONFIRMED = "USER_CONFIRMED"
    const val MANUAL = "MANUAL"
}

@Entity(
    tableName = "bank_rent_assignments",
    indices = [
        Index(value = ["transactionId"], name = "index_bank_rent_assignments_transactionId"),
        Index(value = ["propertyId", "unitId", "rentMonth"], name = "index_bank_rent_assignments_rent_scope"),
        Index(value = ["tenantReference"], name = "index_bank_rent_assignments_tenantReference")
    ]
)
data class BankRentAssignment(
    @PrimaryKey val assignmentId: String,
    val transactionId: String,
    val propertyId: String,
    val unitId: String,
    val rentMonth: String,
    val tenantReference: String,
    val allocatedAmount: Double,
    val paymentType: String,
    val status: String = BankRentAssignmentStatus.CONFIRMED,
    val source: String = BankRentAssignmentSource.USER_CONFIRMED,
    val receiptId: Int? = null,
    val createdAt: String,
    val updatedAt: String
)

@Dao
interface BankRentAssignmentDao {
    @Query("SELECT * FROM bank_rent_assignments ORDER BY rentMonth DESC, createdAt DESC, assignmentId")
    fun observeAll(): Flow<List<BankRentAssignment>>

    @Query("SELECT * FROM bank_rent_assignments ORDER BY rentMonth DESC, createdAt DESC, assignmentId")
    suspend fun getAll(): List<BankRentAssignment>

    @Query("SELECT * FROM bank_rent_assignments WHERE assignmentId = :assignmentId LIMIT 1")
    suspend fun getById(assignmentId: String): BankRentAssignment?

    @Query("SELECT * FROM bank_rent_assignments WHERE transactionId = :transactionId ORDER BY createdAt, assignmentId")
    suspend fun getForTransaction(transactionId: String): List<BankRentAssignment>

    @Query("SELECT * FROM bank_rent_assignments WHERE propertyId = :propertyId AND unitId = :unitId AND rentMonth = :rentMonth AND status = 'CONFIRMED'")
    suspend fun getConfirmedForMonth(propertyId: String, unitId: String, rentMonth: String): List<BankRentAssignment>

    @Query("SELECT COALESCE(SUM(allocatedAmount), 0.0) FROM bank_rent_assignments WHERE propertyId = :propertyId AND unitId = :unitId AND rentMonth = :rentMonth AND status = 'CONFIRMED' AND paymentType = 'MIETE'")
    suspend fun confirmedRentAmount(propertyId: String, unitId: String, rentMonth: String): Double

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(assignment: BankRentAssignment)

    @Query("DELETE FROM bank_rent_assignments WHERE assignmentId = :assignmentId")
    suspend fun deleteById(assignmentId: String)

    @Query("DELETE FROM bank_rent_assignments")
    suspend fun clearAll()
}

object BankRentAssignmentIdentity {
    fun id(transactionId: String, propertyId: String, unitId: String, rentMonth: String, paymentType: String): String =
        "rent-${BankTransactionIdentity.sha256(listOf(transactionId, propertyId, unitId, rentMonth, paymentType).joinToString("|")).take(28)}"
}

object BankRentMonthStatus {
    const val OPEN = "OFFEN"
    const val PARTIAL = "TEILWEISE_BEZAHLT"
    const val PAID = "BEZAHLT"
    const val OVERPAID = "UEBERZAHLT"
    const val REVIEW = "PRUEFEN"

    fun derive(expected: Double, confirmed: Double, hasReview: Boolean = false): String = when {
        hasReview -> REVIEW
        confirmed <= 0.01 -> OPEN
        confirmed + 0.01 < expected -> PARTIAL
        confirmed <= expected + 0.01 -> PAID
        else -> OVERPAID
    }
}