package com.example.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.DatevProfile
import com.example.util.DatevProfileService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RestoredPreferenceStateTest {
    @Test fun liveViewModelReadsRestoredSelectionsProfileDraftsAndRulesWithoutRestart() = runTest {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val drive = application.getSharedPreferences("google_drive_prefs", Context.MODE_PRIVATE)
        drive.edit().putBoolean("user_disconnected", true).putBoolean("auto_backup", true).commit()
        val vm = ReceiptViewModel(application)
        val profile = DatevProfile.createDefaultSkr04().copy(beraterNummer = "2222222", lastModified = 123L)
        DatevProfileService.saveActiveProfile(application, profile)
        drive.edit().putBoolean("auto_backup", false).putString("selected_property_id", "restored-property").commit()
        application.getSharedPreferences("logbook_drafts", Context.MODE_PRIVATE).edit()
            .putString("restored-draft", "{\"purpose\":\"Besichtigung\"}").commit()
        application.getSharedPreferences("ki_learned_rules_prefs", Context.MODE_PRIVATE).edit()
            .putString("rule_restored", "Testfirma|||Renovierung|||Material|||4801|||WE 1|||2").commit()
        vm.refreshPreferencesAfterRestore()
        assertEquals(profile, vm.activeDatevProfile.value)
        assertFalse(vm.autoDriveBackup.value)
        assertEquals("restored-property", vm.selectedPropertyId.value)
        assertTrue(vm.logbookDrafts.value.containsKey("restored-draft"))
        assertTrue(vm.learnedRules.value.any { it.aussteller == "Testfirma" && it.count == 2 })
    }
}
