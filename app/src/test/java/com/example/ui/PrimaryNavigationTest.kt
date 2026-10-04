package com.example.ui

import org.junit.Assert.*
import org.junit.Test

class PrimaryNavigationTest {
    @Test fun everyRouteHasExactlyOnePrimaryDestination() {
        AppScreen.entries.forEach { assertTrue(primaryNavigationDestination(it) in PRIMARY_NAVIGATION_SCREENS) }
        PRIMARY_NAVIGATION_SCREENS.forEach { assertEquals(it, primaryNavigationDestination(it)) }
        assertEquals(AppScreen.RECEIPTS_LIST, primaryNavigationDestination(AppScreen.RECEIPT_DETAIL))
        listOf(AppScreen.BANK, AppScreen.DATEV_EXPORT, AppScreen.LOGBOOK, AppScreen.LEDGER,
            AppScreen.RENT_OVERVIEW, AppScreen.TAX_CALCULATOR, AppScreen.DOCUMENTS).forEach {
            assertEquals(AppScreen.MORE, primaryNavigationDestination(it))
        }
    }
}
