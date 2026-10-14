package com.example.ui

/** Reading damage never grants permission to replace the original preference value. */
internal data class StoredListRead<T>(
    val entries: List<T>,
    val hasStoredData: Boolean,
    val damaged: Boolean = false
) {
    val error: String? get() = if (damaged) ERROR else null
    companion object {
        const val ERROR = "Gespeicherte Daten konnten nicht vollständig gelesen werden. Bitte prüfen. Die Originaldaten bleiben erhalten; Änderungen sind gesperrt."
    }
}
