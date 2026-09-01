package com.example.util

import java.security.MessageDigest
import java.util.Locale

data class AdvisorAnnualValue(
    val label: String,
    val amount: Double,
    val source: String,
    val checkStatus: String,
    val note: String = ""
)

data class AdvisorAnnualSummary(
    val year: Int,
    val propertyTitle: String,
    val closingStatus: String,
    val manualApprovalCurrent: Boolean,
    val approvedAt: String = "",
    val totalIncome: Double,
    val totalExpenses: Double,
    val result: Double,
    val propertyOverview: List<String> = emptyList(),
    val financing: List<String> = emptyList(),
    val rentOverview: List<String> = emptyList(),
    val renovationsAndAfa: List<String> = emptyList(),
    val incomeValues: List<AdvisorAnnualValue> = emptyList(),
    val expenseValues: List<AdvisorAnnualValue> = emptyList(),
    val openIssues: List<String> = emptyList()
)

object AdvisorAnnualPackageContentBuilder {
    private const val ROOT = "05_Jahresabschluss"

    fun buildEntries(summary: AdvisorAnnualSummary): LinkedHashMap<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        entries["$ROOT/00_Jahresuebersicht.txt"] = overview(summary).toByteArray(Charsets.UTF_8)
        entries["$ROOT/01_Objektuebersicht.txt"] = section("OBJEKTÜBERSICHT", summary.propertyOverview).toByteArray(Charsets.UTF_8)
        entries["$ROOT/02_Finanzierung.txt"] = section("FINANZIERUNG", summary.financing).toByteArray(Charsets.UTF_8)
        entries["$ROOT/03_Mieten.txt"] = section("MIETEN", summary.rentOverview).toByteArray(Charsets.UTF_8)
        entries["$ROOT/04_Sanierungen_AfA_15Prozent.txt"] = section("SANIERUNGEN / AfA / 15-%-MONITOR", summary.renovationsAndAfa).toByteArray(Charsets.UTF_8)
        entries["$ROOT/05_Anlage_V_Vorschau.csv"] = AnlageVCsv(summary).toByteArray(Charsets.UTF_8)
        entries["$ROOT/06_Offene_Pruefpunkte.txt"] = section("OFFENE PRÜFPUNKTE", summary.openIssues.ifEmpty { listOf("Keine offenen Prüfpunkte erfasst.") }).toByteArray(Charsets.UTF_8)

        val checksumText = entries.entries.joinToString("\n") { (path, bytes) ->
            "${sha256(bytes)}  $path"
        } + "\n"
        entries["$ROOT/99_Pruefsummen_SHA256.txt"] = checksumText.toByteArray(Charsets.UTF_8)
        return entries
    }

    private fun overview(summary: AdvisorAnnualSummary): String = buildString {
        appendLine("STEUERBERATER-JAHRESÜBERSICHT ${summary.year}")
        appendLine("============================================================")
        appendLine("Objekt: ${summary.propertyTitle}")
        appendLine("Abschlussstatus: ${summary.closingStatus}")
        appendLine("Manuelle Jahresfreigabe: ${if (summary.manualApprovalCurrent) "AKTUELL" else "NICHT AKTUELL"}")
        if (summary.approvedAt.isNotBlank()) appendLine("Freigegeben am: ${summary.approvedAt}")
        appendLine()
        appendLine("Einnahmen: ${money(summary.totalIncome)} EUR")
        appendLine("Werbungskosten: ${money(summary.totalExpenses)} EUR")
        appendLine("Vorläufiges Ergebnis: ${money(summary.result)} EUR")
        appendLine()
        appendLine("Hinweis: Dieses Paket bereitet Unterlagen für Steuerberater/Anlage V vor und ersetzt keine steuerliche Prüfung.")
    }

    private fun section(title: String, lines: List<String>): String = buildString {
        appendLine(title)
        appendLine("=".repeat(title.length.coerceAtLeast(12)))
        if (lines.isEmpty()) appendLine("Keine Angaben vorhanden.") else lines.forEach { appendLine("- $it") }
    }

    private fun AnlageVCsv(summary: AdvisorAnnualSummary): String = buildString {
        appendLine("Bereich;Position;Betrag_EUR;Quelle;Pruefstatus;Hinweis")
        summary.incomeValues.forEach { appendValue("Einnahmen", it) }
        summary.expenseValues.forEach { appendValue("Werbungskosten", it) }
        appendLine("Ergebnis;Vorlaeufiger_Ueberschuss_Verlußt;${csvMoney(summary.result)};Jahresassistent;${csv(summary.closingStatus)};${csv(if (summary.manualApprovalCurrent) "Manuelle Jahresfreigabe aktuell" else "Manuelle Jahresfreigabe fehlt oder ist veraltet")}")
    }

    private fun StringBuilder.appendValue(area: String, value: AdvisorAnnualValue) {
        append(area).append(';')
            .append(csv(value.label)).append(';')
            .append(csvMoney(value.amount)).append(';')
            .append(csv(value.source)).append(';')
            .append(csv(value.checkStatus)).append(';')
            .append(csv(value.note)).append('\n')
    }

    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""
    private fun money(value: Double): String = String.format(Locale.GERMANY, "%.2f", value)
    private fun csvMoney(value: Double): String = String.format(Locale.GERMANY, "%.2f", value)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
