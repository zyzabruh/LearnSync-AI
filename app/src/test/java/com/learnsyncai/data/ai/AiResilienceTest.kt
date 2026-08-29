package com.learnsyncai.data.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AiResilienceTest {

    private val repository = AiRepositoryImpl()

    @Test
    fun testIsTransientError() {
        // Transient errors
        assertTrue(repository.isTransientError(IOException("Connection reset")))
        assertTrue(repository.isTransientError(Exception("Error 429: Too Many Requests")))
        assertTrue(repository.isTransientError(Exception("QUOTA exceeded")))
        assertTrue(repository.isTransientError(Exception("RESOURCE_EXHAUSTED")))
        assertTrue(repository.isTransientError(Exception("Service UNAVAILABLE")))
        assertTrue(repository.isTransientError(Exception("Request TIMEOUT")))
        assertTrue(repository.isTransientError(Exception("Deadline exceeded")))

        // Non-transient errors
        assertFalse(repository.isTransientError(IllegalArgumentException("format invalide")))
        assertFalse(repository.isTransientError(NullPointerException("null value")))
    }

    @Test
    fun testExecuteWithRetrySuccessAfterTransient() = runBlocking {
        var attempts = 0
        val result = repository.executeWithRetry(maxAttempts = 3, initialDelayMs = 10L) {
            attempts++
            if (attempts < 3) {
                throw IOException("Temporary failure")
            }
            "Success"
        }

        assertEquals("Success", result)
        assertEquals(3, attempts)
    }

    @Test
    fun testExecuteWithRetryFailsAfterMaxAttempts() = runBlocking {
        var attempts = 0
        var caughtException: Throwable? = null
        try {
            repository.executeWithRetry(maxAttempts = 3, initialDelayMs = 10L) {
                attempts++
                throw IOException("Persistent network error")
            }
        } catch (e: Throwable) {
            caughtException = e
        }

        assertNotNull(caughtException)
        assertTrue(caughtException is IOException)
        assertEquals(3, attempts)
    }

    @Test
    fun testExecuteWithRetryFailsImmediatelyOnNonTransient() = runBlocking {
        var attempts = 0
        var caughtException: Throwable? = null
        try {
            repository.executeWithRetry(maxAttempts = 3, initialDelayMs = 10L) {
                attempts++
                throw IllegalArgumentException("format invalide")
            }
        } catch (e: Throwable) {
            caughtException = e
        }

        assertNotNull(caughtException)
        assertTrue(caughtException is IllegalArgumentException)
        assertEquals(1, attempts) // Should not retry non-transient errors
    }

    @Test
    fun testMapUserFacingException() {
        // Quota family
        val quotaEx = Exception("Error 429 quota reached")
        val mappedQuota = repository.mapUserFacingException(quotaEx)
        assertTrue(mappedQuota is IllegalStateException)
        assertTrue(mappedQuota.message?.contains("Quota d'IA temporairement atteint") == true)

        // Network / Timeout family
        val netEx = IOException("Connection lost")
        val mappedNet = repository.mapUserFacingException(netEx)
        assertTrue(mappedNet is IllegalStateException)
        assertTrue(mappedNet.message?.contains("Problème de connexion avec le service IA") == true)

        // JSON parsing family
        val jsonEx = Exception("Invalid json response")
        val mappedJson = repository.mapUserFacingException(jsonEx)
        assertTrue(mappedJson is IllegalStateException)
        assertTrue(mappedJson.message?.contains("La réponse de l'IA n'a pas pu être structurée") == true)

        // Other family
        val otherEx = IllegalArgumentException("Custom error")
        val mappedOther = repository.mapUserFacingException(otherEx)
        assertEquals(otherEx, mappedOther)
    }
}
