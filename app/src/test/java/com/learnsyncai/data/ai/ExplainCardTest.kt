package com.learnsyncai.data.ai

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Non-régression de l'explication IA d'une carte : le prompt embarque la
 * question, la réponse attendue et le contexte ; le résultat est nettoyé.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExplainCardTest {

    private fun repoWithCapturedPrompt(
        onPrompt: (String) -> Unit,
        rawAnswer: String = "Parce que c'est ainsi."
    ): AiRepositoryImpl {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val buffer = okio.Buffer()
                chain.request().body?.writeTo(buffer)
                val payload = JSONObject(buffer.readUtf8())
                val messages = payload.getJSONArray("messages")
                val userContent = StringBuilder()
                for (i in 0 until messages.length()) {
                    val m = messages.getJSONObject(i)
                    if (m.optString("role") == "user") userContent.append(m.optString("content"))
                }
                onPrompt(userContent.toString())
                val body = """{"choices":[{"message":{"content":${JSONObject.quote(rawAnswer)}},"finish_reason":"stop"}]}"""
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return AiRepositoryImpl(
            openAiClient = OpenAiCompatibleClient(mockClient),
            configProvider = { AiConfig(baseUrl = "https://api.example.com/v1", apiKey = "k", modelName = "m") }
        )
    }

    @Test
    fun explainPromptContainsCardContent() = runBlocking {
        var prompt = ""
        val repo = repoWithCapturedPrompt(onPrompt = { prompt = it })
        repo.explainCard(
            question = "Quelle est la capitale de la France ?",
            answer = "Paris",
            sourceExcerpt = "La France a pour capitale Paris depuis Clovis."
        ).getOrThrow()

        assertTrue(prompt.contains("Quelle est la capitale de la France ?"))
        assertTrue(prompt.contains("Paris"))
        assertTrue(prompt.contains("Clovis"))
    }

    @Test
    fun explainResultIsTrimmed() = runBlocking {
        val repo = repoWithCapturedPrompt(onPrompt = {}, rawAnswer = "  Explication nette.  ")
        val result = repo.explainCard(
            question = "Q ?",
            answer = "R."
        ).getOrThrow()

        assertEquals("Explication nette.", result)
    }
}
