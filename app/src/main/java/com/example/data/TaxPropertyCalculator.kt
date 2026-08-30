package com.example.data

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
