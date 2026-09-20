package com.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.BankReceiptLink
import com.example.data.BankTransaction
import com.example.data.Receipt
import com.example.data.ReceiptItem
import com.example.data.ReceiptItemConverter
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-420dpi")
class ReceiptDetailComposeTest {
    @get:Rule val ui = createComposeRule()
    private val receipt = Receipt(id = 15, aussteller = "Mieter Klaus & Sabine", datum = "2026-01-04",
        uhrzeit = "", bruttobetrag = 650.0, hauptkategorie = "Einnahmen", unterkategorie = "Mieteinnahmen",
        kontoNr = "", beschreibung = "Miete Januar", displayId = "BLG-2026-000015", zahlungsart = "Überweisung",
        positionenJson = ReceiptItemConverter.toJson(listOf(ReceiptItem("Warmmiete Januar", 1.0, 650.0, 650.0))))
    private val transaction = BankTransaction(transactionId = "test-tx", accountId = "test-account",
        bookingDate = "2026-01-04", amount = 650.0, counterparty = "Miete Januar", purpose = "Miete Januar 2026")
    private val link = BankReceiptLink(linkId = "test-link", transactionId = "test-tx", receiptId = 15, allocatedAmount = 650.0)
    private var shares = 0
    private var downloads = 0
    private var replacements = 0
    private var deletions = 0
    private var unlinks = 0
    private var backPresses = 0
    private var destination: AppScreen? = null

    private fun show(assigned: Boolean = true) {
        val bitmap = Bitmap.createBitmap(300, 420, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textSize = 17f }
        listOf("MUSTERBELEG", "Miete Januar 2026", "04.01.2026", "Mieter Klaus & Sabine", "650,00 EUR").forEachIndexed { index, text ->
            canvas.drawText(text, 20f, 55f + index * 45f, paint)
        }
        ui.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                var editing by remember { mutableStateOf(false) }
                ReceiptDetailLayout(receipt, "Wohnung 1", if (assigned) listOf(link to transaction) else emptyList(), bitmap,
                    editing = editing, onEditingChange = { editing = it },
                    onBack = { backPresses++ }, onNavigate = { destination = it }, onShare = { shares++ }, onFullScreen = {},
                    onDownload = { downloads++ }, onReplace = { replacements++ }, onDelete = { deletions++ },
                    onUnlink = { _, _ -> unlinks++ },
                    editor = { done -> Button(onClick = done) { Text("Editor schließen") } },
                    additionalData = { Text("DATEV-Prüfdaten") })
            }
        }
    }

    @Test fun editShowsOnlyFieldsAndBackClosesEditorBeforeDetail() {
        show()
        ui.onNodeWithText("Bearbeiten").performScrollTo().performClick()
        ui.onNodeWithText("Editor schließen").assertExists()
        ui.onNodeWithTag("edit_receipt_button").assertDoesNotExist()
        ui.onNodeWithText("Kategorie").assertDoesNotExist()
        ui.onNodeWithText("Einzelne Positionen / Artikel (1)").assertDoesNotExist()
        ui.onNodeWithText("Zugeordnete Buchungen (1)").assertDoesNotExist()
        ui.onNodeWithContentDescription("Zurück").performClick()
        ui.onNodeWithText("Editor schließen").assertDoesNotExist()
        ui.onNodeWithTag("receipt_detail_screen").assertExists()
        ui.runOnIdle { assertEquals(0, backPresses) }
        ui.onNodeWithContentDescription("Zurück").performClick()
        ui.runOnIdle { assertEquals(1, backPresses) }
    }

    @Test fun lineItemsAppearOnDetailAndNotInsideTheEditor() {
        show()
        ui.onNodeWithText("Einzelne Positionen / Artikel (1)").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Warmmiete Januar").assertIsDisplayed()
        ui.onNodeWithText("Bearbeiten").performScrollTo().performClick()
        ui.onNodeWithText("Einzelne Positionen / Artikel (1)").assertDoesNotExist()
        ui.onNodeWithText("Zugeordnete Buchungen (1)").assertDoesNotExist()
        ui.onNodeWithText("Editor schließen").assertExists()
    }

    @Test fun additionalDataStayOnOneDetailPage() {
        show()
        ui.onNodeWithText("ImmoPilot").assertExists()
        ui.onNodeWithText("Immobilien. Finanzen. Steuern.").assertExists()
        ui.onAllNodesWithText("Belegdetails").assertCountEquals(1)
        ui.onNodeWithText("Beleg Aktionen").assertDoesNotExist()
        ui.onNodeWithTag("receipt_more_data").performScrollTo().performClick()
        ui.onNodeWithText("DATEV-Prüfdaten").assertExists()
        ui.onNodeWithTag("receipt_more_data").performScrollTo().performClick()
        ui.onNodeWithText("DATEV-Prüfdaten").assertDoesNotExist()
    }

    @Test fun actionsAndAssignmentRemainFunctional() {
        show()
        ui.onNodeWithText("Teilen").performScrollTo().performClick()
        ui.onNodeWithText("Herunterladen").performScrollTo().performClick()
        ui.onNodeWithText("Beleg ersetzen").performScrollTo().performClick()
        ui.onNodeWithText("Miete Januar", substring = false).performScrollTo().performClick()
        ui.onNodeWithText("Verknüpfung lösen").performScrollTo().performClick()
        ui.onNodeWithText("Beleg löschen").performScrollTo().performClick()
        ui.onNodeWithText("Start").performClick()
        ui.runOnIdle {
            assertEquals(1, shares); assertEquals(1, downloads); assertEquals(1, replacements)
            assertEquals(1, unlinks); assertEquals(1, deletions); assertEquals(AppScreen.DASHBOARD, destination)
        }
    }

    @Test fun renderAssignedDetailAtPhoneWidth() {
        show()
        File("app/build/reports/receipt-detail").mkdirs()
        ui.onRoot().captureRoboImage("app/build/reports/receipt-detail/01-top.png")
        ui.onNodeWithText("Beleg löschen").performScrollTo()
        ui.onRoot().captureRoboImage("app/build/reports/receipt-detail/02-bottom.png")
    }

    @Test fun unassignedReceiptHasHonestEmptyState() {
        show(assigned = false)
        ui.onNodeWithText("Offen").assertExists()
        ui.onNodeWithText("Noch keine Buchung zugeordnet.").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Verknüpfung lösen").assertDoesNotExist()
    }

    @Test fun amountInputSupportsGermanDecimalsAndRejectsInvalidValues() {
        assertEquals(1248.50, parseReceiptEditAmount("1.248,50 €")!!, 0.001)
        assertEquals(650.25, parseReceiptEditAmount("650.25")!!, 0.001)
        assertNull(parseReceiptEditAmount("abc"))
        assertNull(parseReceiptEditAmount("NaN"))
        assertEquals("04.01.2026", receiptDisplayDate("2026-01-04"))
    }
}
