package com.learnsyncai.data.ai

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpenAiCompatibleClientTest {

    @Test
    fun testSuccessfulChatCompletion() = runBlocking {
        val mockResponseJson = """
            {
              "id": "chatcmpl-123",
              "object": "chat.completion",
              "created": 1677652288,
              "choices": [{
                "index": 0,
                "message": {
                  "role": "assistant",
                  "content": "Hello, world!"
                },
                "finish_reason": "stop"
              }],
              "usage": {
                "prompt_tokens": 9,
                "completion_tokens": 12,
                "total_tokens": 21
              }
            }
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                assertEquals("https://api.openai.com/v1/chat/completions", request.url.toString())
                assertEquals("Bearer test_key", request.header("Authorization"))
                assertEquals("application/json", request.header("Content-Type"))

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(mockResponseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val client = OpenAiCompatibleClient(mockClient)
        val result = client.generateChatCompletion(
            baseUrl = "https://api.openai.com/v1",
            apiKey = "test_key",
            modelName = "gpt-4o-mini",
            prompt = "Say hello"
        )

        assertEquals("Hello, world!", result)
    }

    @Test
    fun testHttpErrorHandlingExtractsMessage() = runBlocking {
        val errorJson = """
            {
              "error": {
                "message": "Invalid API key provided",
                "type": "invalid_request_error"
              }
            }
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body(errorJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val client = OpenAiCompatibleClient(mockClient)
        var caught: IOException? = null
        try {
            client.generateChatCompletion(
                baseUrl = "https://api.openai.com/v1",
                apiKey = "bad_key",
                modelName = "gpt-4o-mini",
                prompt = "Say hello"
            )
        } catch (e: IOException) {
            caught = e
        }

        assertNotNull(caught)
        assertTrue(caught!!.message?.contains("Invalid API key provided") == true)
    }

    @Test
    fun testTestConnectionSuccess() = runBlocking {
        val mockResponseJson = """
            {
              "choices": [{
                "message": {
                  "role": "assistant",
                  "content": "OK"
                }
              }]
            }
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(mockResponseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val client = OpenAiCompatibleClient(mockClient)
        val result = client.testConnection(
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "sk-or-123",
            modelName = "google/gemini-2.0-flash-exp:free"
        )

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.contains("Connexion réussie") == true)
    }

    @Test
    fun testBlankUrlValidation() = runBlocking {
        val client = OpenAiCompatibleClient()
        var caught: IllegalArgumentException? = null
        try {
            client.generateChatCompletion(
                baseUrl = "",
                apiKey = "",
                modelName = "test",
                prompt = "hello"
            )
        } catch (e: IllegalArgumentException) {
            caught = e
        }
        assertNotNull(caught)
    }
}
