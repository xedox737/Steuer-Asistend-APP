package com.example.e2e

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.*
import com.example.util.PdfExporter
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** PdfDocument requires real Android; the existing disposable-emulator guard remains mandatory. */
@RunWith(AndroidJUnit4::class)
class OfflineBackupFilePublicationEmulatorTest {
    @get:Rule val environment = EmulatorEnvironmentRule { 1.0f }

    @Test
    fun pdfReportIsRegisteredBeforeCachePublication() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = java.io.File(context.cacheDir, "pdf-coordination-${java.util.UUID.randomUUID()}").apply { check(mkdirs()) }
        val coordinator = OfflineBackupCoordinator.shared
        kotlinx.coroutines.runBlocking { coordinator.awaitInitialization() }
        val observed = mutableListOf<Boolean>()
        val probe = object : android.content.ContextWrapper(context) {
            override fun getCacheDir(): java.io.File {
                observed += runCatching { coordinator.beginCapture() }.exceptionOrNull() is OfflineBackupValidationException
                return output
            }
        }
        val before = coordinator.beginCapture()
        val file = requireNotNull(PdfExporter.exportReceiptsToPdf(probe, 2026, listOf(approvedReceipt()), null))
        try {
            assertEquals(listOf(true), observed)
            assertTrue(file.length() > 0)
            assertArrayEquals("%PDF-".toByteArray(), file.readBytes().take(5).toByteArray())
            org.junit.Assert.assertThrows(OfflineBackupValidationException::class.java) { coordinator.completeCapture(before) }
            coordinator.completeCapture(coordinator.beginCapture())
        } finally { output.deleteRecursively() }
    }

    private fun approvedReceipt() = Receipt(id = 2001, aussteller = "Test GmbH", datum = "2026-08-01", uhrzeit = "",
        bruttobetrag = 100.0, hauptkategorie = "Instandhaltung & Reparaturen", unterkategorie = "Reparatur",
        kontoNr = "4801", beschreibung = "Anonymisierter Testbeleg", internalId = "receipt-2001",
        allocationsJson = AccountingApprovalJson.encodeAllocations(listOf(PersistedAllocation("a", "Reparatur", 100.0, 10_000))),
        bookingProposalsJson = AccountingApprovalJson.encodeBookingProposals(listOf(PersistedBookingProposal("a", "4801", "70000", 10_000, ""))),
        freigabestatus = "FREIGEGEBEN")

}
