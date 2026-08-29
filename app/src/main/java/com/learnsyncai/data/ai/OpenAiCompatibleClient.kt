package com.learnsyncai.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAiCompatibleClient(
    private val client: OkHttpClient = createDefaultOkHttpClient()
) {

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun createDefaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                .build()
        }
    }

    /**
     * Envoie une requête de complétion de chat au format standard OpenAI /chat/completions.
     */
    suspend fun generateChatCompletion(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        prompt: String,
        systemPrompt: String? = null,
        temperature: Double = 0.3
    ): String = withContext(Dispatchers.IO) {
        val cleanBaseUrl = baseUrl.trim().trimEnd('/')
        if (cleanBaseUrl.isBlank()) {
            throw IllegalArgumentException("L'URL de base du fournisseur IA est vide.")
        }
        if (modelName.isBlank()) {
            throw IllegalArgumentException("Le nom du modèle IA est vide.")
        }

        val endpoint = if (cleanBaseUrl.endsWith("/chat/completions")) {
            cleanBaseUrl
        } else {
            "$cleanBaseUrl/chat/completions"
        }

        val messagesArray = JSONArray()
        if (!systemPrompt.isNullOrBlank()) {
            val systemMsg = JSONObject()
                .put("role", "system")
                .put("content", systemPrompt)
            messagesArray.put(systemMsg)
        }

        val userMsg = JSONObject()
            .put("role", "user")
            .put("content", prompt)
        messagesArray.put(userMsg)

        val requestPayload = JSONObject().apply {
            put("model", modelName.trim())
            put("messages", messagesArray)
            put("temperature", temperature)
        }

        val requestBody = requestPayload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://learnsync.ai")
            .addHeader("X-Title", "LearnSync AI")

        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
        }

        val response = try {
            android.util.Log.d("LearnSyncAI", "requête commencée: model=$modelName, promptLength=${prompt.length}")
            client.newCall(requestBuilder.build()).execute()
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Échec de connexion au service IA : ${e.localizedMessage}", e)
        }

        response.use { resp ->
            val responseBodyString = resp.body?.string() ?: ""
            android.util.Log.d("LearnSyncAI", "réponse reçue: statusCode=${resp.code}, length=${responseBodyString.length}")
            if (!resp.isSuccessful) {
                val errorMessage = extractErrorMessage(resp.code, responseBodyString)
                throw IOException(errorMessage)
            }

            val extractedContent = extractContentFromResponse(responseBodyString)
            android.util.Log.d("LearnSyncAI", "longueur réponse: ${extractedContent.length}")
            extractedContent
        }
    }

    /**
     * Teste la connectivité et la validité de la configuration du modèle.
     */
    suspend fun testConnection(
        baseUrl: String,
        apiKey: String,
        modelName: String
    ): Result<String> = runCatching {
        val testResponse = generateChatCompletion(
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelName = modelName,
            prompt = "Réponds uniquement par le mot 'OK' en majuscules pour tester la connexion.",
            temperature = 0.0
        )
        if (testResponse.isNotBlank()) {
            "Connexion réussie avec $modelName !"
        } else {
            throw IllegalStateException("Réponse vide reçue du modèle.")
        }
    }

    private fun extractContentFromResponse(responseBody: String): String {
        return try {
            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                val errorMsg = json.optJSONObject("error")?.optString("message")
                throw IllegalStateException(errorMsg ?: "Aucun choix retourné dans la réponse IA.")
            }

            val firstChoice = choices.getJSONObject(0)
            val messageObj = firstChoice.optJSONObject("message")
            val content = messageObj?.optString("content")

            if (content.isNullOrBlank()) {
                // Fallback for some reasoning / raw text formats
                val text = firstChoice.optString("text", "")
                if (text.isNotBlank()) text else throw IllegalStateException("Contenu vide dans la réponse IA.")
            } else {
                content
            }
        } catch (e: Exception) {
            if (e is IllegalStateException) throw e
            throw IllegalStateException("Impossible de décoder la réponse JSON de l'IA : ${e.message}", e)
        }
    }

    private fun extractErrorMessage(statusCode: Int, responseBody: String): String {
        return try {
            val json = JSONObject(responseBody)
            val errorObj = json.optJSONObject("error")
            val message = errorObj?.optString("message")
            if (!message.isNullOrBlank()) {
                "Erreur $statusCode : $message"
            } else {
                "Erreur HTTP $statusCode : $responseBody"
            }
        } catch (_: Exception) {
            "Erreur HTTP $statusCode"
        }
    }
}
