package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Strict QA reproduction: genuine review service + full coordinator; only Drive transport is fake. */
@RunWith(RobolectricTestRunner::class)
class Phase10BReviewedDocumentRestoreTest : Phase10CRestoreFixture() {
    @Test fun confirmedDescriptionAndReviewFieldsMustSurviveFreshDoubleMergeAndReplace() = runTest {
        seedProperty()
        repository.insert(receipt(1))
        val document = repository.getAllManagedDocuments().single()
        val fields = JSONObject().put("_displayDescription", "Gepruefte Handwerkerrechnung Bad")
            .put("rechnungNummer", "SYN-10B-42").toString()
        val reviewed = ManagedDocumentService(context, repository).confirmReview(
            document, ManagedDocumentType.RECHNUNG, document.documentDate, document.unitId, fields)
        assertEquals(DocumentReviewStatus.GEPRUEFT.name, reviewed.reviewStatus)
        backup()
        val folder = File("build/reports/phase10b/reviewed-document").apply { mkdirs() }
        folder.resolve("supplemental-backup.json").writeText(payload.toString(2))
        fun record(label: String, value: ManagedDocument) = JSONObject().apply {
            put("documentId", value.documentId); put("propertyId", value.propertyId); put("unitId", value.unitId)
            put("receiptInternalId", value.receiptInternalId); put("reviewStatus", value.reviewStatus)
            put("extractedFieldsJson", value.extractedFieldsJson); put("source", value.source)
            put("updatedAt", value.updatedAt); put("title", value.title)
        }.also { folder.resolve("$label.json").writeText(it.toString(2)) }
        val before = repository.getManagedDocument(document.documentId)!!
        record("BEFORE", before)
        assertEquals("Gepruefte Handwerkerrechnung Bad", JSONObject(before.extractedFieldsJson).getString("_displayDescription"))
        withContext(Dispatchers.IO) { db.clearAllTables() }
        clearPreferences()
        val observed = mutableListOf<Pair<String, ManagedDocument>>()
        repeat(2) { index ->
            restore(RestoreMode.MERGE)
            val actual = repository.getManagedDocument(document.documentId)!!
            val label = "AFTER_MERGE_${index + 1}"
            record(label, actual); observed += label to actual
        }
        restore(RestoreMode.REPLACE_FULL)
        val replaced = repository.getManagedDocument(document.documentId)!!
        record("AFTER_REPLACE_FULL", replaced); observed += "AFTER_REPLACE_FULL" to replaced
        val failures = mutableListOf<String>()
        for ((label, actual) in observed) {
            assertEquals(before.documentId, actual.documentId)
            assertEquals(before.propertyId, actual.propertyId)
            assertEquals(before.unitId, actual.unitId)
            assertEquals(before.receiptInternalId, actual.receiptInternalId)
            val actualFields = JSONObject(actual.extractedFieldsJson.ifBlank { "{}" })
            for (key in listOf("_displayDescription", "rechnungNummer")) {
                val expected = JSONObject(before.extractedFieldsJson).getString(key)
                if (actualFields.optString(key) != expected) failures += "$label lost $key: expected=$expected actual=${actualFields.optString(key)}"
            }
        }
        folder.resolve("assertion-results.json").writeText(JSONObject().put("failures", org.json.JSONArray(failures)).toString(2))
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
