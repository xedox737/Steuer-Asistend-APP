package com.example.api

import com.example.data.ReceiptItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiReceiptAnalysisTest {
    @Test
    fun plausibleOpenAiKeyCheckRejectsPlaceholderAndWhitespace() {
        assertFalse(AiProviderSettings.isPlausibleOpenAiKey("MY_OPENAI_API_KEY"))
        assertFalse(AiProviderSettings.isPlausibleOpenAiKey("sk-proj-" + "invalid key material"))
        assertTrue(AiProviderSettings.isPlausibleOpenAiKey("sk-proj-" + "a".repeat(26)))
    }

    @Test
    fun completeReceiptPassesSemanticValidation() {
        val receipt = ExtractedReceipt(
            aussteller = "Anonymisierte Test GmbH",
            datum = "2026-08-02",
            bruttobetrag = 119.0,
            hauptkategorie = "Renovierungs- / Reparaturkosten & Investitionen",
            unterkategorie = "Sanitär",
            kontoNr = "4830",
            beschreibung = "Anonymisierter Testbeleg",
            positionen = listOf(
                ReceiptItem(
                    bezeichnung = "Testartikel",
                    menge = 1.0,
                    einzelpreis = 119.0,
                    gesamtpreis = 119.0,
                    hauptkategorie = "Renovierungs- / Reparaturkosten & Investitionen",
                    unterkategorie = "Sanitär",
                    kontoNr = "4830"
                )
            )
        )

        assertTrue(ReceiptAnalysisValidator.errors(receipt).isEmpty())
    }

    @Test
    fun calendarInvalidDateIsRejected() {
        val receipt = ExtractedReceipt(
            aussteller = "Anonymisierte Test GmbH",
            datum = "2026-02-31",
            bruttobetrag = 10.0,
            hauptkategorie = "Sonstige Ausgaben",
            unterkategorie = "Test",
            kontoNr = "4980"
        )

        assertTrue(ReceiptAnalysisValidator.errors(receipt).any { it.contains("Belegdatum") })
    }

    @Test
    fun incompleteModelOutputIsBlockedBeforeFormPopulation() {
        val receipt = ExtractedReceipt(
            aussteller = "",
            datum = "02.08.2026",
            bruttobetrag = 0.0,
            hauptkategorie = "Erfundene Kategorie",
            unterkategorie = "",
            kontoNr = "abc"
        )

        val errors = ReceiptAnalysisValidator.errors(receipt)
        assertTrue(errors.any { it.contains("Aussteller") })
        assertTrue(errors.any { it.contains("Belegdatum") })
        assertTrue(errors.any { it.contains("Bruttobetrag") })
        assertTrue(errors.any { it.contains("Hauptkategorie") })
        assertTrue(errors.any { it.contains("Kontonummer") })
    }
}
