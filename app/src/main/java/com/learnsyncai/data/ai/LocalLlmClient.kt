package com.learnsyncai.data.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Client IA 100% local : fait tourner un modèle Gemma (.task / .litertlm) sur
 * l'appareil via le moteur MediaPipe LLM Inference (le même SDK que Google AI
 * Edge Gallery). Aucun appel réseau.
 *
 * Le modèle est chargé paresseusement à la première génération et conservé en
 * mémoire tant que le chemin ne change pas (le chargement coûte plusieurs
 * secondes et beaucoup de RAM).
 */
class LocalLlmClient(private val context: Context) {

    private var engine: LlmInference? = null
    private var loadedModelPath: String? = null
    private val mutex = Mutex()

    suspend fun generate(modelPath: String, prompt: String, maxTokens: Int = DEFAULT_MAX_TOKENS): String =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val current = engine
                val effective = if (current != null && loadedModelPath == modelPath) {
                    current
                } else {
                    current?.close()
                    val newEngine = loadEngine(modelPath, maxTokens)
                    engine = newEngine
                    loadedModelPath = modelPath
                    newEngine
                }

                val response = try {
                    effective.generateResponse(prompt)
                } catch (e: Exception) {
                    throw IllegalStateException("Échec de l'inférence locale : ${e.message}", e)
                }
                response ?: throw IllegalStateException("Le modèle local a renvoyé une réponse vide.")
            }
        }

    private fun loadEngine(modelPath: String, maxTokens: Int): LlmInference {
        val file = File(modelPath)
        if (!file.exists()) {
            throw IllegalStateException("Fichier modèle introuvable : $modelPath")
        }
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(maxTokens)
            .build()
        return try {
            LlmInference.createFromOptions(context, options)
        } catch (e: Exception) {
            throw IllegalStateException(
                "Impossible de charger le modèle local. Vérifiez que le fichier est un modèle Gemma compatible (.task / .litertlm) et que l'appareil dispose d'assez de RAM. (${e.message})",
                e
            )
        }
    }

    fun unload() {
        engine?.close()
        engine = null
        loadedModelPath = null
    }

    companion object {
        const val DEFAULT_MAX_TOKENS = 4096
    }
}
