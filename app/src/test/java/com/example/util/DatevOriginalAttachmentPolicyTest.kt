package com.example.util

import com.example.data.Receipt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DatevOriginalAttachmentPolicyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun resolvesRealPdfByMagicBytes() {
        val file = temporaryFolder.newFile("receipt.bin")
        file.writeBytes("%PDF-1.7 test".toByteArray())

        val attachment = DatevOriginalAttachmentPolicy.resolve(receipt(file.absolutePath))

        requireNotNull(attachment)
        assertEquals("application/pdf", attachment.mimeType)
        assertEquals("pdf", attachment.extension)
        assertEquals(file.canonicalFile, attachment.file.canonicalFile)
    }

    @Test
    fun missingOrUnsupportedOriginalIsRejected() {
        assertNull(DatevOriginalAttachmentPolicy.resolve(receipt("missing.pdf")))

        val text = temporaryFolder.newFile("receipt.txt")
        text.writeText("not a receipt")
        assertNull(DatevOriginalAttachmentPolicy.resolve(receipt(text.absolutePath)))
    }

    @Test
    fun oneOriginalIsReturnedPerStableReceiptIdentity() {
        val file = temporaryFolder.newFile("receipt.pdf")
        file.writeBytes("%PDF-1.7 test".toByteArray())
        val first = receipt(file.absolutePath)
        val restoredDuplicate = first.copy(id = 99)

        val result = DatevOriginalAttachmentPolicy.unique(listOf(first, restoredDuplicate))

        assertEquals(1, result.size)
        assertTrue(result.single().file.exists())
    }

    private fun receipt(path: String) = Receipt(
        id = 5,
        aussteller = "Test GmbH",
        datum = "2026-08-01",
        uhrzeit = "",
        bruttobetrag = 100.0,
        hauptkategorie = "Werbungskosten",
        unterkategorie = "Reparatur",
        kontoNr = "4801",
        beschreibung = "Test",
        imageUrl = path,
        internalId = "receipt-original-5"
    )
}
