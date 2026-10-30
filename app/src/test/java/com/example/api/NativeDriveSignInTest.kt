package com.example.api

import android.accounts.Account
import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.Status
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tests the SDK Intent contract, not real Google OAuth or console configuration. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NativeDriveSignInTest {
    private fun account(scope: String = NativeDriveSignIn.DRIVE_SCOPE) =
        GoogleSignInAccount.fromAccountAndScopes(Account("synthetic@example.invalid", "com.google"), Scope(scope))
    private fun result(account: GoogleSignInAccount) = Intent().putExtra("googleSignInAccount", account)
    private fun rejection(code: Int, resultCode: Int = Activity.RESULT_CANCELED) =
        NativeDriveSignIn.read(resultCode, Intent().putExtra("googleSignInStatus", Status(code))) as NativeDriveSignIn.Result.Rejected

    @Test fun nativeOptionsRequestOnlyEmailAndDriveFileWithoutWebClient() {
        val options = NativeDriveSignIn.options()
        assertTrue(options.scopes.any { it.scopeUri == NativeDriveSignIn.DRIVE_SCOPE })
        assertFalse(options.isIdTokenRequested)
        assertFalse(options.isServerAuthCodeRequested)
        assertNull(options.serverClientId)
        assertFalse(options.scopes.any { it.scopeUri == "https://www.googleapis.com/auth/drive" })
    }
    @Test fun successfulSdkResultPassesTheSelectedAccount() {
        val value = NativeDriveSignIn.read(Activity.RESULT_OK, result(account()))
        assertEquals("synthetic@example.invalid", (value as NativeDriveSignIn.Result.Authorized).email)
    }
    @Test fun successfulResultWithoutDriveScopeIsNotAuthorization() {
        val value = NativeDriveSignIn.read(Activity.RESULT_OK, result(account("email")))
        assertEquals(NativeDriveSignIn.Reason.SCOPE, (value as NativeDriveSignIn.Result.Rejected).reason)
    }
    @Test fun canceledResultCannotAcceptAnAccountPayload() {
        assertTrue(NativeDriveSignIn.read(Activity.RESULT_CANCELED, result(account())) is NativeDriveSignIn.Result.Rejected)
    }
    @Test fun canceledChooserWithNoIntentIsCancellation() {
        assertEquals(NativeDriveSignIn.Reason.CANCELED, (NativeDriveSignIn.read(Activity.RESULT_CANCELED, null) as NativeDriveSignIn.Result.Rejected).reason)
    }
    @Test fun developerErrorSurvivesCanceledAndroidResult() {
        val value = rejection(10)
        assertEquals(10, value.googleStatus)
        assertEquals(Activity.RESULT_CANCELED, value.activityResult)
        assertEquals(NativeDriveSignIn.Reason.CONFIGURATION, value.reason)
        assertTrue(value.message.contains("10"))
    }
    @Test fun explicitSdkCancelIsNotConfigurationFailure() {
        assertEquals(NativeDriveSignIn.Reason.CANCELED, rejection(12501).reason)
        assertEquals(NativeDriveSignIn.Reason.CANCELED, rejection(16).reason)
    }
    @Test fun networkErrorIsRetryableWithoutReconfiguration() {
        assertEquals(NativeDriveSignIn.Reason.NETWORK, rejection(7).reason)
        assertEquals(NativeDriveSignIn.Reason.NETWORK, rejection(15).reason)
    }
    @Test fun emptySuccessfulIntentDoesNotInventAnAccount() {
        assertTrue(NativeDriveSignIn.read(Activity.RESULT_OK, Intent()) is NativeDriveSignIn.Result.Rejected)
    }
    @Test fun diagnosticsNeverEchoIntentCredentialsOrAccount() {
        val data = Intent().putExtra("googleSignInStatus", Status(10, "secret-token synthetic@example.invalid"))
            .putExtra("access_token", "private-access-token")
        val message = (NativeDriveSignIn.read(Activity.RESULT_CANCELED, data) as NativeDriveSignIn.Result.Rejected).message
        assertFalse(message.contains("secret-token"))
        assertFalse(message.contains("example.invalid"))
        assertFalse(message.contains("private-access-token"))
    }
}
