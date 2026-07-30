package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Representiert ein mandats- und kanzleiabhängiges DATEV-Profil.
 */
data class DatevProfile(
    val profileId: String = "DEFAULT_SKR03",
    val profileName: String = "Standard SKR03 (Anlage V Vermietung)",
    val version: Int = 1,
    val beraterNummer: String = "1111111",
    val mandantenNummer: String = "11111",
    val mandantenName: String = "Sergej Gerweck",
    val wirtschaftsjahrBeginn: String = "2026-01-01",
    val sachkontenLaenge: Int = 4, // 4 bis 8
    val kontenrahmen: String = "SKR03", // "SKR03", "SKR04", "INDIVIDUELL"
    val waehrung: String = "EUR",
    val festschreibungskennzeichen: Boolean = false,
    val belegnummernlogik: String = "RECHNUNGSNR_OR_ID",
    val standardBuchungstext: String = "Anlage V - Vermietung",
    val standardGegenkontoEinnahmen: String = "10000",
    val standardGegenkontoAusgaben: String = "70000",
    val defaultEinnahmenKonto: String = "8100", // SKR03 8100 Miete, SKR04 4100
    val defaultAusgabenKonto: String = "4801",  // SKR03 4801 Reparat., SKR04 6470
    val categoryKontoMapJson: String = "{}",    // JSON Map<CategoryString, AccountString>
    val kost1Logic: String = "OBJEKT",          // "OBJEKT", "WOHNEINHEIT", "NONE"
    val kost2Logic: String = "WOHNEINHEIT",     // "WOHNEINHEIT", "NONE"
    val buSchluesselNormalMwst: String = "9",   // BU-Schlüssel USt 19%
    val buSchluesselErmaessigtMwst: String = "8",// BU-Schlüssel USt 7%
    val privateShareExportMode: String = "SEPARATE_BOOKING_ROW", // "SEPARATE_BOOKING_ROW" or "EXCLUDE_WITH_DOCUMENTATION"
    val lastModified: Long = System.currentTimeMillis()
) {
    companion object {
        fun createDefaultSkr03(): DatevProfile {
            val map = mapOf(
                "Miete, Nebenkosten & Kaution" to "8100",
                "Mieteinnahmen Wohnraum (steuerfrei § 4 Nr. 12 UStG)" to "8100",
                "Umlagen / Nebenkostenvorauszahlungen" to "8110",
                "Kautionen (Treulandkonto)" to "1590",
                "Sonstige Einnahmen" to "8105",
                "Instandhaltung & Reparaturen" to "4801",
                "Erweiterung & Modernisierung (Anschaffungsnah)" to "4802",
                "Zinsen & Geldbeschaffungskosten" to "2110",
                "Grundsteuer & öffentliche Abgaben" to "4360",
                "Gebäudeversicherung & Haftpflicht" to "4360",
                "Heizungs- & Warmwasserkosten" to "4210",
                "Strom & Energie Allgemeinstrom" to "4210",
                "Müllabfuhr & Straßenreinigung" to "4210",
                "Wasser & Abwasser" to "4210",
                "Hausmeister & Gartenpflege" to "4210",
                "Schornsteinfeger" to "4210",
                "Verwaltungskosten & Hausverwaltung" to "4950",
                "Rechts- & Beratungskosten" to "4950",
                "Fahrtkosten & Reisekosten" to "4670",
                "Büromaterial & Porto" to "4910",
                "Geringwertige Wirtschaftsgüter (GWG)" to "0480",
                "Sonstige Werbungskosten" to "4900"
            )
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            val jsonAdapter = moshi.adapter(Map::class.java)
            val jsonStr = try { jsonAdapter.toJson(map) } catch (e: Exception) { "{}" }

            return DatevProfile(
                profileId = "DEFAULT_SKR03",
                profileName = "Standard SKR03 (Anlage V Vermietung)",
                version = 1,
                kontenrahmen = "SKR03",
                categoryKontoMapJson = jsonStr
            )
        }

        fun createDefaultSkr04(): DatevProfile {
            val map = mapOf(
                "Miete, Nebenkosten & Kaution" to "4100",
                "Mieteinnahmen Wohnraum (steuerfrei § 4 Nr. 12 UStG)" to "4100",
                "Umlagen / Nebenkostenvorauszahlungen" to "4110",
                "Kautionen (Treulandkonto)" to "1340",
                "Sonstige Einnahmen" to "4105",
                "Instandhaltung & Reparaturen" to "6470",
                "Erweiterung & Modernisierung (Anschaffungsnah)" to "6471",
                "Zinsen & Geldbeschaffungskosten" to "7310",
                "Grundsteuer & öffentliche Abgaben" to "6430",
                "Gebäudeversicherung & Haftpflicht" to "6430",
                "Heizungs- & Warmwasserkosten" to "6420",
                "Strom & Energie Allgemeinstrom" to "6420",
                "Müllabfuhr & Straßenreinigung" to "6420",
                "Wasser & Abwasser" to "6420",
                "Hausmeister & Gartenpflege" to "6420",
                "Schornsteinfeger" to "6420",
                "Verwaltungskosten & Hausverwaltung" to "6825",
                "Rechts- & Beratungskosten" to "6825",
                "Fahrtkosten & Reisekosten" to "6670",
                "Büromaterial & Porto" to "6800",
                "Geringwertige Wirtschaftsgüter (GWG)" to "0680",
                "Sonstige Werbungskosten" to "6850"
            )
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            val jsonAdapter = moshi.adapter(Map::class.java)
            val jsonStr = try { jsonAdapter.toJson(map) } catch (e: Exception) { "{}" }

            return DatevProfile(
                profileId = "DEFAULT_SKR04",
                profileName = "Standard SKR04 (Anlage V Vermietung)",
                version = 1,
                kontenrahmen = "SKR04",
                defaultEinnahmenKonto = "4100",
                defaultAusgabenKonto = "6470",
                categoryKontoMapJson = jsonStr
            )
        }
    }
}
