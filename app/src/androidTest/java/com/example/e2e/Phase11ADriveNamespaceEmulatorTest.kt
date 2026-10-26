package com.example.e2e

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.api.DriveEnvironment
import com.example.api.GoogleDriveClient
import com.example.data.*
import com.example.qa.Phase11ADriveHttpFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase11ADriveNamespaceEmulatorTest : EmulatorTestSupport() {
    @Test fun androidBuildShowsTestCloudAndNeverReadsOrChangesExistingProductTree() {
        clickMore("Backup & Cloud")
        ui.onNodeWithTag("more_menu").performScrollToNode(hasTestTag("phase11a_drive_notice"))
        ui.onNodeWithTag("phase11a_drive_notice").assertIsDisplayed()
            .assertTextContains("Test-Datenspeicher: ImmoPilot Phase11A Testdaten / _BelegApp-Daten")
        val remote = Phase11ADriveHttpFixture()
        remote.put("prod-root", "Steuerassistent Belege", remote.myDrive, mime = Phase11ADriveHttpFixture.FOLDER)
        remote.put("prod-system", "_BelegApp-Daten", "prod-root", mime = Phase11ADriveHttpFixture.FOLDER)
        remote.put("prod-config", "app-config.json", "prod-system", "{\"sentinel\":\"never-load\"}", "appConfig")
        val before = remote.inventory("prod-root")
        val field = GoogleDriveClient::class.java.getDeclaredField("client").apply { isAccessible = true }
        val original = field.get(null)
        field.set(null, OkHttpClient.Builder().addInterceptor(remote).build())
        GoogleDriveClient.clearFolderCache()
        try {
            runBlocking(Dispatchers.IO) {
                val repo = ReceiptRepository(db.receiptDao(), db.propertyDao(), db.receiptEntityDao(), db.belegDao(),
                    db.exportAuditDao(), db.receiptDocumentDao(), db.managedDocumentDao())
                val drive = DrivePersistenceRepository(context, repo)
                val first = drive.initializeDriveStorage("same-synthetic-account")
                assertTrue(first.toString(), first is DriveInitializationResult.SuccessCreatedNew)
                val config = (first as DriveInitializationResult.SuccessCreatedNew).config
                assertEquals("ImmoPilot Phase11A Testdaten", DriveEnvironment.rootFolderName)
                repeat(2) {
                    assertTrue(drive.saveStammdatenToDrive("same-synthetic-account", config))
                    assertTrue(SupplementalDriveBackup.backup(context, db, "same-synthetic-account", config.systemFolderId).success)
                }
                assertTrue(drive.initializeDriveStorage("same-synthetic-account") is DriveInitializationResult.SuccessLoadedExisting)
                assertFalse(GoogleDriveClient.updateJson("same-synthetic-account", "prod-config", "{}").success)
                assertFalse(GoogleDriveClient.deleteFile("same-synthetic-account", "prod-config"))
                assertNull(GoogleDriveClient.downloadFileBytes("same-synthetic-account", "prod-config"))
                assertEquals(1, remote.entries.values.count { it.name == "ImmoPilot Phase11A Testdaten" })
                assertEquals(1, remote.entries.values.count { it.name == "_BelegApp-Daten" && it.parent == config.rootFolderId })
            }
            assertEquals(before, remote.inventory("prod-root"))
            assertTrue(remote.orphanIds().isEmpty())
            assertFalse(remote.requests.any { it.url.encodedPath.endsWith("/prod-config") && (it.method != "GET" || it.url.queryParameter("alt") == "media") })
            saveArtifact("phase11a-drive-namespace.json", JSONObject().put("status", "AUTOMATISIERT BESTANDEN")
                .put("practicalStatus", "NOCH OFFEN").put("runId", "P11A-2026-10-25-EMULATOR-DRIVE-${java.util.UUID.randomUUID()}")
                .put("transport", "IN_MEMORY_HTTP_SAME_ACCOUNT").put("productInventoryUnchanged", true)
                .put("rootDuplicates", 0).put("orphans", 0).toString(2).toByteArray())
            capture("phase11a-test-drive-notice")
        } finally {
            field.set(null, original)
            GoogleDriveClient.clearFolderCache()
        }
    }
}
