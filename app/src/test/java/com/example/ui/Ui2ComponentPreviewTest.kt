package com.example.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Synthetic gallery for reviewing the shared ImmoPilot brand components; not an app E2E test. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-420dpi")
class Ui2ComponentPreviewTest {
    @get:Rule val ui = createComposeRule()

    @Test fun sharedComponentsRenderWithTheProductionThemeAndProduceAPreview() {
        ui.setContent {
            MyApplicationTheme(darkTheme = false) {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("ImmoPilot · Komponenten", style = MaterialTheme.typography.titleLarge)
                    Ui2Section("Mietübersicht · Testdaten") {
                        Ui2Metric(label = "Belege gesamt", value = "23")
                        Ui2Metric(label = "Offene Buchungen", value = "4")
                    }
                    Ui2ActionCard(Ui2Action(
                        title = "Beleg erfassen",
                        subtitle = "Beispielaktion ohne Datenänderung",
                        icon = Icons.Default.ReceiptLong,
                        onClick = {}
                    ))
                    Ui2Destination(
                        title = "Immobilien",
                        subtitle = "Beispielnavigation ohne Datenänderung",
                        icon = Icons.Default.Home,
                        onClick = {}
                    )
                }
            }
        }
        ui.onNodeWithText("ImmoPilot · Komponenten").assertExists()
        ui.onNodeWithText("Mietübersicht · Testdaten").assertExists()
        ui.onNodeWithText("Beleg erfassen").assertExists()
        ui.onNodeWithText("Immobilien").assertExists()
        ui.waitForIdle()

        // Same Roborazzi capture path convention as MoreMenuComposeTest.
        val path = "build/reports/immopilot-ui-preview/components-phone.png"
        onView(isRoot()).captureRoboImage(path)
        val bitmap = BitmapFactory.decodeFile(path)
        assertNotNull("The screenshot must be readable", bitmap)
        assertTrue("The screenshot must have a useful width", bitmap.width > 300)
        assertTrue("The screenshot must have a useful height", bitmap.height > 300)
        bitmap.recycle()
    }
}
