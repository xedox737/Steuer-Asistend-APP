package com.example.ui

internal object PropertyWizardBackPolicy {
    enum class Action { PREVIOUS, CONFIRM_DISCARD, EXIT }

    fun action(step: Int, hasUnsavedDraft: Boolean): Action = when {
        step > 0 -> Action.PREVIOUS
        hasUnsavedDraft -> Action.CONFIRM_DISCARD
        else -> Action.EXIT
    }
}
