package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.api.GoogleDriveClient
import com.example.comparison.ComparisonDriveHttpFixture
import com.example.data.DriveInitializationResult
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** Real ViewModel/repository/client with simulated Play Services and HTTP, never real OAuth. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [DriveConnectionRegressionTest.AuthShadow::class])
class DriveConnectionRegressionTest {
    @get:Rule val isolation = IsolatedAppDatabaseRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val prefs get() = app.getSharedPreferences("google_drive_prefs", 0)
    private val transport = GoogleDriveClient::class.java.getDeclaredField("client").apply { isAccessible = true }
    private lateinit var previous: OkHttpClient
    private val stores = mutableListOf<ViewModelStore>()
    private lateinit var remote: ComparisonDriveHttpFixture
    @Volatile private var status = 200
    @Volatile private var fault: Exception? = null
    @Volatile private var metadataFault = false
    @Volatile private var rejectUploads = false
    @Volatile private var expireNativeDuringUpload = false
    private val calls = AtomicInteger()

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        prefs.edit().clear().putBoolean("user_disconnected", true).putBoolean("auto_backup", false).commit()
        AuthShadow.failure = null; AuthShadow.calls.set(0); AuthShadow.clears.set(0); AuthShadow.expireFirst = false
        previous = transport.get(null) as OkHttpClient
        remote = ComparisonDriveHttpFixture()
        GoogleDriveClient.clearFolderCache()
        transport.set(null, OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            fault?.let { throw it }
            val r = chain.request()
            val code = if (r.header("Authorization") == "Bearer expired-native" ||
                (expireNativeDuringUpload && r.header("Authorization") == "Bearer synthetic-native" && r.url.encodedPath.startsWith("/upload/")) ||
                (rejectUploads && r.url.encodedPath.startsWith("/upload/"))) 401 else status
            if (code != 200 || (metadataFault && r.url.encodedPath.contains("/files/"))) {
                Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(if (code == 200) 503 else code)
                    .message("Synthetic").body("{}".toResponseBody("application/json".toMediaType())).build()
            } else chain.proceed(r)
        }.addInterceptor(remote).build())
    }

    @After fun cleanup() {
        stores.forEach { it.clear() }
        transport.set(null, previous); GoogleDriveClient.clearFolderCache()
        Dispatchers.resetMain()
    }

    private fun model(): ReceiptViewModel {
        val store = ViewModelStore().also { stores += it }
        return ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[ReceiptViewModel::class.java]
    }
    private fun awaitFailure(vm: ReceiptViewModel) = runBlocking {
        withTimeout(15_000) { vm.driveSyncError.first { !it.isNullOrBlank() } }
    }
    private fun awaitSuccess(vm: ReceiptViewModel) = runBlocking {
        withTimeout(15_000) {
            vm.driveSystemFolderStatus.first { it == "Gefunden" }
            while (vm.driveSyncStatus.value.orEmpty().contains("Initialisiere")) delay(10)
        }
    }
    private fun assertAccountRetained(vm: ReceiptViewModel) {
        assertTrue("A Drive failure must not sign out", vm.isDriveConnected.value)
        assertEquals("synthetic@example.invalid", prefs.getString("connected_email", null))
        assertFalse(prefs.getBoolean("user_disconnected", true))
        assertFalse(vm.driveSyncStatus.value.orEmpty().contains("Anmeldung erforderlich"))
    }

    @Test fun realDriveDiagnosticPreservesInitializationFailureInsteadOfGenericReceiptError() {
        val vm = model(); vm.connectDrive("synthetic@example.invalid"); awaitSuccess(vm)
        val configFile = remote.entries.values.single { it.properties["entityType"] == "appConfig" }
        val json = org.json.JSONObject(configFile.bytes.toString(Charsets.UTF_8))
        json.put("backupsFolderId", "missing-current-backup")
        configFile.bytes = json.toString().toByteArray()
        vm.runRealDriveTest()
        val result = runBlocking { withTimeout(15_000) { vm.driveTestState.first { it != null && !it.isRunning }!! } }
        assertFalse(result.isSuccess)
        assertEquals("Drive-Initialisierung", result.affectedAction)
        assertTrue(result.errorMessage.orEmpty().contains("HTTP_404"))
        assertEquals(404, result.httpStatusCode)
        assertAccountRetained(vm)
    }

    @Test fun networkDuringNativeTokenAcquisitionKeepsLoginAndAllowsRetry() {
        AuthShadow.failure = IOException("secret-account-token-must-not-be-shown")
        val vm = model(); vm.connectDrive("synthetic@example.invalid"); awaitFailure(vm)
        assertAccountRetained(vm)
        assertFalse(vm.driveSyncError.value.orEmpty().contains("secret-account"))
        AuthShadow.failure = null; vm.initializeDrivePersistence(); awaitSuccess(vm)
    }
    @Test fun timeoutDuringNativeTokenAcquisitionDoesNotSignOut() {
        AuthShadow.failure = java.net.SocketTimeoutException("synthetic timeout")
        val vm = model(); vm.connectDrive("synthetic@example.invalid"); awaitFailure(vm); assertAccountRetained(vm)
    }
    @Test fun metadataFailureDoesNotSignOutOrCreateReplacementFolders() {
        metadataFault = true
        val vm = model(); vm.connectDrive("synthetic@example.invalid", "synthetic-manual"); awaitFailure(vm)
        assertAccountRetained(vm)
        assertTrue(remote.entries.values.count { it.name == "_BelegApp-Daten" } <= 1)
    }
    @Test fun rejectedManualTokenRequiresAuthorizationAndKeepsLocalPreferences() {
        prefs.edit().putString("local_sentinel", "keep").commit(); status = 401
        val vm = model(); vm.connectDrive("synthetic@example.invalid", "synthetic-manual"); awaitFailure(vm)
        assertFalse("HTTP401 must require authorization", vm.isDriveConnected.value)
        assertEquals("keep", prefs.getString("local_sentinel", null))
        assertEquals("synthetic@example.invalid", prefs.getString("connected_email", null))
    }
    @Test fun forbiddenIsAnAccessErrorNotALogout() {
        status = 403; val vm = model(); vm.connectDrive("synthetic@example.invalid", "synthetic-manual")
        awaitFailure(vm); assertAccountRetained(vm); assertTrue(vm.driveSyncError.value.orEmpty().contains("403"))
    }
    @Test fun missingMetadataIsNotALogout() {
        status = 404; val vm = model(); vm.connectDrive("synthetic@example.invalid", "synthetic-manual")
        awaitFailure(vm); assertAccountRetained(vm)
    }
    @Test fun restartRestoresAccountWhileOfflineWithoutClaimingLogout() {
        prefs.edit().putBoolean("user_disconnected", false).putString("connected_email", "synthetic@example.invalid").commit()
        AuthShadow.failure = IOException("offline")
        val vm = model(); awaitFailure(vm); assertAccountRetained(vm)
    }
    @Test fun playServicesLoginDoesNotReuseTokenOfPreviousAccount() {
        prefs.edit().putString("access_token", "old-account-token").commit()
        val vm = model(); vm.connectDrive("synthetic@example.invalid"); awaitSuccess(vm)
        assertTrue("Must ask Play Services for selected account", AuthShadow.calls.get() > 0)
        assertFalse(prefs.contains("access_token"))
    }
    @Test fun expiredNativeTokenIsInvalidatedAndRetriedOnce() {
        AuthShadow.expireFirst = true
        val vm = model(); vm.connectDrive("synthetic@example.invalid"); awaitSuccess(vm)
        assertTrue(vm.isDriveConnected.value); assertEquals(1, AuthShadow.clears.get())
    }
    @Test fun simultaneousInitializationsDoNotDuplicateConfig() = runBlocking {
        val vm = model()
        val results = (1..2).map { async(Dispatchers.IO) { vm.drivePersistenceRepository.initializeDriveStorage("synthetic-concurrent") } }.awaitAll()
        assertTrue(results.all { it !is DriveInitializationResult.Failure })
        assertEquals(1, remote.entries.values.count { it.properties["entityType"] == "appConfig" })
        assertEquals(1, remote.entries.values.count { it.name == "_BelegApp-Daten" })
    }
    @Test fun cancellationIsPropagatedByRepository() = runBlocking {
        fault = CancellationException("synthetic cancellation")
        val vm = model()
        try {
            vm.drivePersistenceRepository.initializeDriveStorage("synthetic-cancel")
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
    @Test fun cancelledInitializationDoesNotDisplayLoginError() {
        fault = CancellationException("synthetic cancellation")
        val vm = model(); vm.connectDrive("synthetic@example.invalid", "synthetic-cancel")
        runBlocking { withTimeout(15_000) { while (calls.get() == 0) delay(10) }; delay(150) }
        assertTrue(vm.isDriveConnected.value)
        assertFalse(vm.driveSyncStatus.value.orEmpty().contains("Anmeldung erforderlich"))
        assertFalse(vm.driveSyncStatus.value.orEmpty().contains("Initialisiere"))
        assertNull(vm.driveSyncError.value)
    }

    @Test fun networkDuringDriveInitializationKeepsAccountAndOriginalPreferences() {
        fault = IOException("must-not-display-secret")
        prefs.edit().putString("local_sentinel", "keep").commit()
        val vm = model(); vm.connectDrive("synthetic@example.invalid", "synthetic-manual"); awaitFailure(vm)
        assertAccountRetained(vm)
        assertEquals("keep", prefs.getString("local_sentinel", null))
        assertEquals(1, remote.entries.size) // Only synthetic MyDrive, no replacement structure.
        assertFalse(vm.driveSyncError.value.orEmpty().contains("must-not-display-secret"))
    }
    @Test fun manualTokenDeadlineIsNotExtendedByRestart() {
        val vm = model(); vm.connectDrive("synthetic@example.invalid", "synthetic-manual", 1200); awaitSuccess(vm)
        val deadline = prefs.getLong("access_token_expires_at", 0)
        stores.forEach { it.clear() }; stores.clear()
        val restarted = model(); awaitSuccess(restarted)
        assertEquals(deadline, prefs.getLong("access_token_expires_at", -1))
        assertEquals(0, AuthShadow.calls.get()); assertAccountRetained(restarted)
    }
    @Test fun expiredWebTokenRequiresAuthorizationBeforeAnyDriveAccess() {
        val vm = model(); vm.connectDrive("synthetic@example.invalid", "synthetic-expired", 0); awaitFailure(vm)
        assertFalse(vm.isDriveConnected.value); assertEquals(0, calls.get())
        assertTrue(prefs.getBoolean("drive_reauthorization_required", false))
        assertEquals("synthetic@example.invalid", prefs.getString("connected_email", null))
    }
    @Test fun rejectedAuthorizationRemainsActionableAfterRestart() {
        status = 401
        val vm = model(); vm.connectDrive("synthetic@example.invalid", "synthetic-rejected"); awaitFailure(vm)
        stores.forEach { it.clear() }; stores.clear()
        val count = calls.get(); val restarted = model()
        assertFalse(restarted.isDriveConnected.value)
        assertTrue(restarted.driveSyncStatus.value.orEmpty().contains("Autorisierung"))
        assertEquals(count, calls.get())
    }
    @Test fun nativeAuthorizationFailureRequiresLoginWithoutRemovingAccount() {
        AuthShadow.failure = com.google.android.gms.auth.GoogleAuthException("synthetic-revoked")
        val vm = model(); vm.connectDrive("synthetic@example.invalid"); awaitFailure(vm)
        assertFalse(vm.isDriveConnected.value)
        assertEquals("synthetic@example.invalid", prefs.getString("connected_email", null))
        assertFalse(prefs.getBoolean("user_disconnected", true))
    }
    @Test fun failedSyncDoesNotInvalidateSuccessfulLogin() {
        val vm = model(); vm.connectDrive("synthetic@example.invalid", "synthetic-manual"); awaitSuccess(vm)
        fault = IOException("offline"); vm.syncAllToDrive(); awaitFailure(vm)
        runBlocking { withTimeout(15_000) { vm.isDriveSyncing.first { !it } } }
        assertAccountRetained(vm)
    }
    @Test fun unauthorizedSyncRequiresAuthorizationAndEndsSpinner() {
        val vm = model(); vm.connectDrive("synthetic@example.invalid", "synthetic-manual"); awaitSuccess(vm)
        status = 401; vm.syncAllToDrive(); awaitFailure(vm)
        runBlocking { withTimeout(15_000) { vm.isDriveSyncing.first { !it } } }
        assertFalse(vm.isDriveConnected.value)
        assertTrue(vm.driveSyncStatus.value.orEmpty().contains("Autorisierung"))
    }
    @Test fun revokedTokenDuringUploadIsNotSwallowedAsPartialSuccess() {
        val vm = model(); vm.connectDrive("synthetic@example.invalid", "synthetic-manual"); awaitSuccess(vm)
        runBlocking {
            val db = com.example.data.AppDatabase.getDatabase(app, CoroutineScope(Dispatchers.IO))
            db.receiptDao().insertReceipt(com.example.data.Receipt(aussteller = "Synthetic", datum = "2026-10-27", uhrzeit = "",
                bruttobetrag = 1.0, hauptkategorie = "Test", unterkategorie = "Test", kontoNr = "4800", beschreibung = "",
                internalId = "synthetic-local-original"))
            withTimeout(15_000) { vm.receipts.first { it.isNotEmpty() } }
        }
        rejectUploads = true; vm.syncAllToDrive(); awaitFailure(vm)
        runBlocking { withTimeout(15_000) { vm.isDriveSyncing.first { !it } } }
        assertFalse(vm.isDriveConnected.value)
        assertTrue(vm.driveSyncStatus.value.orEmpty().contains("Autorisierung"))
    }
    @Test fun nativeTokenExpiryDuringUploadRenewsCredentialBeforeRequestingLogin() {
        val vm = model(); vm.connectDrive("synthetic@example.invalid"); awaitSuccess(vm)
        runBlocking {
            val db = com.example.data.AppDatabase.getDatabase(app, CoroutineScope(Dispatchers.IO))
            db.receiptDao().insertReceipt(com.example.data.Receipt(aussteller = "Synthetic", datum = "2026-10-27", uhrzeit = "",
                bruttobetrag = 1.0, hauptkategorie = "Test", unterkategorie = "Test", kontoNr = "4800", beschreibung = "",
                internalId = "synthetic-local-original"))
            withTimeout(15_000) { vm.receipts.first { it.isNotEmpty() } }
        }
        expireNativeDuringUpload = true; vm.syncAllToDrive(); awaitFailure(vm)
        runBlocking { withTimeout(15_000) { vm.isDriveSyncing.first { !it } } }
        assertAccountRetained(vm)
        assertEquals(1, AuthShadow.clears.get())
        assertTrue(vm.driveSyncStatus.value.orEmpty().contains("erneuert"))
    }
    @Test fun repeatedLoginCallbacksDoNotExtendTokenOrDuplicateConfiguration() {
        val vm = model()
        repeat(2) { vm.connectDrive("synthetic@example.invalid", "synthetic-manual", 1800) }
        awaitSuccess(vm)
        assertEquals(1, remote.entries.values.count { it.properties["entityType"] == "appConfig" })
        assertEquals(1, remote.entries.values.count { it.name == "_BelegApp-Daten" })
        assertAccountRetained(vm)
    }
    @Test fun explicitDisconnectCannotBeReversedByInFlightInitialization() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        transport.set(null, OkHttpClient.Builder().addInterceptor { chain ->
            entered.countDown(); check(release.await(10, java.util.concurrent.TimeUnit.SECONDS))
            chain.proceed(chain.request())
        }.addInterceptor(remote).build())
        val vm = model(); vm.connectDrive("synthetic@example.invalid", "synthetic-manual")
        try {
            assertTrue(entered.await(10, java.util.concurrent.TimeUnit.SECONDS))
            vm.disconnectDrive()
        } finally { release.countDown() }
        runBlocking { delay(150) }
        assertFalse(vm.isDriveConnected.value); assertNull(vm.googleAccountEmail.value)
        assertEquals("Verbindung getrennt", vm.driveSyncStatus.value)
        assertNull(prefs.getString("connected_email", null))
    }

    @Test fun recoverableConsentDoesNotBecomeLogout() {
        AuthShadow.failure = com.google.android.gms.auth.UserRecoverableAuthException(
            "synthetic-consent", android.content.Intent("synthetic.CONSENT"))
        val vm = model(); vm.connectDrive("synthetic@example.invalid"); awaitFailure(vm)
        assertAccountRetained(vm)
        assertTrue(vm.driveSyncError.value.orEmpty().contains("Freigabe"))
        assertFalse(prefs.getBoolean("drive_reauthorization_required", false))
    }

    private fun initialization(vm: ReceiptViewModel): Job? = ReceiptViewModel::class.java
        .getDeclaredField("driveInitializationJob").apply { isAccessible = true }.get(vm) as Job?
    private fun awaitInitialization(vm: ReceiptViewModel) = runBlocking { withTimeout(15_000) { initialization(vm)?.join() } }
    private fun pendingConsent(vm: ReceiptViewModel): ReceiptViewModel.DriveConsentRequest = runBlocking {
        withTimeout(15_000) { vm.driveConsentRequest.first { it != null }!! }
    }
    private fun requestConsent(): ReceiptViewModel {
        AuthShadow.failure = com.google.android.gms.auth.UserRecoverableAuthException(
            "synthetic", android.content.Intent("synthetic.CONSENT"))
        val vm = model(); vm.connectDrive("synthetic@example.invalid"); awaitFailure(vm)
        awaitInitialization(vm)
        return vm
    }
    @Test fun consentIsConsumedOnlyOnce() {
        val vm = requestConsent(); val request = pendingConsent(vm)
        assertNotNull(vm.takeDriveConsent(request.id))
        assertNull(vm.takeDriveConsent(request.id))
    }
    @Test fun approvedConsentRequestsFreshTokenAndValidatesDrive() {
        val vm = requestConsent(); val request = pendingConsent(vm)
        vm.takeDriveConsent(request.id); AuthShadow.failure = null
        vm.completeDriveConsent(request.id, true); awaitSuccess(vm)
        assertAccountRetained(vm); assertNull(vm.driveConsentRequest.value)
    }
    @Test fun canceledConsentPreservesAccountAndLocalPreferences() {
        prefs.edit().putString("local_sentinel", "keep").commit()
        val vm = requestConsent(); val request = pendingConsent(vm); vm.takeDriveConsent(request.id)
        vm.completeDriveConsent(request.id, false)
        assertAccountRetained(vm); assertEquals("keep", prefs.getString("local_sentinel", null))
        assertTrue(vm.driveSyncStatus.value.orEmpty().contains("abgebrochen"))
    }
    @Test fun consentCallbackAfterLogoutCannotReconnect() {
        val vm = requestConsent(); val request = pendingConsent(vm); vm.takeDriveConsent(request.id)
        vm.disconnectDrive(); AuthShadow.failure = null; vm.completeDriveConsent(request.id, true)
        assertNull(vm.googleAccountEmail.value); assertFalse(vm.isDriveConnected.value)
        assertNull(vm.driveConsentRequest.value)
    }
    @Test fun consentCallbackCannotOverwriteAnotherAccount() {
        val vm = requestConsent(); val request = pendingConsent(vm); vm.takeDriveConsent(request.id)
        AuthShadow.failure = null; vm.connectDrive("other@example.invalid"); awaitSuccess(vm)
        vm.completeDriveConsent(request.id, true)
        assertEquals("other@example.invalid", vm.googleAccountEmail.value)
    }
    @Test fun unresolvedApprovalDoesNotLoopGoogleDialogs() {
        val vm = requestConsent(); val request = pendingConsent(vm); vm.takeDriveConsent(request.id)
        val previous = initialization(vm)
        vm.completeDriveConsent(request.id, true)
        runBlocking { withTimeout(15_000) { while (initialization(vm) === previous) delay(10) } }
        awaitInitialization(vm)
        assertNull(vm.driveConsentRequest.value); assertAccountRetained(vm)
        assertTrue(vm.driveSyncError.value.orEmpty().contains("Freigabe"))
    }

    @Test fun nativeCallbackHandsSelectedAccountToViewModelAndRejectsDuplicate() {
        val vm = model(); val session = vm.nativeDriveSignInSession()
        vm.completeNativeDriveSignIn(session, com.example.api.NativeDriveSignIn.Result.Authorized("synthetic@example.invalid"))
        awaitSuccess(vm)
        vm.completeNativeDriveSignIn(session, com.example.api.NativeDriveSignIn.Result.Authorized("other@example.invalid"))
        assertEquals("synthetic@example.invalid", vm.googleAccountEmail.value)
        assertEquals(1, remote.entries.values.count { it.properties["entityType"] == "appConfig" })
    }
    @Test fun failedNativeCallbackCannotChangePreferencesOrAccount() {
        val vm = model(); prefs.edit().putString("local_sentinel", "keep").commit()
        val before = prefs.all.toMap()
        vm.completeNativeDriveSignIn(vm.nativeDriveSignInSession(), com.example.api.NativeDriveSignIn.failure(android.app.Activity.RESULT_CANCELED, 10))
        assertEquals(before, prefs.all)
        assertNull(vm.googleAccountEmail.value); assertFalse(vm.isDriveConnected.value)
        vm.runRealDriveTest()
        runBlocking { withTimeout(15_000) { vm.driveTestState.first { it?.isRunning == false } } }
        assertTrue(vm.driveTestState.value!!.errorMessage.orEmpty().contains("10"))
    }
    @Test fun nativeCallbackAfterExplicitLogoutIsIgnored() {
        val vm = model(); val session = vm.nativeDriveSignInSession(); vm.disconnectDrive()
        vm.completeNativeDriveSignIn(session, com.example.api.NativeDriveSignIn.Result.Authorized("synthetic@example.invalid"))
        assertFalse(vm.isDriveConnected.value); assertNull(vm.googleAccountEmail.value)
    }

    @Test fun restartRecreatesRequiredConsentWithoutLosingAccount() {
        val first = requestConsent(); assertNotNull(pendingConsent(first))
        stores.last().clear()
        val restarted = model(); pendingConsent(restarted); awaitFailure(restarted)
        assertAccountRetained(restarted)
    }
    @Test fun missingConsentResolutionRequiresExplicitAuthorization() {
        AuthShadow.failure = com.google.android.gms.auth.UserRecoverableAuthException("synthetic", null)
        val vm = model(); vm.connectDrive("synthetic@example.invalid"); awaitFailure(vm)
        assertFalse(vm.isDriveConnected.value)
        assertNull(vm.driveConsentRequest.value)
        assertEquals("synthetic@example.invalid", vm.googleAccountEmail.value)
        assertTrue(prefs.getBoolean("drive_reauthorization_required", false))
    }

    @Test fun retryFromPublishedNetworkFailureIsNotDroppedByCompletingJob() = runBlocking {
        val vm = model()
        AuthShadow.failure = IOException("synthetic-network")
        val observed = CompletableDeferred<Unit>()
        val retry = CoroutineScope(Dispatchers.Unconfined).launch(start = CoroutineStart.UNDISPATCHED) {
            vm.driveSyncError.first { !it.isNullOrBlank() }
            AuthShadow.failure = null
            repeat(5) { vm.initializeDrivePersistence() }
            observed.complete(Unit)
        }
        try {
            vm.connectDrive("synthetic@example.invalid")
            withTimeout(15_000) { observed.await() }
            awaitSuccess(vm)
            assertAccountRetained(vm)
            assertEquals(1, AuthShadow.calls.get())
            assertEquals(1, remote.entries.values.count { it.properties["entityType"] == "appConfig" })
        } finally { retry.cancel() }
    }

    @Test fun queuedNetworkRetryCannotSurviveLogout() = runBlocking {
        val vm = model(); AuthShadow.failure = IOException("synthetic-network")
        val observed = CompletableDeferred<Unit>()
        val retry = CoroutineScope(Dispatchers.Unconfined).launch(start = CoroutineStart.UNDISPATCHED) {
            vm.driveSyncError.first { !it.isNullOrBlank() }
            AuthShadow.failure = null
            vm.initializeDrivePersistence()
            vm.disconnectDrive()
            observed.complete(Unit)
        }
        try {
            vm.connectDrive("synthetic@example.invalid")
            withTimeout(15_000) { observed.await() }
            awaitInitialization(vm)
            assertNull(vm.googleAccountEmail.value)
            assertFalse(vm.isDriveConnected.value)
            assertEquals(0, AuthShadow.calls.get())
            assertEquals("Verbindung getrennt", vm.driveSyncStatus.value)
        } finally { retry.cancel() }
    }

    @Implements(GoogleAuthUtil::class)
    class AuthShadow {
        companion object {
            @JvmField var failure: Exception? = null
            @JvmField val calls = AtomicInteger()
            @JvmField val clears = AtomicInteger()
            @JvmField var expireFirst = false
            @JvmStatic @Implementation fun getToken(context: Context, account: String, scope: String): String {
                failure?.let { throw it }
                assertEquals("oauth2:https://www.googleapis.com/auth/drive.file", scope)
                val count = calls.incrementAndGet()
                return if (expireFirst && count == 1) "expired-native" else if (clears.get() > 0) "synthetic-native-renewed" else "synthetic-native"
            }
            @JvmStatic @Implementation fun clearToken(context: Context, token: String) { clears.incrementAndGet() }
        }
    }
}
