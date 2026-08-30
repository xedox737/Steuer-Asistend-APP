from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "app/src/main/java/com/example/ui/ReceiptAppUi.kt"
CALC = ROOT / "app/src/main/java/com/example/data/TaxPropertyCalculator.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f"{label}: anchor not found")
    return text.replace(old, new, 1)


calculator = r'''package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Advisory tax calculations for privately rented residential property.
 * Results are preparation aids only and intentionally expose estimates/review items.
 */
data class TaxPhase1Summary(
    val purchasePrice: Double,
    val buildingPurchaseShare: Double,
    val landPurchaseShare: Double,
    val acquisitionAncillaryGross: Double,
    val buildingAncillaryShare: Double,
    val buildingAcquisitionCosts: Double,
    val afaRatePercent: Double,
    val annualAfa: Double,
    val firstYearAfa: Double,
    val afaStartDate: String,
    val monitorStartDate: String,
    val monitorEndDate: String,
    val limit15Percent: Double,
    val relevantModernizationNet: Double,
    val candidateReceiptCount: Int,
    val estimatedNetCount: Int,
    val heuristicallyExcludedCount: Int
) {
    val limitUsagePercent: Double
        get() = if (limit15Percent > 0.0) relevantModernizationNet / limit15Percent * 100.0 else 0.0
    val is15PercentExceeded: Boolean
        get() = limit15Percent > 0.0 && relevantModernizationNet > limit15Percent
}

object TaxPropertyCalculator {
    private val germanDate = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).apply { isLenient = false }

    fun calculate(metadata: PropertyMetadata, receipts: List<Receipt>): TaxPhase1Summary {
        val purchasePrice = metadata.gesamtKaufpreis.coerceAtLeast(0.0)
        val buildingShare = metadata.gebaeudewert.coerceAtLeast(0.0).coerceAtMost(purchasePrice.takeIf { it > 0.0 } ?: Double.MAX_VALUE)
        val landShare = (purchasePrice - buildingShare).coerceAtLeast(0.0)
        val buildingRatio = if (purchasePrice > 0.0) (buildingShare / purchasePrice).coerceIn(0.0, 1.0) else 0.0

        // Existing acquisition-cost receipts are ancillary costs; financing costs live in a separate category.
        val ancillaryGross = receipts
            .filter { it.deletionStatus == "ACTIVE" || it.deletionStatus.isBlank() }
            .filter { it.hauptkategorie == "Anschaffungskosten" }
            .sumOf { it.bruttobetrag.coerceAtLeast(0.0) }
        val buildingAncillary = ancillaryGross * buildingRatio
        val buildingAcquisitionCosts = buildingShare + buildingAncillary

        val afaRate = when {
            metadata.baujahr <= 0 -> 0.0
            metadata.baujahr < 1925 -> 2.5
            metadata.baujahr < 2023 -> 2.0
            else -> 3.0
        }
        val annualAfa = buildingAcquisitionCosts * afaRate / 100.0
        val startText = metadata.uebergangNutzenLasten.ifBlank { metadata.notariellesKaufdatum }
        val startDate = parseDate(startText)
        val firstYearAfa = if (startDate != null && annualAfa > 0.0) {
            val cal = Calendar.getInstance(Locale.GERMANY).apply { time = startDate }
            val month = cal.get(Calendar.MONTH) + 1
            annualAfa * (13 - month) / 12.0
        } else 0.0

        val monitorEnd = startDate?.let {
            Calendar.getInstance(Locale.GERMANY).apply {
                time = it
                add(Calendar.YEAR, 3)
                add(Calendar.DAY_OF_MONTH, -1)
            }.time
        }

        var relevantNet = 0.0
        var candidateCount = 0
        var estimatedCount = 0
        var excludedCount = 0

        if (startDate != null && monitorEnd != null) {
            receipts
                .asSequence()
                .filter { it.deletionStatus == "ACTIVE" || it.deletionStatus.isBlank() }
                .filter { it.hauptkategorie == "Renovierungs- / Reparaturkosten & Investitionen" }
                .forEach { receipt ->
                    val receiptDate = parseDate(receipt.datum) ?: return@forEach
                    if (receiptDate.before(startDate) || receiptDate.after(monitorEnd)) return@forEach

                    val text = (receipt.unterkategorie + " " + receipt.beschreibung).lowercase(Locale.GERMANY)
                    // Obvious statutory exclusions: extensions and usually recurring maintenance.
                    val obviousExclusion = listOf(
                        "erweiterung", "anbau", "aufstockung", "wartung", "schornsteinfeger",
                        "jährliche prüfung", "jaehrliche pruefung", "heizungswartung"
                    ).any { text.contains(it) }
                    if (obviousExclusion) {
                        excludedCount++
                        return@forEach
                    }

                    candidateCount++
                    val net = netAmountForMonitor(receipt)
                    relevantNet += net.first
                    if (net.second) estimatedCount++
                }
        }

        return TaxPhase1Summary(
            purchasePrice = purchasePrice,
            buildingPurchaseShare = buildingShare,
            landPurchaseShare = landShare,
            acquisitionAncillaryGross = ancillaryGross,
            buildingAncillaryShare = buildingAncillary,
            buildingAcquisitionCosts = buildingAcquisitionCosts,
            afaRatePercent = afaRate,
            annualAfa = annualAfa,
            firstYearAfa = firstYearAfa,
            afaStartDate = startDate?.let(germanDate::format).orEmpty(),
            monitorStartDate = startDate?.let(germanDate::format).orEmpty(),
            monitorEndDate = monitorEnd?.let(germanDate::format).orEmpty(),
            limit15Percent = buildingAcquisitionCosts * 0.15,
            relevantModernizationNet = relevantNet,
            candidateReceiptCount = candidateCount,
            estimatedNetCount = estimatedCount,
            heuristicallyExcludedCount = excludedCount
        )
    }

    private fun netAmountForMonitor(receipt: Receipt): Pair<Double, Boolean> {
        val gross = receipt.bruttobetrag.coerceAtLeast(0.0)
        val items = receipt.getPositionenList()
        if (items.isNotEmpty()) {
            val itemGross = items.sumOf { it.gesamtpreis.coerceAtLeast(0.0) }
            val tolerance = max(1.0, gross * 0.05)
            if (itemGross > 0.0 && abs(itemGross - gross) <= tolerance) {
                val net = items.sumOf { item ->
                    val rate = item.steuersatz.coerceAtLeast(0.0)
                    item.gesamtpreis.coerceAtLeast(0.0) / (1.0 + rate / 100.0)
                }
                if (net > 0.0) return net to false
            }
        }
        // Most domestic construction/repair invoices use 19% VAT. Mark this explicitly as an estimate.
        return (gross / 1.19) to true
    }

    private fun parseDate(value: String): java.util.Date? = runCatching {
        if (value.isBlank()) null else germanDate.parse(value)
    }.getOrNull()
}
'''
CALC.write_text(calculator, encoding="utf-8")

s = UI.read_text(encoding="utf-8")

old_calc = '''    // Calculations
    val totalKaufpreis = metadata.gesamtKaufpreis
    val totalGebaeudeAnteil = metadata.gebaeudewert
    val limit15Percent = totalGebaeudeAnteil * 0.15

    val totalAnschaffung = receipts.filter { it.hauptkategorie == "Anschaffungskosten" }.sumOf { it.bruttobetrag }
    val totalFinanzierung = receipts.filter { it.hauptkategorie == "Finanzierung, Kredite & Versicherungen" }.sumOf { it.bruttobetrag }
    val totalRenovierung = receipts.filter { it.hauptkategorie == "Renovierungs- / Reparaturkosten & Investitionen" }.sumOf { it.bruttobetrag }
    val totalSonstige = receipts.filter { it.hauptkategorie == "Sonstige Ausgaben" }.sumOf { it.bruttobetrag }'''
new_calc = '''    // Calculations
    val taxPhase1 = com.example.data.TaxPropertyCalculator.calculate(metadata, receipts)
    val totalKaufpreis = metadata.gesamtKaufpreis
    val totalGebaeudeAnteil = metadata.gebaeudewert
    val limit15Percent = taxPhase1.limit15Percent

    val totalAnschaffung = receipts.filter { it.hauptkategorie == "Anschaffungskosten" }.sumOf { it.bruttobetrag }
    val totalFinanzierung = receipts.filter { it.hauptkategorie == "Finanzierung, Kredite & Versicherungen" }.sumOf { it.bruttobetrag }
    val totalRenovierung = receipts.filter { it.hauptkategorie == "Renovierungs- / Reparaturkosten & Investitionen" }.sumOf { it.bruttobetrag }
    val totalSonstige = receipts.filter { it.hauptkategorie == "Sonstige Ausgaben" }.sumOf { it.bruttobetrag }'''
s = replace_once(s, old_calc, new_calc, "dashboard tax calculation anchor")

marker7 = '        // 7. 15%-Grenze Warning Monitor\n'
marker8 = '        // 8. Objekt-Stammdaten Details Card\n'
if marker7 not in s or marker8 not in s:
    raise SystemExit("15-percent monitor markers not found")
pre, rest = s.split(marker7, 1)
monitor, post = rest.split(marker8, 1)
monitor = monitor.replace('totalRenovierung / limit15Percent', 'taxPhase1.relevantModernizationNet / limit15Percent')
monitor = monitor.replace('totalRenovierung >= limit15Percent', 'taxPhase1.is15PercentExceeded')
monitor = monitor.replace('limit15Percent - totalRenovierung', 'limit15Percent - taxPhase1.relevantModernizationNet')
monitor = monitor.replace(
    '"3-Jahres-Limit: Max. 15% des Gebäudeanteils (${NumberFormatter.format(limit15Percent)}). Bei Überschreitung 50 Jahre Abschreibung!"',
    '"3-Jahres-Prüfwert: 15% der Gebäude-Anschaffungskosten (${NumberFormatter.format(limit15Percent)}), maßgeblich ohne Umsatzsteuer."'
)
monitor = monitor.replace(
    '"STEUER-WARNUNG: Die 15%-Grenze wurde überschritten! Erhaltungsaufwendungen müssen als Herstellungskosten aktiviert werden."',
    '"STEUER-WARNUNG: Der vorläufige 15%-Prüfwert ist überschritten. Einordnung als anschaffungsnahe Herstellungskosten fachlich prüfen; Erweiterungen und jährlich übliche Erhaltungsarbeiten sind gesondert zu behandeln."'
)

extra_monitor_info = '''                Text(
                    "Zeitraum: ${taxPhase1.monitorStartDate.ifBlank { "nicht festgelegt" }} bis ${taxPhase1.monitorEndDate.ifBlank { "nicht festgelegt" }} • Potenziell relevante Belege: ${taxPhase1.candidateReceiptCount}" +
                        if (taxPhase1.estimatedNetCount > 0) " • Netto bei ${taxPhase1.estimatedNetCount} Beleg(en) geschätzt" else "",
                    fontSize = 10.sp,
                    color = SlateGray,
                    lineHeight = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
'''
needle = '                Spacer(modifier = Modifier.height(14.dp))\n'
if extra_monitor_info not in monitor:
    if needle not in monitor:
        raise SystemExit("monitor info insertion anchor not found")
    monitor = monitor.replace(needle, extra_monitor_info + needle, 1)

afa_card = '''        // 7. AfA-Übersicht
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("AfA Gebäude", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text("${taxPhase1.afaRatePercent}% p.a.", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                }
                Text("Gebäude-Kaufpreisanteil: ${NumberFormatter.format(taxPhase1.buildingPurchaseShare)}", fontSize = 11.sp, color = SlateGray)
                Text("+ anteilige Anschaffungsnebenkosten: ${NumberFormatter.format(taxPhase1.buildingAncillaryShare)}", fontSize = 11.sp, color = SlateGray)
                HorizontalDivider(color = BorderColor)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("AfA-Bemessungsgrundlage", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DarkNavy)
                    Text(NumberFormatter.format(taxPhase1.buildingAcquisitionCosts), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("AfA volles Jahr", fontSize = 12.sp, color = SlateGray)
                    Text(NumberFormatter.format(taxPhase1.annualAfa), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                }
                if (taxPhase1.afaStartDate.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Erstes Jahr ab ${taxPhase1.afaStartDate}", fontSize = 11.sp, color = SlateGray)
                        Text(NumberFormatter.format(taxPhase1.firstYearAfa), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                    }
                }
                Text("Vorbereitungshilfe: Gebäudewert und Anschaffungsnebenkosten müssen steuerlich plausibel auf Grund/Boden und Gebäude aufgeteilt sein.", fontSize = 9.5.sp, color = Color.Gray, lineHeight = 12.sp)
            }
        }

        // 8. 15%-Grenze Warning Monitor
'''

s = pre + afa_card + monitor + marker8 + post
UI.write_text(s, encoding="utf-8")
print("Phase 1 AfA + corrected 15-percent monitor patch applied.")
