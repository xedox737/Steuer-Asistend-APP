package com.example.ui

import com.example.api.ManagedDocumentAiResult
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
