package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Phase5AMultipleOriginalExportTest {
    @get:Rule val temp = TemporaryFolder()
    private fun receipt(paths: String) = Receipt(id = 1, internalId = "receipt-multi", displayId = "BELEG-1",
        aussteller = "Handwerk", datum = "2026-10-08", uhrzeit = "", bruttobetrag = 100.0,
        hauptkategorie = "Renovierung", unterkategorie = "Material", kontoNr = "4800", beschreibung = "Reparatur", imageUrl = paths)

    @Test fun bothOriginalsAndIndividualManifestIdentitiesHashesAndOrderReachAdvisorZip() {
        val first = temp.newFile("Seite 1.pdf").apply { writeText("%PDF-1.4 erste Originalseite") }
        val second = temp.newFile("Seite 2.jpg").apply { writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 12, 34, 56)) }
        val receipt = receipt("${first.absolutePath},${second.absolutePath}")
        val originals = listOf(first, second).mapIndexed { index, file -> ManagedDocument(
            documentId = "attachment-$index", receiptInternalId = receipt.internalId, propertyId = "property",
            originalFilename = file.name, localUri = file.absolutePath, fileSizeBytes = file.length(),
            sha256 = StableDocumentIdentity.sha256(file.readBytes()),
            mimeType = if (index == 0) "application/pdf" else "image/jpeg") }
        val profile = DatevProfile.createDefaultSkr03().copy(beraterNummer = "1111111", mandantenNummer = "11111")
        val record = BookingRecord(bookingId = "booking", receiptId = receipt.id, belegnummer = "BELEG-1",
            belegdatum = receipt.datum, buchungsdatum = receipt.datum, zahlungspartner = receipt.aussteller,
            beschreibung = receipt.beschreibung, bruttobetrag = 100.0, sollHaben = "S", sachkonto = "4800",
            gegenkonto = "1200", belegfeld1 = "BELEG-1", hauptkategorie = "Renovierung", objektId = "property",
            kost1 = "property", steuerlichesJahr = 2026)
        val result = AdvisorPackageBuilder.buildPackage(ApplicationProvider.getApplicationContext<Context>(), listOf(record),
            listOf(receipt), emptyList(), true, profile, BookingValidationService.validateRecords(listOf(record), profile),
            originalDocuments = originals)
        try {
            ZipFile(result.zipFile).use { zip ->
                val entries = zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith("02_Originalbelege/") }.toList()
                assertEquals(2, entries.size)
                val manifest = JSONObject(zip.getInputStream(zip.getEntry("08_Pruefprotokoll/manifest.json")).bufferedReader().readText()).getJSONArray("files")
                assertEquals(2, manifest.length())
                listOf(first, second).forEachIndexed { index, file ->
                    val item = manifest.getJSONObject(index)
                    assertEquals(receipt.internalId, item.getString("receiptInternalId"))
                    assertEquals("attachment-$index", item.getString("attachmentId"))
                    assertEquals(file.name, item.getString("originalFilename"))
                    assertEquals(index, item.getInt("order"))
                    assertEquals(file.length(), item.getLong("fileSizeBytes"))
                    assertEquals(originals[index].mimeType, item.getString("mimeType"))
                    assertEquals(originals[index].sha256, item.getString("sha256Hash"))
                    assertArrayEquals(file.readBytes(), zip.getInputStream(zip.getEntry(item.getString("filename"))).readBytes())
                }
            }
        } finally { result.zipFile.delete() }
    }

    @Test fun missingSecondOriginalOrHashMismatchBlocksWholeReceiptInsteadOfExportingFirstOnly() {
        val first = temp.newFile("first.pdf").apply { writeText("%PDF-1.4 original") }
        val receipt = receipt("${first.absolutePath},${temp.root}/missing.jpg")
        assertTrue(DatevOriginalAttachmentPolicy.resolveAll(receipt).isEmpty())
        assertNull(DatevOriginalAttachmentPolicy.resolve(receipt))
        val document = ManagedDocument("first", "property", receiptInternalId = receipt.internalId,
            localUri = first.absolutePath, sha256 = "0".repeat(64), fileSizeBytes = first.length())
        assertTrue(DatevOriginalAttachmentPolicy.resolveAll(receipt(first.absolutePath), listOf(document)).isEmpty())
    }
}
