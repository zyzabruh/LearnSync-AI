package com.learnsyncai.data.sync

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

class AuthManager {

    private val auth: FirebaseAuth?
        get() = FirebaseHelper.getAuth()

    fun getCurrentUser(): FirebaseUser? = auth?.currentUser

    suspend fun signInWithGoogle(context: Context, webClientId: String = ""): Result<FirebaseUser> {
        val currentAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Auth n'est pas initialisé.")
        )

        val resolvedClientId = if (webClientId.isNotBlank()) {
            webClientId
        } else {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) context.getString(resId) else ""
        }

        val credentialManager = CredentialManager.create(context)
        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = hashNonce(rawNonce)

        val credential = try {
            // Step 1: Try GetGoogleIdOption for existing authorized accounts / auto-select
            val googleIdOptionBuilder = GetGoogleIdOption.Builder()
                .setAutoSelectEnabled(false)
                .setNonce(hashedNonce)

            if (resolvedClientId.isNotBlank()) {
                googleIdOptionBuilder.setServerClientId(resolvedClientId)
            }

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOptionBuilder.build())
                .build()

            val result = credentialManager.getCredential(context = context, request = request)
            result.credential
        } catch (e: NoCredentialException) {
            // Step 2: Fallback to explicit GetSignInWithGoogleOption to show account picker
            try {
                val signInOptionBuilder = GetSignInWithGoogleOption.Builder(resolvedClientId.ifBlank { "" })
                    .setNonce(hashedNonce)

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOptionBuilder.build())
                    .build()

                val result = credentialManager.getCredential(context = context, request = request)
                result.credential
            } catch (fallbackEx: GetCredentialCancellationException) {
                return Result.failure(Exception("Connexion annulée par l'utilisateur."))
            } catch (fallbackEx: Exception) {
                return Result.failure(fallbackEx)
            }
        } catch (e: GetCredentialCancellationException) {
            return Result.failure(Exception("Connexion annulée par l'utilisateur."))
        } catch (e: GetCredentialException) {
            // If another GetCredentialException occurs, try fallback to GetSignInWithGoogleOption as well
            try {
                val signInOptionBuilder = GetSignInWithGoogleOption.Builder(resolvedClientId.ifBlank { "" })
                    .setNonce(hashedNonce)

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOptionBuilder.build())
                    .build()

                val result = credentialManager.getCredential(context = context, request = request)
                result.credential
            } catch (fallbackEx: GetCredentialCancellationException) {
                return Result.failure(Exception("Connexion annulée par l'utilisateur."))
            } catch (fallbackEx: Exception) {
                return Result.failure(e)
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return try {
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, rawNonce)
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

    private fun hashNonce(nonce: String): String {
        val bytes = nonce.toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    fun signOut() {
        auth?.signOut()
    }
}
