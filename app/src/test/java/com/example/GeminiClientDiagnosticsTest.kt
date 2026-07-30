package com.example

import com.example.api.GeminiAnalysisException
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiClientDiagnosticsTest {

    @Test
    fun testKeyMissingException() {
        val exception = GeminiAnalysisException.KeyMissing()
        assertEquals("CONFIG", exception.category)
        assertEquals("KEY_MISSING", exception.errorCode)
        assertEquals("Für die KI-Analyse ist noch kein Gemini-Zugang eingerichtet.", exception.userMessage)
    }

    @Test
    fun testKeyInvalidException() {
        val exception = GeminiAnalysisException.KeyInvalid("Invalid Key details")
        assertEquals("AUTH", exception.category)
        assertEquals("KEY_INVALID", exception.errorCode)
        assertEquals("Die Gemini-Anmeldung oder API-Berechtigung ist ungültig.", exception.userMessage)
        assertEquals(401, exception.httpStatus)
        assertEquals("Invalid Key details", exception.cleanedMessage)
    }

    @Test
    fun testQuotaExceededException() {
        val exception = GeminiAnalysisException.QuotaExceeded("Quota exceeded details")
        assertEquals("QUOTA", exception.category)
        assertEquals("RESOURCE_EXHAUSTED", exception.errorCode)
        assertEquals("Das Gemini-Kontingent ist momentan ausgeschöpft. Bitte später erneut versuchen.", exception.userMessage)
        assertEquals(429, exception.httpStatus)
        assertEquals("Quota exceeded details", exception.cleanedMessage)
    }
}
