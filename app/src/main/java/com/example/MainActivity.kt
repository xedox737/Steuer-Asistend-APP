package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.OfflineBackupScheduleReceiver
import com.example.data.OfflineBackupCoordinator
import com.example.data.OfflineRestoreRecovery
import kotlinx.coroutines.CancellationException
import com.example.ui.AppScreen
import com.example.ui.ReceiptAppUi
import com.example.ui.ReceiptViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    // Read before constructing ReceiptViewModel: it opens Room and starts sync work.
    // A malformed or unreadable marker is treated as pending, never as absent.
    val initialRestorePending = OfflineBackupCoordinator.shared.observeRestoreJournal(applicationContext)
    setContent {
      MyApplicationTheme {
        var restorePending by remember { mutableStateOf(initialRestorePending) }
        val restoreGuardPending by OfflineBackupCoordinator.shared.restorePending.collectAsStateWithLifecycle()
        var recoveryFailed by remember { mutableStateOf(false) }
        var recoveryAttempt by remember { mutableStateOf(0) }
        if (restorePending || restoreGuardPending) {
          LaunchedEffect(recoveryAttempt) {
            recoveryFailed = false
            try {
              OfflineRestoreRecovery.recoverOnStartup(applicationContext)
              restorePending = false
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { recoveryFailed = true }
          }
          Surface(Modifier.fillMaxSize().testTag("offline_restore_startup_blocked")) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text("Wiederherstellung noch nicht abgeschlossen", style = MaterialTheme.typography.titleLarge)
              if (recoveryFailed) {
                Text("Der Bestand bleibt geschützt. Die Wiederherstellung konnte nicht abgeschlossen werden. Bitte versuche es erneut.")
                Button(onClick = { recoveryAttempt++ }, modifier = Modifier.testTag("offline_restore_retry")) { Text("Erneut versuchen") }
              } else {
                Text("Der aktuelle Bestand bleibt geschützt. Die Wiederherstellung wird geprüft und abgeschlossen.")
              }
            }
          }
        } else {
        LaunchedEffect(Unit) { OfflineBackupScheduleReceiver.reconcile(applicationContext) }
        val viewModel: ReceiptViewModel = viewModel()
        val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
        val primaryNavigationReset by viewModel.primaryNavigationReset.collectAsStateWithLifecycle()
        var lastPrimaryGeneration by remember { mutableStateOf(primaryNavigationReset.generation) }
        val backStack = remember { mutableStateListOf<AppScreen>() }
        var lastObservedScreen by remember { mutableStateOf(currentScreen) }
        var handlingBackNavigation by remember { mutableStateOf(false) }

        LaunchedEffect(currentScreen, primaryNavigationReset) {
          if (primaryNavigationReset.generation != lastPrimaryGeneration) {
            // The two flows may be observed in either order. Wait for the requested
            // root before accepting the reset; never record its departed child.
            if (currentScreen != primaryNavigationReset.destination) return@LaunchedEffect
            backStack.clear()
            handlingBackNavigation = false
            lastObservedScreen = currentScreen
            lastPrimaryGeneration = primaryNavigationReset.generation
            return@LaunchedEffect
          }
          if (currentScreen != lastObservedScreen) {
            if (handlingBackNavigation) {
              handlingBackNavigation = false
            } else {
              val returnIndex = backStack.indexOfLast { it == currentScreen }
              if (returnIndex >= 0) {
                // Local back arrows/handlers already returned to an earlier destination.
                // Remove that destination and its closed children instead of recording them again.
                while (backStack.size > returnIndex) backStack.removeAt(backStack.lastIndex)
              } else {
                if (backStack.lastOrNull() != lastObservedScreen) {
                  backStack.add(lastObservedScreen)
                }
                if (backStack.size > 20) {
                  backStack.removeAt(0)
                }
              }
            }
            lastObservedScreen = currentScreen
          }
        }

        BackHandler(
          enabled = currentScreen != AppScreen.DASHBOARD || backStack.isNotEmpty()
        ) {
          val previousScreen = backStack.lastOrNull() ?: AppScreen.DASHBOARD
          if (backStack.isNotEmpty()) {
            backStack.removeAt(backStack.lastIndex)
          }
          handlingBackNavigation = true
          viewModel.setScreen(previousScreen)
        }

        ReceiptAppUi(viewModel = viewModel)
        }
      }
    }
  }
}
