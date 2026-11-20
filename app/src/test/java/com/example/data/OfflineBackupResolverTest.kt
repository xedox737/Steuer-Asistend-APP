package com.example.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineBackupResolverTest {
    private lateinit var context: Context
    private lateinit var directory: File
    private lateinit var original: File
    private lateinit var destination: File

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        directory = File(context.cacheDir, "resolver-test-${UUID.randomUUID()}").apply { check(mkdirs()) }
        original = File(directory, "original.bin").apply { writeText("original") }
        destination = File(directory, "staged.bin")
    }
    @After fun cleanUp() { directory.deleteRecursively() }

    private fun contentUri(onOpen: () -> ParcelFileDescriptor): Uri {
        val authority = "offline-backup-resolver-test"
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri) = "application/octet-stream"
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Read only")
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = error("Read only")
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = error("Read only")
            override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
                check(mode == "r")
                return onOpen()
            }
        }
        provider.attachInfo(context, ProviderInfo().apply { this.authority = authority })
        ShadowContentResolver.registerProviderInternal(authority, provider)
        return Uri.parse("content://$authority/original")
    }

    @Test fun unknownProviderLengthIsBoundedWhileStreamingAndPartialStagingIsRemoved() = runBlocking {
        val uri = contentUri { ParcelFileDescriptor.open(original, ParcelFileDescriptor.MODE_READ_ONLY) }
        val failure = runCatching { OfflineBackupResolver(context, maximumBytes = 4)
            .copy(uri.toString(), destination, "document-test", null, null, emptyList()) }.exceptionOrNull()
        assertTrue(failure is OfflineBackupValidationException)
        assertTrue(failure!!.message.orEmpty().contains("Grenze"))
        assertFalse(destination.exists())
        assertEquals("original", original.readText())
    }

    @Test fun theProductionSizeLimitCannotBeRaisedByACaller() {
        assertTrue(runCatching { OfflineBackupResolver(context, maximumBytes = 1024L * 1024 * 1024 + 1) }
            .exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun changedProviderBytesDuringVerificationAbortAndRemoveStaging() = runBlocking {
        var opened = false
        val uri = contentUri {
            if (opened) original.writeText("modified")
            opened = true
            ParcelFileDescriptor.open(original, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        val failure = runCatching { OfflineBackupResolver(context)
            .copy(uri.toString(), destination, "document-test", null, null, emptyList()) }.exceptionOrNull()
        assertTrue(failure is OfflineBackupValidationException)
        assertTrue(failure!!.message.orEmpty().contains("während der Sicherung geändert"))
        assertFalse(destination.exists())
        assertEquals("modified", original.readText())
    }

    @Test fun providerCancellationRemainsCancellationAndNeverLeavesACompleteFile() = runBlocking {
        val stopped = CancellationException("Fixture stopped")
        val uri = contentUri { throw stopped }
        val failure = runCatching { OfflineBackupResolver(context)
            .copy(uri.toString(), destination, "document-test", null, null, emptyList()) }.exceptionOrNull()
        assertSame(stopped, failure)
        assertFalse(destination.exists())
        assertEquals("original", original.readText())
    }

    @Test fun anExistingDestinationIsNeverOverwrittenOrDeleted() = runBlocking {
        destination.writeText("existing backup")
        assertTrue(runCatching { OfflineBackupResolver(context)
            .copy(original.absolutePath, destination, "document-test", null, null, emptyList()) }.isFailure)
        assertEquals("existing backup", destination.readText())
        assertEquals("original", original.readText())
    }

    @Test fun recordedPositiveSizeMustMatchTheCopiedBytes() = runBlocking {
        val failure = runCatching { OfflineBackupResolver(context)
            .copy(original.absolutePath, destination, "document-test", null, 99, emptyList()) }.exceptionOrNull()
        assertTrue(failure is OfflineBackupValidationException)
        assertTrue(failure!!.message.orEmpty().contains("gespeicherten Größe"))
        assertFalse(destination.exists())
        assertEquals("original", original.readText())
    }
}
