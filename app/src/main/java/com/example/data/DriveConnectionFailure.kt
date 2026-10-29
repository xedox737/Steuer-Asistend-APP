package com.example.data

import com.example.api.DriveAccessFailure
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException

/** Only explicit authorization evidence may change the account's connected state. */
internal class DriveAuthorizationRequired : Exception("Google-Autorisierung erforderlich. Bitte erneut verbinden.")
internal class DriveConsentRequired : Exception("Google-Freigabe erforderlich.")

internal enum class DriveFailureKind { CONSENT, AUTHORIZATION, ACCESS, NOT_FOUND, NETWORK, TIMEOUT, METADATA, SAFETY, OTHER }

internal data class DriveConnectionFailure(val kind: DriveFailureKind, val message: String) {
    val requiresAuthorization get() = kind == DriveFailureKind.AUTHORIZATION

    companion object {
        fun from(error: Throwable): DriveConnectionFailure {
            if (error is CancellationException) throw error
            val kind = when {
                error is DriveConsentRequired || error is com.google.android.gms.auth.UserRecoverableAuthException -> DriveFailureKind.CONSENT
                error is DriveAuthorizationRequired || error is com.google.android.gms.auth.GoogleAuthException -> DriveFailureKind.AUTHORIZATION
                error is DriveAccessFailure && error.httpStatus == 401 -> DriveFailureKind.AUTHORIZATION
                error is DriveAccessFailure && error.httpStatus == 403 -> DriveFailureKind.ACCESS
                error is DriveAccessFailure && error.httpStatus == 404 -> DriveFailureKind.NOT_FOUND
                error is SocketTimeoutException || (error is DriveAccessFailure && error.reason == DriveAccessFailure.Reason.TIMEOUT) -> DriveFailureKind.TIMEOUT
                error is DriveAccessFailure -> when (error.reason) {
                    DriveAccessFailure.Reason.NETWORK -> DriveFailureKind.NETWORK
                    DriveAccessFailure.Reason.INVALID_METADATA, DriveAccessFailure.Reason.INCOMPLETE_SEARCH -> DriveFailureKind.METADATA
                    DriveAccessFailure.Reason.HTTP -> DriveFailureKind.OTHER
                    else -> DriveFailureKind.SAFETY
                }
                error is IOException -> DriveFailureKind.NETWORK
                else -> DriveFailureKind.OTHER
            }
            // Do not include exception messages: they may contain account details or response bodies.
            val message = when (kind) {
                DriveFailureKind.CONSENT -> "Google-Freigabe erforderlich. Bitte die Google-Bestätigung abschließen. Konto und lokale Daten bleiben erhalten."
                DriveFailureKind.AUTHORIZATION -> "Google-Autorisierung erforderlich. Bitte erneut mit Google Drive verbinden. Lokale Daten bleiben erhalten."
                DriveFailureKind.ACCESS -> "Google Drive verweigert den Zugriff (HTTP 403). Drive-Berechtigung, Freigabe und OAuth-Konfiguration prüfen. Die Anmeldung bleibt erhalten."
                DriveFailureKind.NOT_FOUND -> "Drive-Ordner oder Datei nicht erreichbar (HTTP 404). Keine Ersatzstruktur angelegt. Bitte Zugriff prüfen und erneut synchronisieren."
                DriveFailureKind.NETWORK -> "Netzwerkverbindung zu Google Drive unterbrochen. Die Anmeldung bleibt erhalten; bitte erneut synchronisieren."
                DriveFailureKind.TIMEOUT -> "Zeitüberschreitung bei Google Drive. Die Anmeldung bleibt erhalten; bitte erneut synchronisieren."
                DriveFailureKind.METADATA -> "Drive-Metadaten konnten nicht sicher geprüft werden. Bitte erneut synchronisieren. Keine Ersatzstruktur angelegt."
                DriveFailureKind.SAFETY -> "Drive-Sicherheitsprüfung fehlgeschlagen. Zugriff blockiert; die Google-Anmeldung bleibt erhalten."
                DriveFailureKind.OTHER -> "Google-Drive-Synchronisierung fehlgeschlagen. Bitte erneut versuchen. Lokale Daten und Anmeldung bleiben erhalten."
            }
            return DriveConnectionFailure(kind, message)
        }
    }
}
