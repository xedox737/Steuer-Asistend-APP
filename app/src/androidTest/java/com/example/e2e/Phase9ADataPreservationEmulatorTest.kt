package com.example.e2e

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import com.example.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase9ADataPreservationEmulatorTest : EmulatorTestSupport() {
    @Test fun openingDamagedHistoryShowsRepairStateAndDoesNotMutateRawData() {
        val property = seedProperty("p9a-history", "Historienhaus", rented = true)
        val key = "history_v2_${property.propertyId}_${property.propertyId}-unit"
        val raw = JSONArray().put(JSONObject().put("id", 501).put("tenantName", "Testmieter Alt")
            .put("startDate", "2021-04-01").put("endDate", "").put("kaltmiete", 600)
            .put("nebenkosten", 0).put("sonstige", 0)).put(17).toString()
        context.getSharedPreferences("tenant_history_prefs", 0).edit().putString(key, raw).commit()
        openUnits(property.propertyId)
        ui.onNodeWithTag("property_units_overview").performScrollToNode(hasText("Testwohnung"))
        ui.onNodeWithText("Testwohnung").performClick()
        ui.onNodeWithTag("unit_history_read_error").assertIsDisplayed().assertTextEquals(StoredListRead.ERROR)
        assertEquals(raw, context.getSharedPreferences("tenant_history_prefs", 0).getString(key, null))
        capture("phase9a-damaged-history")
        scenario.recreate()
        assertEquals(raw, context.getSharedPreferences("tenant_history_prefs", 0).getString(key, null))
    }

    @Test fun overflowingNewPositionIsRejectedAndExistingReceiptReopensWithBothPositions() {
        val property = seedProperty("p9a-items", "Beleghaus")
        val items = listOf(ReceiptItem("Position A", 2.0, 10.0, 20.0), ReceiptItem("Position B", 1.0, 9.0, 9.0))
        val original = Receipt(id = 901, internalId = "p9a-items-receipt", displayId = "BLG-9A-901",
            aussteller = "Original", datum = "2026-10-13", uhrzeit = "", bruttobetrag = 29.0,
            hauptkategorie = "Sonstige Ausgaben", unterkategorie = "", kontoNr = "4970", beschreibung = "Originaltext",
            propertyId = property.propertyId, unitId = "${property.propertyId}-unit", wohneinheit = "WE 01",
            positionenJson = ReceiptItemConverter.toJson(items))
        runBlocking(Dispatchers.IO) { db.receiptDao().insertReceipt(original) }
        val model = vm
        ui.waitUntil(10_000) { model.receipts.value.any { it.id == original.id } }
        ui.runOnIdle { model.openReceiptDetail(original.id, AppScreen.RECEIPTS_LIST) }
        ui.onNodeWithTag("edit_receipt_button").performScrollTo().performClick()
        ui.onNodeWithContentDescription("Position hinzufügen").performScrollTo().performClick()
        input("Artikelbezeichnung / Leistung", "Überlauf", scroll = false)
        input("Menge (z. B. 1,0)", "1" + "0".repeat(200), scroll = false)
        input("Einzelpreis in EUR", "1" + "0".repeat(200), scroll = false)
        ui.onNodeWithText("Hinzufügen").performClick()
        ui.onNodeWithText("Der eingegebene Wert ist zu groß.").assertIsDisplayed()
        assertEquals(original, runBlocking(Dispatchers.IO) { db.receiptDao().getReceiptById(original.id) })
        capture("phase9a-position-overflow")
        ui.onNode(hasText("Abbrechen") and hasAnyAncestor(isDialog())).performClick()
        ui.onNodeWithTag("save_edited_receipt_button").performScrollTo().performClick()
        ui.waitUntil(10_000) { model.currentScreen.value == AppScreen.RECEIPT_DETAIL }
        ui.onNodeWithTag("edit_receipt_button").performScrollTo().performClick()
        assertEquals(items, runBlocking(Dispatchers.IO) { db.receiptDao().getReceiptById(original.id)!!.getPositionenList() })
        assertEquals(29.0, runBlocking(Dispatchers.IO) { db.receiptDao().getReceiptById(original.id)!!.bruttobetrag }, 0.0)
        capture("phase9a-positions-reopened")
    }

    @Test fun restoredReceiptOpensInOriginalPropertyAndRetainsIndependentUnitAndAuditFields() {
        val property = seedProperty("p9a-restored", "Restauriertes Haus")
        val raw = JSONObject().put("schemaVersion", 2).put("receiptId", 902).put("internalId", "p9a-restored-receipt")
            .put("displayId", "BLG-9A-902").put("unitId", "${property.propertyId}-unit").put("propertyId", property.propertyId)
            .put("exportlaufId", "audit-9a").put("kontoNr", "4971").put("isEigenleistungSanierung", true)
            .put("uhrzeit", "12:34").put("mieter", "Mieter").put("aussteller", "Restaurierter Beleg")
            .put("datum", "2026-10-13").put("bruttobetragCent", -11900).put("hauptkategorie", "Sonstige Ausgaben")
            .put("unterkategorie", "Material").put("wohneinheit", "WE 01").put("beschreibung", "Gutschrift")
            .put("createdAt", "2026-10-13T12:00:00Z").put("updatedAt", "2026-10-13T12:00:00Z")
        val receipt = runBlocking(Dispatchers.IO) {
            val repository = ReceiptRepository(db.receiptDao(), db.propertyDao())
            val drive = DrivePersistenceRepository(context, repository)
            val incoming = with(drive) { parsePersistedReceipt(raw.toString()).toLocalReceipt("metadata-9a") }
            repository.upsertRestoredReceipt(incoming, preserveIncomingId = true)
            repository.getReceiptByInternalId("p9a-restored-receipt")!!
        }
        assertEquals(902, receipt.id); assertEquals("${property.propertyId}-unit", receipt.unitId)
        assertEquals("audit-9a", receipt.exportlaufId); assertEquals("4971", receipt.kontoNr)
        assertTrue(receipt.isEigenleistungSanierung); assertEquals(-119.0, receipt.bruttobetrag, 0.0)
        val model = vm
        ui.waitUntil(10_000) { model.receipts.value.any { it.id == receipt.id } }
        ui.runOnIdle { model.openReceiptDetail(receipt.id, AppScreen.RECEIPTS_LIST) }
        ui.onNodeWithText("Restauriertes Haus").performScrollTo().assertIsDisplayed()
        capture("phase9a-restored-receipt-context")
    }
}
