package com.example.util

import com.example.data.BookingRecord
import com.example.data.DatevProfile
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.security.MessageDigest

data class ManifestFileItem(
    val filename: String,
    val receiptId: Int,
    val mimeType: String,
    val fileSizeBytes: Long,
    val sha256Hash: String,
    val belegnummer: String,
    val belegdatum: String,
    val betragEur: Double
)

data class ManifestData(
    val exportId: String,
    val timestampIso: String,
    val generatorSystem: String = "Immobilien-Steuerassistent v2.5",
    val kanzleiprofilName: String,
    val kanzleiprofilVersion: Int,
    val beraterNummer: String,
    val mandantenNummer: String,
    val totalRecords: Int,
    val totalAmountEur: Double,
    val files: List<ManifestFileItem>
)

object ReceiptManifestService {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val jsonAdapter = moshi.adapter(ManifestData::class.java).indent("  ")

    fun calculateSha256(file: File): String {
        return try {
            if (!file.exists()) return "FILE_NOT_FOUND"
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "SHA_ERROR"
        }
    }

    fun calculateSha256Bytes(bytes: ByteArray): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(bytes)
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "SHA_ERROR"
        }
    }

    fun generateManifestJson(
        exportId: String,
        timestampIso: String,
        profile: DatevProfile,
        fileItems: List<ManifestFileItem>,
        totalAmountEur: Double
    ): String {
        val manifest = ManifestData(
            exportId = exportId,
            timestampIso = timestampIso,
            kanzleiprofilName = profile.profileName,
            kanzleiprofilVersion = profile.version,
            beraterNummer = profile.beraterNummer,
            mandantenNummer = profile.mandantenNummer,
            totalRecords = fileItems.size,
            totalAmountEur = totalAmountEur,
            files = fileItems
        )
        return jsonAdapter.toJson(manifest)
    }
}
