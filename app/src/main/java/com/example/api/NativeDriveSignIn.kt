package com.example.api

import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException

/** Native SDK boundary. Diagnostics contain codes only, never Intent extras or account data. */
internal object NativeDriveSignIn {
    const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    sealed interface Result {
        class Authorized(val email: String) : Result
        data class Rejected(val activityResult: Int?, val googleStatus: Int?, val reason: Reason) : Result {
            val message: String get() = when (reason) {
                Reason.CANCELED -> "Google-Anmeldung abgebrochen."
                Reason.CONFIGURATION -> "Google-Anmeldung nicht konfiguriert (Google-Code 10). OAuth-Projekt, Package-ID und APK-Signatur prüfen."
                Reason.NETWORK -> "Netzwerkfehler bei der Google-Anmeldung. Bitte erneut versuchen."
                Reason.SCOPE -> "Drive-Berechtigung nicht erteilt. Bitte erneut mit Google Drive verbinden."
                Reason.ACCOUNT -> "Google hat kein nutzbares Konto zurückgegeben. Bitte erneut verbinden."
                Reason.OTHER -> "Google-Anmeldung fehlgeschlagen (Google-Code ${googleStatus ?: "nicht verfügbar"}). Bitte erneut versuchen."
            } + " Phase: native Anmeldung; Android-Ergebnis: ${activityResult ?: "nicht verfügbar"}."
        }
    }
    enum class Reason { CANCELED, CONFIGURATION, NETWORK, SCOPE, ACCOUNT, OTHER }

    fun options(): GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail().requestScopes(Scope(DRIVE_SCOPE)).build()

    fun failure(resultCode: Int?, statusCode: Int?): Result.Rejected = Result.Rejected(resultCode, statusCode,
        when (statusCode) {
            10 -> Reason.CONFIGURATION
            7, 15 -> Reason.NETWORK
            16, 12501 -> Reason.CANCELED
            else -> if (statusCode == null && resultCode == Activity.RESULT_CANCELED) Reason.CANCELED else Reason.OTHER
        })

    fun read(resultCode: Int, data: Intent?): Result {
        if (data == null) return failure(resultCode, null)
        return try {
            // CANCELED may still carry a real DEVELOPER_ERROR. Preserve the SDK's status first.
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
            when {
                resultCode != Activity.RESULT_OK -> failure(resultCode, null)
                account?.email.isNullOrBlank() -> Result.Rejected(resultCode, null, Reason.ACCOUNT)
                !GoogleSignIn.hasPermissions(account, Scope(DRIVE_SCOPE)) -> Result.Rejected(resultCode, null, Reason.SCOPE)
                else -> Result.Authorized(account!!.email!!)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: ApiException) {
            failure(resultCode, error.statusCode)
        } catch (error: RuntimeException) {
            failure(resultCode, null)
        }
    }
}
