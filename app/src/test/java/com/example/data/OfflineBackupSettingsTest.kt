package com.example.data

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class OfflineBackupSettingsTest {
    private lateinit var root: File
    private lateinit var context: Context
    private val directory get() = File(root, "offline-backup-state")
    private val file get() = File(directory, "settings.json")
    private val first = Uri.parse("content://synthetic.documents/tree/first")
    private val second = Uri.parse("content://synthetic.documents/tree/second")
    private fun settings() = OfflineBackupSettings(context)
    @Before fun setup() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, "settings-${UUID.randomUUID()}").apply { check(mkdirs()) }
        context = object : ContextWrapper(base) { override fun getNoBackupFilesDir() = root }
    }
    @After fun cleanup() { root.deleteRecursively() }

    @Test fun defaultsAreSafeAndReadDoesNotCreateAConfiguration() {
        assertEquals(OfflineBackupSettings.Config(), settings().load()); assertFalse(directory.exists())
    }
    @Test fun targetAndManualOptInSurviveNewStoreWithoutOverwritingEachOther() {
        settings().setTarget(first); settings().setManualCredentials(true)
        assertEquals(OfflineBackupSettings.Config(first.toString(), true), settings().load())
        settings().setTarget(second)
        assertEquals(OfflineBackupSettings.Config(second.toString(), true), settings().load())
        settings().setManualCredentials(false)
        assertEquals(OfflineBackupSettings.Config(second.toString(), false), settings().load())
        settings().setTarget(null); assertEquals(OfflineBackupSettings.Config(), settings().load())
    }
    @Test fun interruptedAtomicWriteRecoversLastCompleteConfiguration() {
        settings().setTarget(first); settings().setManualCredentials(true)
        val expected = settings().load()
        val output = AtomicFile(file).startWrite()
        output.write("{\"version\":".toByteArray()); output.fd.sync(); output.close()
        assertEquals(expected, settings().load())
    }
    @Test fun corruptSettingsNeverSilentlyResetOrBecomeAWriteTarget() {
        settings().setTarget(first)
        val variants = listOf("{}", "{\"version\":1,\"version\":1,\"targetUri\":null,\"manualCredentials\":false}",
            "{\"version\":1,\"targetUri\":\"file:///unsafe\",\"manualCredentials\":false}",
            "{\"version\":1,\"targetUri\":null,\"manualCredentials\":\"true\"}", "x".repeat(65 * 1024))
        variants.forEach { raw ->
            file.writeText(raw); val before = file.readBytes()
            assertThrows(OfflineBackupValidationException::class.java) { settings().load() }
            assertThrows(OfflineBackupValidationException::class.java) { settings().setManualCredentials(true) }
            assertArrayEquals(before, file.readBytes())
        }
    }
    @Test fun invalidNewUriPreservesAlreadySavedConfiguration() {
        settings().setTarget(first); val before = file.readBytes()
        listOf("file:///unsafe", "content://synthetic.documents/document/one").forEach { raw ->
            assertThrows(OfflineBackupValidationException::class.java) { settings().setTarget(Uri.parse(raw)) }
            assertArrayEquals(before, file.readBytes())
        }
    }
}
