package com.example.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.OfflineBackupCoordinator
import com.example.data.OfflineBackupValidationException
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineBackupBankExportCoordinationTest {
    @Test fun savedBankCsvIsRegisteredThroughPublicationAndPreservesContent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "bank-export-coordination-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val coordinator = OfflineBackupCoordinator.shared
        val observed = mutableListOf<Boolean>()
        var shared: Intent? = null
        val probe = object : ContextWrapper(context) {
            override fun getCacheDir(): File {
                observed += runCatching { coordinator.beginCapture() }.exceptionOrNull() is OfflineBackupValidationException
                return directory
            }
            override fun startActivity(intent: Intent) { shared = intent }
        }
        val capture = coordinator.beginCapture()
        val statement = "Synthetischer Kontoauszug; mit Trennzeichen\nzweite Zeile"
        try {
            exportBankStatementToCsv(probe, statement, null)
            val output = File(directory, "exports/Kontoauszug_Bankabgleich.csv")
            assertTrue(output.isFile)
            assertEquals(bankStatementCsv(statement, null), output.readText(Charsets.UTF_8))
            assertNotNull(shared)
            assertEquals(Intent.ACTION_CHOOSER, shared!!.action)
            assertTrue(observed.isNotEmpty())
            assertTrue(observed.all { it })
            assertThrows(OfflineBackupValidationException::class.java) { coordinator.completeCapture(capture) }
            coordinator.completeCapture(coordinator.beginCapture())
        } finally { directory.deleteRecursively() }
    }
}
