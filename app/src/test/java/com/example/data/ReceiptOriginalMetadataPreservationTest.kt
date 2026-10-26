package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReceiptOriginalMetadataPreservationTest {
    private fun document(id: String, fields: String) = ManagedDocument(
        documentId = id, receiptInternalId = "review", propertyId = "property-a", unitId = "unit-a",
        extractedFieldsJson = fields, originalFilename = "$id.pdf", mimeType = "application/pdf")

    @Test fun reviewedSingleOriginalRoundTripsWithoutInventingFields() {
        for (fields in listOf("", "{\"_displayDescription\":\"Bestätigt\",\"rechnungNummer\":\"RE-1\"}")) {
            val original = document("receipt:review", fields)
            val parsed = ReceiptOriginalChain.parseMetadata(ReceiptOriginalChain.metadataJson(listOf(original)), "review", "property-a").single()
            assertEquals(fields, parsed.extractedFieldsJson)
            assertEquals(original.documentId, parsed.documentId)
            assertEquals(original.unitId, parsed.unitId)
        }
    }

    @Test fun multipleReviewedOriginalsRetainExactJsonAndExplicitOrder() {
        val originals = (0..2).map { rank -> document(if (rank == 0) "receipt:review" else "page-$rank",
            JSONObject().put("_receiptOriginalOrder", rank).put("_displayDescription", "Seite $rank geprüft").toString()) }
        val parsed = ReceiptOriginalChain.parseMetadata(ReceiptOriginalChain.metadataJson(originals.reversed()), "review", "property-a")
        assertEquals(originals.map { it.documentId }, ReceiptOriginalChain.ordered(parsed).map { it.documentId })
        assertEquals(originals.map { it.extractedFieldsJson }, parsed.map { it.extractedFieldsJson })
    }

    @Test fun oldBackupsWithoutAuthoredJsonStillReconstructEveryPageRank() {
        val legacy = ReceiptOriginalChain.metadataJson((0..2).map { document("page-$it", "") })
        for (i in 0 until legacy.length()) legacy.getJSONObject(i).remove("extractedFieldsJson")
        val wire = JSONArray(legacy.toString())
        val parsed = ReceiptOriginalChain.parseMetadata(wire, "review", "property-a")
        parsed.forEachIndexed { rank, document ->
            assertEquals(rank, JSONObject(document.extractedFieldsJson).getInt("_receiptOriginalOrder"))
        }
        assertEquals(parsed.map { it.documentId }, ReceiptOriginalChain.ordered(parsed.reversed()).map { it.documentId })
    }
}
