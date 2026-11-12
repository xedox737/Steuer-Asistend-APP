package com.example.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentConfidenceReviewPolicyTest {
    @Test fun lowConfidenceAndInvalidValuesRequireExtraReview() {
        assertTrue(DocumentConfidenceReviewPolicy.requiresSpecialReview(0.42))
        assertTrue(DocumentConfidenceReviewPolicy.requiresSpecialReview(Double.NaN))
        assertTrue(DocumentConfidenceReviewPolicy.requiresSpecialReview(Double.NEGATIVE_INFINITY))
    }

    @Test fun highConfidenceRemainsNormalReviewNotAutoApproval() {
        assertFalse(DocumentConfidenceReviewPolicy.requiresSpecialReview(0.75))
        assertFalse(DocumentConfidenceReviewPolicy.requiresSpecialReview(0.95))
    }
}
