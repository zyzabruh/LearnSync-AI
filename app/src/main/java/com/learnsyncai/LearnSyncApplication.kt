package com.learnsyncai

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.learnsyncai.data.sync.FirebaseHelper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class LearnSyncApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase and App Check with Debug Provider for debug builds or Play Integrity for release
        try {
            FirebaseApp.initializeApp(this)
            val appCheck = FirebaseAppCheck.getInstance()
            if (BuildConfig.DEBUG) {
                appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
            } else {
                appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
            }
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
