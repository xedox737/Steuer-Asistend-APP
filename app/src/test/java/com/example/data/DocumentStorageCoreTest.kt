package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentStorageCoreTest {
    private fun receipt(id: String = "BLG-2026-00127", vendor: String = "Hörn/Bäch GmbH", amount: Double = 84.5) = Receipt(
        aussteller = vendor, datum = "2026-09-04", uhrzeit = "", bruttobetrag = amount,
        hauptkategorie = "Sonstige Ausgaben", unterkategorie = "Sonstiges", kontoNr = "4970",
        beschreibung = "Test", internalId = "internal-1", displayId = id
    )

    @Test fun receiptFilenameUsesIsoDateAmountFriendlyIdAndRealExtension() {
        assertEquals("2026-09-04_Horn_Bach_GmbH_84-50EUR_BLG-2026-00127.pdf", DocumentFilenameGenerator.receipt(receipt(), ".PDF"))
    }

    @Test fun receiptFilenameDoesNotContainMutableClassification() {
        val name = DocumentFilenameGenerator.receipt(receipt(), "jpg")
        assertFalse(name.contains("Sonstige")); assertFalse(name.contains("4970"))
    }

    @Test fun longVendorIsBoundedAndStableIdAvoidsCollision() {
        val first = DocumentFilenameGenerator.receipt(receipt(id = "BLG-2026-1", vendor = "Ä".repeat(200)), "jpeg")
        val second = DocumentFilenameGenerator.receipt(receipt(id = "BLG-2026-2", vendor = "Ä".repeat(200)), "jpeg")
        assertTrue(first.length <= 124); assertNotEquals(first, second); assertTrue(first.endsWith(".jpg"))
    }

    @Test fun sameDateVendorAndAmountRemainUniqueThroughStableDisplayId() {
        val first = DocumentFilenameGenerator.receipt(receipt(id = "BLG-2026-00127"), "pdf")
        val second = DocumentFilenameGenerator.receipt(receipt(id = "BLG-2026-00128"), "pdf")
        assertNotEquals(first, second)
    }

    @Test fun isoDateAndDeterministicGermanFilenameAmountArePreserved() {
        val name = DocumentFilenameGenerator.receipt(receipt(amount = 1_234.5), "png")
        assertTrue(name.startsWith("2026-09-04_")); assertTrue(name.contains("1234-50EUR")); assertTrue(name.endsWith(".png"))
    }

    @Test fun receiptPathIsFlatYearFolderAndPropertyIdentityIsCanonical() {
        val route = DocumentDrivePathResolver.route("stable-property", "Haus", "Musterstraße 12", ManagedDocumentType.RECHNUNG, "2026-09-04")
        assertEquals(listOf("SteuerAssistent_Musterstrasse_12", "02_Belege", "2026"), route.segments)
        assertFalse(route.displayPath.contains("Kategorie")); assertTrue(route.canonicalKey.startsWith("property:stable-property"))
    }

    @Test fun unitDocumentUsesUnitFolderAndDocumentTypeMapping() {
        val route = DocumentDrivePathResolver.route("p", "Haus", "", ManagedDocumentType.MIETVERTRAG, "2026-01-01", "unit-1", "WE 01")
        assertEquals(listOf("SteuerAssistent_Haus", "01_Einheiten", "WE_01", "Mietvertrag"), route.segments)
    }

    @Test fun generalDocumentsWithSameVisibleFactsDoNotCollide() {
        val first = DocumentFilenameGenerator.document(ManagedDocumentType.KAUFVERTRAG, "2025-10-01", "Kaufvertrag", "pdf", "doc00001")
        val second = DocumentFilenameGenerator.document(ManagedDocumentType.KAUFVERTRAG, "2025-10-01", "Kaufvertrag", "pdf", "doc00002")
        assertNotEquals(first, second)
    }

    @Test fun targetMappingsCoverPermanentAndFinanceDocuments() {
        assertTrue(DocumentDrivePathResolver.route("p", "H", "", ManagedDocumentType.ENERGIEAUSWEIS, "").displayPath.endsWith("00_Stammdaten/03_Energie_Technik"))
        assertTrue(DocumentDrivePathResolver.route("p", "H", "", ManagedDocumentType.DARLEHENSVERTRAG, "").displayPath.endsWith("03_Finanzierung_AfA/Darlehen"))
        assertTrue(DocumentDrivePathResolver.route("p", "H", "", ManagedDocumentType.SANIERUNGSUNTERLAGE, "").displayPath.endsWith("04_Sanierungen"))
    }

    @Test fun onlyExplicitlyAcceptedAiValuesAreApplied() {
        val values = DocumentReviewPolicy.confirmedValues(listOf(
            DocumentFieldProposal("price", "Kaufpreis", "250000", decision = DocumentFieldDecision.AUSSTEHEND),
            DocumentFieldProposal("date", "Datum", "2025-10-01", decision = DocumentFieldDecision.UEBERNEHMEN),
            DocumentFieldProposal("seller", "Verkäufer", "X", decision = DocumentFieldDecision.IGNORIEREN)
        ))
        assertEquals(mapOf("date" to "2025-10-01"), values)
    }

    @Test fun explicitlyEditedAiValueIsTheOnlyConfirmedValue() {
        val values = DocumentReviewPolicy.confirmedValues(listOf(
            DocumentFieldProposal("kaufpreis", "Kaufpreis", "250000", decision = DocumentFieldDecision.UEBERNEHMEN, editedValue = "245000"),
            DocumentFieldProposal("adresse", "Adresse", "Falsch", decision = DocumentFieldDecision.IGNORIEREN)
        ))
        assertEquals(mapOf("kaufpreis" to "245000"), values)
    }

    @Test fun exactHashDetectsDuplicateButSameNameDifferentHashDoesNot() {
        val existing = ManagedDocument("one", "p", originalFilename = "same.pdf", sha256 = "aaa", fileSizeBytes = 3)
        val duplicate = ManagedDocument("two", "p", originalFilename = "other.pdf", sha256 = "aaa", fileSizeBytes = 3)
        val different = ManagedDocument("three", "p", originalFilename = "same.pdf", sha256 = "bbb", fileSizeBytes = 4)
        assertEquals(DocumentDuplicateKind.EXACT, DocumentDuplicatePolicy.detect(duplicate, listOf(existing)).kind)
        assertEquals(DocumentDuplicateKind.NONE, DocumentDuplicatePolicy.detect(different, listOf(existing)).kind)
    }

    @Test fun stableLegacyUnitIdentityDoesNotDependOnDisplayLabel() {
        assertEquals(StableDocumentIdentity.legacyUnitId("p", "WE 1"), StableDocumentIdentity.legacyUnitId("p", "  we   1 "))
    }
}
