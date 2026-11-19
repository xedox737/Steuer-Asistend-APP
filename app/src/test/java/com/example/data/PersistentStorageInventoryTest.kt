package com.example.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PersistentStorageInventoryTest {
    private fun sources(): File = listOf(File("src/main/java"), File("app/src/main/java"))
        .firstOrNull { it.isDirectory } ?: error("Produktive Quellen für Inventarprüfung fehlen.")

    // Capacity probes and validated restore path resolution access existing roots.
    private val portableRoot = """\(if \(version\.portablePath\.startsWith\("files/"\)\) context\.filesDir else context\.cacheDir\)\.canonicalFile"""
    private val cacheRoot = """context\.cacheDir\.canonicalFile"""
    private val rootReaders = mapOf(
        "OfflineBackupResourceBudget.kt" to listOf(
            """listOf\(context\.filesDir, context\.cacheDir, context\.noBackupFilesDir,""",
            """context\.getDatabasePath\("receipt_database"\)\.parentFile"""),
        "ReceiptDatabase.kt" to listOf("""context\.getDatabasePath\("receipt_database"\)\.canonicalFile"""),
        "OfflineRestoreFilePlan.kt" to listOf("""context\.filesDir\.canonicalFile"""),
        "OfflineRestoreImportedFiles.kt" to listOf("""context\.filesDir\.(usableSpace|canonicalFile)"""),
        "OfflineRestoreDatabaseVerifier.kt" to listOf(portableRoot),
        "OfflineRestoreOwnedFilesRollback.kt" to listOf(portableRoot),
    )

    @Test fun persistentFileRootsAndDataStoreOwnersRequireExplicitClassification() {
        val classifiedRoots = setOf("filesDir/managed_documents", "filesDir/property-images", "filesDir/offline-originals",
            "filesDir/restored_receipts", "filesDir/receipt_documents", "filesDir/receipt_page_", "filesDir/offline-backup-staging",
            "noBackupFilesDir/drive-pending-writes", "noBackupFilesDir/offline-backup-keys", "noBackupFilesDir/offline-backup-state")
        // No current producer opens this legacy directory; existing referenced bytes remain backup data.
        val retiredProducerRoots = setOf("filesDir/restored_receipts")
        assertTrue(classifiedRoots.containsAll(retiredProducerRoots))
        val found = mutableSetOf<String>()
        val root = Regex("(filesDir|noBackupFilesDir)\\s*,\\s*\"([^\"]+)\"")
        sources().walkTopDown().filter { it.extension in setOf("kt", "java") }.forEach { file ->
            val source = file.readText().replace(Regex("(?s)/\\*.*?\\*/|//[^\\n]*"), "")
            val matches = root.findAll(source).toList()
            val knownUses = (rootReaders[file.name].orEmpty()).flatMap { Regex(it).findAll(source).toList() }
            Regex("\\b(filesDir|noBackupFilesDir|getFilesDir|getNoBackupFilesDir|getExternalFilesDir|getExternalFilesDirs|dataDir|getDataDir|getDir|getDatabasePath)\\b")
                .findAll(source).forEach { use ->
                    assertTrue("${file.name}: neuer Dateispeicher benötigt eine Sicherungszuordnung",
                        (matches + knownUses).any { use.range.first in it.range })
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
        assertEquals("Dateiinventar enthält veraltete oder fehlende aktive Speicher",
            classifiedRoots - retiredProducerRoots, found)
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
            Regex("(?<!fun )getSharedPreferences\\s*\\(") to Regex("getSharedPreferences\\s*\\(\\s*$token\\s*,"),
            Regex("PersistentPreferenceInventory\\.open\\s*\\(") to
                Regex("PersistentPreferenceInventory\\.open\\s*\\((?:[^,()]|\\(\\))+?,\\s*$token\\s*,?\\s*\\)")
        )
        return buildSet {
            forms.forEach { (opening, full) ->
                opening.findAll(source).forEach scanCall@ { call ->
                    val match = full.find(source, call.range.first)?.takeIf { it.range.first == call.range.first }
                    // These restore loops pass the name of each BACKUP entry from the inventory.
                    val registeredRestoreLoop = owner in setOf("OfflineRestoreLivePreferences.kt", "OfflineRestorePreferenceRollback.kt") &&
                        source.substring(call.range.first).startsWith("PersistentPreferenceInventory.open(context, store.name)") &&
                        source.substring(0, call.range.first).substringAfterLast("fun ").contains(
                            "PersistentPreferenceInventory.stores.filter { it.kind == PersistentPreferenceInventory.Kind.BACKUP }.forEach { store ->")
                    if (match == null && registeredRestoreLoop) {
                        addAll(PersistentPreferenceInventory.stores.filter { it.kind == PersistentPreferenceInventory.Kind.BACKUP }.map { it.name })
                        return@scanCall
                    }
                    assertNotNull("$owner: dynamischer oder unbekannter Preference-Zugang benötigt eine explizite Klassifizierung", match)
                    val argument = match!!.groupValues[1]
                    val name = if (argument.startsWith('"')) argument.removeSurrounding("\"")
                    else declaration.findAll(source.substring(0, call.range.first))
                        .lastOrNull { it.groupValues[1] == argument }?.groupValues?.get(2)
                    assertNotNull("$owner: Speichername $argument muss für die Klassifizierung auflösbar sein", name)
                    assertTrue("$owner: $name nicht bewusst klassifiziert", name in classified)
                    if (call.value.startsWith("getSharedPreferences")) {
                        assertFalse("$owner: $name umgeht die Snapshot-Schreibkoordination",
                            PersistentPreferenceInventory.stores.single { it.name == name }.kind ==
                                PersistentPreferenceInventory.Kind.BACKUP)
                    }
                    add(name!!)
                }
            }
        }
    }

    @Test fun classifiedPreferenceFactoryOpeningsAreDetected() {
        val source = """
            private const val PREFS = "tenant_history_prefs"
            PersistentPreferenceInventory.open(context, PREFS)
            context.getSharedPreferences("restore_journal_prefs", Context.MODE_PRIVATE)
        """.trimIndent()
        assertEquals(setOf("tenant_history_prefs", "restore_journal_prefs"), preferenceNames(source, "fixture"))
    }

    @Test fun businessPreferenceOpeningsCannotBypassSnapshotCoordination() {
        PersistentPreferenceInventory.stores.filter { it.kind == PersistentPreferenceInventory.Kind.BACKUP }
            .forEach { store ->
                assertThrows(AssertionError::class.java) {
                    preferenceNames("context.getSharedPreferences(\"${store.name}\", 0)", "fixture")
                }
                assertEquals(setOf(store.name),
                    preferenceNames("PersistentPreferenceInventory.open(context, \"${store.name}\")", "fixture"))
            }
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

    // These rules classify app-owned producers, not arbitrary library cache contents.
    private val cacheRules = mapOf(
        "com/example/util/PdfExporter.kt" to mapOf("saved-pdf" to """File\(context\.cacheDir,\s*filename\)"""),
        "com/example/util/DatevExporter.kt" to mapOf("saved-datev-zip" to """File\(context\.cacheDir,\s*"DATEV_Export_[^"\n]+\.zip"\)"""),
        "com/example/util/DatevCsvOutput.kt" to mapOf("saved-datev-csv" to """File\(context\.cacheDir,\s*"EXTF_Buchungsstapel_[^"\n]+\.csv"\)"""),
        "com/example/util/AdvisorPackageBuilder.kt" to mapOf("saved-advisor-zip" to """File\(context\.cacheDir,\s*zipFileName\)"""),
        "com/example/ui/ReceiptViewModel.kt" to mapOf(
            "saved-renovation-report" to """File\(getApplication<Application>\(\)\.cacheDir,\s*"reports"\)""",
            "saved-datev-csv" to """File\(context\.cacheDir,\s*"EXTF_Buchungsstapel_[^"\n]+\.csv"\)""",
            "saved-datev-xml" to """File\(context\.cacheDir,\s*"document\.xml"\)""",
            "saved-datev-pdf" to """File\(context\.cacheDir,\s*"datev_export\.pdf"\)"""),
        "com/example/ui/ReceiptDetailScreen.kt" to mapOf("temporary-replacement" to
            """File\.createTempFile\("receipt_replacement_",\s*"\.tmp",\s*context\.cacheDir\)"""),
        "com/example/ui/ReceiptAppUi.kt" to mapOf(
            "temporary-repair" to """File\(context\.cacheDir,\s*"repair_file_[^"\n]+"\)""",
            "temporary-scan-repair" to """File\(context\.cacheDir,\s*"repair_scan_[^"\n]+\.jpg"\)""",
            "retained-camera-original" to """File\(context\.cacheDir,\s*"temp_receipt_[^"\n]+\.jpg"\)""",
            "saved-bank-export" to """File\(context\.cacheDir,\s*"exports"\)""",
            "temporary-overview-repair" to """File\(context\.cacheDir,\s*"overview_repair_[^"\n]+"\)"""),
        "com/example/data/OfflineBackupService.kt" to mapOf(
            "technical-export-staging" to """File\(context\.cacheDir,\s*"offline-export-[^"\n]+"\)""",
            "technical-import-staging" to """File\(context\.cacheDir,\s*"offline-import-[^"\n]+"\)"""),
        "com/example/data/OfflineBackupResourceBudget.kt" to mapOf("capacity-check" to
            """listOf\(context\.filesDir, context\.cacheDir, context\.noBackupFilesDir,"""),
        "com/example/data/OfflineRestoreFilePlan.kt" to mapOf("validated-cache-root" to cacheRoot),
        "com/example/data/OfflineRestoreImportedFiles.kt" to mapOf(
            "validated-cache-root" to cacheRoot, "capacity-check" to """context\.cacheDir\.usableSpace >= required"""),
        "com/example/data/OfflineRestoreDatabaseVerifier.kt" to mapOf("validated-cache-root" to portableRoot),
        "com/example/data/OfflineRestoreOwnedFilesRollback.kt" to mapOf("validated-cache-root" to portableRoot),
        "com/example/data/OfflineBackupFileInventory.kt" to mapOf("classified-inventory-reader" to
            """context\.cacheDir\.canonicalFile""")
    )

    private fun classifiedCacheUses(source: String, owner: String): Map<String, Int> {
        val clean = source.replace(Regex("(?s)/\\*.*?\\*/|//[^\\n]*"), "")
        val matches = cacheRules[owner].orEmpty().mapValues { (_, pattern) -> Regex(pattern).findAll(clean).toList() }
        Regex("\\b(cacheDir|externalCacheDir|getCacheDir|getExternalCacheDir|getExternalCacheDirs)\\b").findAll(clean).forEach { use ->
            assertTrue("$owner: Cache-Zugriff braucht eine ausdrückliche Sicherungszuordnung",
                matches.values.flatten().count { use.range.first in it.range } == 1)
        }
        return matches.mapValues { it.value.size }.filterValues { it > 0 }
    }

    @Test fun everyAppCacheAccessRequiresExplicitSavedExportOrTemporaryClassification() {
        val root = sources()
        val found = root.walkTopDown().filter { it.extension in setOf("kt", "java") }.mapNotNull { file ->
            val owner = file.relativeTo(root).invariantSeparatorsPath
            classifiedCacheUses(file.readText(), owner).takeIf { it.isNotEmpty() }?.let { owner to it }
        }.toMap()
        val expected = cacheRules.mapValues { (owner, rules) -> rules.mapValues { (kind, _) ->
            when {
                owner == "com/example/ui/ReceiptDetailScreen.kt" && kind == "temporary-replacement" -> 2
                owner == "com/example/data/OfflineRestoreOwnedFilesRollback.kt" && kind == "validated-cache-root" -> 2
                else -> 1
            }
        } }
        assertEquals("Cache-Inventar enthält veraltete oder fehlende aktive Eigentümer", expected, found)
    }

    @Test fun newOrComputedCacheProducersCannotSilentlyBypassClassification() {
        listOf(
            "new/Unknown.kt" to "File(context.cacheDir, \"new-business-data\")",
            "com/example/ui/ReceiptAppUi.kt" to "File(context.cacheDir, \"unknown-output\")",
            "com/example/ui/ReceiptAppUi.kt" to "File(context.cacheDir, runtimeName)",
            "com/example/ui/ReceiptAppUi.kt" to "context.externalCacheDir"
        ).forEach { (owner, source) ->
            assertThrows(AssertionError::class.java) { classifiedCacheUses(source, owner) }
        }
        assertEquals(mapOf("saved-bank-export" to 1),
            classifiedCacheUses("File(context.cacheDir, \"exports\")", "com/example/ui/ReceiptAppUi.kt"))
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
