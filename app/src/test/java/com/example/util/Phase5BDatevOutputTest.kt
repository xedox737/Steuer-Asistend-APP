package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import java.util.zip.ZipFile
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Phase5BDatevOutputTest {
    private val profile = DatevProfile.createDefaultSkr03().copy(beraterNummer = "1111111", mandantenNummer = "11111")
    private val records get() = (1..2).map { id -> BookingRecord(bookingId = "booking-$id", receiptId = id,
        belegnummer = "BELEG-$id", belegdatum = "2026-10-08", buchungsdatum = "2026-10-08", zahlungspartner = "Handwerk",
        beschreibung = "Reparatur", bruttobetrag = 100.0, sollHaben = "S", sachkonto = "4800", gegenkonto = "1200",
        belegfeld1 = "BELEG-$id", hauptkategorie = "Renovierung", objektId = "property", kost1 = "property", steuerlichesJahr = 2026) }

    @Test fun savedCsvAndAdvisorZipAreRegisteredBeforeCachePublication() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val coordinator = OfflineBackupCoordinator.shared
        val observed = mutableListOf<Boolean>()
        val probe = object : android.content.ContextWrapper(context) {
            override fun getCacheDir(): java.io.File {
                observed += runCatching { coordinator.beginCapture() }.exceptionOrNull() is OfflineBackupValidationException
                return context.cacheDir
            }
        }
        val bookings = records
        val report = BookingValidationService.validateRecords(bookings, profile)
        val creators: List<() -> AdvisorPackageResult> = listOf(
            { DatevCsvOutput.create(probe, bookings, profile, report, "2026") },
            { AdvisorPackageBuilder.buildPackage(probe, bookings, emptyList(), emptyList(), false, profile, report) }
        )
        creators.forEach { create ->
            observed.clear()
            val before = coordinator.beginCapture()
            val result = create()
            try {
                assertEquals(listOf(true), observed)
                assertTrue(result.outputFile.isFile && result.outputFile.length() > 0)
                assertEquals(ReceiptManifestService.calculateSha256(result.outputFile), result.sha256Checksum)
                assertEquals(2, result.totalRecords)
                org.junit.Assert.assertThrows(OfflineBackupValidationException::class.java) { coordinator.completeCapture(before) }
                coordinator.completeCapture(coordinator.beginCapture())
            } finally { result.outputFile.delete() }
        }
    }

    @Test fun legacyZipIsRegisteredBeforeCachePublication() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val coordinator = OfflineBackupCoordinator.shared
        val observed = mutableListOf<Boolean>()
        val probe = object : android.content.ContextWrapper(context) {
            override fun getCacheDir(): java.io.File {
                observed += runCatching { coordinator.beginCapture() }.exceptionOrNull() is OfflineBackupValidationException
                return context.cacheDir
            }
        }
        val before = coordinator.beginCapture()
        val file = DatevExporter.createDatevZipPackage(probe, listOf(approvedReceipt()),
            DatevConfig(beraterNummer = "1111111", mandantenNummer = "11111", mandantenName = "Testmandant", wirtschaftsjahr = 2026))
        try {
            assertEquals(listOf(true), observed)
            ZipFile(file).use { zip ->
                assertNotNull(zip.getEntry("EXTF_Buchungsstapel.csv"))
                assertNotNull(zip.getEntry("document.xml"))
            }
            org.junit.Assert.assertThrows(OfflineBackupValidationException::class.java) { coordinator.completeCapture(before) }
            coordinator.completeCapture(coordinator.beginCapture())
        } finally { file.delete() }
    }

    private fun approvedReceipt() = Receipt(id = 2001, aussteller = "Test GmbH", datum = "2026-08-01", uhrzeit = "",
        bruttobetrag = 100.0, hauptkategorie = "Instandhaltung & Reparaturen", unterkategorie = "Reparatur",
        kontoNr = "4801", beschreibung = "Anonymisierter Testbeleg", internalId = "receipt-2001",
        allocationsJson = AccountingApprovalJson.encodeAllocations(listOf(PersistedAllocation("a", "Reparatur", 100.0, 10_000))),
        bookingProposalsJson = AccountingApprovalJson.encodeBookingProposals(listOf(PersistedBookingProposal("a", "4801", "70000", 10_000, ""))),
        freigabestatus = "FREIGEGEBEN")

    @Test fun csvOutputIsDirectExtfWithCorrectMimeAndTwoRecordsWithoutOriginals() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bookings = records
        val result = DatevCsvOutput.create(context, bookings, profile, BookingValidationService.validateRecords(bookings, profile), "2026")
        try {
            assertEquals("csv", result.outputFile.extension)
            assertEquals("text/csv", result.mimeType)
            assertEquals(2, result.totalRecords)
            val bytes = result.outputFile.readBytes()
            assertArrayEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.take(3).toByteArray())
            val text = bytes.drop(3).toByteArray().toString(Charsets.UTF_8)
            assertTrue(text.startsWith("\"EXTF\""))
            assertTrue(DatevFormatValidator.validate(text).isValid)
            assertEquals(4, text.trimEnd().split("\r\n").size)
            assertFalse(result.packageStructureVerified)
            assertEquals(ReceiptManifestService.calculateSha256(result.outputFile), result.sha256Checksum)
        } finally { result.outputFile.delete() }
    }

    @Test fun zipManifestCountsBookingsIndependentlyOfAttachmentsAndMatchesDescription() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bookings = records
        val result = AdvisorPackageBuilder.buildPackage(context, bookings, emptyList(), emptyList(), false, profile,
            BookingValidationService.validateRecords(bookings, profile))
        try {
            assertEquals("zip", result.outputFile.extension)
            assertEquals("application/zip", result.mimeType)
            ZipFile(result.outputFile).use { zip ->
                val manifest = JSONObject(zip.getInputStream(zip.getEntry("08_Pruefprotokoll/manifest.json")).bufferedReader().readText())
                assertEquals(2, manifest.getInt("totalRecords"))
                assertEquals(0, manifest.getJSONArray("files").length())
                AdvisorPackageStructure.requiredFolders.forEach { folder ->
                    assertNotNull(zip.getEntry("$folder/"))
                    assertTrue(AdvisorPackageStructure.formatDescription.contains("$folder/"))
                }
                assertFalse(AdvisorPackageStructure.formatDescription.contains("02_Belege/"))
            }
        } finally { result.outputFile.delete() }
    }
}
