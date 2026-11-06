package com.example.ui

internal object DocumentConfidenceReviewPolicy {
    // Heuristic visual attention threshold, not a claim about model calibration or accuracy.
    const val ATTENTION_THRESHOLD = 0.75
    fun requiresSpecialReview(confidence: Double): Boolean =
        !confidence.isFinite() || confidence < ATTENTION_THRESHOLD
}
