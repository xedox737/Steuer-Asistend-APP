package com.example.ui

internal val PRIMARY_NAVIGATION_SCREENS = listOf(
    AppScreen.DASHBOARD,
    AppScreen.RECEIPTS_LIST,
    AppScreen.ADD_RECEIPT,
    AppScreen.PROPERTIES,
    AppScreen.MORE
)

/** A durable UI reset intent: StateFlow must emit even for an active-tab reselect. */
data class PrimaryNavigationReset(
    val generation: Long = 0,
    val destination: AppScreen = AppScreen.DASHBOARD
)

internal fun primaryNavigationDestination(screen: AppScreen): AppScreen = when (screen) {
    AppScreen.DASHBOARD, AppScreen.RECEIPTS_LIST, AppScreen.ADD_RECEIPT,
    AppScreen.PROPERTIES, AppScreen.MORE -> screen
    AppScreen.RECEIPT_DETAIL -> AppScreen.RECEIPTS_LIST
    AppScreen.BANK, AppScreen.DATEV_EXPORT, AppScreen.LOGBOOK, AppScreen.LEDGER,
    AppScreen.RENT_OVERVIEW, AppScreen.TAX_CALCULATOR, AppScreen.DOCUMENTS -> AppScreen.MORE
}
