package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Revisionssicheres Protokoll eines Exportlaufs.
 */
@Entity(tableName = "export_audit_runs")
data class ExportAuditRun(
    @PrimaryKey val exportlaufId: String,  // Unique ID (z.B. EXPORT_20260727_120000_A1B2)
    val timestamp: Long = System.currentTimeMillis(),
    val user: String = "Sergej Gerweck",
    val propertyName: String = "",
    val periodStart: String = "",
    val periodEnd: String = "",
    val filterSummary: String = "",
    val exportierteReceiptIdsJson: String = "[]", // Serialisierte List<Int>
    val ausgeschlosseneReceiptIdsJson: String = "[]", // Serialisierte List<Int>
    val kanzleiprofilNameVersion: String = "",
    val zipFileName: String = "",
    val zipFileSizeBytes: Long = 0L,
    val zipSha256: String = "",
    val status: String = "SUCCESS", // SUCCESS, CANCELLED, FAILED
    val totalAmount: Double = 0.0,
    val bookingCount: Int = 0,
    val warningsCount: Int = 0,
    val logMessage: String = ""
)
