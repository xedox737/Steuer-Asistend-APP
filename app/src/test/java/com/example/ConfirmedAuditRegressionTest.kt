package com.example

import com.example.data.PropertyMetadata
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ConfirmedAuditRegressionTest {
    private fun source(path: String): String {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Quelle fehlt: $path")
    }

    @Test fun freshInstallContainsNoProductiveDemoSeedData() {
        val database = source("src/main/java/com/example/data/ReceiptDatabase.kt")
        listOf(
            "Mieter Hans Peter",
            "Erika Mustermann",
            "Sparkasse Musterstadt",
            "Finanzamt Musterstadt",
            "Notar Dr. Joachim Müller"
        ).forEach { marker ->
            assertFalse("Produktive Datenbank enthält Demo-Daten: $marker", database.contains(marker))
        }
        assertFalse("Fresh install darf keine fachlichen Datensätze automatisch anlegen",
            database.contains("populateDatabase("))
    }

    @Test fun propertyDefaultsAreNeutralAndNeverPretendToBeUserData() {
        val metadata = PropertyMetadata()
        assertEquals("", metadata.name)
        assertEquals("", metadata.adresse)
        assertEquals("", metadata.wohnort)
        assertEquals(0, metadata.baujahr)
        assertEquals(0.0, metadata.wohnflaeche, 0.0)
        assertEquals(0.0, metadata.grundstuecksgroesse, 0.0)
        assertEquals("", metadata.notariellesKaufdatum)
        assertEquals("", metadata.uebergangNutzenLasten)
        assertEquals("", metadata.wohneinheiten)
        assertEquals(0.0, metadata.gesamtKaufpreis, 0.0)
        assertEquals(0.0, metadata.gebaeudewert, 0.0)
        assertEquals(0.0, metadata.grundUndBodenWert, 0.0)
    }

    @Test fun receiptAiUsesNoHardcodedPropertyOrCalendarFacts() {
        val gemini = source("src/main/java/com/example/api/GeminiClient.kt")
        assertFalse(gemini.contains("7-Familienhauses"))
        assertFalse(gemini.contains("Notarieller Kaufvertrag: 01.10.2025"))
        assertFalse(gemini.contains("Übergang von Nutzen und Lasten: 01.01.2026"))
        assertFalse(gemini.contains("2026-07-14"))
        assertFalse(gemini.contains("Belegdatum zwischen 2025-10-01"))
    }

    @Test fun productionViewModelContainsNoPersonalDriveFallbackAndPropertyDeletionKeepsIdentity() {
        val viewModel = source("src/main/java/com/example/ui/ReceiptViewModel.kt")
        assertFalse("Keine personenbezogene Default-Mailadresse im Produktivcode", viewModel.contains("@gmail.com"))
        assertFalse("Keine Demo-Mieter in produktiven Unit-Defaults", viewModel.contains("Hans Peter") || viewModel.contains("Erika Mustermann"))
        assertFalse("Keine erfundene Default-Lage für KI-Funktionen", viewModel.contains("München / Deutschland"))
        val deleteFunction = viewModel.substringAfter("fun deleteProperty(").substringBefore("\n    }")
        assertFalse("Immobilien dürfen nicht physisch aus der Identitätstabelle gelöscht werden",
            deleteFunction.contains("deletePropertyByPropertyId"))
        assertTrue("Löschen muss als Archivierung umgesetzt sein",
            deleteFunction.contains("status = \"Archiviert\""))
    }

    @Test fun datevAndAuditDefaultsContainNoPersonalOrPropertyData() {
        val profile = com.example.data.DatevProfile()
        val audit = com.example.data.ExportAuditRun("test")
        val config = com.example.util.DatevConfig()
        assertEquals("", profile.beraterNummer)
        assertEquals("", profile.mandantenNummer)
        assertEquals("", profile.mandantenName)
        assertEquals("", audit.user)
        assertEquals("", config.beraterNummer)
        assertEquals("", config.mandantenNummer)
        assertEquals("", config.mandantenName)
        assertEquals("", config.propertyName)
        assertEquals("", config.propertyShort)

        listOf(
            source("src/main/java/com/example/data/ExportAuditRun.kt"),
            source("src/main/java/com/example/data/DatevProfile.kt"),
            source("src/main/java/com/example/util/DatevExporter.kt"),
            source("src/main/java/com/example/ui/ReceiptViewModel.kt")
        ).forEach { productionSource ->
            assertFalse(productionSource.contains("Gerweck"))
            assertFalse(productionSource.contains("Sulzerstraße"))
        }
    }

    @Test fun geminiSecretsCannotEnterBuildConfig() {
        val gemini = source("src/main/java/com/example/api/GeminiClient.kt")
        val settings = source("src/main/java/com/example/api/AiProviderSettings.kt")
        val build = source("build.gradle.kts")
        assertFalse(gemini.contains("BuildConfig.GEMINI_API_KEY"))
        assertFalse(settings.contains("BuildConfig.GEMINI_API_KEY"))
        assertTrue(build.contains("ignoreList.add(\"GEMINI_API_KEY\")"))
    }
}
