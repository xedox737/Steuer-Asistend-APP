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
    val dataFingerprint: String = "",
    val approvedFingerprint: String = "",
    val criticalAnnualIssues: Int = 0,
    val criticalClosingChecks: Int = 0,
    val missingRequiredOriginals: List<String> = emptyList(),
    val totalIncome: Double,
    val totalExpenses: Double,
    val result: Double,
    val propertyOverview: List<String> = emptyList(),
    val financing: List<String> = emptyList(),
    val rentOverview: List<String> = emptyList(),
    val renovationsAndAfa: List<String> = emptyList(),
    val incomeValues: List<AdvisorAnnualValue> = emptyList(),
    val expenseValues: List<AdvisorAnnualValue> = emptyList(),
    val openIssues: List<String> = emptyList(),
    val attachedOriginalDocuments: List<String> = emptyList()
)

data class AdvisorPackageReadiness(
    val ready: Boolean,
    val status: String,
    val blockers: List<String>
)

object AdvisorPackageReadinessEvaluator {
    fun evaluate(summary: AdvisorAnnualSummary): AdvisorPackageReadiness {
        val blockers = buildList {
            if (summary.criticalAnnualIssues > 0) {
                add("${summary.criticalAnnualIssues} kritische Jahresprüfpunkte sind offen.")
            }
            if (summary.criticalClosingChecks > 0) {
                add("${summary.criticalClosingChecks} kritische Abschlusschecks sind offen.")
            }
            if (!summary.manualApprovalCurrent) {
                add("Die manuelle Jahresfreigabe fehlt oder ist für den aktuellen Datenstand veraltet.")
            }
            if (summary.dataFingerprint.isNotBlank() &&
                summary.approvedFingerprint != summary.dataFingerprint
            ) {
                add("Der freigegebene Fingerprint stimmt nicht mit den aktuellen Jahresdaten überein.")
            }
            if (summary.missingRequiredOriginals.isNotEmpty()) {
                add("Erforderliche Originalunterlagen fehlen: " +
                    summary.missingRequiredOriginals.joinToString())
            }
        }
        return AdvisorPackageReadiness(
            ready = blockers.isEmpty(),
            status = if (blockers.isEmpty()) "BEREIT FÜR STEUERBERATER" else "NICHT BEREIT",
            blockers = blockers
        )
    }
}

object AdvisorPackageStructure {
    val requiredFolders = listOf(
        "00_Start",
        "01_DATEV",
        "02_Originalbelege",
        "03_Anlage_V",
        "04_Mieten",
        "05_Finanzierung",
        "06_AfA_und_15Prozent",
        "07_Sanierungen",
        "08_Pruefprotokoll"
    )
}

data class AdvisorPackageStructureValidation(
    val valid: Boolean,
    val errors: List<String>
)

object AdvisorPackageStructureValidator {
    fun validateEntryNames(entryNames: List<String>): AdvisorPackageStructureValidation {
        val errors = mutableListOf<String>()
        AdvisorPackageStructure.requiredFolders.forEach { folder ->
            if (entryNames.none { it == "$folder/" || it.startsWith("$folder/") }) {
                errors += "Pflichtordner fehlt: $folder"
            }
        }
        val originals = entryNames.filter { it.startsWith("02_Originalbelege/") && !it.endsWith("/") }
        val duplicateOriginalNames = originals.groupingBy { it.substringAfterLast('/') }
            .eachCount().filterValues { it > 1 }.keys
        if (duplicateOriginalNames.isNotEmpty()) {
            errors += "Doppelte Originalbelege: ${duplicateOriginalNames.joinToString()}"
        }
        return AdvisorPackageStructureValidation(errors.isEmpty(), errors)
    }
}

object AdvisorAnnualPackageContentBuilder {
    fun buildEntries(summary: AdvisorAnnualSummary): LinkedHashMap<String, ByteArray> {
        val readiness = AdvisorPackageReadinessEvaluator.evaluate(summary)
        val entries = linkedMapOf<String, ByteArray>()

        entries["00_Start/README.txt"] = startReadme(summary, readiness).bytes()
        entries["03_Anlage_V/01_Anlage_V_Vorschau.csv"] = anlageVCsv(summary).bytes()
        entries["03_Anlage_V/02_Werteherkunft_und_Pruefstatus.csv"] = provenanceCsv(summary).bytes()
        entries["04_Mieten/01_Mietpruefung.txt"] =
            section("MIETEN / JAHRES-MIETPRÜFUNG", summary.rentOverview).bytes()
        entries["05_Finanzierung/01_Finanzierung.txt"] =
            section("FINANZIERUNG / SCHULDZINSEN", summary.financing).bytes()
        entries["06_AfA_und_15Prozent/01_AfA_und_15Prozent.txt"] =
            section("AfA / 15-%-MONITOR", summary.renovationsAndAfa).bytes()
        entries["07_Sanierungen/01_Sanierungen.txt"] =
            section("SANIERUNGEN", summary.renovationsAndAfa).bytes()
        entries["08_Pruefprotokoll/01_Offene_Pruefpunkte.txt"] =
            section("OFFENE PRÜFPUNKTE", summary.openIssues.ifEmpty {
                listOf("Keine offenen Prüfpunkte erfasst.")
            }).bytes()
        entries["08_Pruefprotokoll/02_Abschluss_und_Freigabe.txt"] =
            readinessReport(summary, readiness).bytes()
        entries["08_Pruefprotokoll/03_Beigefuegte_Originalunterlagen.txt"] =
            section("BEIGEFÜGTE ORIGINALUNTERLAGEN", summary.attachedOriginalDocuments.ifEmpty {
                listOf("Keine zusätzlichen Originalunterlagen angegeben.")
            }).bytes()

        val checksumText = entries.entries.joinToString("\n") { (path, bytes) ->
            "${sha256(bytes)}  $path"
        } + "\n"
        entries["08_Pruefprotokoll/99_Pruefsummen_SHA256.txt"] = checksumText.bytes()
        return entries
    }

    private fun startReadme(
        summary: AdvisorAnnualSummary,
        readiness: AdvisorPackageReadiness
    ): String = buildString {
        appendLine("STEUERBERATER-JAHRESABSCHLUSSPAKET ${summary.year}")
        appendLine("============================================================")
        appendLine("Objekt: ${summary.propertyTitle}")
        appendLine("Status: ${readiness.status}")
        appendLine()
        appendLine("Dieses Paket ist eine Vorbereitungshilfe und ersetzt keine Steuerberatung.")
        appendLine("Es werden keine festen ELSTER-Zeilennummern vorgegeben.")
        appendLine()
        appendLine("Reihenfolge des Startdokuments:")
        appendLine("1. Objektübersicht")
        summary.propertyOverview.forEach { appendLine("   - $it") }
        appendLine("2. Finanzierung")
        summary.financing.forEach { appendLine("   - $it") }
        appendLine("3. Mieten")
        summary.rentOverview.forEach { appendLine("   - $it") }
        appendLine("4. Sanierungen")
        summary.renovationsAndAfa.forEach { appendLine("   - $it") }
        appendLine("5. AfA / 15-%-Monitor")
        appendLine("6. Werbungskosten: ${money(summary.totalExpenses)} EUR")
        appendLine("7. Offene Prüfhinweise: ${summary.openIssues.size}")
        appendLine("8. Beigefügte Originalunterlagen: ${summary.attachedOriginalDocuments.size}")
        appendLine()
        appendLine("Einnahmen: ${money(summary.totalIncome)} EUR")
        appendLine("Werbungskosten: ${money(summary.totalExpenses)} EUR")
        appendLine("Vorläufiges Ergebnis: ${money(summary.result)} EUR")
    }

    private fun readinessReport(
        summary: AdvisorAnnualSummary,
        readiness: AdvisorPackageReadiness
    ): String = buildString {
        appendLine("ABSCHLUSS- UND FREIGABEPRÜFUNG")
        appendLine("================================")
        appendLine("Status: ${readiness.status}")
        appendLine("Manuelle Jahresfreigabe: " +
            if (summary.manualApprovalCurrent) "AKTUELL" else "FEHLT/VERALTET")
        if (summary.approvedAt.isNotBlank()) appendLine("Freigegeben am: ${summary.approvedAt}")
        appendLine("Aktueller Fingerprint: ${summary.dataFingerprint.ifBlank { "nicht übergeben" }}")
        appendLine("Freigegebener Fingerprint: ${summary.approvedFingerprint.ifBlank { "nicht übergeben" }}")
        if (readiness.blockers.isEmpty()) {
            appendLine("Keine blockierenden Prüfpunkte.")
        } else {
            appendLine("Blockierende Prüfpunkte:")
            readiness.blockers.forEach { appendLine("- $it") }
        }
    }

    private fun provenanceCsv(summary: AdvisorAnnualSummary): String = buildString {
        appendLine("Bereich;Position;Betrag_EUR;Quelle;Pruefstatus;Hinweis")
        summary.incomeValues.forEach { appendValue("Einnahmen", it) }
        summary.expenseValues.forEach { appendValue("Werbungskosten", it) }
    }

    private fun anlageVCsv(summary: AdvisorAnnualSummary): String = buildString {
        appendLine("Bereich;Position;Betrag_EUR;Quelle;Pruefstatus;Hinweis")
        summary.incomeValues.forEach { appendValue("Einnahmen", it) }
        summary.expenseValues.forEach { appendValue("Werbungskosten", it) }
        appendLine("Ergebnis;${csv("Vorlaeufiger Ueberschuss/Verlust")};${csvMoney(summary.result)};" +
            "${csv("Jahresassistent")};${csv(summary.closingStatus)};" +
            "${csv("Keine festen ELSTER-Zeilennummern; steuerlich prüfen")}")
    }

    private fun section(title: String, lines: List<String>): String = buildString {
        appendLine(title)
        appendLine("=".repeat(title.length.coerceAtLeast(12)))
        if (lines.isEmpty()) appendLine("Keine Angaben vorhanden.")
        else lines.forEach { appendLine("- $it") }
    }

    private fun StringBuilder.appendValue(area: String, value: AdvisorAnnualValue) {
        append(csv(area)).append(';')
            .append(csv(value.label)).append(';')
            .append(csvMoney(value.amount)).append(';')
            .append(csv(value.source)).append(';')
            .append(csv(value.checkStatus)).append(';')
            .append(csv(value.note)).append('\n')
    }

    private fun String.bytes() = toByteArray(Charsets.UTF_8)
    private fun csv(value: String): String =
        "\"" + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""
    private fun money(value: Double): String = String.format(Locale.GERMANY, "%.2f", value)
    private fun csvMoney(value: Double): String = String.format(Locale.GERMANY, "%.2f", value)
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
