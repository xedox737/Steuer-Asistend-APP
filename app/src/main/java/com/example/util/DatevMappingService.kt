package com.example.util

import com.example.data.BookingRecord
import com.example.data.DatevProfile
import com.example.data.Receipt
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.Locale
import java.util.UUID

data class ReceiptAllocation(
    val receiptId: Int,
    val propertyId: String,          // Immobilie / Objekt
    val unitId: String,              // Wohneinheit / Kostenstelle 2
    val projectId: String,           // Maßnahme / Projekt
    val costCategory: String,        // Kostenart (Hauptkategorie)
    val subCategory: String,         // Unterkategorie
    val account: String,             // Sachkonto
    val counterAccount: String,      // Gegenkonto
    val taxKey: String,              // BU-/Steuerschlüssel
    val vatRate: Double,             // USt-Satz
    val privateSharePercent: Double, // Privatanteil
    val debitCredit: String,         // Soll/Haben ("S" oder "H")
    val currency: String = "EUR",
    val amountInCents: Long,         // Exakter Cent-Betrag
    val description: String,         // Artikelbezeichnung oder Beschreibung
    val isUnallocated: Boolean = false
) {
    fun createGroupKey(): String {
        return listOf(
            receiptId,
            propertyId.ifBlank { "OBJEKT_ALLG" },
            unitId.ifBlank { "GESAMT" },
            projectId.ifBlank { "OHNE_MASSNAHME" },
            costCategory.ifBlank { "OHNE_KOSTENART" },
            subCategory,
            account,
            counterAccount,
            taxKey,
            vatRate,
            privateSharePercent,
            debitCredit,
            currency
        ).joinToString("|")
    }
}

object DatevMappingService {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private fun parseCategoryMap(json: String): Map<String, String> {
        return try {
            val adapter = moshi.adapter(Map::class.java)
            val raw = adapter.fromJson(json) as? Map<String, String>
            raw ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Erzeugt aus einem Receipt und seinen Positionen die buchhalterischen Einzelaufteilungen.
     */
    fun buildAllocationsFromReceipt(receipt: Receipt, profile: DatevProfile): List<ReceiptAllocation> {
        val catMap = parseCategoryMap(profile.categoryKontoMapJson)
        val isIncome = receipt.hauptkategorie.contains("Einnahmen", ignoreCase = true) ||
                receipt.hauptkategorie.contains("Miete", ignoreCase = true)
        val isCreditNote = receipt.bruttobetrag < 0 ||
                receipt.beschreibung.contains("Gutschrift", ignoreCase = true) ||
                receipt.beschreibung.contains("Storno", ignoreCase = true)

        val totalCents = Math.round(Math.abs(receipt.bruttobetrag) * 100.0)

        val propertyId = profile.profileName.take(30)
        val defaultUnit = receipt.wohneinheit.ifBlank { "Gesamtobjekt / Allgemein" }

        val defaultAccount = when {
            receipt.kontoNr.isNotBlank() && receipt.kontoNr.length == profile.sachkontenLaenge -> receipt.kontoNr
            catMap.containsKey(receipt.hauptkategorie) -> catMap[receipt.hauptkategorie] ?: ""
            catMap.containsKey(receipt.unterkategorie) -> catMap[receipt.unterkategorie] ?: ""
            isIncome -> profile.defaultEinnahmenKonto
            else -> profile.defaultAusgabenKonto
        }.ifBlank { if (isIncome) profile.defaultEinnahmenKonto else profile.defaultAusgabenKonto }

        val defaultCounterAccount = if (isIncome) profile.standardGegenkontoEinnahmen else profile.standardGegenkontoAusgaben
        val debitCredit = if (isIncome) (if (isCreditNote) "S" else "H") else (if (isCreditNote) "H" else "S")

        val positions = receipt.getPositionenList()

        if (positions.isEmpty()) {
            val isPrivat = receipt.hauptkategorie.contains("Privat", ignoreCase = true) || receipt.unterkategorie.contains("Privat", ignoreCase = true)
            return listOf(
                ReceiptAllocation(
                    receiptId = receipt.id,
                    propertyId = propertyId,
                    unitId = defaultUnit,
                    projectId = "OHNE_MASSNAHME",
                    costCategory = receipt.hauptkategorie,
                    subCategory = receipt.unterkategorie,
                    account = defaultAccount,
                    counterAccount = defaultCounterAccount,
                    taxKey = "",
                    vatRate = 0.0,
                    privateSharePercent = if (isPrivat) 100.0 else 0.0,
                    debitCredit = debitCredit,
                    currency = profile.waehrung,
                    amountInCents = totalCents,
                    description = receipt.beschreibung.ifBlank { receipt.aussteller },
                    isUnallocated = receipt.hauptkategorie.isBlank()
                )
            )
        }

        // Multiple OCR positions
        val rawSumCents = positions.sumOf { Math.round(Math.abs(it.gesamtpreis) * 100.0) }
        val useScaling = rawSumCents > 0 && Math.abs(rawSumCents - totalCents) > 1 // > 1 Cent difference

        var runningCents = 0L
        val allocations = mutableListOf<ReceiptAllocation>()

        positions.forEachIndexed { idx, item ->
            val itemCat = item.hauptkategorie.ifBlank { receipt.hauptkategorie }
            val itemSub = item.unterkategorie.ifBlank { receipt.unterkategorie }
            val itemUnit = item.wohneinheit.ifBlank { defaultUnit }
            val itemProject = item.massnahme.ifBlank { "OHNE_MASSNAHME" }

            val itemAccount = when {
                item.kontoNr.isNotBlank() && item.kontoNr.length == profile.sachkontenLaenge -> item.kontoNr
                catMap.containsKey(itemCat) -> catMap[itemCat] ?: ""
                catMap.containsKey(itemSub) -> catMap[itemSub] ?: ""
                else -> defaultAccount
            }.ifBlank { defaultAccount }

            val itemTaxKey = item.buSchluessel.ifBlank { "" }
            val itemVatRate = item.steuersatz
            val isPrivat = itemCat.contains("Privat", ignoreCase = true) || itemSub.contains("Privat", ignoreCase = true)
            val itemPrivatPercent = if (isPrivat) 100.0 else item.privatanteilProzent

            val itemCents = if (idx == positions.size - 1) {
                // Ensure total sum equals exact totalCents
                totalCents - runningCents
            } else {
                val rawItemCents = Math.round(Math.abs(item.gesamtpreis) * 100.0)
                val computed = if (useScaling && rawSumCents > 0) {
                    Math.round((rawItemCents.toDouble() / rawSumCents.toDouble()) * totalCents)
                } else {
                    rawItemCents
                }
                runningCents += computed
                computed
            }

            allocations.add(
                ReceiptAllocation(
                    receiptId = receipt.id,
                    propertyId = propertyId,
                    unitId = itemUnit,
                    projectId = itemProject,
                    costCategory = itemCat,
                    subCategory = itemSub,
                    account = itemAccount,
                    counterAccount = defaultCounterAccount,
                    taxKey = itemTaxKey,
                    vatRate = itemVatRate,
                    privateSharePercent = itemPrivatPercent,
                    debitCredit = debitCredit,
                    currency = profile.waehrung,
                    amountInCents = itemCents,
                    description = item.bezeichnung.ifBlank { "Position #${idx + 1}" },
                    isUnallocated = itemCat.isBlank()
                )
            )
        }

        return allocations
    }

    /**
     * Gruppiert buchhalterisch identische Aufteilungen zu zusammengefassten DATEV-Buchungszeilen.
     */
    fun buildDatevBookingRows(
        receipt: Receipt,
        profile: DatevProfile,
        exportlaufId: String = ""
    ): List<BookingRecord> {
        val allAllocations = buildAllocationsFromReceipt(receipt, profile)

        // Filter private shares if configured to exclude with documentation
        val exportableAllocations = if (profile.privateShareExportMode == "EXCLUDE_WITH_DOCUMENTATION") {
            allAllocations.filter { it.privateSharePercent < 100.0 && !it.costCategory.contains("Privat", ignoreCase = true) }
        } else {
            allAllocations
        }

        if (exportableAllocations.isEmpty()) {
            return emptyList()
        }

        // Group by groupKey
        val groupedMap = exportableAllocations.groupBy { it.createGroupKey() }

        val rawGuid = UUID.nameUUIDFromBytes("RECEIPT_${receipt.id}_${receipt.datum}".toByteArray(Charsets.UTF_8))
            .toString().replace("-", "").uppercase(Locale.GERMANY)
        val belegfeld1 = if (rawGuid.length >= 12) "BLG-${receipt.id}-${rawGuid.take(6)}" else "BLG-${receipt.id}"

        val kost1 = when (profile.kost1Logic) {
            "OBJEKT" -> profile.profileName.take(15)
            "WOHNEINHEIT" -> receipt.wohneinheit.ifBlank { "ALLG" }
            else -> ""
        }

        val records = mutableListOf<BookingRecord>()

        groupedMap.forEach { (groupKey, groupItems) ->
            val sumCents = groupItems.sumOf { it.amountInCents }
            val amountEur = sumCents / 100.0

            val sample = groupItems.first()

            // Construct Buchungstext
            val text = when {
                sample.costCategory.contains("Privat", ignoreCase = true) || sample.privateSharePercent == 100.0 ->
                    "Privatanteil ${receipt.aussteller}"
                sample.subCategory.isNotBlank() && sample.subCategory != sample.costCategory ->
                    "${sample.subCategory} ${if (sample.unitId != "Gesamtobjekt / Allgemein" && sample.unitId != "GESAMT") sample.unitId else ""}".trim()
                sample.projectId.isNotBlank() && sample.projectId != "OHNE_MASSNAHME" ->
                    "${sample.costCategory} ${sample.projectId}".trim()
                else ->
                    "${receipt.aussteller}: ${sample.costCategory}"
            }.take(60)

            val kost2 = when (profile.kost2Logic) {
                "WOHNEINHEIT" -> sample.unitId.ifBlank { "ALLG" }
                else -> ""
            }

            records.add(
                BookingRecord(
                    bookingId = "GRP_${receipt.id}_${Math.abs(groupKey.hashCode())}",
                    receiptId = receipt.id,
                    originalFileId = receipt.imageUrl,
                    belegnummer = belegfeld1,
                    belegdatum = receipt.datum,
                    buchungsdatum = receipt.datum,
                    zahlungspartner = receipt.aussteller,
                    beschreibung = text,
                    bruttobetrag = Math.round(amountEur * 100.0) / 100.0,
                    sollHaben = sample.debitCredit,
                    waehrung = sample.currency,
                    sachkonto = sample.account,
                    gegenkonto = sample.counterAccount,
                    buSchluessel = sample.taxKey,
                    belegfeld1 = belegfeld1,
                    kost1 = kost1,
                    kost2 = kost2,
                    wohneinheitId = sample.unitId,
                    hauptkategorie = sample.costCategory,
                    unterkategorie = sample.subCategory,
                    steuerlichesJahr = try { receipt.datum.take(4).toInt() } catch (e: Exception) { profile.wirtschaftsjahrBeginn.take(4).toInt() },
                    exportStatus = receipt.exportStatus.ifBlank { "EXPORTBEREIT" },
                    pruefstatus = receipt.pruefstatus.ifBlank { "GEPRUEFT" },
                    exportlaufId = exportlaufId,
                    kanzleiprofilVersion = profile.version
                )
            )
        }

        return records
    }

    fun mapReceiptToBookingRecords(
        receipt: Receipt,
        profile: DatevProfile,
        exportlaufId: String = ""
    ): List<BookingRecord> {
        return buildDatevBookingRows(receipt, profile, exportlaufId)
    }
}
