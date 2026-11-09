package com.example.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PersistentStorageInventoryTest {
    private fun sources(): File = listOf(File("src/main/java"), File("app/src/main/java"))
        .firstOrNull { it.isDirectory } ?: error("Produktive Quellen für Inventarprüfung fehlen.")

    @Test fun persistentFileRootsAndDataStoreOwnersRequireExplicitClassification() {
        val classifiedRoots = setOf("filesDir/managed_documents", "filesDir/property-images", "filesDir/offline-originals",
            "filesDir/restored_receipts", "filesDir/receipt_documents", "filesDir/receipt_page_", "filesDir/offline-backup-staging",
            "noBackupFilesDir/drive-pending-writes")
        val found = mutableSetOf<String>()
        val root = Regex("(filesDir|noBackupFilesDir)\\s*,\\s*\"([^\"]+)\"")
        sources().walkTopDown().filter { it.extension in setOf("kt", "java") }.forEach { file ->
            val source = file.readText().replace(Regex("(?s)/\\*.*?\\*/|//[^\\n]*"), "")
            val matches = root.findAll(source).toList()
            Regex("\\b(filesDir|noBackupFilesDir|getFilesDir|getNoBackupFilesDir|getExternalFilesDir|getExternalFilesDirs|dataDir|getDataDir|getDir|getDatabasePath)\\b")
                .findAll(source).forEach { use ->
                    assertTrue("${file.name}: neuer Dateispeicher benötigt eine Sicherungszuordnung",
                        matches.any { use.range.first in it.range })
                }
            matches.forEach { match ->
                val directory = match.groupValues[1] + "/" + match.groupValues[2].substringBefore('/').substringBefore('$')
                assertTrue("${file.name}: $directory ist nicht klassifiziert", directory in classifiedRoots)
                found.add(directory)
            }
            assertFalse("${file.name}: neuer DataStore benötigt eine Sicherungszuordnung",
                Regex("\\b(preferencesDataStore|dataStore|DataStoreFactory|PreferenceDataStoreFactory)\\s*[.(]")
                    .containsMatchIn(source))
        }
        assertEquals("Dateiinventar enthält veraltete oder fehlende Speicher", classifiedRoots, found)
    }

    @Test fun everySharedPreferenceOpeningMustResolveToAnExplicitClassification() {
        val classified = PersistentPreferenceInventory.stores.map { it.name }.toSet()
        val found = mutableSetOf<String>()
        sources().walkTopDown().filter { it.extension in setOf("kt", "java") && it.name != "PersistentPreferenceInventory.kt" }
            .forEach { file ->
                found.addAll(preferenceNames(file.readText(), file.name))
            }
        assertEquals("Inventar enthält veraltete oder fehlende Speicher", classified, found)
        val exported = PersistentPreferenceInventory.stores.filter { it.kind == PersistentPreferenceInventory.Kind.BACKUP }
        assertTrue(exported.all { it.payloadKey != null })
        assertEquals(exported.size, exported.map { it.payloadKey }.toSet().size)
        listOf("ai_provider_settings", "google_drive_prefs").forEach { mixed ->
            assertNotNull(PersistentPreferenceInventory.stores.single { it.name == mixed }.allowedKeys)
        }
    }

    private fun preferenceNames(source: String, owner: String): Set<String> {
        val classified = PersistentPreferenceInventory.stores.map { it.name }.toSet()
        val token = "(\"[^\"]+\"|[A-Za-z_][A-Za-z0-9_]*)"
        val declaration = Regex("const\\s+val\\s+(\\w+)\\s*=\\s*\"([^\"]+)\"")
        val forms = listOf(
            Regex("getSharedPreferences\\s*\\(") to Regex("getSharedPreferences\\s*\\(\\s*$token\\s*,"),
            Regex("PersistentPreferenceInventory\\.open\\s*\\(") to
                Regex("PersistentPreferenceInventory\\.open\\s*\\((?:[^,()]|\\(\\))+?,\\s*$token\\s*,?\\s*\\)")
        )
        return buildSet {
            forms.forEach { (opening, full) ->
                opening.findAll(source).forEach { call ->
                    val match = full.find(source, call.range.first)?.takeIf { it.range.first == call.range.first }
                    assertNotNull("$owner: dynamischer oder unbekannter Preference-Zugang benötigt eine explizite Klassifizierung", match)
                    val argument = match!!.groupValues[1]
                    val name = if (argument.startsWith('"')) argument.removeSurrounding("\"")
                    else declaration.findAll(source.substring(0, call.range.first))
                        .lastOrNull { it.groupValues[1] == argument }?.groupValues?.get(2)
                    assertNotNull("$owner: Speichername $argument muss für die Klassifizierung auflösbar sein", name)
                    assertTrue("$owner: $name nicht bewusst klassifiziert", name in classified)
                    add(name!!)
                }
            }
        }
    }

    @Test fun classifiedPreferenceFactoryOpeningsAreDetected() {
        val source = """
            private const val PREFS = "tenant_history_prefs"
            PersistentPreferenceInventory.open(context, PREFS)
            context.getSharedPreferences("rent_plan_prefs", Context.MODE_PRIVATE)
        """.trimIndent()
        assertEquals(setOf("tenant_history_prefs", "rent_plan_prefs"), preferenceNames(source, "fixture"))
    }

    @Test fun unclassifiedAndComputedPreferenceNamesAreRejected() {
        listOf(
            "context.getSharedPreferences(\"unknown-store\", 0)",
            "context.getSharedPreferences(\"rent_plan_prefs\" + suffix, 0)",
            "PersistentPreferenceInventory.open(context, \"unknown-store\")",
            "PersistentPreferenceInventory.open(context, \"tenant_history_prefs\" + suffix)",
            "PersistentPreferenceInventory.open(context, runtimeName)"
        ).forEach { source ->
            org.junit.Assert.assertThrows(AssertionError::class.java) { preferenceNames(source, "fixture") }
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
