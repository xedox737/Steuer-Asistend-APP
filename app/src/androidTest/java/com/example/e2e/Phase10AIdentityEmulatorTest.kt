package com.example.e2e

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import com.example.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Production persistence and screens on the existing disposable emulator, including real back actions. */
abstract class Phase10AIdentityMatrix : EmulatorTestSupport() {
    @Test fun commaNamePrecisionAndRelationsSurviveRenameReopenAndRecreate() {
        ui.waitUntil(10_000) {
            var actual = 0f
            scenario.onActivity { actual = it.resources.configuration.fontScale }
            kotlin.math.abs(actual - fontScale) < .01f
        }
        assertEquals(393, context.resources.configuration.screenWidthDp)
        val propertyId = "phase10a-native"
        val first = WohneinheitStatus("WE 01, EG links", "WE 01, EG links", "Leerstand", "", 0.0, 41.25, unitId = "U1")
        val second = first.copy(name = "WE 02", label = "WE 02", wohnflaeche = 55.5, unitId = "U2")
        val model = vm
        ui.runOnIdle { model.createProperty(PropertyMetadata(propertyId = propertyId, name = "Kommahaus"), listOf(first, second)) }
        ui.waitUntil(10_000) { model.properties.value.any { it.propertyId == propertyId } && model.propertyMetadata.value?.propertyId == propertyId && model.wohneinheitenStatus.value.size == 2 }
        val property = model.properties.value.single { it.propertyId == propertyId }
        val period = TenantPeriod(10101, first.name, "Martin Weber", "2020-01-01", "", 600.0, 100.0, 0.0)
        val receipt = Receipt(1010, "Handwerker", "2026-10-01", "", 119.0, "Werbungskosten", "Reparatur", "4801", "Testbeleg",
            propertyId = propertyId, unitId = "U1", wohneinheit = first.name, internalId = "phase10a-receipt")
        val document = ManagedDocument(documentId = "phase10a-document", propertyId = propertyId, unitId = "U1", title = "Einheitsnachweis",
            receiptInternalId = receipt.internalId, originalFilename = "Original.pdf", mimeType = "application/pdf", sha256 = "original-hash")
        val assignment = BankRentAssignment("phase10a-assignment", "phase10a-tx", propertyId, "U1", "2026-10", "10101", 700.0,
            BankSplitPaymentType.RENT, createdAt = "", updatedAt = "")
        runBlocking(Dispatchers.IO) {
            TenantHistoryStore.save(context, propertyId, "U1", first.name, listOf(period))
            db.receiptDao().insertReceipt(receipt)
            db.managedDocumentDao().upsert(document)
            db.bankDao().upsertAccount(BankAccount("phase10a-account", "Testkonto"))
            db.bankDao().upsertTransaction(BankTransaction("phase10a-tx", "phase10a-account", "2026-10-01", amount = 700.0, propertyId = propertyId, unitId = "U1"))
            db.bankRentAssignmentDao().upsert(assignment)
        }
        fun detail(name: String) {
            ui.onNodeWithTag("property_units_overview").performScrollToNode(hasTestTag("unit_card_U1"))
            ui.onNodeWithTag("unit_card_U1").assertIsDisplayed().performClick()
            ui.onNodeWithTag("unit_detail_list").performScrollToNode(hasText("41,25 m²"))
            ui.onNodeWithText("41,25 m²").assertIsDisplayed()
            assertEquals(listOf("U1", "U2"), vm.getWohneinheitenForProperty(vm.properties.value.single { it.propertyId == propertyId }).map { it.unitId })
            assertEquals(name, vm.getWohneinheitenForProperty(vm.properties.value.single { it.propertyId == propertyId }).first().name)
        }
        openUnits(propertyId)
        ui.onNodeWithTag("property_units_overview").performScrollToNode(hasText(first.name))
        ui.onNodeWithText(first.name).assertIsDisplayed()
        detail(first.name)
        capture("phase10a-area-font-$fontScale")
        scenario.recreate()
        ui.onNodeWithTag("unit_detail_list").performScrollToNode(hasText("41,25 m²"))
        ui.onNodeWithText("41,25 m²").assertIsDisplayed()
        systemBack()
        ui.onNodeWithTag("property_units_overview").assertIsDisplayed()
        val renamed = "WE 01, EG links, Altbau"
        ui.runOnIdle { vm.updatePropertyMetadata(property.copy(wohneinheiten = UnitIdentity.encode(listOf(UnitIdentity.Entry("U1", renamed), UnitIdentity.Entry("U2", second.name))))) }
        ui.waitUntil(10_000) { vm.properties.value.any { it.propertyId == propertyId && UnitIdentity.display(it.wohneinheiten).contains(renamed) } }
        detail(renamed)
        ui.onNodeWithContentDescription("Zurück").performClick()
        ui.onNodeWithTag("property_units_overview").assertIsDisplayed()
        scenario.recreate()
        detail(renamed)
        assertEquals(listOf(period), TenantHistoryStore.load(context, propertyId, "U1", renamed))
        runBlocking(Dispatchers.IO) {
            assertEquals(receipt, db.receiptDao().getReceiptById(receipt.id))
            assertEquals(document, db.managedDocumentDao().getById(document.documentId))
            assertEquals(listOf(assignment), db.bankRentAssignmentDao().getAll())
        }
        // Historical ambiguity is visibly different from an empty property and never rewrites raw data.
        val ambiguous = "WE 01, EG links, WE 02"
        val rawPreferences = context.getSharedPreferences("wohneinheiten_prefs", 0).all.toMap()
        runBlocking(Dispatchers.IO) { db.propertyDao().insertPropertyMetadata(property.copy(wohneinheiten = ambiguous)) }
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("unit_identity_error").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("unit_identity_error").assertIsDisplayed()
        ui.onNodeWithTag("unit_card_U1").assertDoesNotExist()
        capture("phase10a-legacy-error-font-$fontScale")
        scenario.recreate()
        ui.onNodeWithTag("unit_identity_error").assertIsDisplayed()
        assertEquals(ambiguous, runBlocking(Dispatchers.IO) { db.propertyDao().getPropertyByPropertyId(propertyId)!!.wohneinheiten })
        assertEquals(rawPreferences, context.getSharedPreferences("wohneinheiten_prefs", 0).all)
    }
}

@RunWith(AndroidJUnit4::class)
class Phase10AIdentity10EmulatorTest : Phase10AIdentityMatrix() { override val fontScale = 1.0f }
@RunWith(AndroidJUnit4::class)
class Phase10AIdentity13EmulatorTest : Phase10AIdentityMatrix() { override val fontScale = 1.3f }
@RunWith(AndroidJUnit4::class)
class Phase10AIdentity15EmulatorTest : Phase10AIdentityMatrix() { override val fontScale = 1.5f }
