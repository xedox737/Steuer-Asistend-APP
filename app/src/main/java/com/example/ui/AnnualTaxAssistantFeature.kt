package com.example.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Loan
import com.example.data.PropertyMetadata
import com.example.data.Receipt
import com.example.data.TaxPropertyCalculator
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs

private enum class TaxIssueSeverity { RED, YELLOW }
private enum class ClosingCheckState { OK, REVIEW, BLOCKED }

private data class TaxIssue(
    val title: String,
    val message: String,
    val severity: TaxIssueSeverity,
    val receipts: List<Receipt> = emptyList()
)

private data class TaxBucket(
    val title: String,
    val amount: Double,
    val receipts: List<Receipt> = emptyList(),
    val note: String = "",
    val warning: Boolean = false
)

private data class AnnualTaxSummary(
    val year: Int,
    val totalIncome: Double,
    val totalExpenses: Double,
    val result: Double,
    val incomeBuckets: List<TaxBucket>,
    val expenseBuckets: List<TaxBucket>,
    val issues: List<TaxIssue>
) {
    val redCount: Int get() = issues.count { it.severity == TaxIssueSeverity.RED }
    val yellowCount: Int get() = issues.count { it.severity == TaxIssueSeverity.YELLOW }
}

private data class UnitAnnualSummary(
    val name: String,
    val label: String,
    val income: Double,
    val expenses: Double,
    val receipts: List<Receipt>
) {
    val result: Double get() = income - expenses
}

private data class AnnualRentRow(
    val unit: WohneinheitStatus,
    val expected: Double,
    val actual: Double,
    val missingMonths: List<Int>,
    val receipts: List<Receipt>
) {
    val difference: Double get() = actual - expected
    val missing: Double get() = (expected - actual).coerceAtLeast(0.0)
}

private data class AnnualClosingCheck(
    val title: String,
    val detail: String,
    val state: ClosingCheckState
)

private data class AnlageVPreviewValue(
    val label: String,
    val amount: Double,
    val source: String,
    val checkStatus: String,
    val note: String = ""
)

private const val ANNUAL_APPROVAL_PREFS = "annual_tax_approval_prefs"

private fun annualApprovalFingerprint(
    summary: AnnualTaxSummary,
    closingChecks: List<AnnualClosingCheck>,
    rentRows: List<AnnualRentRow>,
    receipts: List<Receipt>,
    metadata: PropertyMetadata,
    loans: List<Loan>
): String {
    val canonical = buildString {
        append("year=").append(summary.year).append('\n')
        append("income=").append(summary.totalIncome).append('\n')
        append("expenses=").append(summary.totalExpenses).append('\n')
        append("result=").append(summary.result).append('\n')
        summary.incomeBuckets.sortedBy { it.title }.forEach {
            append("I|").append(it.title).append('|').append(it.amount).append('\n')
        }
        summary.expenseBuckets.sortedBy { it.title }.forEach {
            append("E|").append(it.title).append('|').append(it.amount).append('|').append(it.warning).append('\n')
        }
        summary.issues.sortedWith(compareBy<TaxIssue> { it.title }.thenBy { it.severity.name }).forEach {
            append("ISSUE|").append(it.severity.name).append('|').append(it.title).append('|').append(it.receipts.size).append('\n')
        }
        closingChecks.sortedBy { it.title }.forEach {
            append("CHECK|").append(it.title).append('|').append(it.state.name).append('|').append(it.detail).append('\n')
        }
        rentRows.sortedBy { it.unit.name }.forEach {
            append("RENT|").append(it.unit.name).append('|').append(it.expected).append('|').append(it.actual)
                .append('|').append(it.missingMonths.joinToString(",")).append('\n')
        }
        receipts.filter { it.yearOrNull() == summary.year }
            .sortedWith(compareBy<Receipt> { it.internalId }.thenBy { it.id })
            .forEach { r ->
                append("R|").append(r.internalId).append('|').append(r.id).append('|').append(r.datum)
                    .append('|').append(r.bruttobetrag).append('|').append(r.hauptkategorie)
                    .append('|').append(r.unterkategorie).append('|').append(r.beschreibung)
                    .append('|').append(r.wohneinheit).append('|').append(r.freigabestatus)
                    .append('|').append(r.exportStatus).append('|').append(r.pruefstatus)
                    .append('|').append(r.syncStatus).append('\n')
            }
        append("META|").append(metadata.toString()).append('\n')
        loans.sortedBy { it.id }.forEach { append("LOAN|").append(it.toString()).append('\n') }
    }
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}

private fun Receipt.yearOrNull(): Int? = datum.take(4).toIntOrNull()

private fun receiptText(r: Receipt): String =
    (r.hauptkategorie + " " + r.unterkategorie + " " + r.beschreibung + " " + r.kontoNr).lowercase()

private fun isDeposit(r: Receipt): Boolean = receiptText(r).contains("kaution")

private fun isRentalIncome(r: Receipt): Boolean {
    if (isDeposit(r)) return false
    if (r.hauptkategorie == "Miete, Nebenkosten & Kaution") return true
    if (r.hauptkategorie == "Sonstige Einnahmen") {
        val text = receiptText(r)
        return text.contains("miete") || text.contains("betriebskosten") || text.contains("nebenkosten")
    }
    return false
}

private fun isPrincipalOrLoanFlow(r: Receipt): Boolean {
    val text = receiptText(r)
    return text.contains("tilgung") || text.contains("kreditrate") || text.contains("darlehensrate") ||
        text.contains("kreditauszahlung") || text.contains("sondertilgung")
}

private fun isInterest(r: Receipt): Boolean =
    r.hauptkategorie == "Finanzierung, Kredite & Versicherungen" &&
        (r.unterkategorie.equals("Kreditzinsen", true) ||
            receiptText(r).contains("sollzins") || receiptText(r).contains("schuldzins"))

private fun afaForYear(metadata: PropertyMetadata, all: List<Receipt>, year: Int): Double {
    val phase1 = TaxPropertyCalculator.calculate(metadata, all)
    val startYear = phase1.afaStartDate.take(4).toIntOrNull() ?: return 0.0
    return when {
        year < startYear -> 0.0
        year == startYear -> phase1.firstYearAfa
        else -> phase1.annualAfa
    }
}

private fun classifyGenericExpense(r: Receipt): String {
    val text = receiptText(r)
    return when {
        text.contains("fahrt") || text.contains("kilometer") || text.contains("fahrtenbuch") -> "Fahrtkosten"
        text.contains("versicherung") || text.contains("rechtsschutz") -> "Versicherungen"
        text.contains("verwaltung") || text.contains("steuerberater") || text.contains("buchhaltung") ||
            text.contains("kontoführung") || text.contains("kontofuehrung") -> "Verwaltung & Beratung"
        text.contains("grundsteuer") || text.contains("wasser") || text.contains("abwasser") ||
            text.contains("müll") || text.contains("muell") || text.contains("strom") ||
            text.contains("heizung") || text.contains("schornstein") -> "Laufende Objektkosten"
        else -> "Sonstige Werbungskosten"
    }
}

private fun buildUnitAnnualSummaries(
    year: Int,
    all: List<Receipt>,
    units: List<WohneinheitStatus>
): List<UnitAnnualSummary> {
    val yearReceipts = all.filter { it.yearOrNull() == year }
    return units.map { unit ->
        val direct = yearReceipts.filter { it.wohneinheit == unit.name }
        val income = direct.filter(::isRentalIncome).sumOf { it.bruttobetrag }
        val expenses = direct.filter {
            !isRentalIncome(it) && it.hauptkategorie != "Anschaffungskosten" && !isPrincipalOrLoanFlow(it)
        }.sumOf { it.bruttobetrag }
        UnitAnnualSummary(unit.name, unit.label, income, expenses, direct)
    }.filter { it.receipts.isNotEmpty() || it.income != 0.0 || it.expenses != 0.0 }
}

private fun fallbackTenantPeriod(unit: WohneinheitStatus, nk: Double, other: Double): TenantPeriod? {
    if (unit.status != "Vermietet") return null
    if (unit.mieter.isBlank() && unit.kaltmiete <= 0.0 && nk <= 0.0 && other <= 0.0) return null
    return TenantPeriod(
        -unit.name.hashCode().toLong(),
        unit.name,
        unit.mieter,
        unit.mietvertragsstart,
        "",
        unit.kaltmiete,
        nk,
        other
    )
}

private fun expectedInMonth(period: TenantPeriod, month: YearMonth): Double {
    val start = runCatching { LocalDate.parse(period.startDate) }.getOrNull() ?: month.atDay(1)
    val end = runCatching { LocalDate.parse(period.endDate) }.getOrNull() ?: month.atEndOfMonth()
    val from = maxOf(start, month.atDay(1))
    val to = minOf(end, month.atEndOfMonth())
    if (to.isBefore(from)) return 0.0
    val days = java.time.temporal.ChronoUnit.DAYS.between(from, to).toDouble() + 1.0
    return period.monatSoll * days / month.lengthOfMonth().toDouble()
}

private fun buildAnnualRentRows(
    context: Context,
    year: Int,
    all: List<Receipt>,
    units: List<WohneinheitStatus>
): List<AnnualRentRow> {
    val rentPrefs = context.getSharedPreferences("rent_plan_prefs", Context.MODE_PRIVATE)
    return units.map { unit ->
        val nk = rentPrefs.getFloat("nk_${unit.name}", 0f).toDouble()
        val other = rentPrefs.getFloat("other_${unit.name}", 0f).toDouble()
        val stored = TenantHistoryStore.load(context, unit.name)
        val periods = if (stored.isNotEmpty()) stored else listOfNotNull(fallbackTenantPeriod(unit, nk, other))
        val expected = TenantHistoryStore.expectedForYear(periods, year)
        val unitReceipts = all.filter {
            it.yearOrNull() == year && it.wohneinheit == unit.name && isRentalIncome(it)
        }
        val actual = unitReceipts.sumOf { it.bruttobetrag }
        val missingMonths = (1..12).filter { monthNo ->
            val month = YearMonth.of(year, monthNo)
            val monthExpected = periods.sumOf { expectedInMonth(it, month) }
            val monthActual = unitReceipts.filter { it.datum.take(7) == month.toString() }.sumOf { it.bruttobetrag }
            monthExpected > 0.01 && monthActual + 0.01 < monthExpected
        }
        AnnualRentRow(unit, expected, actual, missingMonths, unitReceipts)
    }.filter { it.expected > 0.01 || it.actual > 0.01 }
}

private fun buildAnnualTaxSummary(
    context: Context,
    year: Int,
    all: List<Receipt>,
    metadata: PropertyMetadata,
    loans: List<Loan>
): AnnualTaxSummary {
    val receipts = all.filter { it.yearOrNull() == year }
    val phase1 = TaxPropertyCalculator.calculate(metadata, all)
    val rentalReceipts = receipts.filter(::isRentalIncome)
    val regularRental = rentalReceipts.filter { it.hauptkategorie == "Miete, Nebenkosten & Kaution" }
    val otherRental = rentalReceipts.filter { it.hauptkategorie == "Sonstige Einnahmen" }

    val assignmentPrefs = context.getSharedPreferences("loan_interest_assignments", Context.MODE_PRIVATE)
    val loansById = loans.associateBy { it.id }
    val interestReceipts = receipts.filter(::isInterest)
    val assignedInterest = interestReceipts.mapNotNull { r ->
        loansById[assignmentPrefs.getInt("receipt_${r.id}", 0)]?.let { r to it }
    }
    val debtInterest = assignedInterest.sumOf { (r, loan) ->
        r.bruttobetrag * loan.vermietungsanteilProzent.coerceIn(0.0, 100.0) / 100.0
    }

    val afa = afaForYear(metadata, all, year)
    val renovationReceipts = receipts.filter {
        it.hauptkategorie == "Renovierungs- / Reparaturkosten & Investitionen"
    }
    val inMonitor = phase1.monitorStartDate.take(4).toIntOrNull()?.let { start ->
        phase1.monitorEndDate.take(4).toIntOrNull()?.let { end -> year in start..end }
    } ?: false
    val renovationBlocked = phase1.is15PercentExceeded && inMonitor
    val renovationDeductible = if (renovationBlocked) 0.0 else renovationReceipts.sumOf { it.bruttobetrag }

    val otherFinancing = receipts.filter {
        it.hauptkategorie == "Finanzierung, Kredite & Versicherungen" &&
            !isInterest(it) && !isPrincipalOrLoanFlow(it)
    }
    val generic = receipts.filter {
        !isRentalIncome(it) && it.hauptkategorie !in setOf(
            "Anschaffungskosten",
            "Finanzierung, Kredite & Versicherungen",
            "Renovierungs- / Reparaturkosten & Investitionen"
        )
    }
    val grouped = generic.groupBy(::classifyGenericExpense)

    val incomeBuckets = listOf(
        TaxBucket(
            "Mieten & umlagefähige Nebenkosten",
            regularRental.sumOf { it.bruttobetrag },
            regularRental,
            "Kautionen ausgeschlossen"
        ),
        TaxBucket(
            "Sonstige Einnahmen aus Vermietung",
            otherRental.sumOf { it.bruttobetrag },
            otherRental
        )
    ).filter { it.amount != 0.0 || it.receipts.isNotEmpty() }

    val expenseBuckets = mutableListOf(
        TaxBucket(
            "Schuldzinsen",
            debtInterest,
            assignedInterest.map { it.first },
            "Zugeordnete Zinsbelege × Vermietungsanteil"
        ),
        TaxBucket(
            "AfA Gebäude",
            afa,
            note = "Aus Objekt-Stammdaten und AfA-Bemessungsgrundlage"
        ),
        TaxBucket(
            "Erhaltungsaufwand / Reparaturen",
            renovationDeductible,
            renovationReceipts,
            if (renovationBlocked) "Nicht als Sofortaufwand angesetzt: 15-%-Prüfung erforderlich"
            else "Vorläufiger Sofortaufwand; steuerlich prüfen",
            renovationBlocked
        ),
        TaxBucket(
            "Weitere Finanzierungskosten",
            otherFinancing.sumOf { it.bruttobetrag },
            otherFinancing,
            "Tilgung und Darlehensauszahlung ausgeschlossen"
        )
    )
    listOf(
        "Verwaltung & Beratung",
        "Versicherungen",
        "Laufende Objektkosten",
        "Fahrtkosten",
        "Sonstige Werbungskosten"
    ).forEach { title ->
        grouped[title].orEmpty().takeIf { it.isNotEmpty() }?.let {
            expenseBuckets += TaxBucket(title, it.sumOf { r -> r.bruttobetrag }, it)
        }
    }

    val issues = mutableListOf<TaxIssue>()
    val unassignedInterest = interestReceipts.filter {
        loansById[assignmentPrefs.getInt("receipt_${it.id}", 0)] == null
    }
    if (unassignedInterest.isNotEmpty()) {
        issues += TaxIssue(
            "Schuldzinsen ohne Darlehenszuordnung",
            "${unassignedInterest.size} Zinsbeleg(e) ohne gültiges Darlehen.",
            TaxIssueSeverity.RED,
            unassignedInterest
        )
    }

    val rentWithoutUnit = rentalReceipts.filter {
        it.wohneinheit.isBlank() || it.wohneinheit == "Gesamtobjekt / Allgemein"
    }
    if (rentWithoutUnit.isNotEmpty()) {
        issues += TaxIssue(
            "Miete ohne Wohneinheit",
            "${rentWithoutUnit.size} Miet-/Nebenkostenbeleg(e) ohne konkrete Wohneinheit.",
            TaxIssueSeverity.YELLOW,
            rentWithoutUnit
        )
    }

    val open = receipts.filter { it.freigabestatus != "FREIGEGEBEN" }
    if (open.isNotEmpty()) {
        issues += TaxIssue(
            "Nicht freigegebene Belege",
            "${open.size} Beleg(e) noch nicht steuerlich freigegeben.",
            TaxIssueSeverity.YELLOW,
            open
        )
    }

    val review = receipts.filter {
        it.exportStatus == "ZU_PRUEFEN" || it.pruefstatus == "UNGEPRUEFT" || it.syncStatus == "REVIEW_REQUIRED"
    }
    if (review.isNotEmpty()) {
        issues += TaxIssue(
            "Belege mit Prüfstatus",
            "${review.size} Beleg(e) benötigen noch Prüfung.",
            TaxIssueSeverity.YELLOW,
            review
        )
    }

    if (phase1.allocationNeedsReview) {
        issues += TaxIssue(
            "Kaufpreisaufteilung prüfen",
            "Gebäude und Grund/Boden weichen um ${NumberFormatter.format(abs(phase1.allocationDifference))} vom Gesamtkaufpreis ab.",
            TaxIssueSeverity.RED
        )
    }
    if (phase1.is15PercentExceeded) {
        issues += TaxIssue(
            "15-%-Grenze überschritten",
            "Betroffene Sanierungskosten werden nicht automatisch als Sofortaufwand angesetzt.",
            TaxIssueSeverity.RED,
            renovationReceipts
        )
    } else if (phase1.limitUsagePercent >= 80.0) {
        issues += TaxIssue(
            "15-%-Grenze nähert sich",
            "Monitor: ${"%.1f".format(phase1.limitUsagePercent)} %.",
            TaxIssueSeverity.YELLOW,
            renovationReceipts
        )
    }

    if (phase1.estimatedNetCount > 0) {
        val ids = phase1.monitorDetails.filter { it.included && it.estimatedNet }.map { it.displayId }.toSet()
        issues += TaxIssue(
            "Nettobeträge im 15-%-Monitor geschätzt",
            "Bei ${phase1.estimatedNetCount} Beleg(en) wurde netto geschätzt.",
            TaxIssueSeverity.YELLOW,
            all.filter { it.getEffectiveDisplayId() in ids }
        )
    }

    val duplicates = receipts.filter { it.internalId.isNotBlank() }
        .groupBy { it.internalId }
        .filterValues { it.size > 1 }
        .values
        .flatten()
    if (duplicates.isNotEmpty()) {
        issues += TaxIssue(
            "Doppelte Beleg-ID erkannt",
            "Doppelte stabile Beleg-IDs vorhanden.",
            TaxIssueSeverity.RED,
            duplicates
        )
    }

    val totalIncome = incomeBuckets.sumOf { it.amount }
    val totalExpenses = expenseBuckets.sumOf { it.amount }
    return AnnualTaxSummary(
        year,
        totalIncome,
        totalExpenses,
        totalIncome - totalExpenses,
        incomeBuckets,
        expenseBuckets,
        issues
    )
}

private fun buildAnnualClosingChecks(
    summary: AnnualTaxSummary,
    rentRows: List<AnnualRentRow>,
    metadata: PropertyMetadata,
    allReceipts: List<Receipt>
): List<AnnualClosingCheck> {
    val phase1 = TaxPropertyCalculator.calculate(metadata, allReceipts)
    val rentMissing = rentRows.sumOf { it.missing }
    val rentMonths = rentRows.sumOf { it.missingMonths.size }
    val titles = summary.issues.map { it.title }.toSet()
    val afa = summary.expenseBuckets.firstOrNull { it.title == "AfA Gebäude" }?.amount ?: 0.0

    return listOf(
        AnnualClosingCheck(
            "Mieteinnahmen abgeglichen",
            if (rentMissing <= 0.01 && rentMonths == 0)
                "Jahres-Soll und erfasste Zahlungen ohne offene Unterdeckung."
            else "${NumberFormatter.format(rentMissing)} Unterdeckung; $rentMonths auffällige Monatszuordnung(en).",
            if (rentMissing <= 0.01 && rentMonths == 0) ClosingCheckState.OK else ClosingCheckState.REVIEW
        ),
        AnnualClosingCheck(
            "Belege freigegeben",
            if ("Nicht freigegebene Belege" !in titles && "Belege mit Prüfstatus" !in titles)
                "Keine offenen Freigabe-/Prüfstatus gefunden."
            else "Es bestehen noch offene oder zu prüfende Belege.",
            if ("Nicht freigegebene Belege" !in titles && "Belege mit Prüfstatus" !in titles)
                ClosingCheckState.OK else ClosingCheckState.REVIEW
        ),
        AnnualClosingCheck(
            "Schuldzinsen zugeordnet",
            if ("Schuldzinsen ohne Darlehenszuordnung" !in titles)
                "Alle erkannten Schuldzinsen sind einem gültigen Darlehen zugeordnet."
            else "Mindestens ein Schuldzins-Beleg hat keine gültige Darlehenszuordnung.",
            if ("Schuldzinsen ohne Darlehenszuordnung" !in titles)
                ClosingCheckState.OK else ClosingCheckState.BLOCKED
        ),
        AnnualClosingCheck(
            "AfA-Grundlage plausibel",
            when {
                phase1.allocationNeedsReview -> "Kaufpreisaufteilung Gebäude/Grund und Boden ist nicht schlüssig."
                afa <= 0.0 -> "Für das gewählte Jahr wurde keine AfA ermittelt; Stammdaten prüfen."
                else -> "AfA ${NumberFormatter.format(afa)}; Kaufpreisaufteilung rechnerisch plausibel."
            },
            when {
                phase1.allocationNeedsReview -> ClosingCheckState.BLOCKED
                afa <= 0.0 -> ClosingCheckState.REVIEW
                else -> ClosingCheckState.OK
            }
        ),
        AnnualClosingCheck(
            "15-%-Sanierungsmonitor",
            when {
                phase1.is15PercentExceeded -> "Grenze überschritten; steuerliche Behandlung der betroffenen Maßnahmen muss geklärt werden."
                phase1.limitUsagePercent >= 80.0 -> "Monitor bei ${"%.1f".format(phase1.limitUsagePercent)} %; vor Abschluss prüfen."
                else -> "Monitor bei ${"%.1f".format(phase1.limitUsagePercent)} %; kein automatischer Grenzkonflikt."
            },
            when {
                phase1.is15PercentExceeded -> ClosingCheckState.BLOCKED
                phase1.limitUsagePercent >= 80.0 || phase1.estimatedNetCount > 0 -> ClosingCheckState.REVIEW
                else -> ClosingCheckState.OK
            }
        ),
        AnnualClosingCheck(
            "Miet-/Belegzuordnungen vollständig",
            if ("Miete ohne Wohneinheit" !in titles)
                "Keine erfasste Mietzahlung ohne Wohneinheit erkannt."
            else "Mindestens eine Miet-/Nebenkostenzahlung ist keiner Wohneinheit zugeordnet.",
            if ("Miete ohne Wohneinheit" !in titles) ClosingCheckState.OK else ClosingCheckState.REVIEW
        ),
        AnnualClosingCheck(
            "Keine Dubletten",
            if ("Doppelte Beleg-ID erkannt" !in titles)
                "Keine doppelte stabile Beleg-ID im Steuerjahr erkannt."
            else "Doppelte stabile Beleg-IDs müssen vor dem Abschluss bereinigt werden.",
            if ("Doppelte Beleg-ID erkannt" !in titles) ClosingCheckState.OK else ClosingCheckState.BLOCKED
        )
    )
}

private fun buildAnlageVPreview(
    summary: AnnualTaxSummary,
    closingChecks: List<AnnualClosingCheck>
): Pair<List<AnlageVPreviewValue>, List<AnlageVPreviewValue>> {
    fun stateFor(vararg titles: String): String {
        val states = closingChecks.filter { it.title in titles }.map { it.state }
        return when {
            states.any { it == ClosingCheckState.BLOCKED } -> "KRITISCH"
            states.any { it == ClosingCheckState.REVIEW } -> "PRÜFEN"
            else -> "OK"
        }
    }

    fun sourceFor(bucket: TaxBucket): String = when (bucket.title) {
        "Mieten & umlagefähige Nebenkosten", "Sonstige Einnahmen aus Vermietung" ->
            "${bucket.receipts.size} erfasste Einnahmebeleg(e); Kautionen ausgeschlossen"
        "Schuldzinsen" ->
            "${bucket.receipts.size} zugeordnete Zinsbeleg(e) + Vermietungsanteil des Darlehens"
        "AfA Gebäude" ->
            "Objekt-Stammdaten + Kaufpreisaufteilung + AfA-Berechnung"
        "Erhaltungsaufwand / Reparaturen" ->
            "${bucket.receipts.size} Sanierungs-/Reparaturbeleg(e) + 15-%-Monitor"
        else -> "${bucket.receipts.size} zugeordnete Beleg(e)"
    }

    fun statusFor(bucket: TaxBucket): String = when (bucket.title) {
        "Mieten & umlagefähige Nebenkosten", "Sonstige Einnahmen aus Vermietung" ->
            stateFor("Mieteinnahmen abgeglichen", "Miet-/Belegzuordnungen vollständig", "Belege freigegeben")
        "Schuldzinsen" -> stateFor("Schuldzinsen zugeordnet", "Belege freigegeben")
        "AfA Gebäude" -> stateFor("AfA-Grundlage plausibel")
        "Erhaltungsaufwand / Reparaturen" -> stateFor("15-%-Sanierungsmonitor", "Belege freigegeben")
        else -> stateFor("Belege freigegeben", "Keine Dubletten")
    }

    val income = summary.incomeBuckets.map { bucket ->
        AnlageVPreviewValue(
            label = bucket.title,
            amount = bucket.amount,
            source = sourceFor(bucket),
            checkStatus = statusFor(bucket),
            note = if (bucket.title.startsWith("Mieten"))
                "Tatsächlich erfasste Zahlungen; Kautionen ausgeschlossen"
            else bucket.note
        )
    }
    val expenses = summary.expenseBuckets.map { bucket ->
        AnlageVPreviewValue(
            label = bucket.title,
            amount = bucket.amount,
            source = sourceFor(bucket),
            checkStatus = statusFor(bucket),
            note = bucket.note
        )
    }
    return income to expenses
}


internal fun buildAdvisorAnnualSummary(
    context: Context,
    year: Int,
    receipts: List<Receipt>,
    metadata: PropertyMetadata,
    loans: List<Loan>,
    units: List<WohneinheitStatus>
): com.example.util.AdvisorAnnualSummary {
    val summary = buildAnnualTaxSummary(context, year, receipts, metadata, loans)
    val rentRows = buildAnnualRentRows(context, year, receipts, units)
    val closingChecks = buildAnnualClosingChecks(summary, rentRows, metadata, receipts)
    val preview = buildAnlageVPreview(summary, closingChecks)
    val dataFingerprint = annualApprovalFingerprint(
        summary,
        closingChecks,
        rentRows,
        receipts,
        metadata,
        loans
    )
    val approvalPrefs = context.getSharedPreferences(
        ANNUAL_APPROVAL_PREFS,
        Context.MODE_PRIVATE
    )
    val approvedFingerprint = approvalPrefs.getString("fingerprint_$year", "").orEmpty()
    val approvedAt = approvalPrefs.getString("approved_at_$year", "").orEmpty()
    val approvalIsCurrent =
        approvedFingerprint.isNotBlank() && approvedFingerprint == dataFingerprint
    val phase1 = TaxPropertyCalculator.calculate(metadata, receipts)

    val yearReceipts = receipts.filter { it.yearOrNull() == year }
    val exportEligibleReceipts = yearReceipts.filter {
        com.example.util.DatevReceiptEligibility.issues(it).isEmpty()
    }
    val originalsByReceipt = exportEligibleReceipts.associateWith {
        com.example.util.DatevOriginalAttachmentPolicy.resolve(it)
    }
    val attachedOriginals = originalsByReceipt
        .filterValues { it != null }
        .keys
        .map { it.getEffectiveDisplayId() }
    val missingOriginals = originalsByReceipt
        .filterValues { it == null }
        .keys
        .map { it.getEffectiveDisplayId() }

    fun money(value: Double): String =
        java.text.NumberFormat.getCurrencyInstance(Locale.GERMANY).format(value)

    fun percent(value: Double): String =
        String.format(Locale.GERMANY, "%.2f %%", value)

    fun area(value: Double): String =
        String.format(Locale.GERMANY, "%.2f m²", value)

    fun germanDate(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            java.time.LocalDate.parse(value)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        }.getOrDefault(value)
    }

    val propertyOverview = buildList {
        if (metadata.name.isNotBlank()) add("Objektbezeichnung: ${metadata.name}")
        if (metadata.adresse.isNotBlank()) add("Adresse: ${metadata.adresse}")
        if (metadata.baujahr > 0) add("Baujahr: ${metadata.baujahr}")
        if (metadata.wohneinheiten.isNotBlank()) {
            add("Wohneinheiten: ${metadata.wohneinheiten}")
        }
        if (metadata.wohnflaeche > 0.0) add("Wohnfläche: ${area(metadata.wohnflaeche)}")
        if (metadata.grundstuecksgroesse > 0.0) {
            add("Grundstücksgröße: ${area(metadata.grundstuecksgroesse)}")
        }
        if (metadata.gesamtKaufpreis > 0.0) {
            add("Gesamtkaufpreis: ${money(metadata.gesamtKaufpreis)}")
        }
        if (phase1.buildingPurchaseShare > 0.0) {
            add("Gebäudeanteil Kaufpreis: ${money(phase1.buildingPurchaseShare)}")
        }
        if (phase1.landPurchaseShare > 0.0) {
            add("Grund und Boden: ${money(phase1.landPurchaseShare)}")
        }
        if (metadata.kaufpreisAufteilungQuelle.isNotBlank()) {
            add("Quelle der Kaufpreisaufteilung: ${metadata.kaufpreisAufteilungQuelle}")
        }
        if (metadata.notariellesKaufdatum.isNotBlank()) {
            add("Notarielles Kaufdatum: ${germanDate(metadata.notariellesKaufdatum)}")
        }
        if (metadata.uebergangNutzenLasten.isNotBlank()) {
            add("Übergang Nutzen und Lasten: ${germanDate(metadata.uebergangNutzenLasten)}")
        }
        if (phase1.buildingAcquisitionCosts > 0.0) {
            add("AfA-Bemessungsgrundlage Gebäude: ${money(phase1.buildingAcquisitionCosts)}")
        }
        if (phase1.afaRatePercent > 0.0) {
            add("AfA-Satz: ${percent(phase1.afaRatePercent)}")
        }
        if (phase1.annualAfa > 0.0) {
            add("AfA pro vollem Jahr: ${money(phase1.annualAfa)}")
        }
        if (phase1.firstYearAfa > 0.0) {
            add("Zeitanteilige AfA im Anschaffungsjahr: ${money(phase1.firstYearAfa)}")
        }
        if (phase1.afaStartDate.isNotBlank()) {
            add("AfA-Beginn: ${germanDate(phase1.afaStartDate)}")
        }
    }

    val financing = loans.flatMapIndexed { index, loan ->
        buildList {
            val title = loan.bezeichnung.ifBlank { "Darlehen ${index + 1}" }
            add("Darlehen ${index + 1}: $title")
            if (loan.bank.isNotBlank()) add("  Bank: ${loan.bank}")
            if (loan.darlehensbetrag > 0.0) {
                add("  Darlehensbetrag: ${money(loan.darlehensbetrag)}")
            }
            if (loan.restschuld > 0.0) add("  Restschuld: ${money(loan.restschuld)}")
            if (loan.sollzinsProzent > 0.0) {
                add("  Sollzins: ${percent(loan.sollzinsProzent)}")
            }
            if (loan.tilgungProzent > 0.0) {
                add("  Tilgung: ${percent(loan.tilgungProzent)}")
            }
            if (loan.monatlicheRate > 0.0) {
                add("  Monatsrate: ${money(loan.monatlicheRate)}")
            }
            if (loan.startDatum.isNotBlank()) {
                add("  Startdatum: ${germanDate(loan.startDatum)}")
            }
            if (loan.zinsbindungBis.isNotBlank()) {
                add("  Zinsbindung bis: ${germanDate(loan.zinsbindungBis)}")
            }
            if (loan.laufzeitBis.isNotBlank()) {
                add("  Laufzeit bis: ${germanDate(loan.laufzeitBis)}")
            }
            add("  Vermietungsanteil: ${percent(loan.vermietungsanteilProzent)}")
            if (loan.notiz.isNotBlank()) add("  Notiz: ${loan.notiz}")
            if (!loan.aktiv) add("  Status: Inaktiv")
        }
    }

    val renovationsAndAfa = buildList {
        if (phase1.afaStartDate.isNotBlank()) {
            add("AfA-Beginn: ${germanDate(phase1.afaStartDate)}")
        }
        if (phase1.buildingAcquisitionCosts > 0.0) {
            add("AfA-Bemessungsgrundlage: ${money(phase1.buildingAcquisitionCosts)}")
        }
        if (phase1.annualAfa > 0.0) add("Jährliche AfA: ${money(phase1.annualAfa)}")
        if (phase1.monitorStartDate.isNotBlank() && phase1.monitorEndDate.isNotBlank()) {
            add(
                "15-%-Prüfzeitraum: ${germanDate(phase1.monitorStartDate)} bis " +
                    germanDate(phase1.monitorEndDate)
            )
        }
        if (phase1.limit15Percent > 0.0) {
            add("15-%-Grenze: ${money(phase1.limit15Percent)}")
            add("Berücksichtigte Modernisierungskosten netto: " +
                money(phase1.relevantModernizationNet))
            add("Auslastung der 15-%-Grenze: ${percent(phase1.limitUsagePercent)}")
            add(
                "Status 15-%-Monitor: " +
                    if (phase1.is15PercentExceeded) "Grenze überschritten – steuerlich prüfen"
                    else "Grenze nach aktuellem Datenstand nicht überschritten"
            )
        }
    }

    return com.example.util.AdvisorAnnualSummary(
        year = year,
        propertyTitle = metadata.name.ifBlank { "Immobilienobjekt" },
        closingStatus = when {
            closingChecks.any { it.state == ClosingCheckState.BLOCKED } -> "KRITISCH"
            closingChecks.any { it.state == ClosingCheckState.REVIEW } -> "PRÜFEN"
            else -> "OK"
        },
        manualApprovalCurrent = approvalIsCurrent,
        approvedAt = approvedAt,
        dataFingerprint = dataFingerprint,
        approvedFingerprint = approvedFingerprint,
        criticalAnnualIssues = summary.redCount,
        criticalClosingChecks = closingChecks.count {
            it.state == ClosingCheckState.BLOCKED
        },
        missingRequiredOriginals = missingOriginals,
        totalIncome = summary.totalIncome,
        totalExpenses = summary.totalExpenses,
        result = summary.result,
        propertyOverview = propertyOverview,
        financing = financing,
        rentOverview = rentRows.map {
            "${it.unit.label}: Soll ${money(it.expected)}, Ist ${money(it.actual)}, " +
                "Differenz ${money(it.difference)}"
        },
        renovationsAndAfa = renovationsAndAfa,
        incomeValues = preview.first.map {
            com.example.util.AdvisorAnnualValue(
                it.label,
                it.amount,
                it.source,
                it.checkStatus,
                it.note
            )
        },
        expenseValues = preview.second.map {
            com.example.util.AdvisorAnnualValue(
                it.label,
                it.amount,
                it.source,
                it.checkStatus,
                it.note
            )
        },
        openIssues = summary.issues.map {
            "${it.severity.name}: ${it.title} – ${it.message}"
        } + closingChecks.filter { it.state != ClosingCheckState.OK }.map {
            "${it.state.name}: ${it.title} – ${it.detail}"
        },
        attachedOriginalDocuments = attachedOriginals
    )
}

@Composable
fun AnnualTaxAssistantScreen(viewModel: ReceiptViewModel) {
    val context = LocalContext.current
    val receipts by viewModel.receipts.collectAsState()
    val propertyState by viewModel.propertyMetadata.collectAsState()
    val units by viewModel.wohneinheitenStatus.collectAsState()
    val loans by viewModel.loans.collectAsState()
    val metadata = propertyState ?: PropertyMetadata()
    val availableYears = remember(receipts) {
        receipts.mapNotNull { it.yearOrNull() }.distinct().sortedDescending()
    }
    var year by remember(availableYears) {
        mutableIntStateOf(availableYears.firstOrNull() ?: LocalDate.now().year)
    }
    var details by remember { mutableStateOf<TaxBucket?>(null) }
    var issueDetails by remember { mutableStateOf<TaxIssue?>(null) }
    var unitDetails by remember { mutableStateOf<UnitAnnualSummary?>(null) }
    var rentDetails by remember { mutableStateOf<AnnualRentRow?>(null) }
    var approvalVersion by remember(year) { mutableIntStateOf(0) }

    val summary = remember(context, year, receipts, metadata, loans) {
        buildAnnualTaxSummary(context, year, receipts, metadata, loans)
    }
    val previous = remember(context, year, receipts, metadata, loans) {
        buildAnnualTaxSummary(context, year - 1, receipts, metadata, loans)
    }
    val unitSummaries = remember(year, receipts, units) {
        buildUnitAnnualSummaries(year, receipts, units)
    }
    val rentRows = remember(context, year, receipts, units) {
        buildAnnualRentRows(context, year, receipts, units)
    }
    val closingChecks = remember(summary, rentRows, metadata, receipts) {
        buildAnnualClosingChecks(summary, rentRows, metadata, receipts)
    }
    val preview = remember(summary, closingChecks) { buildAnlageVPreview(summary, closingChecks) }
    val approvalFingerprint = remember(summary, closingChecks, rentRows, receipts, metadata, loans) {
        annualApprovalFingerprint(summary, closingChecks, rentRows, receipts, metadata, loans)
    }
    val approvalPrefs = remember(context) { context.getSharedPreferences(ANNUAL_APPROVAL_PREFS, Context.MODE_PRIVATE) }
    val storedApprovalFingerprint = remember(year, approvalFingerprint, approvalVersion) {
        approvalPrefs.getString("fingerprint_$year", "").orEmpty()
    }
    val approvedAt = remember(year, approvalFingerprint, approvalVersion) {
        approvalPrefs.getString("approved_at_$year", "").orEmpty()
    }
    val approvalIsCurrent = storedApprovalFingerprint.isNotBlank() && storedApprovalFingerprint == approvalFingerprint
    val approvalWasSet = storedApprovalFingerprint.isNotBlank()
    val approvalBlocked = closingChecks.any { it.state == ClosingCheckState.BLOCKED }


    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("anlage_v_annual_assistant"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Anlage-V-Jahresassistent", fontSize = 21.sp, fontWeight = FontWeight.Black, color = DarkNavy)
                Text(
                    "Jahresübersicht mit Einnahmen, Werbungskosten, Mietprüfung, Abschlusscheck und Anlage-V-Vorschau",
                    fontSize = 11.sp,
                    color = SlateGray
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { year-- }) { Text("‹") }
                Text("Steuerjahr $year", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                OutlinedButton(onClick = { year++ }) { Text("›") }
            }
        }
        item { AnnualStatusCard(summary) }
        item { AnnualClosingCheckCard(closingChecks, year) }
        item {
            AnnualManualApprovalCard(
                year = year,
                isCurrent = approvalIsCurrent,
                wasSet = approvalWasSet,
                approvedAt = approvedAt,
                blocked = approvalBlocked,
                reviewCount = closingChecks.count { it.state == ClosingCheckState.REVIEW },
                onApprove = {
                    approvalPrefs.edit()
                        .putString("fingerprint_$year", approvalFingerprint)
                        .putString("approved_at_$year", java.time.OffsetDateTime.now().toString())
                        .apply()
                    approvalVersion++
                },
                onRevoke = {
                    approvalPrefs.edit()
                        .remove("fingerprint_$year")
                        .remove("approved_at_$year")
                        .apply()
                    approvalVersion++
                }
            )
        }
        item { AnlageVPreviewCard(summary, closingChecks, preview.first, preview.second) }

        if (summary.issues.isNotEmpty()) {
            item { SectionTitle("Offene Prüfpunkte") }
            items(summary.issues, key = { "issue_${it.severity}_${it.title}" }) {
                AnnualTaxIssueCard(it) { issueDetails = it }
            }
        }

        item { AnnualYearComparisonCard(summary, previous) }
        item { AnnualRentCheckCard(rentRows, year) }
        items(
            rentRows.filter { abs(it.difference) > 0.01 || it.missingMonths.isNotEmpty() },
            key = { "rent_${it.unit.name}" }
        ) { row ->
            AnnualRentRowCard(row) { rentDetails = row }
        }

        if (unitSummaries.isNotEmpty()) {
            item { SectionTitle("Wohneinheiten – direkte Zuordnung") }
            item {
                Text(
                    "Nur direkt zugeordnete Belege; AfA und allgemeine Objektkosten bleiben im Gesamtobjekt.",
                    fontSize = 9.sp,
                    color = SlateGray
                )
            }
            items(unitSummaries, key = { "unit_${it.name}" }) {
                AnnualUnitCard(it) { unitDetails = it }
            }
        }

        item { SectionTitle("Einnahmen – Anlage V") }
        items(summary.incomeBuckets, key = { "income_${it.title}" }) {
            AnnualTaxBucketCard(it) { details = it }
        }
        item { SectionTitle("Werbungskosten – Anlage V") }
        items(summary.expenseBuckets, key = { "expense_${it.title}" }) {
            AnnualTaxBucketCard(it) { details = it }
        }
        item { AnnualResultCard(summary) }
    }

    details?.let {
        AnnualTaxReceiptDialog(it.title, it.amount, it.note, it.warning, it.receipts) { details = null }
    }
    issueDetails?.let {
        AnnualTaxReceiptDialog(it.title, null, it.message, it.severity == TaxIssueSeverity.RED, it.receipts) {
            issueDetails = null
        }
    }
    unitDetails?.let {
        AnnualTaxReceiptDialog(
            it.label,
            it.result,
            "Einnahmen ${NumberFormatter.format(it.income)} · direkt zugeordnete Ausgaben ${NumberFormatter.format(it.expenses)}",
            it.result < 0,
            it.receipts
        ) { unitDetails = null }
    }
    rentDetails?.let { row ->
        AnnualTaxReceiptDialog(
            "Mietprüfung ${row.unit.label}",
            row.actual,
            "Jahres-Soll ${NumberFormatter.format(row.expected)} · Differenz ${(if (row.difference >= 0) "+" else "") + NumberFormatter.format(row.difference)}" +
                if (row.missingMonths.isNotEmpty())
                    " · Auffällige Monate: ${row.missingMonths.joinToString(", ") { it.toString().padStart(2, '0') }}"
                else "",
            row.missing > 0.01,
            row.receipts
        ) { rentDetails = null }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
}

@Composable
private fun AnnualStatusCard(summary: AnnualTaxSummary) {
    val status = when {
        summary.redCount > 0 -> "KRITISCH · ${summary.redCount} rote Punkte"
        summary.yellowCount > 0 -> "PRÜFEN · ${summary.yellowCount} gelbe Punkte"
        else -> "BEREIT · keine offenen automatischen Prüfpunkte"
    }
    val color = when {
        summary.redCount > 0 -> CrimsonRed
        summary.yellowCount > 0 -> WarmOrange
        else -> EmeraldGreen
    }
    Card(
        Modifier.fillMaxWidth().testTag("annual_tax_review_status"),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, color.copy(alpha = .5f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Jahres-Prüfstatus", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            Text(status, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun AnnualClosingCheckCard(checks: List<AnnualClosingCheck>, year: Int) {
    val blocked = checks.count { it.state == ClosingCheckState.BLOCKED }
    val review = checks.count { it.state == ClosingCheckState.REVIEW }
    val ready = blocked == 0 && review == 0
    val statusColor = when {
        blocked > 0 -> CrimsonRed
        review > 0 -> WarmOrange
        else -> EmeraldGreen
    }
    val statusText = when {
        blocked > 0 -> "NICHT BEREIT · $blocked kritische Abschlussprüfung(en)"
        review > 0 -> "PRÜFEN · $review Punkt(e) vor Jahresabschluss"
        else -> "BEREIT · Anlage-V-Vorbereitung kann abgeschlossen werden"
    }

    Card(
        Modifier.fillMaxWidth().testTag("annual_closing_check"),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, statusColor.copy(alpha = .55f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Jahresabschluss-Check $year", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            Text(statusText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusColor)
            HorizontalDivider(color = BorderColor)
            checks.forEach { AnnualClosingCheckRow(it) }
            Text(
                if (ready)
                    "Alle automatischen Abschlussprüfungen sind erfüllt. Die Werte bleiben eine Vorbereitungshilfe und sollten vor Abgabe fachlich geprüft werden."
                else
                    "Offene Punkte zuerst in den jeweiligen Bereichen korrigieren. Der Abschlussstatus aktualisiert sich automatisch.",
                fontSize = 9.sp,
                color = SlateGray
            )
        }
    }
}

@Composable
private fun AnnualClosingCheckRow(check: AnnualClosingCheck) {
    val marker = when (check.state) {
        ClosingCheckState.OK -> "✓"
        ClosingCheckState.REVIEW -> "!"
        ClosingCheckState.BLOCKED -> "×"
    }
    val color = when (check.state) {
        ClosingCheckState.OK -> EmeraldGreen
        ClosingCheckState.REVIEW -> WarmOrange
        ClosingCheckState.BLOCKED -> CrimsonRed
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(marker, fontSize = 12.sp, fontWeight = FontWeight.Black, color = color)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(check.title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            Text(check.detail, fontSize = 9.sp, color = SlateGray)
        }
    }
}

@Composable
private fun AnnualManualApprovalCard(
    year: Int,
    isCurrent: Boolean,
    wasSet: Boolean,
    approvedAt: String,
    blocked: Boolean,
    reviewCount: Int,
    onApprove: () -> Unit,
    onRevoke: () -> Unit
) {
    val color = when {
        blocked -> CrimsonRed
        isCurrent -> EmeraldGreen
        wasSet -> WarmOrange
        else -> AccentBlue
    }
    val status = when {
        blocked -> "NICHT FREIGEBBAR · kritische Punkte offen"
        isCurrent -> "MANUELL GEPRÜFT · Freigabe aktuell"
        wasSet -> "FREIGABE VERALTET · relevante Jahresdaten wurden geändert"
        else -> "NOCH NICHT MANUELL FREIGEGEBEN"
    }
    Card(
        Modifier.fillMaxWidth().testTag("annual_manual_approval"),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, color.copy(alpha = .55f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Manuelle Jahres-Freigabe $year", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            Text(status, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
            if (isCurrent && approvedAt.isNotBlank()) {
                Text("Freigegeben: ${approvedAt.replace('T', ' ').take(16)}", fontSize = 9.sp, color = SlateGray)
            }
            if (!blocked && reviewCount > 0) {
                Text("$reviewCount gelbe Prüfpunkt(e) bleiben sichtbar. Die Freigabe bestätigt, dass sie bewusst geprüft wurden.", fontSize = 9.sp, color = WarmOrange)
            }
            Text(
                "Die Freigabe ist an einen Fingerabdruck der Jahreswerte, Belege, Mietprüfung, Objekt- und Darlehensdaten gekoppelt. Relevante Änderungen machen sie automatisch ungültig.",
                fontSize = 9.sp,
                color = SlateGray
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isCurrent) {
                    OutlinedButton(onClick = onApprove, enabled = !blocked, modifier = Modifier.weight(1f)) {
                        Text(if (wasSet) "Erneut freigeben" else "Als geprüft freigeben", fontSize = 10.sp)
                    }
                } else {
                    OutlinedButton(onClick = onRevoke, modifier = Modifier.weight(1f)) {
                        Text("Freigabe aufheben", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnlageVPreviewCard(
    summary: AnnualTaxSummary,
    closingChecks: List<AnnualClosingCheck>,
    incomeValues: List<AnlageVPreviewValue>,
    expenseValues: List<AnlageVPreviewValue>
) {
    val blocked = closingChecks.count { it.state == ClosingCheckState.BLOCKED }
    val review = closingChecks.count { it.state == ClosingCheckState.REVIEW }
    val statusColor = when {
        blocked > 0 -> CrimsonRed
        review > 0 -> WarmOrange
        else -> EmeraldGreen
    }
    val statusText = when {
        blocked > 0 -> "Vorschau mit kritischen offenen Punkten"
        review > 0 -> "Vorschau – Werte vor Übernahme prüfen"
        else -> "Vorschau – automatische Prüfungen ohne offenen Punkt"
    }

    Card(
        Modifier.fillMaxWidth().testTag("anlage_v_preview"),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, statusColor.copy(alpha = .5f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Anlage-V-Vorschau ${summary.year}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            Text(statusText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor)
            Text(
                "Vorbereitete Übernahmewerte aus den aktuellen App-Daten. Keine festen ELSTER-Zeilennummern, da Formularfelder je Steuerjahr abweichen können.",
                fontSize = 9.sp,
                color = SlateGray
            )

            HorizontalDivider(color = BorderColor)
            Text("Einnahmen", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            if (incomeValues.isEmpty()) {
                Text("Keine auswertbaren Einnahmen erfasst.", fontSize = 9.sp, color = SlateGray)
            } else {
                incomeValues.forEach { AnlageVPreviewRow(it, EmeraldGreen) }
            }
            AnnualTaxAmountRow("Einnahmen gesamt", summary.totalIncome, EmeraldGreen)

            HorizontalDivider(color = BorderColor)
            Text("Werbungskosten", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            if (expenseValues.isEmpty()) {
                Text("Keine auswertbaren Werbungskosten erfasst.", fontSize = 9.sp, color = SlateGray)
            } else {
                expenseValues.forEach { AnlageVPreviewRow(it, DarkNavy) }
            }
            AnnualTaxAmountRow("Werbungskosten gesamt", summary.totalExpenses, CrimsonRed)

            HorizontalDivider(color = BorderColor)
            AnnualTaxAmountRow(
                if (summary.result >= 0) "Vorläufiger Überschuss" else "Vorläufiger Verlust",
                abs(summary.result),
                if (summary.result >= 0) EmeraldGreen else CrimsonRed
            )
            if (blocked > 0 || review > 0) {
                Text(
                    "$blocked kritische und $review zu prüfende Abschlussposition(en) sind noch offen. Diese Vorschau nicht ungeprüft in ELSTER übernehmen.",
                    fontSize = 9.sp,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun AnlageVPreviewRow(value: AnlageVPreviewValue, amountColor: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(value.label, fontSize = 10.sp, color = DarkNavy)
            Text("Quelle: ${value.source}", fontSize = 8.sp, color = SlateGray)
            if (value.note.isNotBlank()) Text(value.note, fontSize = 8.sp, color = SlateGray)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                NumberFormatter.format(value.amount),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = amountColor
            )
            val checkColor = when (value.checkStatus) {
                "KRITISCH" -> CrimsonRed
                "PRÜFEN" -> WarmOrange
                else -> EmeraldGreen
            }
            Text(value.checkStatus, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = checkColor)
        }
    }
}

@Composable
private fun AnnualRentCheckCard(rows: List<AnnualRentRow>, year: Int) {
    val expected = rows.sumOf { it.expected }
    val actual = rows.sumOf { it.actual }
    val missing = rows.sumOf { it.missing }
    val affected = rows.count { it.missing > 0.01 || it.missingMonths.isNotEmpty() }
    Card(
        Modifier.fillMaxWidth().testTag("annual_rent_check"),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, if (missing > .01) WarmOrange else BorderColor),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Jahres-Mietprüfung $year", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            AnnualTaxAmountRow("Soll laut Mietverlauf", expected, DarkNavy)
            AnnualTaxAmountRow("Ist laut Belegen", actual, EmeraldGreen)
            AnnualTaxAmountRow("Offener Unterschied", missing, if (missing > .01) CrimsonRed else EmeraldGreen)
            Text(
                if (affected > 0) "$affected Wohneinheit(en) mit Abweichung – Details unten."
                else "Alle erwarteten Jahreszahlungen sind anhand der erfassten Belege abgedeckt.",
                fontSize = 9.sp,
                color = if (affected > 0) WarmOrange else EmeraldGreen
            )
            Text(
                "Soll berücksichtigt Mieterwechsel zeitanteilig. Die Anlage-V-Einnahmen bleiben die tatsächlich erfassten Zahlungen.",
                fontSize = 9.sp,
                color = SlateGray
            )
        }
    }
}

@Composable
private fun AnnualRentRowCard(row: AnnualRentRow, onClick: () -> Unit) {
    val warning = row.missing > .01 || row.missingMonths.isNotEmpty()
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, if (warning) WarmOrange else BorderColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.unit.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                Text(
                    (if (row.difference >= 0) "+" else "") + NumberFormatter.format(row.difference),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (row.difference >= 0) EmeraldGreen else CrimsonRed
                )
            }
            Text(
                "Soll ${NumberFormatter.format(row.expected)} · Ist ${NumberFormatter.format(row.actual)}",
                fontSize = 9.sp,
                color = SlateGray
            )
            if (row.missingMonths.isNotEmpty()) {
                Text(
                    "Monate mit Unterdeckung: ${row.missingMonths.joinToString(", ") { it.toString().padStart(2, '0') }}",
                    fontSize = 9.sp,
                    color = CrimsonRed
                )
            }
        }
    }
}

@Composable
private fun AnnualYearComparisonCard(current: AnnualTaxSummary, previous: AnnualTaxSummary) {
    Card(
        Modifier.fillMaxWidth().testTag("annual_tax_year_comparison"),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, BorderColor),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                "Vorjahresvergleich ${previous.year} → ${current.year}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = DarkNavy
            )
            AnnualComparisonRow("Einnahmen", previous.totalIncome, current.totalIncome)
            AnnualComparisonRow("Werbungskosten", previous.totalExpenses, current.totalExpenses)
            AnnualComparisonRow("Ergebnis", previous.result, current.result)
        }
    }
}

@Composable
private fun AnnualComparisonRow(label: String, previous: Double, current: Double) {
    val delta = current - previous
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            "$label: ${NumberFormatter.format(previous)} → ${NumberFormatter.format(current)}",
            fontSize = 10.sp,
            color = SlateGray
        )
        Text(
            (if (delta >= 0) "+" else "") + NumberFormatter.format(delta),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (delta >= 0) EmeraldGreen else CrimsonRed
        )
    }
}

@Composable
private fun AnnualUnitCard(unit: UnitAnnualSummary, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, BorderColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(unit.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                Text(
                    (if (unit.result >= 0) "+" else "") + NumberFormatter.format(unit.result),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (unit.result >= 0) EmeraldGreen else CrimsonRed
                )
            }
            Text(
                "Einnahmen ${NumberFormatter.format(unit.income)} · Ausgaben ${NumberFormatter.format(unit.expenses)}",
                fontSize = 9.sp,
                color = SlateGray
            )
        }
    }
}

@Composable
private fun AnnualTaxIssueCard(issue: TaxIssue, onClick: () -> Unit) {
    val color = if (issue.severity == TaxIssueSeverity.RED) CrimsonRed else WarmOrange
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, color.copy(alpha = .55f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                (if (issue.severity == TaxIssueSeverity.RED) "ROT · " else "GELB · ") + issue.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(issue.message, fontSize = 9.sp, color = SlateGray)
        }
    }
}

@Composable
private fun AnnualTaxBucketCard(bucket: TaxBucket, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, if (bucket.warning) CrimsonRed.copy(alpha = .5f) else BorderColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(bucket.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                if (bucket.note.isNotBlank()) {
                    Text(
                        bucket.note,
                        fontSize = 9.sp,
                        color = if (bucket.warning) CrimsonRed else SlateGray
                    )
                }
            }
            Text(
                NumberFormatter.format(bucket.amount),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (bucket.warning) CrimsonRed else DarkNavy
            )
        }
    }
}

@Composable
private fun AnnualResultCard(summary: AnnualTaxSummary) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, BorderColor),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Vorläufiges Jahresergebnis", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            AnnualTaxAmountRow("Einnahmen gesamt", summary.totalIncome, EmeraldGreen)
            AnnualTaxAmountRow("Werbungskosten gesamt", summary.totalExpenses, CrimsonRed)
            HorizontalDivider(color = BorderColor)
            AnnualTaxAmountRow(
                if (summary.result >= 0) "Überschuss" else "Verlust",
                abs(summary.result),
                if (summary.result >= 0) EmeraldGreen else CrimsonRed
            )
            Text("Vorbereitungshilfe, keine Steuerberatung.", fontSize = 9.sp, color = SlateGray)
        }
    }
}

@Composable
private fun AnnualTaxReceiptDialog(
    title: String,
    amount: Double?,
    note: String,
    warning: Boolean,
    receipts: List<Receipt>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                if (amount != null) {
                    item {
                        Text(
                            "Summe: ${NumberFormatter.format(amount)}",
                            fontWeight = FontWeight.Bold,
                            color = DarkNavy
                        )
                    }
                }
                if (note.isNotBlank()) {
                    item {
                        Text(note, fontSize = 10.sp, color = if (warning) CrimsonRed else SlateGray)
                    }
                }
                if (receipts.isEmpty()) {
                    item {
                        Text(
                            "Kein einzelner Beleg hinterlegt – Wert stammt aus Stammdaten oder Berechnung.",
                            fontSize = 10.sp,
                            color = SlateGray
                        )
                    }
                } else {
                    items(receipts, key = { "annual_detail_${it.id}_${it.internalId}" }) { r ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SoftBackground),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Column(Modifier.padding(9.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(r.getEffectiveDisplayId(), fontSize = 9.sp, color = SlateGray)
                                    Text(
                                        NumberFormatter.format(r.bruttobetrag),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkNavy
                                    )
                                }
                                Text(r.aussteller, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                                Text("${r.datum} · ${r.unterkategorie}", fontSize = 9.sp, color = SlateGray)
                                if (r.wohneinheit.isNotBlank()) {
                                    Text(r.wohneinheit, fontSize = 9.sp, color = SlateGray)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
}

@Composable
private fun AnnualTaxAmountRow(
    label: String,
    amount: Double,
    color: androidx.compose.ui.graphics.Color
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 11.sp, color = SlateGray)
        Text(
            NumberFormatter.format(amount),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
