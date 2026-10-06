package com.example.ui

import com.example.api.ManagedDocumentAiResult
import com.example.data.ManagedDocument
import com.example.data.PropertyMetadata
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
