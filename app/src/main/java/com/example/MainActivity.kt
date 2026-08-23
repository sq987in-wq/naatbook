package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import com.example.ui.NaatApp
import com.example.viewmodel.NaatViewModel

class MainActivity : ComponentActivity() {

  private lateinit var viewModel: NaatViewModel

  override fun onCreate(savedInstanceState: Bundle?) {
    // Handle the branded splash screen transition (must be called before super.onCreate).
    installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    viewModel = ViewModelProvider(this)[NaatViewModel::class.java]

    setContent {
      NaatApp(viewModel = viewModel)
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
