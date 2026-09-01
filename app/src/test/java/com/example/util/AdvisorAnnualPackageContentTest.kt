package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorAnnualPackageContentTest {
    @Test
    fun `creates ordered annual advisor files with Anlage V provenance`() {
        val summary = AdvisorAnnualSummary(
            year = 2026,
            propertyTitle = "Testobjekt",
            closingStatus = "PRÜFEN",
            manualApprovalCurrent = true,
            approvedAt = "2026-09-01T12:00:00+02:00",
            totalIncome = 24000.0,
            totalExpenses = 10000.0,
            result = 14000.0,
            propertyOverview = listOf("Kaufpreis 520.000 EUR", "7 Wohneinheiten"),
            financing = listOf("Darlehen A"),
            rentOverview = listOf("Soll/Ist geprüft"),
            renovationsAndAfa = listOf("AfA-Basis geprüft", "15-%-Monitor aktiv"),
            incomeValues = listOf(AdvisorAnnualValue("Mieten", 24000.0, "24 Belege", "OK")),
            expenseValues = listOf(AdvisorAnnualValue("Schuldzinsen", 10000.0, "Darlehenszuordnung", "PRÜFEN", "Anteil prüfen")),
            openIssues = listOf("Gelber Prüfpunkt")
        )

        val entries = AdvisorAnnualPackageContentBuilder.buildEntries(summary)

        assertEquals("05_Jahresabschluss/00_Jahresuebersicht.txt", entries.keys.first())
        assertTrue(entries.containsKey("05_Jahresabschluss/05_Anlage_V_Vorschau.csv"))
        assertTrue(entries.containsKey("05_Jahresabschluss/99_Pruefsummen_SHA256.txt"))
        val csv = entries.getValue("05_Jahresabschluss/05_Anlage_V_Vorschau.csv").toString(Charsets.UTF_8)
        assertTrue(csv.contains("\"Mieten\";24000,00;\"24 Belege\";\"OK\""))
        assertTrue(csv.contains("\"Schuldzinsen\";10000,00;\"Darlehenszuordnung\";\"PRÜFEN\""))
        val checksums = entries.getValue("05_Jahresabschluss/99_Pruefsummen_SHA256.txt").toString(Charsets.UTF_8)
        assertTrue(checksums.contains("05_Jahresabschluss/00_Jahresuebersicht.txt"))
        assertTrue(checksums.contains("05_Jahresabschluss/06_Offene_Pruefpunkte.txt"))
    }
}
