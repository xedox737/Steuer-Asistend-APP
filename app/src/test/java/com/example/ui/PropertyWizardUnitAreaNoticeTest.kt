package com.example.ui

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyWizardUnitAreaNoticeTest {
    @Test fun warnsWhenEightUnitsHaveNoAreaDespiteTotal620() {
        val warning = PropertyWizardUnitAreaNotice.message("620", List(8) { "" })
        assertTrue(warning!!.contains("8 Einheitsfläche"))
        assertTrue(warning.contains("620"))
    }

    @Test fun warnsAboutDifferentSumButDoesNotRequireEquality() {
        val warning = PropertyWizardUnitAreaNotice.message("620", listOf("300", "200"))
        assertTrue(warning!!.contains("weicht"))
        assertNull(PropertyWizardUnitAreaNotice.message("620", listOf("300", "320")))
    }
}
