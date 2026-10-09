package com.example.util

import android.content.Context
import com.example.data.BookingRecord
import com.example.data.DatevProfile
import java.io.File
import java.util.UUID

/** Only the output container differs; mapping, approval and validation stay upstream. */
object DatevCsvOutput {
    fun create(context: Context, records: List<BookingRecord>, profile: DatevProfile,
        report: ValidationReport, period: String): AdvisorPackageResult {
        require(report.isValidForExport && records.isNotEmpty()) { "Der Buchungsstapel ist nicht für den Export freigegeben." }
        val csv = DatevCsvSerializer.serializeToCsvString(records, profile, period.take(4))
        val format = DatevFormatValidator.validate(csv)
        require(format.isValid) { "DATEV-Export wegen Formatfehlern blockiert: ${format.errors.joinToString(" | ")}" }
        val exportId = "EXP_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        val file = File(context.cacheDir, "EXTF_Buchungsstapel_${period}_$exportId.csv")
        file.outputStream().use { output ->
            output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            output.write(csv.toByteArray(Charsets.UTF_8))
        }
        return AdvisorPackageResult(file, exportId, records.size, report.totalAmount,
            ReceiptManifestService.calculateSha256(file), report.warnings.size,
            "Buchungsstapel erstellt", false, "text/csv")
    }
}
