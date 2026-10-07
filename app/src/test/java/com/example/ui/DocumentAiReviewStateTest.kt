package com.example.ui

import com.example.api.ManagedDocumentAiResult
import com.example.data.DocumentReviewStatus
import com.example.data.ManagedDocument
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DocumentAiReviewStateTest {
    private val a = ManagedDocument("a", "property-a", unitId = "unit-a")
    private val b = ManagedDocument("b", "property-b", unitId = "unit-b")
    private val store = DocumentAiReviewStore()

    @Test fun analyzingBPreservesPendingAAndBothKeepTheirContext() {
        store.offer(a, ManagedDocumentAiResult(documentDate = "2026-10-01"))
        val reviewA = store.state.value.pending.getValue("a")
        store.offer(b, ManagedDocumentAiResult(documentDate = "2026-10-02"))
        assertEquals(reviewA, store.state.value.pending["a"])
        assertTrue(reviewA.matches(a))
        assertFalse(reviewA.matches(b))
        assertEquals(2, store.state.value.pending.size)
        assertFalse(reviewA.matches(a.copy(propertyId = "property-b")))
        assertFalse(reviewA.matches(a.copy(unitId = "unit-b")))
    }

    @Test fun dismissAndReopenAOnlyChangeItsDialogVisibility() {
        store.offer(a, ManagedDocumentAiResult())
        store.offer(b, ManagedDocumentAiResult())
        store.dismiss("a")
        assertEquals(2, store.state.value.pending.size)
        assertEquals(setOf("a"), store.state.value.dismissed)
        store.reopen("a")
        assertTrue(store.state.value.dismissed.isEmpty())
    }

    @Test fun reanalysisReplacesOnlyAAndRejectsOldConfirmationEvenForIdenticalResults() {
        val result = ManagedDocumentAiResult()
        store.offer(a, result)
        val old = store.state.value.pending.getValue("a")
        store.offer(b, result)
        val reviewB = store.state.value.pending.getValue("b")
        store.dismiss("a")
        store.offer(a, result)
        assertFalse(store.beginConfirmation(old))
        store.remove("a", old.revision)
        assertNotEquals(old.revision, store.state.value.pending.getValue("a").revision)
        assertEquals(reviewB, store.state.value.pending["b"])
        assertFalse("a" in store.state.value.dismissed)
    }

    @Test fun repeatedAnalyzeClicksAndConfirmClicksDoNotStartDuplicateOperations() {
        store.offer(a, ManagedDocumentAiResult())
        val review = store.state.value.pending.getValue("a")
        assertTrue(store.beginAnalysis("a"))
        assertFalse(store.beginAnalysis("a"))
        assertTrue(store.beginAnalysis("b"))
        assertFalse(store.beginConfirmation(review))
        store.endAnalysis("a")
        assertTrue(store.beginConfirmation(review))
        assertFalse(store.beginConfirmation(review))
        assertFalse(store.beginAnalysis("a"))
        store.remove("a", review.revision)
        store.endConfirmation("a")
        assertFalse("a" in store.state.value.pending)
        assertTrue(store.beginAnalysis("a"))
    }

    @Test fun failedReanalysisPreservesPriorResultAndMessageDoesNotLeakToB() {
        store.offer(a, ManagedDocumentAiResult())
        val review = store.state.value.pending.getValue("a")
        store.beginAnalysis("a")
        store.message("a", "Die Dokumentanalyse ist fehlgeschlagen.")
        store.endAnalysis("a")
        assertEquals(review, store.state.value.pending["a"])
        assertNull(store.state.value.messages["b"])
        assertTrue(store.state.value.analyzing.isEmpty())
    }

    @Test fun openingReassignedDocumentInvalidatesOldContextInsteadOfShowingItsReview() {
        store.offer(a, ManagedDocumentAiResult())
        store.restore(a.copy(unitId = "new-unit"))
        assertFalse("a" in store.state.value.pending)
        assertTrue(store.state.value.messages.getValue("a").contains("Zuordnung"))
    }

    @Test fun persistedPendingAnalysisCanBeOpenedAgainWithoutAnalyzingOrOverwritingOtherReview() {
        val persisted = a.copy(extractedFieldsJson = """{"documentType":"MIETVERTRAG","confidence":0.9,"documentDate":"2026-10-01","suggestedUnitId":"unit-a","fields":[{"key":"kaltmiete","label":"Kaltmiete","value":"690","confidence":0.9}]}""")
        store.offer(b, ManagedDocumentAiResult())
        store.restore(persisted)
        val review = store.state.value.pending.getValue("a")
        assertEquals("690", review.result.fields.single().value)
        store.dismiss("a")
        store.restore(persisted)
        assertEquals(review, store.state.value.pending["a"])
        assertTrue("a" in store.state.value.dismissed)
        assertTrue("b" in store.state.value.pending)
        assertNull(pendingDocumentAnalysis(persisted.copy(reviewStatus = DocumentReviewStatus.GEPRUEFT.name)))
        assertNull(pendingDocumentAnalysis(persisted.copy(extractedFieldsJson = "broken")))
        assertNull(pendingDocumentAnalysis(persisted.copy(extractedFieldsJson = "{}")))
        assertTrue(store.beginConfirmation(review))
        store.remove("a", review.revision)
        store.restore(persisted)
        assertFalse("a" in store.state.value.pending)
        store.endConfirmation("a")
    }
}
