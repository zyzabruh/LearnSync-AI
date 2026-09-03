package com.learnsyncai

import android.app.Application
import com.google.firebase.FirebaseApp
import com.learnsyncai.data.sync.FirebaseHelper
import com.learnsyncai.di.AppContainer
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class LearnSyncApplication : Application() {

    /** Conteneur d'injection : unique point de câblage des dépendances. */
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase safely
        try {
            FirebaseApp.initializeApp(this)
        } catch (_: Throwable) {
            // Ignored if Firebase is not configured or fails to initialize
        }

        // Safely initialize Firebase if configured
        FirebaseHelper.initialize(this)

        // Preload PDFBox resource loader safely
        try {
            PDFBoxResourceLoader.init(this)
        } catch (_: Throwable) {
            // Ignored if already loaded or fallback needed
        }
    }
}
