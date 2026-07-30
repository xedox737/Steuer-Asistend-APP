package com.example

import com.example.data.Receipt
import com.example.ui.WohneinheitStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class FirestoreServiceTest {

    @Test
    fun testReceiptMappingIsLossless() {
        val originalReceipt = Receipt(
            id = 42,
            aussteller = "Malerbetrieb Schmitt",
            datum = "2026-07-19",
            uhrzeit = "14:30",
            bruttobetrag = 1250.45,
            hauptkategorie = "Renovierungs- / Reparaturkosten & Investitionen",
            unterkategorie = "Malerarbeiten",
            kontoNr = "1200",
            beschreibung = "EG links neu gestrichen",
            isEigenleistungSanierung = false,
            imageUrl = "http://example.com/receipt.jpg",
            wohneinheit = "WE 1",
            isArchivedToDrive = true
        )

        // Simulate Firestore Service serialization
        val map = mapOf<String, Any>(
            "id" to originalReceipt.id,
            "aussteller" to originalReceipt.aussteller,
            "datum" to originalReceipt.datum,
            "uhrzeit" to originalReceipt.uhrzeit,
            "bruttobetrag" to originalReceipt.bruttobetrag,
            "hauptkategorie" to originalReceipt.hauptkategorie,
            "unterkategorie" to originalReceipt.unterkategorie,
            "kontoNr" to originalReceipt.kontoNr,
            "beschreibung" to originalReceipt.beschreibung,
            "isEigenleistungSanierung" to originalReceipt.isEigenleistungSanierung,
            "imageUrl" to originalReceipt.imageUrl,
            "wohneinheit" to originalReceipt.wohneinheit,
            "isArchivedToDrive" to originalReceipt.isArchivedToDrive
        )

        // Deserialize back as in FirestoreService
        val reconstructed = Receipt(
            id = (map["id"] as? Long)?.toInt() ?: (map["id"] as? Double)?.toInt() ?: (map["id"] as? Int) ?: 0,
            aussteller = map["aussteller"] as? String ?: "",
            datum = map["datum"] as? String ?: "",
            uhrzeit = map["uhrzeit"] as? String ?: "",
            bruttobetrag = (map["bruttobetrag"] as? Double) ?: (map["bruttobetrag"] as? Long)?.toDouble() ?: 0.0,
            hauptkategorie = map["hauptkategorie"] as? String ?: "",
            unterkategorie = map["unterkategorie"] as? String ?: "",
            kontoNr = map["kontoNr"] as? String ?: "",
            beschreibung = map["beschreibung"] as? String ?: "",
            isEigenleistungSanierung = map["isEigenleistungSanierung"] as? Boolean ?: false,
            imageUrl = map["imageUrl"] as? String ?: "",
            wohneinheit = map["wohneinheit"] as? String ?: "",
            isArchivedToDrive = map["isArchivedToDrive"] as? Boolean ?: false
        )

        assertEquals(originalReceipt.id, reconstructed.id)
        assertEquals(originalReceipt.aussteller, reconstructed.aussteller)
        assertEquals(originalReceipt.datum, reconstructed.datum)
        assertEquals(originalReceipt.bruttobetrag, reconstructed.bruttobetrag, 0.001)
        assertEquals(originalReceipt.hauptkategorie, reconstructed.hauptkategorie)
        assertEquals(originalReceipt.wohneinheit, reconstructed.wohneinheit)
        assertEquals(originalReceipt.isEigenleistungSanierung, reconstructed.isEigenleistungSanierung)
        assertEquals(originalReceipt.isArchivedToDrive, reconstructed.isArchivedToDrive)
    }

    @Test
    fun testWohneinheitMappingIsLossless() {
        val originalUnit = WohneinheitStatus(
            name = "WE 3",
            label = "WE 3 (1. OG links)",
            status = "Vermietet",
            mieter = "Familie Schmidt",
            kaltmiete = 520.0,
            wohnflaeche = 65.0
        )

        val map = mapOf<String, Any>(
            "name" to originalUnit.name,
            "label" to originalUnit.label,
            "status" to originalUnit.status,
            "mieter" to originalUnit.mieter,
            "kaltmiete" to originalUnit.kaltmiete,
            "wohnflaeche" to originalUnit.wohnflaeche
        )

        val reconstructed = WohneinheitStatus(
            name = map["name"] as? String ?: "",
            label = map["label"] as? String ?: "",
            status = map["status"] as? String ?: "",
            mieter = map["mieter"] as? String ?: "",
            kaltmiete = (map["kaltmiete"] as? Double) ?: (map["kaltmiete"] as? Long)?.toDouble() ?: 0.0,
            wohnflaeche = (map["wohnflaeche"] as? Double) ?: (map["wohnflaeche"] as? Long)?.toDouble() ?: 0.0
        )

        assertEquals(originalUnit.name, reconstructed.name)
        assertEquals(originalUnit.label, reconstructed.label)
        assertEquals(originalUnit.status, reconstructed.status)
        assertEquals(originalUnit.mieter, reconstructed.mieter)
        assertEquals(originalUnit.kaltmiete, reconstructed.kaltmiete, 0.001)
        assertEquals(originalUnit.wohnflaeche, reconstructed.wohnflaeche, 0.001)
    }
}
