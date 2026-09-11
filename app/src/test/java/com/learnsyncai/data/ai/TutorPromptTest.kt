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
 * Non-régression du prompt tuteur : la question en cours ne doit apparaître
 * qu'une fois (pas de duplication via l'historique) et le contexte doit
 * privilégier les passages pertinents dans la limite du budget.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TutorPromptTest {

    private fun repoWithCapturedPrompt(
        onPrompt: (String) -> Unit,
        answer: String = "Réponse du tuteur."
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
                val body = """{"choices":[{"message":{"content":${JSONObject.quote(answer)}},"finish_reason":"stop"}]}"""
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
    fun tutorPromptContainsCurrentQuestionOnce() = runBlocking {
        var prompt = ""
        val repo = repoWithCapturedPrompt(onPrompt = { prompt = it })
        val question = "Résume le chapitre deux en trois phrases"
        repo.tutorAsk(
            courseTitle = "SVT",
            courseContext = "Contenu du cours sur plusieurs chapitres.",
            history = listOf("user" to "Bonjour", "tutor" to "Bonjour, que veux-tu étudier ?"),
            question = question,
            language = "fr"
        ).getOrThrow()

        assertEquals(1, prompt.split(question).size - 1)
        assertTrue(prompt.contains("Élève : Bonjour"))
        assertTrue(prompt.contains("QUESTION DE L'ÉLÈVE"))
    }

    @Test
    fun tutorContextPrefersRelevantPassage() = runBlocking {
        var prompt = ""
        val repo = repoWithCapturedPrompt(onPrompt = { prompt = it })
        val filler = "Phrase de remplissage sans intérêt pédagogique. ".repeat(1500)
        val context = "Chapitre un. Introduction générale.\n\n$filler\n\n" +
            "Chapitre deux. La photosynthèse produit du glucose dans les chloroplastes."
        repo.tutorAsk(
            courseTitle = "SVT",
            courseContext = context,
            history = emptyList(),
            question = "Qu'est-ce que la photosynthèse ?",
            language = "fr"
        ).getOrThrow()

        assertTrue(prompt.contains("glucose"))
    }

    @Test
    fun tutorContextRespectsBudget() = runBlocking {
        var prompt = ""
        val repo = repoWithCapturedPrompt(onPrompt = { prompt = it })
        repo.tutorAsk(
            courseTitle = "Cours",
            courseContext = "mot ".repeat(30000),
            history = emptyList(),
            question = "De quoi parle ce cours ?",
            language = "fr"
        ).getOrThrow()

        // 20000 caractères de contexte + gabarit du prompt.
        assertTrue(prompt.length < 24000)
    }
}
