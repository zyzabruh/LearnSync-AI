package com.learnsyncai

import android.app.Application
import com.learnsyncai.data.sync.FirebaseHelper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class LearnSyncApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
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
