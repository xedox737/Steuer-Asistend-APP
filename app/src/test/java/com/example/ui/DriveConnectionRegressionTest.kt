package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.api.GoogleDriveClient
import com.example.comparison.ComparisonDriveHttpFixture
import com.example.data.AppDatabase
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
            val code = if (r.header("Authorization") == "Bearer expired-native") 401 else status
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
        withTimeout(15_000) { vm.driveSystemFolderStatus.first { it == "Gefunden" } }
    }
    private fun assertAccountRetained(vm: ReceiptViewModel) {
        assertTrue("A Drive failure must not sign out", vm.isDriveConnected.value)
        assertEquals("synthetic@example.invalid", prefs.getString("connected_email", null))
        assertFalse(prefs.getBoolean("user_disconnected", true))
        assertFalse(vm.driveSyncStatus.value.orEmpty().contains("Anmeldung erforderlich"))
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
                return if (expireFirst && count == 1) "expired-native" else "synthetic-native"
            }
            @JvmStatic @Implementation fun clearToken(context: Context, token: String) { clears.incrementAndGet() }
        }
    }
}
