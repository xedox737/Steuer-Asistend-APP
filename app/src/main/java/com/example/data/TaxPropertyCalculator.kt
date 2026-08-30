package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

data class AcquisitionCostDetail(
    val displayId: String,
    val datum: String,
    val description: String,
    val grossAmount: Double,
    val buildingAllocatedAmount: Double
)

data class ModernizationMonitorDetail(
    val displayId: String,
    val datum: String,
    val description: String,
    val grossAmount: Double,
    val netAmount: Double,
    val included: Boolean,
    val estimatedNet: Boolean,
    val reason: String
)

/** Preparation aid only. Values stay transparent so the user/tax adviser can review them. */
data class TaxPhase1Summary(
    val purchasePrice: Double,
    val buildingPurchaseShare: Double,
    val landPurchaseShare: Double,
    val allocationDifference: Double,
    val allocationSource: String,
    val acquisitionAncillaryGross: Double,
    val buildingAncillaryShare: Double,
    val buildingAcquisitionCosts: Double,
    val acquisitionCostDetails: List<AcquisitionCostDetail>,
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
    val heuristicallyExcludedCount: Int,
    val monitorDetails: List<ModernizationMonitorDetail>
) {
    val limitUsagePercent: Double
        get() = if (limit15Percent > 0.0) relevantModernizationNet / limit15Percent * 100.0 else 0.0
    val is15PercentExceeded: Boolean
        get() = limit15Percent > 0.0 && relevantModernizationNet > limit15Percent
    val allocationNeedsReview: Boolean
        get() = purchasePrice > 0.0 && abs(allocationDifference) > 1.0
}

object TaxPropertyCalculator {
    private val germanDate = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).apply { isLenient = false }

    fun calculate(metadata: PropertyMetadata, receipts: List<Receipt>): TaxPhase1Summary {
        val purchasePrice = metadata.gesamtKaufpreis.coerceAtLeast(0.0)
        val explicitLand = metadata.grundUndBodenWert.coerceAtLeast(0.0)
        val rawBuilding = metadata.gebaeudewert.coerceAtLeast(0.0)
        val buildingShare = when {
            rawBuilding > 0.0 -> rawBuilding
            purchasePrice > 0.0 && explicitLand > 0.0 -> (purchasePrice - explicitLand).coerceAtLeast(0.0)
            else -> 0.0
        }
        val landShare = when {
            explicitLand > 0.0 -> explicitLand
            purchasePrice > 0.0 -> (purchasePrice - buildingShare).coerceAtLeast(0.0)
            else -> 0.0
        }
        val allocationDifference = purchasePrice - buildingShare - landShare
        val allocationBase = (buildingShare + landShare).takeIf { it > 0.0 } ?: purchasePrice
        val buildingRatio = if (allocationBase > 0.0) (buildingShare / allocationBase).coerceIn(0.0, 1.0) else 0.0

        val activeReceipts = receipts.filter { it.deletionStatus == "ACTIVE" || it.deletionStatus.isBlank() }
        val acquisitionReceipts = activeReceipts.filter { it.hauptkategorie == "Anschaffungskosten" }
        val acquisitionDetails = acquisitionReceipts.map { receipt ->
            AcquisitionCostDetail(
                displayId = receipt.getEffectiveDisplayId(),
                datum = receipt.datum,
                description = receipt.beschreibung.ifBlank { receipt.aussteller },
                grossAmount = receipt.bruttobetrag.coerceAtLeast(0.0),
                buildingAllocatedAmount = receipt.bruttobetrag.coerceAtLeast(0.0) * buildingRatio
            )
        }
        val ancillaryGross = acquisitionDetails.sumOf { it.grossAmount }
        val buildingAncillary = acquisitionDetails.sumOf { it.buildingAllocatedAmount }
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
            annualAfa * (13 - (cal.get(Calendar.MONTH) + 1)) / 12.0
        } else 0.0
        val monitorEnd = startDate?.let {
            Calendar.getInstance(Locale.GERMANY).apply {
                time = it
                add(Calendar.YEAR, 3)
                add(Calendar.DAY_OF_MONTH, -1)
            }.time
        }

        val monitorDetails = mutableListOf<ModernizationMonitorDetail>()
        if (startDate != null && monitorEnd != null) {
            activeReceipts
                .filter { it.hauptkategorie == "Renovierungs- / Reparaturkosten & Investitionen" }
                .forEach { receipt ->
                    val receiptDate = parseDate(receipt.datum)
                    val text = (receipt.unterkategorie + " " + receipt.beschreibung).lowercase(Locale.GERMANY)
                    val exclusion = listOf(
                        "erweiterung", "anbau", "aufstockung", "wartung", "schornsteinfeger",
                        "jährliche prüfung", "jaehrliche pruefung", "heizungswartung"
                    ).firstOrNull { text.contains(it) }
                    val inPeriod = receiptDate != null && !receiptDate.before(startDate) && !receiptDate.after(monitorEnd)
                    val net = netAmountForMonitor(receipt)
                    val included = inPeriod && exclusion == null
                    val reason = when {
                        receiptDate == null -> "Belegdatum nicht auswertbar"
                        !inPeriod -> "Außerhalb des 3-Jahres-Zeitraums"
                        exclusion != null -> "Heuristisch ausgeschlossen: $exclusion"
                        net.second -> "Einbezogen; Nettobetrag mangels belastbarer MwSt.-Daten geschätzt"
                        else -> "Einbezogen; Nettobetrag aus Positions-/MwSt.-Daten"
                    }
                    monitorDetails += ModernizationMonitorDetail(
                        displayId = receipt.getEffectiveDisplayId(),
                        datum = receipt.datum,
                        description = receipt.beschreibung.ifBlank { receipt.aussteller },
                        grossAmount = receipt.bruttobetrag.coerceAtLeast(0.0),
                        netAmount = if (included) net.first else 0.0,
                        included = included,
                        estimatedNet = included && net.second,
                        reason = reason
                    )
                }
        }
        val includedDetails = monitorDetails.filter { it.included }

        return TaxPhase1Summary(
            purchasePrice = purchasePrice,
            buildingPurchaseShare = buildingShare,
            landPurchaseShare = landShare,
            allocationDifference = allocationDifference,
            allocationSource = metadata.kaufpreisAufteilungQuelle,
            acquisitionAncillaryGross = ancillaryGross,
            buildingAncillaryShare = buildingAncillary,
            buildingAcquisitionCosts = buildingAcquisitionCosts,
            acquisitionCostDetails = acquisitionDetails,
            afaRatePercent = afaRate,
            annualAfa = annualAfa,
            firstYearAfa = firstYearAfa,
            afaStartDate = startDate?.let(germanDate::format).orEmpty(),
            monitorStartDate = startDate?.let(germanDate::format).orEmpty(),
            monitorEndDate = monitorEnd?.let(germanDate::format).orEmpty(),
            limit15Percent = buildingAcquisitionCosts * 0.15,
            relevantModernizationNet = includedDetails.sumOf { it.netAmount },
            candidateReceiptCount = includedDetails.size,
            estimatedNetCount = includedDetails.count { it.estimatedNet },
            heuristicallyExcludedCount = monitorDetails.count { !it.included },
            monitorDetails = monitorDetails
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
        return (gross / 1.19) to true
    }

    private fun parseDate(value: String): java.util.Date? = runCatching {
        if (value.isBlank()) null else germanDate.parse(value)
    }.getOrNull()
}
