package com.learnsyncai.data.sync

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class AuthManager {

    private val auth: FirebaseAuth?
        get() = FirebaseHelper.getAuth()

    fun getCurrentUser(): FirebaseUser? = auth?.currentUser

    suspend fun signInWithGoogle(context: Context, webClientId: String = ""): Result<FirebaseUser> {
        val currentAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Auth n'est pas initialisé.")
        )

        return try {
            val credentialManager = CredentialManager.create(context)

            val googleIdOptionBuilder = GetGoogleIdOption.Builder()
                .setAutoSelectEnabled(false)

            if (webClientId.isNotBlank()) {
                googleIdOptionBuilder.setServerClientId(webClientId)
            } else {
                // Fallback default client id if available in resources
                val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                if (resId != 0) {
                    googleIdOptionBuilder.setServerClientId(context.getString(resId))
                }
            }

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOptionBuilder.build())
                .build()

            val result = credentialManager.getCredential(context = context, request = request)
            val credential = result.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = currentAuth.signInWithCredential(authCredential).await()
                val user = authResult.user ?: return Result.failure(IllegalStateException("Utilisateur introuvable après connexion."))
                Result.success(user)
            } else {
                Result.failure(IllegalStateException("Type d'identifiant non pris en charge."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        auth?.signOut()
    }
}
