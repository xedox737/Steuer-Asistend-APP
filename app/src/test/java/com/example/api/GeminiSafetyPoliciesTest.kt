package com.example.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiSafetyPoliciesTest {
    @Test
    fun retriesOnly429And503WithBoundedExponentialBackoff() {
        assertTrue(GeminiRetryPolicy.shouldRetry(429, 0))
        assertTrue(GeminiRetryPolicy.shouldRetry(503, 1))
        assertFalse(GeminiRetryPolicy.shouldRetry(503, 2))
        assertFalse(GeminiRetryPolicy.shouldRetry(400, 0))
        assertFalse(GeminiRetryPolicy.shouldRetry(401, 0))
        assertEquals(500L, GeminiRetryPolicy.delayMillis(0))
        assertEquals(1_000L, GeminiRetryPolicy.delayMillis(1))
    }

    @Test
    fun receiptPageAndImageLimitsAreEnforced() {
        assertTrue(GeminiReceiptSizePolicy.isAllowed(20, 20L * 1024 * 1024))
        assertFalse(GeminiReceiptSizePolicy.isAllowed(21, 1))
        assertFalse(GeminiReceiptSizePolicy.isAllowed(1, 20L * 1024 * 1024 + 1))
    }
}
