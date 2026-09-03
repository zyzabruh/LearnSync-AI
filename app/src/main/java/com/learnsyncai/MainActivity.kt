package com.learnsyncai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.learnsyncai.ui.navigation.LearnSyncNavigation
import com.learnsyncai.ui.theme.LearnSyncTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      LearnSyncTheme {
        LearnSyncNavigation()
      }
    }
  }
}
