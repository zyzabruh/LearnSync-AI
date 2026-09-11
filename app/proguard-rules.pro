# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- LearnSync-AI : règles requises par isMinifyEnabled=true ---
# Room (entités/DAO accédés par réflexion).
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.DatabaseView class *
-dontwarn androidx.room.**
# MediaPipe LLM Inference (moteur natif + réflexion).
-keep class com.google.mediapipe.tasks.genai.** { *; }
-dontwarn com.google.mediapipe.**
# Firebase / Play Services (désérialisation Firestore, Auth).
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
# PdfBox-Android (chargé par réflexion).
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.**
# OkHttp / Credentials.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class androidx.credentials.** { *; }
