package com.learnsyncai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.learnsyncai.ui.navigation.LearnSyncNavigation
import com.learnsyncai.ui.theme.LearnSyncTheme

class MainActivity : ComponentActivity() {
  private var navigationRequest by mutableStateOf<NavigationRequest?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    navigationRequest = navigationRouteFromIntent(intent)?.let { NavigationRequest(it, 0) }
    renderContent()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    navigationRouteFromIntent(intent)?.let { route ->
      navigationRequest = NavigationRequest(route, (navigationRequest?.requestId ?: 0) + 1)
    }
  }

  private fun renderContent() {
    setContent {
      LearnSyncTheme {
        LearnSyncNavigation(
          requestedRoute = navigationRequest?.route,
          requestId = navigationRequest?.requestId ?: 0
        )
      }
    }
  }
}

data class NavigationRequest(
  val route: String,
  val requestId: Int
)

internal fun navigationRouteFromIntent(intent: Intent?): String? =
  navigationRouteFromExtra(intent?.getStringExtra("navigate_to"))

internal fun navigationRouteFromExtra(navigateTo: String?): String? =
  if (navigateTo == "review") "review" else null
