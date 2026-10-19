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
        after.getJSONObject("supplemental").getJSONObject("propertyUnitPrefs").put("property_p_unit_WE 01, EG links_id", "wrong-shape")
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
    @Test fun completeRoomRowsKeepEveryBusinessFieldAndOnlyReconstructDeclaredLocalState() {
        val projection = raw().toString()
        val before = JSONObject().put("receipts", JSONArray().put(JSONObject()
            .put("internalId", "r").put("syncStatus", "PENDING").put("imageUrl", "old-device")
            .put("exportStatus", "EXPORTIERT").put("pruefstatus", "GEPRUEFT")))
            .put("managedDocuments", JSONArray().put(JSONObject(raw().getJSONObject("supplemental")
                .getJSONArray("managedDocuments").getJSONObject(0).toString()).put("localUri", "old-device")))
        before.put("receiptOriginalChains", JSONObject().put("r", JSONObject().put("0", JSONObject()
            .put("documentId", "receipt:r").put("sha256", "original-hash"))))
        val after = JSONObject(before.toString())
        after.getJSONArray("receipts").getJSONObject(0).put("syncStatus", "SYNCED").put("imageUrl", "new-device")
        after.getJSONArray("managedDocuments").getJSONObject(0).put("localUri", "new-device").put("ocrText", "rebuilt")
        fun full(value: JSONObject) = RestoreContractSnapshot.fromRaw(projection, value.toString()).canonical()
        assertEquals(full(before), full(after))
        after.getJSONArray("receipts").getJSONObject(0).put("exportStatus", "EXPORTBEREIT")
        assertNotEquals(full(before), full(after))
        after.getJSONArray("receipts").getJSONObject(0).put("exportStatus", "EXPORTIERT").put("unknownBusinessField", "changed")
        assertNotEquals(full(before), full(after))
        val changedOriginal = JSONObject(before.toString())
        changedOriginal.getJSONObject("receiptOriginalChains").getJSONObject("r").getJSONObject("0").put("sha256", "wrong-original")
        assertNotEquals(full(before), full(changedOriginal))
    }
    @Test fun moneyCentsAreExactAndOnlyBackedAdjacentFloatingRepresentationsMayNormalize() {
        val projection = raw().apply { getJSONArray("receipts").getJSONObject(0).put("cent", 690L) }.toString()
        fun room(amount: Double) = JSONObject().put("managedDocuments", JSONArray())
            .put("receipts", JSONArray().put(JSONObject().put("internalId", "r").put("bruttobetrag", amount)))
        fun contract(amount: Double, source: String = projection) =
            RestoreContractSnapshot.fromRaw(source, room(amount).toString()).canonical()
        val arithmetic = 23 * 0.30
        assertNotEquals(6.9, arithmetic, 0.0)
        assertEquals(contract(arithmetic), contract(6.9))
        assertNotEquals(contract(6.9), contract(6.91))
        assertNotEquals(contract(6.9), contract(6.901))
        assertNotEquals(contract(6.9), contract(Math.nextDown(Math.nextDown(6.9))))
        val wrongCent = JSONObject(projection).apply { getJSONArray("receipts").getJSONObject(0).put("cent", 691L) }.toString()
        assertNotEquals(contract(6.9), contract(6.9, wrongCent))
        assertNotEquals(contract(6.9, raw().toString()), contract(arithmetic, raw().toString()))
    }

}
