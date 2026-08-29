package com.learnsyncai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.learnsyncai.ui.navigation.LearnSyncNavigation
import com.learnsyncai.ui.theme.LearnSyncTheme
import com.learnsyncai.ui.viewmodels.LearnSyncViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      LearnSyncTheme {
        val viewModel: LearnSyncViewModel = viewModel()
        LearnSyncNavigation(viewModel = viewModel)
      }
    }
  }
}

