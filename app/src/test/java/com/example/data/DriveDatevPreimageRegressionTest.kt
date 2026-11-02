package com.example.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.api.DriveSyncCheck
import com.example.api.DriveSyncOutcome
import com.example.api.DriveSyncPhase
import com.example.api.DriveSyncRecorder
import com.example.api.DriveSyncTrace
import com.example.api.GoogleDriveClient
import com.example.api.canonicalDriveJsonText
import com.example.comparison.ComparisonDriveHttpFixture
import com.example.ui.IsolatedAppDatabaseRule
import com.example.util.DatevProfileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Real repository/client with synthetic ownership and HTTP; never a live Drive acceptance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DriveDatevPreimageRegressionTest {
    @get:Rule val isolation = IsolatedAppDatabaseRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val db get() = AppDatabase.getDatabase(app, CoroutineScope(Dispatchers.IO))
    private val repo get() = ReceiptRepository(db.receiptDao(), db.propertyDao(), db.receiptEntityDao(), db.belegDao(),
        db.exportAuditDao(), db.receiptDocumentDao(), db.managedDocumentDao())
    private val drive get() = DrivePersistenceRepository(app, repo)
    private val token = "synthetic-datev-preimage"
    private val transport = GoogleDriveClient::class.java.getDeclaredField("client").apply { isAccessible = true }
    private lateinit var previous: OkHttpClient
    private lateinit var remote: ComparisonDriveHttpFixture
    private lateinit var config: DriveAppConfig
    private val profile = DatevProfile.createDefaultSkr03().copy(lastModified = 1_700_000_000_000L)

    @Before fun setup() = runBlocking {
        app.getSharedPreferences("datev_kanzleiprofil_prefs", 0).edit().clear().commit()
        app.getSharedPreferences("google_drive_prefs", 0).edit().clear().putBoolean("auto_backup", false).commit()
        File(app.noBackupFilesDir, "drive-pending-writes").deleteRecursively()
        DatevProfileService.saveActiveProfile(app, profile)
        repo.restorePropertyMetadata(PropertyMetadata(name = "Synthetic DATEV preimage"))
        previous = transport.get(null) as OkHttpClient
        remote = ComparisonDriveHttpFixture()
        transport.set(null, OkHttpClient.Builder().addInterceptor(remote).build())
        GoogleDriveClient.clearFolderCache()
        config = when (val result = drive.initializeDriveStorage(token)) {
            is DriveInitializationResult.SuccessCreatedNew -> result.config
            is DriveInitializationResult.SuccessLoadedExisting -> result.config
            is DriveInitializationResult.Failure -> error(result.error)
        }
        remote.requests.clear()
    }

    @After fun cleanup() {
        transport.set(null, previous)
        GoogleDriveClient.clearFolderCache()
    }

    private fun datevFile() = remote.entries.values.single { it.properties["entityType"] == "datevProfiles" }

    private fun forgetOldInstallationBaselines() {
        // An upgraded/restored installation may have business data but no new sync journal.
        File(app.noBackupFilesDir, "drive-pending-writes").deleteRecursively()
        assertNull(DrivePendingWrites(app).jsonBaseline(datevFile().id))
    }

    private suspend fun backUp(): Pair<Boolean, DriveSyncRecorder> {
        val recorder = DriveSyncRecorder({})
        val result = DriveSyncTrace.run(recorder) {
            GoogleDriveClient.withSyncReadProof(token) { drive.saveStammdatenToDrive(token, config) }
        }
        return result to recorder
    }

    @Test fun identicalDatevBusinessDataWithDifferentCreationTimestampIsReadOnly() = runBlocking {
        forgetOldInstallationBaselines()
        val before = datevFile().bytes.copyOf()
        val local = profile.copy(lastModified = profile.lastModified + 1_000)
        DatevProfileService.saveActiveProfile(app, local)
        val (success, recorder) = backUp()
        assertTrue(recorder.snapshot().toJson(), success)
        assertArrayEquals(before, datevFile().bytes)
        assertEquals(local, DatevProfileService.getActiveProfile(app))
        assertTrue(remote.requests.all { it.method == "GET" })
        assertEquals(DriveSyncOutcome.UNCHANGED,
            recorder.snapshot().steps.single { it.phase == DriveSyncPhase.DATEV_PROFILES }.outcome)
    }

    @Test fun identicalDatevBusinessDataKeepsNewerRemoteTimestampWithoutOverwritingIt() = runBlocking {
        val file = datevFile()
        file.bytes = drive.serializeDatevProfile(profile.copy(lastModified = profile.lastModified + 1_000)).toByteArray()
        file.revision++
        forgetOldInstallationBaselines()
        val before = file.bytes.copyOf()
        val (success, recorder) = backUp()
        assertTrue(recorder.snapshot().toJson(), success)
        assertArrayEquals(before, file.bytes)
        assertEquals(profile, DatevProfileService.getActiveProfile(app))
        assertTrue(remote.requests.all { it.method == "GET" })
    }

    @Test fun unknownRemoteDatevBusinessChangesStillBlockWithoutOverwritingEitherSide() = runBlocking {
        val file = datevFile()
        file.bytes = drive.serializeDatevProfile(profile.copy(mandantenName = "Synthetic remote change",
            lastModified = profile.lastModified + 1_000)).toByteArray()
        file.revision++
        forgetOldInstallationBaselines()
        val before = file.bytes.copyOf()
        val (success, recorder) = backUp()
        assertFalse(success)
        val failure = recorder.snapshot().steps.single { it.phase == DriveSyncPhase.DATEV_PROFILES }.failure!!
        assertEquals(DriveSyncCheck.BACKUP_PREIMAGE_CONFLICT, failure.check)
        assertTrue(failure.differentFields.contains("mandantenName"))
        assertTrue(failure.differentFields.all { it in setOf("mandantenName", "lastModified") })
        assertFalse(recorder.snapshot().toJson().contains("Synthetic remote change"))
        assertArrayEquals(before, file.bytes)
        assertEquals(profile, DatevProfileService.getActiveProfile(app))
        assertTrue(remote.requests.all { it.method == "GET" })
    }

    @Test fun unknownRemoteDatevFieldsMustNotBeDiscardedAsAnUnchangedProfile() = runBlocking {
        val file = datevFile()
        val json = JSONObject(file.bytes.toString(Charsets.UTF_8))
        json.getJSONObject("activeProfile").put("PrivateSyntheticName_314159", "must survive")
        file.bytes = json.toString().toByteArray()
        file.revision++
        forgetOldInstallationBaselines()
        val before = file.bytes.copyOf()
        val (success, recorder) = backUp()
        assertFalse(success)
        val failure = recorder.snapshot().steps.single { it.phase == DriveSyncPhase.DATEV_PROFILES }.failure!!
        assertEquals(DriveSyncCheck.BACKUP_PREIMAGE_CONFLICT, failure.check)
        assertEquals(listOf("unknownFields"), failure.differentFields)
        assertFalse(recorder.snapshot().toJson().contains("PrivateSyntheticName_314159"))
        assertFalse(recorder.snapshot().toJson().contains("must survive"))
        assertArrayEquals(before, file.bytes)
        assertEquals(profile, DatevProfileService.getActiveProfile(app))
        assertTrue(remote.requests.all { it.method == "GET" })
    }

    @Test fun knownDatevPreimageStillAllowsAnIntentionalLocalProfileEdit() = runBlocking {
        val local = profile.copy(mandantenName = "Synthetic local edit", lastModified = profile.lastModified + 1_000)
        val oldId = datevFile().id
        assertNotNull(DrivePendingWrites(app).jsonBaseline(oldId))
        DatevProfileService.saveActiveProfile(app, local)
        val (success, recorder) = backUp()
        assertTrue(recorder.snapshot().toJson(), success)
        assertEquals(oldId, datevFile().id)
        assertEquals(local, drive.deserializeDatevProfile(datevFile().bytes.toString(Charsets.UTF_8)))
        assertEquals(local, DatevProfileService.getActiveProfile(app))
        assertEquals(1, remote.entries.values.count { it.properties["entityType"] == "datevProfiles" })
    }

    @Test fun equivalentDatevAccountMapOrderingAndWhitespaceIsReadOnly() = runBlocking {
        val local = profile.copy(categoryKontoMapJson = "{\"Synthetic A\":\"4801\",\"Synthetic B\":\"8100\"}")
        val cloud = local.copy(categoryKontoMapJson = "{\n  \"Synthetic B\": \"8100\",\n  \"Synthetic A\": \"4801\"\n}")
        val file = datevFile()
        file.bytes = drive.serializeDatevProfile(cloud).toByteArray()
        file.revision++
        DatevProfileService.saveActiveProfile(app, local)
        forgetOldInstallationBaselines()
        val before = file.bytes.copyOf()
        val (success, recorder) = backUp()
        assertTrue(recorder.snapshot().toJson(), success)
        assertArrayEquals(before, file.bytes)
        assertEquals(local, DatevProfileService.getActiveProfile(app))
        assertTrue(remote.requests.all { it.method == "GET" })
    }

    @Test fun changedDatevAccountNumberIsNotAnEquivalentMapping() = runBlocking {
        val local = profile.copy(categoryKontoMapJson = "{\"Synthetic A\":\"4801\"}")
        val cloud = local.copy(categoryKontoMapJson = "{\"Synthetic A\":\"8100\"}")
        val file = datevFile()
        file.bytes = drive.serializeDatevProfile(cloud).toByteArray()
        file.revision++
        DatevProfileService.saveActiveProfile(app, local)
        forgetOldInstallationBaselines()
        val before = file.bytes.copyOf()
        val (success, recorder) = backUp()
        assertFalse(success)
        assertEquals(DriveSyncCheck.BACKUP_PREIMAGE_CONFLICT,
            recorder.snapshot().steps.single { it.phase == DriveSyncPhase.DATEV_PROFILES }.failure?.check)
        assertArrayEquals(before, file.bytes)
        assertEquals(local, DatevProfileService.getActiveProfile(app))
        assertTrue(remote.requests.all { it.method == "GET" })
    }

    @Test fun supplementalDatevCopyIsConservedThroughNoOpAndAnUnrelatedLocalAddition() = runBlocking {
        val cloud = SupplementalDriveBackup.createPayload(app, db)
        val typed = cloud.getJSONObject("datevProfilePrefs").getJSONObject("active_profile_json")
        assertEquals("string", typed.getString("type"))
        // Preserve even the embedded string representation, including whitespace.
        val originalRaw = JSONObject(typed.getString("value")).toString(2)
        typed.put("value", originalRaw)
        val supplemental = remote.put("synthetic-datev-supplemental", "supplementalBackup.json",
            config.systemFolderId, cloud.toString(4), "supplementalBackup")
        forgetOldInstallationBaselines()
        val local = profile.copy(lastModified = profile.lastModified + 1_000)
        DatevProfileService.saveActiveProfile(app, local)
        val before = supplemental.bytes.copyOf()
        val first = GoogleDriveClient.withSyncReadProof(token) {
            SupplementalDriveBackup.backup(app, db, token, config.systemFolderId)
        }
        assertTrue(first.message, first.success)
        assertArrayEquals(before, supplemental.bytes)
        assertTrue(remote.requests.all { it.method == "GET" })
        assertEquals(local, DatevProfileService.getActiveProfile(app))

        val route = StandardRoute(id = 123, name = "Synthetic added route", startAddress = "Synthetic A",
            destinationAddress = "Synthetic B", distanceKm = 12.5, createdAt = "2026-11-02T00:00:00Z",
            updatedAt = "2026-11-02T00:00:00Z")
        db.logbookDao().upsertStandardRoute(route)
        val expected = SupplementalDriveBackup.createPayload(app, db)
        expected.getJSONObject("datevProfilePrefs").getJSONObject("active_profile_json").put("value", originalRaw)
        remote.requests.clear()
        val second = GoogleDriveClient.withSyncReadProof(token) {
            SupplementalDriveBackup.backup(app, db, token, config.systemFolderId)
        }
        assertTrue(second.message, second.success)
        val actual = JSONObject(supplemental.bytes.toString(Charsets.UTF_8))
        assertEquals(canonicalDriveJsonText(expected.toString()), canonicalDriveJsonText(actual.toString()))
        assertEquals(originalRaw, actual.getJSONObject("datevProfilePrefs").getJSONObject("active_profile_json").getString("value"))
        assertEquals(listOf(route), db.logbookDao().getAllStandardRoutes())
        assertEquals(local, DatevProfileService.getActiveProfile(app))
        assertEquals(1, remote.entries.values.count { it.properties["entityType"] == "supplementalBackup" })

        remote.requests.clear()
        val finalBytes = supplemental.bytes.copyOf()
        val third = GoogleDriveClient.withSyncReadProof(token) {
            SupplementalDriveBackup.backup(app, db, token, config.systemFolderId)
        }
        assertTrue(third.message, third.success)
        assertArrayEquals(finalBytes, supplemental.bytes)
        assertTrue(remote.requests.all { it.method == "GET" })
    }

    @Test fun malformedDatevAccountMapMustNotBorrowAValidMapsEquivalence() = runBlocking {
        val validMap = "{\"Synthetic_A\":\"4801\"}"
        val malformedMaps = listOf(
            validMap + " trailing-junk",
            "{Synthetic_A:\"4801\"}",
            "{'Synthetic_A':'4801'}"
        )
        for (malformed in malformedMaps) {
            for ((localMap, remoteMap) in listOf(malformed to validMap, validMap to malformed)) {
                val local = profile.copy(categoryKontoMapJson = localMap)
                val file = datevFile()
                file.bytes = drive.serializeDatevProfile(profile.copy(categoryKontoMapJson = remoteMap)).toByteArray()
                file.revision++
                DatevProfileService.saveActiveProfile(app, local)
                forgetOldInstallationBaselines()
                remote.requests.clear()
                val before = file.bytes.copyOf()
                val (success, recorder) = backUp()
                assertFalse("Malformed map must retain the ordinary conflict guard: $malformed", success)
                assertEquals(DriveSyncCheck.BACKUP_PREIMAGE_CONFLICT,
                    recorder.snapshot().steps.single { it.phase == DriveSyncPhase.DATEV_PROFILES }.failure?.check)
                assertArrayEquals(before, file.bytes)
                assertEquals(local, DatevProfileService.getActiveProfile(app))
                assertTrue(remote.requests.all { it.method == "GET" })
            }
        }
    }

    @Test fun invalidProfileTimestampsCannotAuthorizeUnknownRemoteChanges() = runBlocking {
        val file = datevFile()
        for (timestamp in listOf<Any>(-1L, "not-a-timestamp", JSONObject.NULL, 12.5)) {
            val cloud = JSONObject(drive.serializeDatevProfile(profile))
            cloud.getJSONObject("activeProfile").put("lastModified", timestamp)
            file.bytes = cloud.toString().toByteArray()
            file.revision++
            forgetOldInstallationBaselines()
            remote.requests.clear()
            val before = file.bytes.copyOf()
            val (success, recorder) = backUp()
            assertFalse("Invalid timestamp must not create equivalence: $timestamp", success)
            assertEquals(DriveSyncCheck.BACKUP_PREIMAGE_CONFLICT,
                recorder.snapshot().steps.single { it.phase == DriveSyncPhase.DATEV_PROFILES }.failure?.check)
            assertArrayEquals(before, file.bytes)
            assertEquals(profile, DatevProfileService.getActiveProfile(app))
            assertTrue(remote.requests.all { it.method == "GET" })
        }
        // A missing historical timestamp may be added under existing rules, but does not
        // prove that different business content is safe to overwrite.
        val missingTimestamp = JSONObject(drive.serializeDatevProfile(profile))
        missingTimestamp.getJSONObject("activeProfile").apply {
            remove("lastModified")
            put("mandantenName", "Synthetic unknown legacy profile")
        }
        file.bytes = missingTimestamp.toString().toByteArray()
        file.revision++
        forgetOldInstallationBaselines()
        remote.requests.clear()
        val before = file.bytes.copyOf()
        val (success, recorder) = backUp()
        assertFalse(success)
        assertEquals(DriveSyncCheck.BACKUP_PREIMAGE_CONFLICT,
            recorder.snapshot().steps.single { it.phase == DriveSyncPhase.DATEV_PROFILES }.failure?.check)
        assertArrayEquals(before, file.bytes)
        assertTrue(remote.requests.all { it.method == "GET" })
    }

    @Test fun differentDatevIdentityAndProfileVersionRemainConflicts() = runBlocking {
        val file = datevFile()
        for (cloud in listOf(profile.copy(profileId = "SYNTHETIC_OTHER_PROFILE"), profile.copy(version = 2))) {
            file.bytes = drive.serializeDatevProfile(cloud).toByteArray()
            file.revision++
            forgetOldInstallationBaselines()
            remote.requests.clear()
            val before = file.bytes.copyOf()
            val (success, recorder) = backUp()
            assertFalse(success)
            assertEquals(DriveSyncCheck.BACKUP_PREIMAGE_CONFLICT,
                recorder.snapshot().steps.single { it.phase == DriveSyncPhase.DATEV_PROFILES }.failure?.check)
            assertArrayEquals(before, file.bytes)
            assertEquals(profile, DatevProfileService.getActiveProfile(app))
            assertTrue(remote.requests.all { it.method == "GET" })
            assertEquals(1, remote.entries.values.count { it.properties["entityType"] == "datevProfiles" })
        }
    }

    @Test fun malformedOrUnidentifiedSupplementalProfilesMustNotBorrowAValidProfilesEquivalence() = runBlocking {
        val valid = DatevProfileService.exportProfileToJson(profile)
        val invalidProfiles = listOf(
            valid + " trailing-junk",
            "[]",
            JSONObject(valid).apply { remove("profileId") }.toString(),
            JSONObject(valid).put("lastModified", "invalid-timestamp").toString()
        )
        for (invalid in invalidProfiles) {
            for ((localRaw, remoteRaw) in listOf(invalid to valid, valid to invalid)) {
                val cloud = SupplementalDriveBackup.createPayload(app, db)
                cloud.getJSONObject("datevProfilePrefs").getJSONObject("active_profile_json").put("value", remoteRaw)
                val file = remote.put("synthetic-invalid-profile-supplemental", "supplementalBackup.json",
                    config.systemFolderId, cloud.toString(), "supplementalBackup")
                app.getSharedPreferences("datev_kanzleiprofil_prefs", 0).edit()
                    .putString("active_profile_json", localRaw).commit()
                forgetOldInstallationBaselines()
                remote.requests.clear()
                val before = file.bytes.copyOf()
                val result = GoogleDriveClient.withSyncReadProof(token) {
                    SupplementalDriveBackup.backup(app, db, token, config.systemFolderId)
                }
                assertFalse(result.message, result.success)
                assertArrayEquals(before, file.bytes)
                assertEquals(localRaw, app.getSharedPreferences("datev_kanzleiprofil_prefs", 0)
                    .getString("active_profile_json", null))
                assertTrue(remote.requests.all { it.method == "GET" })
                assertEquals(1, remote.entries.values.count { it.properties["entityType"] == "supplementalBackup" })
            }
        }
    }
}
