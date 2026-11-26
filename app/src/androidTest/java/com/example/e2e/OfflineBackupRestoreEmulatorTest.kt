package com.example.e2e

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.AppDatabase
import com.example.data.OfflineBackupArchiveEngine
import com.example.data.OfflineBackupCoordinator
import com.example.data.OfflineBackupHistory
import com.example.data.OfflineBackupKeyManager
import com.example.data.OfflineBackupRestoreInspector
import com.example.data.OfflineRestoreCoordinator
import com.example.data.PropertyMetadata
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineBackupRestoreEmulatorTest {
    @get:Rule val environment = EmulatorEnvironmentRule { 1f }

    @Test
    fun encryptedArchiveRestoresStablePropertyWithoutSourceKeyState() = runBlocking(Dispatchers.IO) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir.canonicalFile, "restore-emulator-${UUID.randomUUID()}").apply { check(mkdirs()) }
        fun device(name: String) = object : ContextWrapper(base) {
            private val privateState = File(root, name).apply { check(mkdirs()) }
            override fun getNoBackupFilesDir(): File = privateState
        }
        val source = device("source")
        val target = device("target")
        val database = AppDatabase.getDatabase(base, this)
        database.openHelper.writableDatabase
        OfflineBackupCoordinator.shared.awaitInitialization()
        val properties = database.propertyDao()
        val property = PropertyMetadata(id = properties.nextPropertyId(), propertyId = "portable-property", name = "Testobjekt")
        val password = "Emulator-only-test-password"
        try {
            properties.insertPropertyMetadata(property)
            OfflineBackupKeyManager(source).initialize(password.toCharArray(), password.toCharArray())
            OfflineBackupArchiveEngine(source, database).prepare().use { archive ->
                properties.deletePropertyByPropertyId(property.propertyId)
                assertNull(properties.getPropertyByPropertyId(property.propertyId))
                OfflineBackupRestoreInspector.inspect(target, password.toCharArray(), archive.file::inputStream).use { prepared ->
                    prepared.acquireLease().use {
                        OfflineRestoreCoordinator.restore(target, database, prepared, false, {})
                    }
                }
                assertEquals(property, properties.getPropertyByPropertyId(property.propertyId))
                // Opening a screen subscribes through Room's invalidation tracker after restore.
                val observed = withTimeout(10_000) {
                    properties.getAllPropertiesFlow().first { rows -> rows.any { it.propertyId == property.propertyId } }
                }
                assertEquals(property, observed.single { it.propertyId == property.propertyId })
                assertEquals(OfflineBackupHistory.Outcome.RESTORED, OfflineBackupHistory(target).load().lastRestore?.outcome)
            }
        } finally {
            if (!OfflineBackupCoordinator.shared.restorePending.value) root.deleteRecursively()
        }
        Unit
    }
}
