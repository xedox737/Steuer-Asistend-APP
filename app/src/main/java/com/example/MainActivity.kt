package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppScreen
import com.example.ui.ReceiptAppUi
import com.example.ui.ReceiptViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val viewModel: ReceiptViewModel = viewModel()
        val currentScreen by viewModel.currentScreen.collectAsState()
        val backStack = remember { mutableStateListOf<AppScreen>() }
        var lastObservedScreen by remember { mutableStateOf(currentScreen) }
        var handlingBackNavigation by remember { mutableStateOf(false) }

        LaunchedEffect(currentScreen) {
          if (currentScreen != lastObservedScreen) {
            if (handlingBackNavigation) {
              handlingBackNavigation = false
            } else {
              if (backStack.lastOrNull() != lastObservedScreen) {
                backStack.add(lastObservedScreen)
              }
              if (backStack.size > 20) {
                backStack.removeAt(0)
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
