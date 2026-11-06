package com.example.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PropertyWizardBackPolicyTest {
    @Test fun systemBackFromLaterStepReturnsToPreviousStep() {
        for (step in 1..4) {
            assertEquals(PropertyWizardBackPolicy.Action.PREVIOUS, PropertyWizardBackPolicy.action(step, true))
        }
    }

    @Test fun backAtFirstStepNeverDropsEditedDraftSilently() {
        assertEquals(PropertyWizardBackPolicy.Action.CONFIRM_DISCARD, PropertyWizardBackPolicy.action(0, true))
        assertEquals(PropertyWizardBackPolicy.Action.EXIT, PropertyWizardBackPolicy.action(0, false))
    }
}
