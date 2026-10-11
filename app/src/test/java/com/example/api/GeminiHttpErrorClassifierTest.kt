package com.example.api

import org.junit.Assert.*
import org.junit.Test

class GeminiHttpErrorClassifierTest {
    private fun response(status: String, message: String, reason: String? = null): String {
        val details = reason?.let { ""","details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"$it"}]""" } ?: ""
        return """{"error":{"code":400,"status":"$status","message":"$message"$details}}"""
    }

    @Test fun invalidKeyOn400IsNeverSizeError() {
        val byReason = GeminiHttpErrorClassifier.classify(400, response("INVALID_ARGUMENT", "Bad configuration", "API_KEY_INVALID"))
        val byMessage = GeminiHttpErrorClassifier.classify(400, response("INVALID_ARGUMENT", "API key not valid. Please pass a valid API key."))
        assertTrue(byReason is GeminiAnalysisException.KeyInvalid)
        assertTrue(byMessage is GeminiAnalysisException.KeyInvalid)
        assertFalse(byReason is GeminiAnalysisException.RequestTooLarge)
        assertTrue(byReason.userMessage.contains("API-Key"))
        assertFalse(byReason.userMessage.contains("Anfrage zu groß"))
    }

    @Test fun actualPayloadErrorRemainsSizeError() {
        assertTrue(GeminiHttpErrorClassifier.classify(400, response("INVALID_ARGUMENT", "Request payload size exceeds the limit"))
            is GeminiAnalysisException.RequestTooLarge)
        assertTrue(GeminiHttpErrorClassifier.classify(400, response("INVALID_ARGUMENT", "bad request", "PAYLOAD_TOO_LARGE"))
            is GeminiAnalysisException.RequestTooLarge)
    }

    @Test fun invalidArgumentAndBadBodiesAreSafe() {
        for (body in listOf(response("INVALID_ARGUMENT", "Unrecognized parameter"), "", "not-json", "{}")) {
            val exception = GeminiHttpErrorClassifier.classify(400, body)
            assertTrue(exception is GeminiAnalysisException.GenericError)
            assertEquals("INVALID_REQUEST", exception.errorCode)
            assertFalse(exception.userMessage.contains("Anfrage zu groß"))
        }
    }

    @Test fun authPermissionQuotaAndRetryCodes() {
        assertTrue(GeminiHttpErrorClassifier.classify(401, "") is GeminiAnalysisException.KeyInvalid)
        assertTrue(GeminiHttpErrorClassifier.classify(403, "") is GeminiAnalysisException.PermissionDenied)
        assertTrue(GeminiHttpErrorClassifier.classify(429, "") is GeminiAnalysisException.QuotaExceeded)
        assertTrue(GeminiHttpErrorClassifier.classify(400, response("PERMISSION_DENIED", "not allowed"))
            is GeminiAnalysisException.PermissionDenied)
        assertTrue(GeminiRetryPolicy.shouldRetry(429, 0))
        assertTrue(GeminiRetryPolicy.shouldRetry(503, 0))
        assertFalse(GeminiRetryPolicy.shouldRetry(503, 2))
        assertFalse(GeminiRetryPolicy.shouldRetry(400, 0))
        assertFalse(GeminiRetryPolicy.shouldRetry(401, 0))
        assertFalse(GeminiRetryPolicy.shouldRetry(403, 0))
    }

    @Test fun diagnosticsNeverContainCredentialsOrBackendMessage() {
        val key = "AIzaSy" + "A".repeat(33)
        val response = response("INVALID_ARGUMENT", "API key not valid: $key")
        val exception = GeminiHttpErrorClassifier.classify(400, response, key)
        assertFalse(exception.cleanedMessage.orEmpty().contains(key))
        assertFalse(exception.cleanedMessage.orEmpty().contains("API key not valid"))
        assertFalse(GeminiHttpErrorClassifier.sanitizeErrorBody(response, key).contains(key))
        assertEquals("Gemini error response (unstructured)", GeminiHttpErrorClassifier.sanitizeErrorBody("broken $key", key))
    }

    @Test fun localPageAndByteLimitsStayEnforced() {
        assertTrue(GeminiReceiptSizePolicy.isAllowed(1, 100_000))
        assertFalse(GeminiReceiptSizePolicy.isAllowed(GeminiReceiptSizePolicy.maxPages + 1, 100))
        assertFalse(GeminiReceiptSizePolicy.isAllowed(1, GeminiReceiptSizePolicy.maxImageBytes.toLong() + 1))
    }
}
