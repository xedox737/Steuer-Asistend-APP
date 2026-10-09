package com.example.e2e

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import com.example.util.DatevFormatValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.io.File

@RunWith(AndroidJUnit4::class)
class DatevCsvEmulatorTest : EmulatorTestSupport() {
    @Test fun csvSelectedInRealWizardCreatesReadableCsvInsteadOfZip() {
        val property = seedProperty()
        val original = File(context.filesDir, "e2e-datev-original.pdf")
        PdfDocument().use { pdf ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            page.canvas.drawText("Synthetischer freigegebener Testbeleg", 40f, 80f, Paint())
            pdf.finishPage(page)
            original.outputStream().use { pdf.writeTo(it) }
        }
        runBlocking(Dispatchers.IO) {
            db.receiptDao().insertReceipt(expense(901, 100.0, property.propertyId, LocalDate.now().toString()).copy(
                imageUrl = original.absolutePath, originalMimeType = "application/pdf",
                freigabestatus = "FREIGEGEBEN",
                allocationsJson = AccountingApprovalJson.encodeAllocations(listOf(PersistedAllocation("e2e-row", "Reparatur", 100.0, 10000))),
                bookingProposalsJson = AccountingApprovalJson.encodeBookingProposals(listOf(PersistedBookingProposal("e2e-row", "4800", "1200", 10000, "")))
            ))
        }
        val model = vm
        ui.waitUntil(10_000) { model.receipts.value.size == 1 }
        ui.runOnIdle {
            model.updateActiveDatevProfile(DatevProfile.createDefaultSkr03().copy(beraterNummer = "1111111", mandantenNummer = "11111"))
        }
        val previousZips = context.cacheDir.walkTopDown().filter { it.extension == "zip" }.map { it.absolutePath }.toSet()
        clickMore("DATEV Export")
        ui.onNodeWithText("DATEV Export erstellen").performScrollTo().performClick()
        repeat(3) { ui.onNodeWithText("Weiter").assertIsDisplayed().performClick() }
        ui.onNodeWithText("Reiner EXTF Buchungsstapel (.csv)").performScrollTo().performClick()
        ui.onNodeWithText("Buchungsstapel jetzt erzeugen").performScrollTo().assertIsEnabled().performClick()
        ui.waitUntil(10_000) { model.lastExportResult.value != null && model.allAuditRuns.value.size == 1 }
        val result = requireNotNull(model.lastExportResult.value)
        assertEquals("csv", result.outputFile.extension)
        assertEquals("text/csv", result.mimeType)
        assertTrue(result.outputFile.isFile)
        assertEquals(1, result.totalRecords)
        assertEquals(1, model.allAuditRuns.value.single().bookingCount)
        assertTrue(model.allAuditRuns.value.single().zipFileName.endsWith(".csv"))
        val content = result.outputFile.readText(Charsets.UTF_8).removePrefix("\uFEFF")
        assertTrue(DatevFormatValidator.validate(content).isValid)
        assertEquals(3, content.trimEnd().split("\r\n").size)
        assertEquals(previousZips, context.cacheDir.walkTopDown().filter { it.extension == "zip" }.map { it.absolutePath }.toSet())
        ui.onNodeWithText("Buchungsstapel erfolgreich erstellt!").assertIsDisplayed()
        ui.onNodeWithTag("datev_share_output").performScrollTo().assertIsDisplayed().assertIsEnabled()
        capture("datev-csv-result")
    }
}
