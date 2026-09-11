package com.learnsyncai.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Ouvre une URL (http/https ou schéma système) dans l'application externalisee.
 *  Gère le FLAG_ACTIVITY_NEW_TASK pour eviter l'exception
 *  'startActivity() requires FLAG_ACTIVITY_NEW_TASK' quand on est appele
 *  depuis un Context non-Activity (ViewModel, AlertDialog, etc.). */
fun Context.startAppUrl(url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    } catch (_: Exception) {
        // Silently fail : aucune application ne peut ouvrir ce lien.
    }
}
