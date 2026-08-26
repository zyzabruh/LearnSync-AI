package com.example.data.sync

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

object FirebaseHelper {

    fun initialize(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
        } catch (_: Throwable) {
            // Gracefully ignore if Firebase config is not present
        }
    }

    fun isAvailable(context: Context? = null): Boolean {
        return try {
            if (context != null) {
                FirebaseApp.getApps(context).isNotEmpty()
            } else {
                FirebaseApp.getInstance()
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun getAuth(): FirebaseAuth? {
        return try {
            FirebaseAuth.getInstance()
        } catch (_: Throwable) {
            null
        }
    }

    fun getFirestore(): FirebaseFirestore? {
        return try {
            FirebaseFirestore.getInstance()
        } catch (_: Throwable) {
            null
        }
    }

    fun getStorage(): FirebaseStorage? {
        return try {
            FirebaseStorage.getInstance()
        } catch (_: Throwable) {
            null
        }
    }
}

