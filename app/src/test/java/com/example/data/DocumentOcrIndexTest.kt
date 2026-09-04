package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentOcrIndexTest {
    @Test fun embeddedPdfTextIsReadBeforeOcrWouldBeNeeded() {
        val pdf = "%PDF-1.4\n1 0 obj <<>> stream\nBT (Hornbach Rechnung Nummer AB-12345) Tj ET\nendstream\nendobj\n%%EOF".toByteArray()
        val text = PdfEmbeddedTextExtractor.extract(pdf)
        assertTrue(text.contains("Hornbach Rechnung Nummer AB-12345"))
        assertTrue(PdfEmbeddedTextExtractor.isUsable(text))
    }

    @Test fun invalidPdfDoesNotProduceInventedText() {
        assertEquals("", PdfEmbeddedTextExtractor.extract("not a pdf".toByteArray()))
    }

    @Test fun searchTextContainsOcrAndReceiptIdentityButNoSecrets() {
        val document = ManagedDocument("d", "p", title = "Police", ocrText = "Versicherungsnummer ABC", documentType = ManagedDocumentType.VERSICHERUNGSPOLICE.name)
        val receipt = Receipt(aussteller = "Allianz", datum = "2026-01-01", uhrzeit = "", bruttobetrag = 417.0, hauptkategorie = "Versicherung", unterkategorie = "Gebäudeversicherung", kontoNr = "4970", beschreibung = "Jahresrechnung", internalId = "internal", displayId = "BLG-2026-1")
        val text = DocumentSearchTextBuilder.build(document, receipt)
        assertTrue(text.contains("versicherungsnummer")); assertTrue(text.contains("allianz")); assertTrue(text.contains("blg-2026-1")); assertTrue(text.contains("417.00"))
        assertFalse(text.contains("api_key"))
    }
}
