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
        private val MAX_TOKENS_FALLBACKS = intArrayOf(131072, 65535, 32768, 8192)

        fun createDefaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .callTimeout(300, TimeUnit.SECONDS)
                .build()
        }

        fun normalizeModelName(rawModel: String, baseUrl: String): String {
            val trimmed = rawModel.trim()
            val isGoogle = baseUrl.contains("googleapis.com") || baseUrl.contains("google")
            return when {
                isGoogle && (trimmed.equals("gemini-2.0-flash", ignoreCase = true) ||
                        trimmed.equals("gemini-1.5-flash", ignoreCase = true) ||
                        trimmed.equals("gemini-1.5-pro", ignoreCase = true) ||
                        trimmed.equals("gemini-2.0-flash-exp", ignoreCase = true) ||
                        trimmed.equals("gemini-pro", ignoreCase = true) ||
                        trimmed.isBlank()) -> "gemini-2.5-flash"
                trimmed.equals("google/gemini-2.0-flash-exp:free", ignoreCase = true) -> "google/gemini-2.5-flash"
                trimmed.isBlank() -> if (isGoogle) "gemini-2.5-flash" else "gemini-2.5-flash"
                else -> trimmed
            }
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
        temperature: Double = 0.2,
        maxTokens: Int = 262144
    ): String = withContext(Dispatchers.IO) {
        val cleanBaseUrl = baseUrl.trim().trimEnd('/')
        if (cleanBaseUrl.isBlank()) {
            throw IllegalArgumentException("L'URL de base du fournisseur IA est vide.")
        }
        val effectiveModel = normalizeModelName(modelName, cleanBaseUrl)

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

        val hasJsonFormat = cleanBaseUrl.contains("googleapis.com") || cleanBaseUrl.contains("openai.com") || cleanBaseUrl.contains("openrouter.ai")

        val fallbacks = (listOf(maxTokens) + MAX_TOKENS_FALLBACKS.toList())
            .distinct()
            .filter { it <= maxTokens }
        var lastError: IOException? = null
        var success: String? = null
        for (attemptMaxTokens in fallbacks) {
            try {
                success = executeChatRequest(
                    endpoint = endpoint,
                    apiKey = apiKey,
                    model = effectiveModel,
                    messagesArray = messagesArray,
                    temperature = temperature,
                    maxTokens = attemptMaxTokens,
                    useJsonFormat = hasJsonFormat
                )
                break
            } catch (e: MaxTokensExceededException) {
                lastError = e
                // Le plafond du modèle est plus bas : on retente avec la valeur suivante.
            }
        }
        success ?: throw lastError ?: IOException("Échec de la requête IA.")
    }

    private class MaxTokensExceededException(message: String) : IOException(message)

    /**
     * Exécute une requête /chat/completions avec gestion du retry sans response_format.
     */
    private suspend fun executeChatRequest(
        endpoint: String,
        apiKey: String,
        model: String,
        messagesArray: JSONArray,
        temperature: Double,
        maxTokens: Int,
        useJsonFormat: Boolean
    ): String = withContext(Dispatchers.IO) {
        val requestPayload = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            if (useJsonFormat) {
                put("response_format", JSONObject().put("type", "json_object"))
            }
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
            client.newCall(requestBuilder.build()).execute()
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Échec de connexion au service IA : ${e.localizedMessage}", e)
        }

        var shouldRetryWithoutJsonFormat = false
        val content = response.use { resp ->
            val responseBodyString = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                if (resp.code == 400 && isMaxTokensError(responseBodyString)) {
                    throw MaxTokensExceededException("max_tokens dépasse le plafond du modèle : $maxTokens")
                } else if (resp.code == 400 && useJsonFormat) {
                    shouldRetryWithoutJsonFormat = true
                    ""
                } else {
                    val errorMessage = extractErrorMessage(resp.code, responseBodyString)
                    throw IOException(errorMessage)
                }
            } else {
                extractContentFromResponse(responseBodyString)
            }
        }

        if (shouldRetryWithoutJsonFormat) {
            executeWithoutResponseFormat(
                endpoint = endpoint,
                apiKey = apiKey,
                model = model,
                messagesArray = messagesArray,
                temperature = temperature,
                maxTokens = maxTokens
            )
        } else {
            content
        }
    }

    private fun isMaxTokensError(responseBody: String): Boolean {
        val lower = responseBody.lowercase()
        return lower.contains("max_tokens") &&
                (lower.contains("exceed") || lower.contains("too large") ||
                        lower.contains("greater than") || lower.contains("maximum") ||
                        lower.contains("out of range") || lower.contains("unsupported_value"))
    }

    private fun executeWithoutResponseFormat(
        endpoint: String,
        apiKey: String,
        model: String,
        messagesArray: JSONArray,
        temperature: Double,
        maxTokens: Int
    ): String {
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
        }
        val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://learnsync.ai")
            .addHeader("X-Title", "LearnSync AI")

        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
        }

        val response = client.newCall(requestBuilder.build()).execute()
        response.use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw IOException(extractErrorMessage(resp.code, body))
            }
            return extractContentFromResponse(body)
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
        val effectiveModel = normalizeModelName(modelName, baseUrl)
        val testResponse = generateChatCompletion(
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelName = effectiveModel,
            prompt = "{\"test\": true}",
            systemPrompt = "Réponds avec {\"status\": \"ok\"} en JSON.",
            temperature = 0.0
        )
        if (testResponse.isNotBlank()) {
            "Connexion réussie avec $effectiveModel !"
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
            val finishReason = firstChoice.optString("finish_reason", "")
            if (finishReason.equals("length", ignoreCase = true)) {
                throw IllegalStateException("réponse tronquée, réessayez avec une quantité plus faible")
            }

            val messageObj = firstChoice.optJSONObject("message")
            val content = messageObj?.optString("content")

            val effectiveContent = if (content.isNullOrBlank()) {
                // Fallback for some reasoning / raw text formats
                val text = firstChoice.optString("text", "")
                if (text.isNotBlank()) text else null
            } else {
                content
            }

            if (effectiveContent.isNullOrBlank()) {
                val truncated = finishReason.equals("length", ignoreCase = true)
                throw IllegalStateException(
                    if (truncated) "Réponse tronquée par la limite de sortie du modèle (contenu vide), réessayez."
                    else "Contenu vide dans la réponse IA."
                )
            }
            return effectiveContent
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
