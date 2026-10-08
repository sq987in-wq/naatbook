package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.ui.NaatApp
import com.example.viewmodel.BackupViewModel
import com.example.viewmodel.NaatViewModel
import com.example.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  private val viewModel: NaatViewModel by viewModels()
  private val settingsViewModel: SettingsViewModel by viewModels()
  private val backupViewModel: BackupViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    // Handle the branded splash screen transition (must be called before super.onCreate).
    installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      NaatApp(
        viewModel = viewModel,
        settingsViewModel = settingsViewModel,
        backupViewModel = backupViewModel
      )
    }
  }

  override fun onStop() {
    super.onStop()
    // Don't release audio on rotation/config changes — only on real backgrounding
    if (!isChangingConfigurations) {
      viewModel.onAppBackgrounded()
    }
  }
}
