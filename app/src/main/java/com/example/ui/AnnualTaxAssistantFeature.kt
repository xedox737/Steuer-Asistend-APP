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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDatabase
import com.example.data.Loan
import com.example.data.PropertyMetadata
import com.example.data.Receipt
import com.example.data.TaxPropertyCalculator
import java.time.LocalDate
import kotlin.math.abs

private enum class TaxIssueSeverity { RED, YELLOW }

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

private fun Receipt.yearOrNull(): Int? = datum.take(4).toIntOrNull()

private fun receiptText(receipt: Receipt): String =
    (receipt.hauptkategorie + " " + receipt.unterkategorie + " " + receipt.beschreibung + " " + receipt.kontoNr).lowercase()

private fun isDeposit(receipt: Receipt): Boolean =
    receiptText(receipt).contains("kaution")

private fun isRentalIncome(receipt: Receipt): Boolean {
    if (isDeposit(receipt)) return false
    if (receipt.hauptkategorie == "Miete, Nebenkosten & Kaution") return true
    if (receipt.hauptkategorie == "Sonstige Einnahmen") {
        val text = receiptText(receipt)
        return text.contains("miete") || text.contains("betriebskosten") || text.contains("nebenkosten")
    }
    return false
}

private fun isPrincipalOrLoanFlow(receipt: Receipt): Boolean {
    val text = receiptText(receipt)
    return text.contains("tilgung") || text.contains("kreditrate") ||
        text.contains("darlehensrate") || text.contains("kreditauszahlung") ||
        text.contains("sondertilgung")
}

private fun isInterest(receipt: Receipt): Boolean =
    receipt.hauptkategorie == "Finanzierung, Kredite & Versicherungen" &&
        (receipt.unterkategorie.equals("Kreditzinsen", true) ||
            receiptText(receipt).contains("sollzins") || receiptText(receipt).contains("schuldzins"))

private fun afaForYear(metadata: PropertyMetadata, allReceipts: List<Receipt>, year: Int): Double {
    val phase1 = TaxPropertyCalculator.calculate(metadata, allReceipts)
    val startYear = phase1.afaStartDate.take(4).toIntOrNull() ?: return 0.0
    return when {
        year < startYear -> 0.0
        year == startYear -> phase1.firstYearAfa
        else -> phase1.annualAfa
    }
}

private fun classifyGenericExpense(receipt: Receipt): String {
    val text = receiptText(receipt)
    return when {
        text.contains("fahrt") || text.contains("kilometer") || text.contains("fahrtenbuch") -> "Fahrtkosten"
        text.contains("versicherung") || text.contains("rechtsschutz") -> "Versicherungen"
        text.contains("verwaltung") || text.contains("steuerberater") || text.contains("buchhaltung") || text.contains("kontoführung") || text.contains("kontofuehrung") -> "Verwaltung & Beratung"
        text.contains("grundsteuer") || text.contains("wasser") || text.contains("abwasser") || text.contains("müll") || text.contains("muell") || text.contains("strom") || text.contains("heizung") || text.contains("schornstein") -> "Laufende Objektkosten"
        else -> "Sonstige Werbungskosten"
    }
}

private fun buildAnnualTaxSummary(
    context: Context,
    year: Int,
    allReceipts: List<Receipt>,
    metadata: PropertyMetadata,
    loans: List<Loan>
): AnnualTaxSummary {
    val receipts = allReceipts.filter { it.yearOrNull() == year }
    val phase1 = TaxPropertyCalculator.calculate(metadata, allReceipts)

    val rentalReceipts = receipts.filter(::isRentalIncome)
    val regularRental = rentalReceipts.filter { it.hauptkategorie == "Miete, Nebenkosten & Kaution" }
    val otherRental = rentalReceipts.filter { it.hauptkategorie == "Sonstige Einnahmen" }

    val assignmentPrefs = context.getSharedPreferences("loan_interest_assignments", Context.MODE_PRIVATE)
    val loansById = loans.associateBy { it.id }
    val interestReceipts = receipts.filter(::isInterest)
    val assignedInterest = interestReceipts.mapNotNull { receipt ->
        val loanId = assignmentPrefs.getInt("receipt_${receipt.id}", 0)
        val loan = loansById[loanId] ?: return@mapNotNull null
        receipt to loan
    }
    val debtInterest = assignedInterest.sumOf { (receipt, loan) ->
        receipt.bruttobetrag * (loan.vermietungsanteilProzent.coerceIn(0.0, 100.0) / 100.0)
    }

    val afa = afaForYear(metadata, allReceipts, year)
    val renovationReceipts = receipts.filter { it.hauptkategorie == "Renovierungs- / Reparaturkosten & Investitionen" }
    val renovationGross = renovationReceipts.sumOf { it.bruttobetrag }
    val monitorStartYear = phase1.monitorStartDate.take(4).toIntOrNull()
    val monitorEndYear = phase1.monitorEndDate.take(4).toIntOrNull()
    val yearInsideMonitor = monitorStartYear != null && monitorEndYear != null && year in monitorStartYear..monitorEndYear
    val renovationBlocked = phase1.is15PercentExceeded && yearInsideMonitor
    val renovationDeductible = if (renovationBlocked) 0.0 else renovationGross

    val otherFinancingReceipts = receipts.filter {
        it.hauptkategorie == "Finanzierung, Kredite & Versicherungen" &&
            !isInterest(it) && !isPrincipalOrLoanFlow(it)
    }

    val genericExpenseReceipts = receipts.filter { receipt ->
        !isRentalIncome(receipt) &&
            receipt.hauptkategorie !in setOf(
                "Anschaffungskosten",
                "Finanzierung, Kredite & Versicherungen",
                "Renovierungs- / Reparaturkosten & Investitionen"
            )
    }
    val groupedGenericExpenses = genericExpenseReceipts.groupBy(::classifyGenericExpense)

    val incomeBuckets = listOf(
        TaxBucket("Mieten & umlagefähige Nebenkosten", regularRental.sumOf { it.bruttobetrag }, regularRental, "Kautionen werden nicht als normale Mieteinnahme erfasst"),
        TaxBucket("Sonstige Einnahmen aus Vermietung", otherRental.sumOf { it.bruttobetrag }, otherRental)
    ).filter { it.amount != 0.0 || it.receipts.isNotEmpty() }

    val expenseBuckets = mutableListOf(
        TaxBucket("Schuldzinsen", debtInterest, assignedInterest.map { it.first }, "Nur zugeordnete Zinsbelege × Vermietungsanteil"),
        TaxBucket("AfA Gebäude", afa, note = "Automatisch aus Objekt-Stammdaten und AfA-Bemessungsgrundlage"),
        TaxBucket(
            "Erhaltungsaufwand / Reparaturen",
            renovationDeductible,
            renovationReceipts,
            if (renovationBlocked) "Nicht automatisch als Sofortaufwand angesetzt: 15-%-Prüfung erforderlich" else "Vorläufig als Sofortaufwand berücksichtigt; steuerlich prüfen",
            renovationBlocked
        ),
        TaxBucket(
            "Weitere Finanzierungskosten",
            otherFinancingReceipts.sumOf { it.bruttobetrag },
            otherFinancingReceipts,
            "Tilgung, Kreditrate, Sondertilgung und Darlehensauszahlung ausgeschlossen"
        )
    )
    listOf("Verwaltung & Beratung", "Versicherungen", "Laufende Objektkosten", "Fahrtkosten", "Sonstige Werbungskosten").forEach { title ->
        val bucketReceipts = groupedGenericExpenses[title].orEmpty()
        if (bucketReceipts.isNotEmpty()) {
            expenseBuckets += TaxBucket(title, bucketReceipts.sumOf { it.bruttobetrag }, bucketReceipts)
        }
    }

    val issues = mutableListOf<TaxIssue>()

    val unassignedInterestReceipts = interestReceipts.filter {
        val loanId = assignmentPrefs.getInt("receipt_${it.id}", 0)
        loanId <= 0 || loansById[loanId] == null
    }
    if (unassignedInterestReceipts.isNotEmpty()) {
        issues += TaxIssue(
            "Schuldzinsen ohne Darlehenszuordnung",
            "${unassignedInterestReceipts.size} Zinsbeleg${if (unassignedInterestReceipts.size == 1) " ist" else "e sind"} keinem gültigen Darlehen zugeordnet.",
            TaxIssueSeverity.RED,
            unassignedInterestReceipts
        )
    }

    val rentWithoutUnit = rentalReceipts.filter { it.wohneinheit.isBlank() || it.wohneinheit == "Gesamtobjekt / Allgemein" }
    if (rentWithoutUnit.isNotEmpty()) {
        issues += TaxIssue(
            "Miete ohne Wohneinheit",
            "${rentWithoutUnit.size} Miet-/Nebenkostenbeleg${if (rentWithoutUnit.size == 1) " ist" else "e sind"} keiner konkreten Wohneinheit zugeordnet.",
            TaxIssueSeverity.YELLOW,
            rentWithoutUnit
        )
    }

    val openReceipts = receipts.filter { it.freigabestatus != "FREIGEGEBEN" }
    if (openReceipts.isNotEmpty()) {
        issues += TaxIssue(
            "Nicht freigegebene Belege",
            "${openReceipts.size} Beleg${if (openReceipts.size == 1) " ist" else "e sind"} noch nicht steuerlich freigegeben.",
            TaxIssueSeverity.YELLOW,
            openReceipts
        )
    }

    val reviewReceipts = receipts.filter { it.exportStatus == "ZU_PRUEFEN" || it.pruefstatus == "UNGEPRUEFT" || it.syncStatus == "REVIEW_REQUIRED" }
    if (reviewReceipts.isNotEmpty()) {
        issues += TaxIssue(
            "Belege mit Prüfstatus",
            "${reviewReceipts.size} Beleg${if (reviewReceipts.size == 1) " benötigt" else "e benötigen"} noch eine fachliche oder technische Prüfung.",
            TaxIssueSeverity.YELLOW,
            reviewReceipts
        )
    }

    if (phase1.allocationNeedsReview) {
        issues += TaxIssue(
            "Kaufpreisaufteilung prüfen",
            "Gebäude und Grund/Boden weichen zusammen um ${NumberFormatter.format(abs(phase1.allocationDifference))} vom Gesamtkaufpreis ab.",
            TaxIssueSeverity.RED
        )
    }

    if (phase1.is15PercentExceeded) {
        issues += TaxIssue(
            "15-%-Grenze überschritten",
            "Betroffene Sanierungskosten werden nicht automatisch als sofort abzugsfähiger Erhaltungsaufwand angesetzt.",
            TaxIssueSeverity.RED,
            renovationReceipts
        )
    } else if (phase1.limitUsagePercent >= 80.0) {
        issues += TaxIssue(
            "15-%-Grenze nähert sich",
            "Der 15-%-Monitor liegt bei ${"%.1f".format(phase1.limitUsagePercent)} %.",
            TaxIssueSeverity.YELLOW,
            renovationReceipts
        )
    }

    if (phase1.estimatedNetCount > 0) {
        val estimatedIds = phase1.monitorDetails.filter { it.included && it.estimatedNet }.map { it.displayId }.toSet()
        val estimatedReceipts = allReceipts.filter { it.getEffectiveDisplayId() in estimatedIds }
        issues += TaxIssue(
            "Nettobeträge im 15-%-Monitor geschätzt",
            "Bei ${phase1.estimatedNetCount} Beleg${if (phase1.estimatedNetCount == 1) "" else "en"} wurde der Nettobetrag mangels belastbarer MwSt.-Daten geschätzt.",
            TaxIssueSeverity.YELLOW,
            estimatedReceipts
        )
    }

    val duplicateReceipts = receipts.filter { it.internalId.isNotBlank() }
        .groupBy { it.internalId }
        .filterValues { it.size > 1 }
        .values.flatten()
    if (duplicateReceipts.isNotEmpty()) {
        issues += TaxIssue(
            "Doppelte Beleg-ID erkannt",
            "${duplicateReceipts.map { it.internalId }.distinct().size} doppelte stabile Beleg-ID${if (duplicateReceipts.map { it.internalId }.distinct().size == 1) " wurde" else "s wurden"} erkannt.",
            TaxIssueSeverity.RED,
            duplicateReceipts
        )
    }

    val totalIncome = incomeBuckets.sumOf { it.amount }
    val totalExpenses = expenseBuckets.sumOf { it.amount }

    return AnnualTaxSummary(
        year = year,
        totalIncome = totalIncome,
        totalExpenses = totalExpenses,
        result = totalIncome - totalExpenses,
        incomeBuckets = incomeBuckets,
        expenseBuckets = expenseBuckets,
        issues = issues
    )
}

@Composable
fun AnnualTaxAssistantScreen(viewModel: ReceiptViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember(context) { AppDatabase.getDatabase(context.applicationContext, scope) }
    val receipts by viewModel.receipts.collectAsState()
    val propertyState by viewModel.propertyMetadata.collectAsState()
    val loans by database.loanDao().getAllLoansFlow().collectAsState(initial = emptyList())
    val metadata = propertyState ?: PropertyMetadata()
    val availableYears = remember(receipts) { receipts.mapNotNull { it.yearOrNull() }.distinct().sortedDescending() }
    var year by remember(availableYears) { mutableIntStateOf(availableYears.firstOrNull() ?: LocalDate.now().year) }
    var details by remember { mutableStateOf<TaxBucket?>(null) }
    var issueDetails by remember { mutableStateOf<TaxIssue?>(null) }
    val summary = remember(context, year, receipts, metadata, loans) {
        buildAnnualTaxSummary(context, year, receipts, metadata, loans)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("anlage_v_annual_assistant"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Anlage-V-Jahresassistent", fontSize = 21.sp, fontWeight = FontWeight.Black, color = DarkNavy)
                Text("Jahresübersicht mit nachvollziehbaren Einnahmen, Werbungskosten und Prüfpunkten", fontSize = 11.sp, color = SlateGray)
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { year-- }) { Text("‹") }
                Text("Steuerjahr $year", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                OutlinedButton(onClick = { year++ }) { Text("›") }
            }
        }
        item {
            val statusText = when {
                summary.redCount > 0 -> "KRITISCH · ${summary.redCount} rote Punkte"
                summary.yellowCount > 0 -> "PRÜFEN · ${summary.yellowCount} gelbe Punkte"
                else -> "BEREIT · keine offenen automatischen Prüfpunkte"
            }
            val statusColor = when {
                summary.redCount > 0 -> CrimsonRed
                summary.yellowCount > 0 -> WarmOrange
                else -> EmeraldGreen
            }
            Card(
                modifier = Modifier.fillMaxWidth().testTag("annual_tax_review_status"),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Jahres-Prüfstatus", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text(statusText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = statusColor)
                    if (summary.issues.isEmpty()) {
                        Text("Die derzeit automatisch prüfbaren Angaben sind plausibel.", fontSize = 10.sp, color = SlateGray)
                    } else {
                        Text("Tippe einen Prüfpunkt an, um die betroffenen Belege zu sehen.", fontSize = 9.sp, color = SlateGray)
                    }
                }
            }
        }

        if (summary.issues.isNotEmpty()) {
            item { Text("Offene Prüfpunkte", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkNavy) }
            items(summary.issues, key = { "issue_${it.severity}_${it.title}" }) { issue ->
                AnnualTaxIssueCard(issue) { issueDetails = issue }
            }
        }

        item { Text("Einnahmen – Anlage V", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkNavy) }
        items(summary.incomeBuckets, key = { "income_${it.title}" }) { bucket ->
            AnnualTaxBucketCard(bucket) { details = bucket }
        }

        item { Text("Werbungskosten – Anlage V", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkNavy) }
        items(summary.expenseBuckets, key = { "expense_${it.title}" }) { bucket ->
            AnnualTaxBucketCard(bucket) { details = bucket }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Vorläufiges Jahresergebnis", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    AnnualTaxAmountRow("Einnahmen gesamt", summary.totalIncome, EmeraldGreen)
                    AnnualTaxAmountRow("Werbungskosten gesamt", summary.totalExpenses, CrimsonRed)
                    HorizontalDivider(color = BorderColor)
                    AnnualTaxAmountRow(
                        if (summary.result >= 0) "Überschuss" else "Verlust",
                        abs(summary.result),
                        if (summary.result >= 0) EmeraldGreen else CrimsonRed
                    )
                    Text("Vorbereitungshilfe, keine Steuerberatung. Kritische und gelbe Prüfpunkte vor Übernahme in ELSTER/DATEV klären.", fontSize = 9.sp, color = SlateGray)
                }
            }
        }
    }

    details?.let { bucket -> AnnualTaxReceiptDialog(bucket.title, bucket.amount, bucket.note, bucket.warning, bucket.receipts) { details = null } }
    issueDetails?.let { issue ->
        AnnualTaxReceiptDialog(
            issue.title,
            null,
            issue.message,
            issue.severity == TaxIssueSeverity.RED,
            issue.receipts
        ) { issueDetails = null }
    }
}

@Composable
private fun AnnualTaxIssueCard(issue: TaxIssue, onClick: () -> Unit) {
    val color = if (issue.severity == TaxIssueSeverity.RED) CrimsonRed else WarmOrange
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(if (issue.severity == TaxIssueSeverity.RED) "ROT · ${issue.title}" else "GELB · ${issue.title}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
            Text(issue.message, fontSize = 9.sp, color = SlateGray)
            if (issue.receipts.isNotEmpty()) Text("${issue.receipts.size} betroffene Beleg${if (issue.receipts.size == 1) "" else "e"} · Details öffnen", fontSize = 9.sp, color = AccentBlue)
        }
    }
}

@Composable
private fun AnnualTaxBucketCard(bucket: TaxBucket, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, if (bucket.warning) CrimsonRed.copy(alpha = 0.5f) else BorderColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(bucket.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                if (bucket.note.isNotBlank()) Text(bucket.note, fontSize = 9.sp, color = if (bucket.warning) CrimsonRed else SlateGray)
                if (bucket.receipts.isNotEmpty()) Text("${bucket.receipts.size} Beleg${if (bucket.receipts.size == 1) "" else "e"} · Details öffnen", fontSize = 9.sp, color = AccentBlue)
            }
            Text(NumberFormatter.format(bucket.amount), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (bucket.warning) CrimsonRed else DarkNavy)
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
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (amount != null) item { Text("Summe: ${NumberFormatter.format(amount)}", fontWeight = FontWeight.Bold, color = DarkNavy) }
                if (note.isNotBlank()) item { Text(note, fontSize = 10.sp, color = if (warning) CrimsonRed else SlateGray) }
                if (receipts.isEmpty()) {
                    item { Text("Kein einzelner Beleg hinterlegt – der Prüfpunkt bzw. Wert stammt aus Stammdaten oder einer Berechnung.", fontSize = 10.sp, color = SlateGray) }
                } else {
                    items(receipts, key = { "annual_detail_${it.id}_${it.internalId}" }) { receipt ->
                        Card(colors = CardDefaults.cardColors(containerColor = SoftBackground), border = BorderStroke(1.dp, BorderColor)) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(receipt.getEffectiveDisplayId(), fontSize = 9.sp, color = SlateGray)
                                    Text(NumberFormatter.format(receipt.bruttobetrag), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                                }
                                Text(receipt.aussteller, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                                Text("${receipt.datum} · ${receipt.unterkategorie}", fontSize = 9.sp, color = SlateGray)
                                if (receipt.beschreibung.isNotBlank()) Text(receipt.beschreibung, fontSize = 9.sp, color = SlateGray)
                                if (receipt.wohneinheit.isNotBlank()) Text(receipt.wohneinheit, fontSize = 9.sp, color = SlateGray)
                                if (receipt.freigabestatus != "FREIGEGEBEN") Text("Freigabe: ${receipt.freigabestatus}", fontSize = 9.sp, color = WarmOrange)
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
private fun AnnualTaxAmountRow(label: String, amount: Double, color: androidx.compose.ui.graphics.Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 11.sp, color = SlateGray)
        Text(NumberFormatter.format(amount), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
