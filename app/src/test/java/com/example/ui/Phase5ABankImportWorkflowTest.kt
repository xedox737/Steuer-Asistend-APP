package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Phase5ABankImportWorkflowTest {
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var vm: ReceiptViewModel
    private val store = ViewModelStore()
    private val instance = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
    private var previous: AppDatabase? = null

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        PersistentPreferenceInventory.stores.forEach { app.getSharedPreferences(it.name, 0).edit().clear().commit() }
        app.getSharedPreferences("google_drive_prefs", 0).edit().putBoolean("user_disconnected", true).commit()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        previous = instance.get(null) as AppDatabase?
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        instance.set(null, db)
        vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[ReceiptViewModel::class.java]
    }
    @After fun tearDown() {
        store.clear()
        instance.set(null, previous)
        db.close()
        PersistentPreferenceInventory.stores.forEach { app.getSharedPreferences(it.name, 0).edit().clear().commit() }
        Dispatchers.resetMain()
    }

    @Test fun noIdentifierRequiresExplicitChoiceDoubleConfirmationCreatesOnlyOneAccountAndRenamedFileDeduplicates() = runTest {
        val text = "Buchungstag;Betrag;Verwendungszweck;Referenz\n" +
            (1..60).joinToString("\n") { "08.10.2026;${it},00;Miete $it;REF-$it" } + "\n30.02.2026;NaN;Fehler;INVALID"
        val source = File(app.cacheDir, "TEST-KONTO-A.csv").apply { writeText(text) }
        val renamed = File(app.cacheDir, "Kontoauszug-Oktober-neu.csv").apply { writeText(text) }
        try {
            vm.importBankFile(Uri.fromFile(source))
            vm.pendingBankAccountImports.first { it.isNotEmpty() }
            assertTrue(db.bankDao().getAllAccounts().isEmpty())
            assertTrue(db.bankDao().getAllTransactions().isEmpty())
            vm.confirmBankAccountImport(newAccountName = "Hauskonto")
            vm.confirmBankAccountImport(newAccountName = "Doppeltes Konto")
            val status = vm.bankImportStatus.first { it?.startsWith("60 neue Buchungen") == true }!!
            assertTrue(status.contains("Zeile 62"))
            assertTrue(status.contains("30.02.2026"))
            assertTrue(status.contains("NaN"))
            val account = db.bankDao().getAllAccounts().single()
            assertEquals("Hauskonto", account.displayName)
            val ids = db.bankDao().getAllTransactions().map { it.transactionId }.toSet()
            assertEquals(60, ids.size)
            vm.importBankFile(Uri.fromFile(renamed))
            vm.pendingBankAccountImports.first { it.isNotEmpty() }
            vm.confirmBankAccountImport(account.accountId)
            vm.bankImportStatus.first { it?.startsWith("0 neue Buchungen • 60 Dubletten") == true }
            assertEquals(1, db.bankDao().getAllAccounts().size)
            assertEquals(ids, db.bankDao().getAllTransactions().map { it.transactionId }.toSet())
            vm.importBankFile(Uri.fromFile(source))
            vm.pendingBankAccountImports.first { it.isNotEmpty() }
            vm.cancelBankAccountImport()
            assertTrue(vm.pendingBankAccountImports.value.isEmpty())
            assertEquals(ids, db.bankDao().getAllTransactions().map { it.transactionId }.toSet())
        } finally { source.delete(); renamed.delete() }
    }
}
