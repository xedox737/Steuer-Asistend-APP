package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RestoreContractSnapshotTest {
    private fun raw() = JSONObject().put("supplemental", JSONObject()
        .put("propertyUnitPrefs", JSONObject())
        .put("managedDocuments", JSONArray().put(JSONObject()
            .put("documentId", "receipt:r").put("receiptInternalId", "r").put("propertyId", "p")
            .put("createdAt", "2026-01-01").put("updatedAt", "2026-01-01")
            .put("source", "DATEIIMPORT").put("extractedFieldsJson", ""))))
        .put("receipts", JSONArray().put(JSONObject().put("internalId", "r").put("allocations", "").put("proposals", "")))
        .put("units", JSONArray().put(JSONObject().put("propertyId", "p").put("units",
            JSONArray().put(JSONObject().put("unitId", "u").put("name", "WE 01, EG links")))))
    private fun contract(raw: JSONObject) = RestoreContractSnapshot.fromRaw(raw.toString()).canonical()

    @Test fun onlyEquivalentOriginalRankAndSafeAliasMayReconstruct() {
        val before = raw(); val after = JSONObject(before.toString())
        after.getJSONObject("supplemental").getJSONArray("managedDocuments").getJSONObject(0)
            .put("extractedFieldsJson", "{\"_receiptOriginalOrder\":0}")
        after.getJSONObject("supplemental").getJSONObject("propertyUnitPrefs")
            .put("property_p_unit_WE 01, EG links_id", JSONObject().put("type", "string").put("value", "u"))
        assertNotEquals(canonicalJson(before), canonicalJson(after))
        assertEquals(contract(before), contract(after))
    }
    @Test fun everyObservedAuthoritativeMetadataChangeStaysRed() {
        val before = raw()
        listOf("source" to "RECEIPT", "updatedAt" to "2026-02-01", "createdAt" to "2026-02-01",
            "extractedFieldsJson" to "{\"rechnungNummer\":\"NEW\"}").forEach { (key, value) ->
            val after = JSONObject(before.toString())
            after.getJSONObject("supplemental").getJSONArray("managedDocuments").getJSONObject(0).put(key, value)
            assertNotEquals(key, contract(before), contract(after))
        }
    }
    @Test fun rawApprovalRepresentationsAreNotBlindlyCanonicalized() {
        val before = raw()
        for (key in listOf("allocations", "proposals")) {
            val after = JSONObject(before.toString())
            after.getJSONArray("receipts").getJSONObject(0).put(key, "[]")
            assertNotEquals(key, contract(before), contract(after))
        }
    }
    @Test fun wrongLegacyIdentityAndNewUnknownFieldsStayRed() {
        val before = raw(); val after = JSONObject(before.toString())
        after.getJSONObject("supplemental").getJSONObject("propertyUnitPrefs")
            .put("property_p_unit_WE 01, EG links_id", JSONObject().put("type", "string").put("value", "wrong"))
        assertNotEquals(contract(before), contract(after))
        assertNotEquals(contract(before), contract(JSONObject(before.toString()).put("unknownFutureMetadata", "changed")))
    }
    @Test fun reorderingTwoOriginalsStaysRed() {
        val before = raw()
        val docs = before.getJSONObject("supplemental").getJSONArray("managedDocuments")
        docs.put(JSONObject(docs.getJSONObject(0).toString()).put("documentId", "attachment:r"))
        val after = JSONObject(before.toString())
        after.getJSONObject("supplemental").getJSONArray("managedDocuments").getJSONObject(1)
            .put("extractedFieldsJson", "{\"_receiptOriginalOrder\":0}")
        assertNotEquals(contract(before), contract(after))
    }
}
