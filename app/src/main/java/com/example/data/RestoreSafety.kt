package com.example.data

enum class FullRestorePhase {
    DOWNLOAD, VALIDATION, CORE_IMPORT, CORE_VERIFY, SUPPLEMENTAL_IMPORT, SUPPLEMENTAL_VERIFY, COMPLETE, FAILED
}

data class FullRestoreOutcome(
    val coreReport: DriveRestoreReport,
    val supplementalAttempted: Boolean,
    val supplementalResult: SupplementalDriveBackup.Result? = null,
    val isSuccess: Boolean,
    val phase: FullRestorePhase,
    val message: String
)

/** Enforces the safety boundary between primary and supplemental restore. */
object FullRestoreCoordinator {
    suspend fun execute(
        coreRestore: suspend () -> DriveRestoreReport,
        supplementalRestore: suspend () -> SupplementalDriveBackup.Result,
        onPhase: (FullRestorePhase) -> Unit = {}
    ): FullRestoreOutcome {
        onPhase(FullRestorePhase.CORE_IMPORT)
        val core = coreRestore()
        onPhase(FullRestorePhase.CORE_VERIFY)
        if (!core.isSuccess) {
            onPhase(FullRestorePhase.FAILED)
            return FullRestoreOutcome(
                coreReport = core, supplementalAttempted = false, isSuccess = false,
                phase = FullRestorePhase.FAILED,
                message = "Primäre Wiederherstellung fehlgeschlagen. Zusatzdaten wurden unverändert gelassen."
            )
        }
        onPhase(FullRestorePhase.SUPPLEMENTAL_IMPORT)
        val supplemental = try {
            supplementalRestore()
        } catch (e: Exception) {
            SupplementalDriveBackup.Result(false, e.message ?: "Zusatzdaten-Wiederherstellung fehlgeschlagen")
        }
        onPhase(FullRestorePhase.SUPPLEMENTAL_VERIFY)
        if (!supplemental.success) {
            onPhase(FullRestorePhase.FAILED)
            return FullRestoreOutcome(
                coreReport = core, supplementalAttempted = true, supplementalResult = supplemental,
                isSuccess = false, phase = FullRestorePhase.FAILED,
                message = "Belege und Stammdaten wurden wiederhergestellt, Zusatzdaten jedoch nicht vollständig: ${supplemental.message}"
            )
        }
        onPhase(FullRestorePhase.COMPLETE)
        return FullRestoreOutcome(
            coreReport = core, supplementalAttempted = true, supplementalResult = supplemental,
            isSuccess = true, phase = FullRestorePhase.COMPLETE,
            message = "Wiederherstellung einschließlich Zusatzdaten erfolgreich."
        )
    }
}
