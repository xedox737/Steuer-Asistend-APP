package com.example.api

import com.example.data.DocumentFieldDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedDocumentAiRulesTest {
    @Test fun unknownTypeAndConfidenceAreConservativelyNormalized() {
        val result = ManagedDocumentAiRules.validate(ManagedDocumentAiResult("ERFUNDEN", 4.0, fields = listOf(ManagedDocumentAiField("k", "K", "v", -2.0))))
        assertEquals("SONSTIGES", result.documentType); assertEquals(1.0, result.confidence, 0.0); assertEquals(0.0, result.fields.single().confidence, 0.0)
    }

    @Test fun aiFieldsStartUnconfirmed() {
        val proposals = ManagedDocumentAiResult(fields = listOf(ManagedDocumentAiField("kaufpreis", "Kaufpreis", "250000", .9))).reviewFields(emptyMap())
        assertTrue(proposals.all { it.decision == DocumentFieldDecision.AUSSTEHEND })
    }
}
