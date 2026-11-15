package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.screens.AurixApp
import com.example.viewmodel.AurixViewModel

class MainActivity : ComponentActivity() {
  private val viewModel: AurixViewModel by viewModels()

  override fun attachBaseContext(newBase: Context) {
    val lang = LocaleHelper.getPersistedLanguage(newBase)
    super.attachBaseContext(LocaleHelper.wrapBaseContext(newBase, lang))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      AurixApp(viewModel = viewModel)
    }
  }
}
