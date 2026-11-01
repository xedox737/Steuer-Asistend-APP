package com.example.api

import java.io.IOException

/** Stable, privacy-safe diagnostics: never includes a token, account, Drive ID or response body. */
internal open class DriveAccessFailure(val reason: Reason, val httpStatus: Int? = null) : IOException(
    "Drive-Prüfung [${if (httpStatus != null) "HTTP_$httpStatus" else reason.name}]: " + when (reason) {
        Reason.HTTP -> "Google Drive ist nicht erreichbar oder der Zugriff ist nicht erlaubt. Keine Ersatzstruktur angelegt."
        Reason.NETWORK -> "Netzwerkverbindung unterbrochen. Bitte erneut versuchen."
        Reason.TIMEOUT -> "Zeitüberschreitung. Bitte erneut versuchen."
        Reason.INVALID_ID -> "Die gespeicherte Drive-ID hat ein ungültiges Format."
        Reason.INVALID_METADATA -> "Die Antwort von Google Drive enthält keine gültigen Metadaten."
        Reason.WRONG_MIME -> "Die gespeicherte Ordner-ID bezeichnet keinen Ordner."
        Reason.WRONG_LOCATION -> "Der gespeicherte Ordner gehört nicht zum erwarteten Elternordner oder Namen."
        Reason.NAMESPACE -> "Drive-Sicherheitsabbruch: Zugriff außerhalb des eigenen App-Datenspeichers verweigert."
        Reason.INCOMPLETE_SEARCH -> "Die Drive-Suche ist unvollständig. Keine Ersatzstruktur angelegt."
    }
) {
    enum class Reason { HTTP, NETWORK, TIMEOUT, INVALID_ID, INVALID_METADATA, WRONG_MIME, WRONG_LOCATION, NAMESPACE, INCOMPLETE_SEARCH }
}

/** Local failed invariants keep the legacy conflict status internally, but must never be
 * presented as an HTTP response or proof that somebody changed the cloud data. */
internal class DriveSyncConflict(
    val check: DriveSyncCheck,
    val localRevision: Long? = null,
    val driveRevision: Long? = null,
    differentFields: List<String> = emptyList(),
    val expectedBytes: Long? = null,
    val actualBytes: Long? = null
) : DriveAccessFailure(Reason.HTTP, 412) {
    override val message: String get() = "Lokale Drive-Sicherheitsprüfung [${check.name}]: Schreiben gesperrt."
    val differentFields = differentFields.filter { it.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,63}")) }.distinct().sorted().take(64)
}

/** Control failures must survive legacy helpers returning false/null on ordinary Drive errors. */
internal fun Throwable.rethrowDriveControlFailure() {
    if (this is kotlinx.coroutines.CancellationException || (this is DriveAccessFailure && httpStatus == 401)) throw this
}

/** Partial FileList responses may omit an empty repeated field; malformed/error objects are not emptiness. */
internal fun org.json.JSONObject.driveFileListItems(): org.json.JSONArray {
    fun malformed(): Nothing = throw DriveAccessFailure(DriveAccessFailure.Reason.INVALID_METADATA)
    if (has("error") || (has("kind") && optString("kind") != "drive#fileList")) malformed()
    if (has("incompleteSearch") && opt("incompleteSearch") !is Boolean) malformed()
    if (has("nextPageToken") && opt("nextPageToken") !is String) malformed()
    val files = optJSONArray("files")
    if (files != null) {
        repeat(files.length()) {
            val row = files.optJSONObject(it) ?: malformed()
            val id = row.opt("id")
            if (id !is String || !Regex("[A-Za-z0-9_-]+").matches(id)) malformed()
        }
        return files
    }
    if ((has("files") && !isNull("files")) || keys().asSequence().any {
        it !in setOf("files", "kind", "nextPageToken", "incompleteSearch")
    }) malformed()
    return org.json.JSONArray()
}
