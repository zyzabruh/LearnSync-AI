package com.learnsyncai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.appcheck.FirebaseAppCheck
import com.learnsyncai.ui.navigation.LearnSyncNavigation
import com.learnsyncai.ui.theme.LearnSyncTheme
import com.learnsyncai.ui.viewmodels.LearnSyncViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    if (BuildConfig.DEBUG) {
      try {
        FirebaseAppCheck.getInstance().getAppCheckToken(false)
          .addOnSuccessListener { result ->
            val token = result.token
            if (!token.isNullOrEmpty()) {
              try {
                android.app.AlertDialog.Builder(this)
                  .setTitle("Firebase App Check Debug Token")
                  .setMessage(token)
                  .setPositiveButton("Copier") { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("AppCheck Debug Token", token)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, "Jeton copié dans le presse-papiers", Toast.LENGTH_SHORT).show()
                  }
                  .setNegativeButton("Fermer", null)
                  .show()
              } catch (_: Exception) {}
            }
          }
          .addOnFailureListener { _ -> }
      } catch (_: Throwable) {}
    }

    setContent {
      LearnSyncTheme {
        val viewModel: LearnSyncViewModel = viewModel()
        LearnSyncNavigation(viewModel = viewModel)
      }
    }
  }
}

