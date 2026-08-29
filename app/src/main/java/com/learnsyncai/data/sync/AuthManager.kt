package com.learnsyncai.data.sync

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
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

    private fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    suspend fun signInWithGoogle(context: Context, webClientId: String = ""): Result<FirebaseUser> {
        val currentAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Auth n'est pas initialisé.")
        )

        val activityContext = context.findActivity() ?: context
        Log.d("AuthManager", "signInWithGoogle started. Context: $context, ActivityContext: $activityContext")

        val resolvedClientId = if (webClientId.isNotBlank()) {
            webClientId
        } else {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            val id = if (resId != 0) context.getString(resId) else ""
            Log.d("AuthManager", "Resolved default_web_client_id from resources: $id (resId=$resId)")
            id
        }

        val credentialManager = CredentialManager.create(context)
        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = hashNonce(rawNonce)
        Log.d("AuthManager", "Generated rawNonce: $rawNonce, hashedNonce: $hashedNonce")

        val credential = try {
            // Step 1: Try GetGoogleIdOption with filterByAuthorizedAccounts = true
            Log.d("AuthManager", "Step 1: GetGoogleIdOption (filterByAuthorizedAccounts = true)")
            val googleIdOptionBuilder = GetGoogleIdOption.Builder()
                .setAutoSelectEnabled(false)
                .setFilterByAuthorizedAccounts(true)
                .setNonce(hashedNonce)

            if (resolvedClientId.isNotBlank()) {
                googleIdOptionBuilder.setServerClientId(resolvedClientId)
            }

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOptionBuilder.build())
                .build()

            val result = credentialManager.getCredential(context = activityContext, request = request)
            Log.d("AuthManager", "Step 1 GetGoogleIdOption (authorized=true) succeeded with credential type: ${result.credential.type}")
            result.credential
        } catch (e: NoCredentialException) {
            Log.w("AuthManager", "Step 1 NoCredentialException (authorized accounts): ${e.message}", e)
            // Step 2: Try GetGoogleIdOption with filterByAuthorizedAccounts = false
            try {
                Log.d("AuthManager", "Step 2: GetGoogleIdOption (filterByAuthorizedAccounts = false)")
                val googleIdOptionBuilder2 = GetGoogleIdOption.Builder()
                    .setAutoSelectEnabled(false)
                    .setFilterByAuthorizedAccounts(false)
                    .setNonce(hashedNonce)

                if (resolvedClientId.isNotBlank()) {
                    googleIdOptionBuilder2.setServerClientId(resolvedClientId)
                }

                val request2 = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOptionBuilder2.build())
                    .build()

                val result2 = credentialManager.getCredential(context = activityContext, request = request2)
                Log.d("AuthManager", "Step 2 GetGoogleIdOption (authorized=false) succeeded with credential type: ${result2.credential.type}")
                result2.credential
            } catch (e2: NoCredentialException) {
                Log.w("AuthManager", "Step 2 NoCredentialException (all accounts): ${e2.message}", e2)
                // Step 3: Fallback to explicit GetSignInWithGoogleOption to show account picker
                try {
                    Log.d("AuthManager", "Step 3: GetSignInWithGoogleOption fallback")
                    val signInOptionBuilder = GetSignInWithGoogleOption.Builder(resolvedClientId.ifBlank { "" })
                        .setNonce(hashedNonce)
                        .build()

                    val request3 = GetCredentialRequest.Builder()
                        .addCredentialOption(signInOptionBuilder)
                        .build()

                    val result3 = credentialManager.getCredential(context = activityContext, request = request3)
                    Log.d("AuthManager", "Step 3 GetSignInWithGoogleOption succeeded with credential type: ${result3.credential.type}")
                    result3.credential
                } catch (fallbackEx: GetCredentialCancellationException) {
                    Log.i("AuthManager", "User cancelled GetSignInWithGoogleOption flow", fallbackEx)
                    return Result.failure(Exception("Connexion annulée par l'utilisateur."))
                } catch (fallbackEx: GetCredentialException) {
                    Log.e("AuthManager", "GetCredentialException in GetSignInWithGoogleOption: type=${fallbackEx.type}, message=${fallbackEx.message}", fallbackEx)
                    return Result.failure(Exception("Erreur Credential Manager (${fallbackEx.type}): ${fallbackEx.message ?: "Inconnue"}"))
                } catch (fallbackEx: Exception) {
                    Log.e("AuthManager", "Unexpected exception in GetSignInWithGoogleOption", fallbackEx)
                    return Result.failure(fallbackEx)
                }
            } catch (ex2: GetCredentialCancellationException) {
                Log.i("AuthManager", "User cancelled GetGoogleIdOption (authorized=false) flow", ex2)
                return Result.failure(Exception("Connexion annulée par l'utilisateur."))
            } catch (ex2: GetCredentialException) {
                Log.e("AuthManager", "GetCredentialException in GetGoogleIdOption (authorized=false): type=${ex2.type}, message=${ex2.message}", ex2)
                // Fallback to GetSignInWithGoogleOption on GetCredentialException as well
                try {
                    Log.d("AuthManager", "Step 3 (fallback after ex2): GetSignInWithGoogleOption")
                    val signInOptionBuilder = GetSignInWithGoogleOption.Builder(resolvedClientId.ifBlank { "" })
                        .setNonce(hashedNonce)
                        .build()

                    val request3 = GetCredentialRequest.Builder()
                        .addCredentialOption(signInOptionBuilder)
                        .build()

                    val result3 = credentialManager.getCredential(context = activityContext, request = request3)
                    result3.credential
                } catch (fallbackEx: GetCredentialCancellationException) {
                    return Result.failure(Exception("Connexion annulée par l'utilisateur."))
                } catch (fallbackEx: Exception) {
                    return Result.failure(Exception("Erreur Google Sign-In (${ex2.type}): ${ex2.message}"))
                }
            } catch (ex2: Exception) {
                Log.e("AuthManager", "Unexpected exception in GetGoogleIdOption (authorized=false)", ex2)
                return Result.failure(ex2)
            }
        } catch (ex1: GetCredentialCancellationException) {
            Log.i("AuthManager", "User cancelled GetGoogleIdOption (authorized=true) flow", ex1)
            return Result.failure(Exception("Connexion annulée par l'utilisateur."))
        } catch (ex1: GetCredentialException) {
            Log.e("AuthManager", "GetCredentialException in GetGoogleIdOption (authorized=true): type=${ex1.type}, message=${ex1.message}", ex1)
            // Try Step 2 (authorized = false)
            try {
                Log.d("AuthManager", "Step 2 (fallback after ex1): GetGoogleIdOption (filterByAuthorizedAccounts = false)")
                val googleIdOptionBuilder2 = GetGoogleIdOption.Builder()
                    .setAutoSelectEnabled(false)
                    .setFilterByAuthorizedAccounts(false)
                    .setNonce(hashedNonce)

                if (resolvedClientId.isNotBlank()) {
                    googleIdOptionBuilder2.setServerClientId(resolvedClientId)
                }

                val request2 = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOptionBuilder2.build())
                    .build()

                val result2 = credentialManager.getCredential(context = activityContext, request = request2)
                result2.credential
            } catch (fallbackEx: GetCredentialCancellationException) {
                return Result.failure(Exception("Connexion annulée par l'utilisateur."))
            } catch (fallbackEx: Exception) {
                // Try Step 3 GetSignInWithGoogleOption
                try {
                    Log.d("AuthManager", "Step 3 (fallback after ex1/ex2): GetSignInWithGoogleOption")
                    val signInOptionBuilder = GetSignInWithGoogleOption.Builder(resolvedClientId.ifBlank { "" })
                        .setNonce(hashedNonce)
                        .build()

                    val request3 = GetCredentialRequest.Builder()
                        .addCredentialOption(signInOptionBuilder)
                        .build()

                    val result3 = credentialManager.getCredential(context = activityContext, request = request3)
                    result3.credential
                } catch (finalEx: GetCredentialCancellationException) {
                    return Result.failure(Exception("Connexion annulée par l'utilisateur."))
                } catch (finalEx: Exception) {
                    Log.e("AuthManager", "All credential attempts failed. Original ex1: ${ex1.message}", ex1)
                    return Result.failure(Exception("Erreur Google Sign-In (${ex1.type}): ${ex1.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e("AuthManager", "Unexpected exception during credential retrieval", e)
            return Result.failure(e)
        }

        return try {
            Log.d("AuthManager", "Processing retrieved credential...")
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                Log.d("AuthManager", "GoogleIdTokenCredential successfully created. ID token length: ${idToken.length}")

                val authCredential = GoogleAuthProvider.getCredential(idToken, rawNonce)
                Log.d("AuthManager", "GoogleAuthProvider credential created. Authenticating with Firebase...")

                val authResult = currentAuth.signInWithCredential(authCredential).await()
                val user = authResult.user ?: return Result.failure(IllegalStateException("Utilisateur introuvable après connexion Firebase."))
                Log.d("AuthManager", "Firebase authentication successful! User: ${user.email}")
                Result.success(user)
            } else {
                Log.e("AuthManager", "Unsupported credential type: ${if (credential is CustomCredential) credential.type else credential.javaClass.name}")
                Result.failure(IllegalStateException("Type d'identifiant Google non pris en charge."))
            }
        } catch (e: Exception) {
            Log.e("AuthManager", "Firebase signInWithCredential failed", e)
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
