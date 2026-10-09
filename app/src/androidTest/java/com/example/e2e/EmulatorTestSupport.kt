package com.example.e2e

import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.BuildConfig
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.PersistentPreferenceInventory
import com.example.data.PropertyMetadata
import com.example.ui.AppScreen
import com.example.ui.ReceiptViewModel
import com.example.ui.WohneinheitStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

private const val DEVICE_ARTIFACTS = "/sdcard/Download/immopilot-emulator-e2e"
private val databaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
    InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
).bufferedReader().use { it.readText().trim() }

/** Only clears data in an explicitly permitted, disposable debug emulator. */
class EmulatorEnvironmentRule(private val fontScale: () -> Float) : ExternalResource() {
    private var originalFontScale: String? = null

    override fun before() {
        check(BuildConfig.DEBUG && Build.HARDWARE in setOf("ranchu", "goldfish")) {
            "Diese Tests dürfen ausschließlich auf einem isolierten Debug-Emulator laufen."
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.aistudio.steuerassistent.vnhqkp")
        check(InstrumentationRegistry.getArguments().getString("immopilotDisposableEmulator") == "true") {
            "Testdatenbereinigung benötigt -Pandroid.testInstrumentationRunnerArguments.immopilotDisposableEmulator=true"
        }
        runBlocking(Dispatchers.IO) {
            AppDatabase.getDatabase(context, databaseScope).clearAllTables()
        }
        PersistentPreferenceInventory.stores.forEach {
            check(context.getSharedPreferences(it.name, 0).edit().clear().commit())
        }
        originalFontScale = shell("settings get system font_scale")
        shell("settings put system font_scale ${fontScale()}")
        shell("mkdir -p $DEVICE_ARTIFACTS")
    }

    override fun after() {
        originalFontScale?.let {
            if (it == "null") shell("settings delete system font_scale")
            else shell("settings put system font_scale $it")
        }
    }
}

/** Real MainActivity and production Room/preferences; no alternate app or navigation host. */
abstract class EmulatorTestSupport {
    protected open val fontScale = 1.0f
    @get:Rule(order = 0) val environment = EmulatorEnvironmentRule { fontScale }
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    protected lateinit var scenario: ActivityScenario<MainActivity>
    protected val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    protected val db get() = AppDatabase.getDatabase(context, databaseScope)
    protected val vm: ReceiptViewModel
        get() {
            var result: ReceiptViewModel? = null
            scenario.onActivity { result = ViewModelProvider(it)[ReceiptViewModel::class.java] }
            return requireNotNull(result)
        }

    // Launch after Compose has registered its roots; close after failure diagnostics.
    @get:Rule(order = 2) val activityLifecycle = object : ExternalResource() {
        override fun before() {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            ui.onNodeWithText("Willkommen bei ImmoPilot").assertIsDisplayed()
            val resources = context.resources
            saveText("environment-font-${fontScale}.txt", buildString {
                appendLine("api=${Build.VERSION.SDK_INT}; android=${Build.VERSION.RELEASE}")
                appendLine("width_px=${resources.displayMetrics.widthPixels}; height_px=${resources.displayMetrics.heightPixels}")
                appendLine("density_dpi=${resources.displayMetrics.densityDpi}; density=${resources.displayMetrics.density}")
                appendLine("width_dp=${resources.configuration.screenWidthDp}; height_dp=${resources.configuration.screenHeightDp}")
                appendLine("font_scale=${resources.configuration.fontScale}")
            })
        }
        override fun after() { if (::scenario.isInitialized) scenario.close() }
    }
    @get:Rule(order = 3) val failureDiagnostics = object : TestWatcher() {
        override fun failed(error: Throwable, description: Description) {
            val name = "${description.testClass.simpleName}-${description.methodName}-failure"
            // Diagnostics must not replace the original assertion/exception.
            runCatching { capture(name) }
            runCatching { saveText("$name.txt", ui.onRoot(useUnmergedTree = true).printToString()) }
        }
    }

    protected fun capture(name: String) {
        ui.waitForIdle()
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val file = File(requireNotNull(context.getExternalFilesDir(null)), "$name.png")
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        copyDiagnostic(file)
    }

    private fun saveText(name: String, text: String) {
        val file = File(requireNotNull(context.getExternalFilesDir(null)), name)
        file.writeText(text)
        copyDiagnostic(file)
    }

    private fun copyDiagnostic(file: File) {
        // Shared shell-owned output survives AGP uninstalling the APK after tests.
        // UiAutomation executes argv directly, without shell quotes or &&.
        // These generated package/file paths have no whitespace.
        val destination = "$DEVICE_ARTIFACTS/${file.name}"
        shell("cp ${file.absolutePath} $destination")
        val copiedBytes = shell("stat -c %s $destination").toLongOrNull()
        check(copiedBytes == file.length()) {
            "Diagnoseartefakt konnte nicht gesichert werden: ${file.name}; bytes=$copiedBytes/${file.length()}"
        }
    }

    protected fun clickTab(screen: AppScreen) {
        ui.onNodeWithTag("nav_item_${screen.name.lowercase()}").assertIsDisplayed().performClick()
        ui.onNodeWithTag("nav_item_${screen.name.lowercase()}").assertIsSelected()
        assertEquals(screen, vm.currentScreen.value)
    }

    protected fun clickMore(label: String) {
        clickTab(AppScreen.MORE)
        ui.onNodeWithTag("more_menu").performScrollToNode(hasTestTag("more_item_$label"))
        ui.onNodeWithTag("more_item_$label").assertIsDisplayed().performClick()
    }

    protected fun systemBack() {
        // Espresso injects a real Android BACK key event into the focused window.
        // Do not call onBackPressedDispatcher or a navigation callback here.
        Espresso.pressBack()
        ui.waitForIdle()
    }

    protected fun seedProperty(id: String = "e2e-property", name: String = "Testobjekt", rented: Boolean = false): PropertyMetadata {
        val model = vm
        val property = PropertyMetadata(propertyId = id, name = name, adresse = "Beispielstraße 1", wohneinheiten = "WE 01")
        val unit = WohneinheitStatus(name = "WE 01", label = "Testwohnung", status = if (rented) "Vermietet" else "Leerstand",
            mieter = if (rented) "Testmieter Alt" else "", kaltmiete = if (rented) 600.0 else 0.0,
            wohnflaeche = 41.25, mietvertragsstart = if (rented) "2021-04-01" else "", unitId = "$id-unit")
        ui.runOnIdle { model.createProperty(property, listOf(unit)) }
        ui.waitUntil(10_000) { model.properties.value.any { it.propertyId == id } &&
            model.propertyMetadata.value?.propertyId == id && model.wohneinheitenStatus.value.any { it.unitId == unit.unitId } }
        return model.properties.value.single { it.propertyId == id }
    }

    protected fun openUnits(propertyId: String) {
        clickTab(AppScreen.PROPERTIES)
        ui.onNodeWithTag("property_card_$propertyId").performScrollTo().performClick()
        ui.onNode(hasText("Einheiten") and hasClickAction()).performScrollTo().performClick()
        ui.onNodeWithTag("property_units_overview").assertIsDisplayed()
    }

    protected fun input(label: String, value: String, scroll: Boolean = true) {
        val node = ui.onNode(hasText(label) and hasSetTextAction())
        if (scroll) node.performScrollTo()
        node.performTextReplacement(value)
        // Close the real IME without using BACK (which is separately asserted).
        Espresso.closeSoftKeyboard()
    }
}
