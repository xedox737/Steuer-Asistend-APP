package com.example.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmobilienNavigationTest {
    @Test fun `primary navigation is start receipts scan properties more`() {
        assertEquals(
            listOf(AppScreen.DASHBOARD, AppScreen.RECEIPTS_LIST, AppScreen.ADD_RECEIPT, AppScreen.PROPERTIES, AppScreen.MORE),
            PRIMARY_NAVIGATION_SCREENS
        )
        assertTrue(AppScreen.LOGBOOK !in PRIMARY_NAVIGATION_SCREENS)
        assertTrue(AppScreen.LEDGER !in PRIMARY_NAVIGATION_SCREENS)
        assertTrue(AppScreen.DOCUMENTS !in PRIMARY_NAVIGATION_SCREENS)
    }
}
