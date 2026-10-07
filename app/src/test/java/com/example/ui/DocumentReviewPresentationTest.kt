package com.example.ui

import com.example.api.ManagedDocumentAiResult
import com.example.data.ManagedDocument
import com.example.data.PropertyMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentReviewPresentationTest {
    private val review = "document-a" to ManagedDocumentAiResult()

    @Test
    fun pendingReviewIsVisibleOnlyForMatchingDocumentId() {
        assertTrue(DocumentReviewPresentation.isPendingFor("document-a", review))
        assertFalse(DocumentReviewPresentation.isPendingFor("document-b", review))
        assertFalse(DocumentReviewPresentation.isPendingFor("document-a", null))
    }

    @Test
    fun documentPropertyWinsOverGloballySelectedProperty() {
        val propertyA = PropertyMetadata(propertyId = "property-a", name = "A")
        val propertyB = PropertyMetadata(propertyId = "property-b", name = "B")
        val documentB = ManagedDocument(documentId = "document-b", propertyId = "property-b")

        val resolved = DocumentReviewPresentation.propertyFor(
            documentB,
            listOf(propertyA, propertyB),
            propertyA
        )

        assertTrue(resolved?.propertyId == "property-b")
    }


    @Test
    fun reviewContextRejectsGloballySelectedForeignProperty() {
        val propertyA = PropertyMetadata(propertyId = "property-a", name = "A")
        val documentB = ManagedDocument(documentId = "document-b", propertyId = "property-b")

        val result = DocumentReviewContextPolicy.validate(
            document = documentB,
            property = propertyA,
            units = emptyList(),
            requestedUnitId = null
        )

        assertFalse(result.allowed)
        assertTrue(result.error.orEmpty().contains("Immobilie"))
    }

    @Test
    fun reviewContextAcceptsOnlyStableUnitIdFromDocumentProperty() {
        val propertyB = PropertyMetadata(propertyId = "property-b", name = "B")
        val documentB = ManagedDocument(documentId = "document-b", propertyId = "property-b")
        val unitB = WohneinheitStatus(
            name = "WE 01",
            label = "WE 01",
            status = "Vermietet",
            mieter = "B",
            kaltmiete = 850.0,
            wohnflaeche = 60.0,
            unitId = "unit-b"
        )

        val accepted = DocumentReviewContextPolicy.validate(
            document = documentB,
            property = propertyB,
            units = listOf(unitB),
            requestedUnitId = "unit-b"
        )
        val rejected = DocumentReviewContextPolicy.validate(
            document = documentB,
            property = propertyB,
            units = listOf(unitB),
            requestedUnitId = "unit-a"
        )

        assertTrue(accepted.allowed)
        assertEquals("unit-b", accepted.confirmedUnitId)
        assertFalse(rejected.allowed)
        assertTrue(rejected.error.orEmpty().contains("Wohneinheit"))
    }

    @Test
    fun dismissedReviewStaysPendingButDialogDoesNotLeakToOtherDocument() {
        assertFalse(
            DocumentReviewPresentation.shouldShowDialog(
                "document-a",
                review,
                setOf("document-a")
            )
        )
        assertFalse(
            DocumentReviewPresentation.shouldShowDialog(
                "document-b",
                review,
                emptySet()
            )
        )
        assertTrue(
            DocumentReviewPresentation.shouldShowDialog(
                "document-a",
                review,
                emptySet()
            )
        )
    }
}
