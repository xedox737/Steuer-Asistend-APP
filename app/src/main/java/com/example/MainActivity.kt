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
import com.example.data.OfflineRestoreJournal
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
    val restorePending = try { OfflineRestoreJournal(applicationContext).load() != null }
      catch (_: Exception) { true }
    setContent {
      MyApplicationTheme {
        if (restorePending) {
          Surface(Modifier.fillMaxSize().testTag("offline_restore_startup_blocked")) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text("Wiederherstellung noch nicht abgeschlossen", style = MaterialTheme.typography.titleLarge)
              Text("Der aktuelle Bestand bleibt geschützt. Die Wiederherstellung muss abgeschlossen werden, bevor Daten angezeigt werden können.")
            }
          }
        } else {
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
