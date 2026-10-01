package com.example.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PersistentStorageInventoryTest {
    private fun sources(): File = listOf(File("src/main/java"), File("app/src/main/java"))
        .firstOrNull { it.isDirectory } ?: error("Produktive Quellen für Inventarprüfung fehlen.")

    @Test fun everySharedPreferenceOpeningMustResolveToAnExplicitClassification() {
        val classified = PersistentPreferenceInventory.stores.map { it.name }.toSet()
        val found = mutableSetOf<String>()
        val opening = Regex("getSharedPreferences\\s*\\(\\s*(\"[^\"]+\"|[A-Za-z_][A-Za-z0-9_]*)")
        val declaration = Regex("const\\s+val\\s+(\\w+)\\s*=\\s*\"([^\"]+)\"")
        sources().walkTopDown().filter { it.extension == "kt" && it.name != "PersistentPreferenceInventory.kt" }
            .forEach { file ->
                val source = file.readText()
                opening.findAll(source).forEach { call ->
                    val argument = call.groupValues[1]
                    val name = if (argument.startsWith('"')) argument.removeSurrounding("\"")
                    else declaration.findAll(source.substring(0, call.range.first))
                        .lastOrNull { it.groupValues[1] == argument }?.groupValues?.get(2)
                    assertNotNull("${file.name}: Speichername $argument muss für die Klassifizierung auflösbar sein", name)
                    assertTrue("${file.name}: $name nicht bewusst klassifiziert", name in classified)
                    found.add(name!!)
                }
            }
        assertEquals("Inventar enthält veraltete oder fehlende Speicher", classified, found)
        val exported = PersistentPreferenceInventory.stores.filter { it.kind == PersistentPreferenceInventory.Kind.BACKUP }
        assertTrue(exported.all { it.payloadKey != null })
        assertEquals(exported.size, exported.map { it.payloadKey }.toSet().size)
        listOf("ai_provider_settings", "google_drive_prefs").forEach { mixed ->
            assertNotNull(PersistentPreferenceInventory.stores.single { it.name == mixed }.allowedKeys)
        }
    }

    @Test fun productionLogsAcceptOnlyStaticEventsAndNeverThrowables() {
        val calls = Regex("DiagnosticLog\\.[diew]\\s*\\(([^\\n]*)\\)")
        sources().walkTopDown().filter { it.extension == "kt" && it.name != "DiagnosticLog.kt" }
            .forEach { file ->
                val source = file.readText()
                assertFalse("${file.name}: Direkte Android-Logs umgehen Datenschutz", Regex("(?<!Diagnostic)Log\\.[diew]\\(").containsMatchIn(source))
                assertFalse("${file.name}: Stacktrace kann Inhalte enthalten", source.contains("printStackTrace("))
                calls.findAll(source).forEach { call ->
                    val args = call.groupValues[1]
                    assertTrue("${file.name}: Log muss ein statisches Ereignis sein: $args",
                        Regex("(?:TAG|\"[^\"]+\"), \"[^\"$]*\"").matches(args))
                }
            }
    }
}
