package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorAnnualPackageContentTest {
    private fun readySummary() = AdvisorAnnualSummary(
        year = 2026,
        propertyTitle = "Testobjekt",
        closingStatus = "OK",
        manualApprovalCurrent = true,
        approvedAt = "2026-09-01T12:00:00+02:00",
        dataFingerprint = "abc",
        approvedFingerprint = "abc",
        totalIncome = 24000.0,
        totalExpenses = 10000.0,
        result = 14000.0,
        propertyOverview = listOf("Kaufpreis 520.000 EUR", "7 Wohneinheiten"),
        financing = listOf("Darlehen A"),
        rentOverview = listOf("Soll/Ist geprüft"),
        renovationsAndAfa = listOf("AfA-Basis geprüft", "15-%-Monitor aktiv"),
        incomeValues = listOf(AdvisorAnnualValue("Mieten", 24000.0, "24 Belege", "OK")),
        expenseValues = listOf(
            AdvisorAnnualValue(
                "Schuldzinsen",
                10000.0,
                "Darlehenszuordnung",
                "PRÜFEN",
                "Anteil prüfen"
            )
        ),
        attachedOriginalDocuments = listOf("BELEG-1")
    )

    @Test
    fun `creates ordered advisor files with Anlage V provenance`() {
        val entries = AdvisorAnnualPackageContentBuilder.buildEntries(readySummary())

        assertEquals("00_Start/README.txt", entries.keys.first())
        assertTrue(entries.containsKey("03_Anlage_V/01_Anlage_V_Vorschau.csv"))
        assertTrue(entries.containsKey("03_Anlage_V/02_Werteherkunft_und_Pruefstatus.csv"))
        assertTrue(entries.containsKey("08_Pruefprotokoll/99_Pruefsummen_SHA256.txt"))
        val csv = entries.getValue("03_Anlage_V/01_Anlage_V_Vorschau.csv")
            .toString(Charsets.UTF_8)
        assertTrue(csv.contains("\"Mieten\";24000,00;\"24 Belege\";\"OK\""))
        assertTrue(csv.contains("\"Schuldzinsen\";10000,00;\"Darlehenszuordnung\";\"PRÜFEN\""))
        val checksums = entries.getValue("08_Pruefprotokoll/99_Pruefsummen_SHA256.txt")
            .toString(Charsets.UTF_8)
        assertTrue(checksums.contains("00_Start/README.txt"))
        assertTrue(checksums.contains("08_Pruefprotokoll/01_Offene_Pruefpunkte.txt"))
    }

    @Test
    fun `readiness expires for changed fingerprint`() {
        val readiness = AdvisorPackageReadinessEvaluator.evaluate(
            readySummary().copy(dataFingerprint = "new")
        )

        assertFalse(readiness.ready)
        assertEquals("NICHT BEREIT", readiness.status)
        assertTrue(readiness.blockers.any { it.contains("Fingerprint") })
    }

    @Test
    fun `readiness blocks critical checks and missing originals`() {
        val readiness = AdvisorPackageReadinessEvaluator.evaluate(
            readySummary().copy(
                criticalAnnualIssues = 1,
                criticalClosingChecks = 2,
                missingRequiredOriginals = listOf("BELEG-2")
            )
        )

        assertFalse(readiness.ready)
        assertEquals(3, readiness.blockers.size)
    }

    @Test
    fun `validates all nine mandatory folders`() {
        val names = AdvisorPackageStructure.requiredFolders.map { "$it/" } +
            listOf("02_Originalbelege/BELEG-1.pdf")

        val validation = AdvisorPackageStructureValidator.validateEntryNames(names)

        assertTrue(validation.errors.joinToString(), validation.valid)
    }

    @Test
    fun `rejects missing folder`() {
        val validation = AdvisorPackageStructureValidator.validateEntryNames(
            AdvisorPackageStructure.requiredFolders
                .filterNot { it == "06_AfA_und_15Prozent" }
                .map { "$it/" }
        )

        assertFalse(validation.valid)
        assertTrue(validation.errors.any { it.contains("06_AfA_und_15Prozent") })
    }
}
