from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
DB = ROOT / "app/src/main/java/com/example/data/ReceiptDatabase.kt"
DRIVE = ROOT / "app/src/main/java/com/example/data/DrivePersistenceRepository.kt"
CALC = ROOT / "app/src/main/java/com/example/data/TaxPropertyCalculator.kt"
UI = ROOT / "app/src/main/java/com/example/ui/ReceiptAppUi.kt"


def replace_once(text, old, new, label):
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f"{label}: anchor not found")
    return text.replace(old, new, 1)

# ---------- Room/property metadata ----------
s = DB.read_text(encoding="utf-8")
s = replace_once(
    s,
    '''    val gesamtKaufpreis: Double = 250000.0,\n    val gebaeudewert: Double = 200000.0\n)''',
    '''    val gesamtKaufpreis: Double = 250000.0,\n    val gebaeudewert: Double = 200000.0,\n    val grundUndBodenWert: Double = 50000.0,\n    val kaufpreisAufteilungQuelle: String = "MANUELL"\n)''',
    "property metadata tax fields",
)

migration = '''\nval MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {\n    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {\n        db.execSQL("ALTER TABLE property_metadata ADD COLUMN grundUndBodenWert REAL NOT NULL DEFAULT 0.0")\n        db.execSQL("ALTER TABLE property_metadata ADD COLUMN kaufpreisAufteilungQuelle TEXT NOT NULL DEFAULT 'ABGELEITET'")\n        db.execSQL("UPDATE property_metadata SET grundUndBodenWert = CASE WHEN gesamtKaufpreis > gebaeudewert THEN gesamtKaufpreis - gebaeudewert ELSE 0 END")\n    }\n}\n'''
anchor = '\n@Database(entities = [Receipt::class, PropertyMetadata::class, ReceiptEntity::class, Beleg::class, ExportAuditRun::class, ReceiptDocumentReference::class], version = 15, exportSchema = false)'
if 'MIGRATION_15_16' not in s:
    if anchor not in s:
        raise SystemExit("database version anchor not found")
    s = s.replace(anchor, migration + '\n@Database(entities = [Receipt::class, PropertyMetadata::class, ReceiptEntity::class, Beleg::class, ExportAuditRun::class, ReceiptDocumentReference::class], version = 16, exportSchema = false)', 1)
else:
    s = s.replace('version = 15, exportSchema = false', 'version = 16, exportSchema = false', 1)

s = replace_once(
    s,
    '.addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)',
    '.addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)',
    "register migration 15 16",
)
DB.write_text(s, encoding="utf-8")

# ---------- Drive property metadata ----------
s = DRIVE.read_text(encoding="utf-8")
s = replace_once(
    s,
    '''            put("gesamtKaufpreis", gesamtKaufpreis)\n            put("gebaeudewert", gebaeudewert)''',
    '''            put("gesamtKaufpreis", gesamtKaufpreis)\n            put("gebaeudewert", gebaeudewert)\n            put("grundUndBodenWert", grundUndBodenWert)\n            put("kaufpreisAufteilungQuelle", kaufpreisAufteilungQuelle)''',
    "drive property write tax fields",
)
s = replace_once(
    s,
    '''            gesamtKaufpreis = json.optDouble("gesamtKaufpreis", 0.0),\n            gebaeudewert = json.optDouble("gebaeudewert", 0.0)''',
    '''            gesamtKaufpreis = json.optDouble("gesamtKaufpreis", 0.0),\n            gebaeudewert = json.optDouble("gebaeudewert", 0.0),\n            grundUndBodenWert = if (json.has("grundUndBodenWert")) json.optDouble("grundUndBodenWert", 0.0) else (json.optDouble("gesamtKaufpreis", 0.0) - json.optDouble("gebaeudewert", 0.0)).coerceAtLeast(0.0),\n            kaufpreisAufteilungQuelle = json.optString("kaufpreisAufteilungQuelle", "ABGELEITET")''',
    "drive property parse tax fields",
)
DRIVE.write_text(s, encoding="utf-8")

# ---------- Tax calculator ----------
calc = r'''package com.example.data

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
'''
CALC.write_text(calc, encoding="utf-8")

# ---------- UI ----------
s = UI.read_text(encoding="utf-8")

# Dashboard dialog state
s = replace_once(
    s,
    '''    var showAuthDialog by remember { mutableStateOf(false) }\n    var showKiPowerCenterDialog by remember { mutableStateOf(false) }''',
    '''    var showAuthDialog by remember { mutableStateOf(false) }\n    var showKiPowerCenterDialog by remember { mutableStateOf(false) }\n    var showAfaDetails by remember { mutableStateOf(false) }\n    var showMonitorDetails by remember { mutableStateOf(false) }''',
    "dashboard tax detail states",
)

# Add land row to visible AfA card (first matching AfA card in compact dashboard)
needle = '''                Text("Gebäude-Kaufpreisanteil: ${NumberFormatter.format(taxPhase1.buildingPurchaseShare)}", fontSize = 11.sp, color = SlateGray)\n                Text("+ anteilige Anschaffungsnebenkosten: ${NumberFormatter.format(taxPhase1.buildingAncillaryShare)}", fontSize = 11.sp, color = SlateGray)'''
replacement = '''                Text("Gebäude-Kaufpreisanteil: ${NumberFormatter.format(taxPhase1.buildingPurchaseShare)}", fontSize = 11.sp, color = SlateGray)\n                Text("Grund und Boden: ${NumberFormatter.format(taxPhase1.landPurchaseShare)}", fontSize = 11.sp, color = SlateGray)\n                Text("+ anteilige Anschaffungsnebenkosten: ${NumberFormatter.format(taxPhase1.buildingAncillaryShare)}", fontSize = 11.sp, color = SlateGray)\n                if (taxPhase1.allocationNeedsReview) {\n                    Text("⚠ Kaufpreisaufteilung weicht um ${NumberFormatter.format(taxPhase1.allocationDifference)} vom Gesamtkaufpreis ab.", fontSize = 10.sp, color = WarmOrange)\n                }'''
s = replace_once(s, needle, replacement, "visible afa land row")

# Add AfA details button after preparation note (first occurrence)
needle = '''                Text(\n                    "Vorbereitungswert. Kaufpreisaufteilung, Nebenkosten und AfA bitte vor der Steuererklärung prüfen.",\n                    fontSize = 10.sp,\n                    color = SlateGray,\n                    lineHeight = 13.sp\n                )'''
replacement = needle + '''\n                TextButton(onClick = { showAfaDetails = true }, modifier = Modifier.align(Alignment.End)) {\n                    Text("Aufteilung & Nebenkosten anzeigen", fontSize = 11.sp, fontWeight = FontWeight.Bold)\n                }'''
s = replace_once(s, needle, replacement, "visible afa details button")

# Add monitor details button to compact card by status line
needle = '''                Text(\n                    "Zeitraum: ${taxPhase1.monitorStartDate.ifBlank { "nicht festgelegt" }} bis ${taxPhase1.monitorEndDate.ifBlank { "nicht festgelegt" }} • ${taxPhase1.candidateReceiptCount} potenziell relevante Belege" +\n                        if (taxPhase1.estimatedNetCount > 0) " • ${taxPhase1.estimatedNetCount} Netto-Schätzung(en)" else "",\n                    fontSize = 10.sp, color = SlateGray, lineHeight = 13.sp\n                )'''
replacement = needle + '''\n                TextButton(onClick = { showMonitorDetails = true }, modifier = Modifier.align(Alignment.End)) {\n                    Text("Belege im Monitor anzeigen", fontSize = 11.sp, fontWeight = FontWeight.Bold)\n                }'''
s = replace_once(s, needle, replacement, "visible monitor details button")

# Insert dialogs immediately before compact dashboard settings note.
dialog_anchor = '''        Text(\n            "Objekt, Drive, KI und weitere Einstellungen findest du oben rechts über das Zahnrad.",'''
dialogs = '''        if (showAfaDetails) {\n            AlertDialog(\n                onDismissRequest = { showAfaDetails = false },\n                title = { Text("AfA – Kaufpreisaufteilung", fontWeight = FontWeight.Bold) },\n                text = {\n                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {\n                        Text("Kaufpreis: ${NumberFormatter.format(taxPhase1.purchasePrice)}", fontWeight = FontWeight.Bold)\n                        Text("Gebäude: ${NumberFormatter.format(taxPhase1.buildingPurchaseShare)}")\n                        Text("Grund und Boden: ${NumberFormatter.format(taxPhase1.landPurchaseShare)}")\n                        Text("Quelle: ${taxPhase1.allocationSource}", fontSize = 11.sp, color = SlateGray)\n                        HorizontalDivider()\n                        Text("Anschaffungsnebenkosten", fontWeight = FontWeight.Bold)\n                        if (taxPhase1.acquisitionCostDetails.isEmpty()) {\n                            Text("Keine Belege der Kategorie Anschaffungskosten vorhanden.", fontSize = 11.sp, color = SlateGray)\n                        } else {\n                            taxPhase1.acquisitionCostDetails.forEach { item ->\n                                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))) {\n                                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {\n                                        Text("${item.displayId} • ${item.datum}", fontSize = 10.sp, fontWeight = FontWeight.Bold)\n                                        Text(item.description, fontSize = 10.sp)\n                                        Text("Gesamt: ${NumberFormatter.format(item.grossAmount)} • Gebäudeanteil: ${NumberFormatter.format(item.buildingAllocatedAmount)}", fontSize = 10.sp, color = SlateGray)\n                                    }\n                                }\n                            }\n                        }\n                    }\n                },\n                confirmButton = { TextButton(onClick = { showAfaDetails = false }) { Text("Schließen") } }\n            )\n        }\n\n        if (showMonitorDetails) {\n            AlertDialog(\n                onDismissRequest = { showMonitorDetails = false },\n                title = { Text("15%-Monitor – Belegprüfung", fontWeight = FontWeight.Bold) },\n                text = {\n                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {\n                        if (taxPhase1.monitorDetails.isEmpty()) {\n                            Text("Keine Renovierungs-/Reparaturbelege vorhanden.", color = SlateGray)\n                        } else {\n                            taxPhase1.monitorDetails.forEach { item ->\n                                Card(\n                                    colors = CardDefaults.cardColors(containerColor = if (item.included) Color(0xFFECFDF5) else Color(0xFFF8FAFC)),\n                                    border = BorderStroke(1.dp, if (item.included) EmeraldGreen.copy(alpha = 0.35f) else BorderColor)\n                                ) {\n                                    Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {\n                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {\n                                            Text("${item.displayId} • ${item.datum}", fontSize = 10.sp, fontWeight = FontWeight.Bold)\n                                            Text(if (item.included) "EINBEZOGEN" else "AUSGESCHLOSSEN", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (item.included) EmeraldGreen else SlateGray)\n                                        }\n                                        Text(item.description, fontSize = 10.sp)\n                                        Text(item.reason, fontSize = 9.sp, color = SlateGray)\n                                        if (item.included) Text("Netto Prüfwert: ${NumberFormatter.format(item.netAmount)}" + if (item.estimatedNet) " (geschätzt)" else "", fontSize = 10.sp, fontWeight = FontWeight.Bold)\n                                    }\n                                }\n                            }\n                        }\n                    }\n                },\n                confirmButton = { TextButton(onClick = { showMonitorDetails = false }) { Text("Schließen") } }\n            )\n        }\n\n''' + dialog_anchor
s = replace_once(s, dialog_anchor, dialogs, "dashboard tax dialogs")

# Property form state
s = replace_once(
    s,
    '''    var editGesamtKaufpreis by remember(metadata) { mutableStateOf(metadata.gesamtKaufpreis.toString()) }\n    var editGebaeudewert by remember(metadata) { mutableStateOf(metadata.gebaeudewert.toString()) }''',
    '''    var editGesamtKaufpreis by remember(metadata) { mutableStateOf(metadata.gesamtKaufpreis.toString()) }\n    var editGebaeudewert by remember(metadata) { mutableStateOf(metadata.gebaeudewert.toString()) }\n    var editGrundUndBodenWert by remember(metadata) { mutableStateOf(metadata.grundUndBodenWert.toString()) }''',
    "property form land state",
)

# Add completeness check
s = replace_once(
    s,
    '''                    Pair("Gesamtkaufpreis", editGesamtKaufpreis.toDoubleOrNull() != null && editGesamtKaufpreis.toDouble() > 0.0),\n                    Pair("Gebäudewert", editGebaeudewert.toDoubleOrNull() != null && editGebaeudewert.toDouble() > 0.0)''',
    '''                    Pair("Gesamtkaufpreis", editGesamtKaufpreis.toDoubleOrNull() != null && editGesamtKaufpreis.toDouble() > 0.0),\n                    Pair("Gebäudewert", editGebaeudewert.toDoubleOrNull() != null && editGebaeudewert.toDouble() > 0.0),\n                    Pair("Grund und Boden", editGrundUndBodenWert.toDoubleOrNull() != null && editGrundUndBodenWert.toDouble() >= 0.0)''',
    "property completeness land",
)

# Insert field after building field by locating its complete OutlinedTextField block and appending another.
pattern = re.compile(r'(\n\s*OutlinedTextField\(\n\s*value = editGebaeudewert,.*?testTag\("edit_property_gebaeudewert"\).*?\n\s*\)\n)', re.S)
m = pattern.search(s)
if not m:
    raise SystemExit("property building field block not found")
if 'edit_property_grund_boden' not in s:
    field = '''\n                    OutlinedTextField(\n                        value = editGrundUndBodenWert,\n                        onValueChange = { editGrundUndBodenWert = it },\n                        label = { Text("Grund und Boden (€)") },\n                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),\n                        modifier = Modifier.fillMaxWidth().testTag("edit_property_grund_boden"),\n                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)\n                    )\n                    val allocationDiffPreview = (editGesamtKaufpreis.toDoubleOrNull() ?: 0.0) - (editGebaeudewert.toDoubleOrNull() ?: 0.0) - (editGrundUndBodenWert.toDoubleOrNull() ?: 0.0)\n                    if (kotlin.math.abs(allocationDiffPreview) > 1.0) {\n                        Text("Hinweis: Gebäude + Grund/Boden weichen um ${NumberFormatter.format(allocationDiffPreview)} vom Kaufpreis ab.", fontSize = 10.sp, color = WarmOrange)\n                    }\n'''
    s = s[:m.end()] + field + s[m.end():]

# Save field
s = replace_once(
    s,
    '''                    val finalGesamtKaufpreis = editGesamtKaufpreis.toDoubleOrNull() ?: metadata.gesamtKaufpreis\n                    val finalGebaeudewert = editGebaeudewert.toDoubleOrNull() ?: metadata.gebaeudewert''',
    '''                    val finalGesamtKaufpreis = editGesamtKaufpreis.toDoubleOrNull() ?: metadata.gesamtKaufpreis\n                    val finalGebaeudewert = editGebaeudewert.toDoubleOrNull() ?: metadata.gebaeudewert\n                    val finalGrundUndBodenWert = editGrundUndBodenWert.toDoubleOrNull() ?: metadata.grundUndBodenWert''',
    "property save land parse",
)
s = replace_once(
    s,
    '''                        gesamtKaufpreis = finalGesamtKaufpreis,\n                        gebaeudewert = finalGebaeudewert''',
    '''                        gesamtKaufpreis = finalGesamtKaufpreis,\n                        gebaeudewert = finalGebaeudewert,\n                        grundUndBodenWert = finalGrundUndBodenWert,\n                        kaufpreisAufteilungQuelle = "MANUELL"''',
    "property save land fields",
)

UI.write_text(s, encoding="utf-8")
print("Tax phase 1 details patch applied")
