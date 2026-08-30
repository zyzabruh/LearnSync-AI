package com.learnsyncai.data.ai

import com.learnsyncai.domain.model.GeneratedFlashcard
import com.learnsyncai.domain.model.GeneratedQuizQuestion
import com.learnsyncai.domain.model.StudyGenerationResult
import com.learnsyncai.domain.repository.AiRepository
import com.learnsyncai.domain.usecase.QuizValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class AiConfig(
    val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta/openai",
    val apiKey: String = "",
    val modelName: String = "gemini-2.5-flash"
)

class AiRepositoryImpl(
    private val openAiClient: OpenAiCompatibleClient = OpenAiCompatibleClient(),
    private val configProvider: (suspend () -> AiConfig)? = null
) : AiRepository {

    override suspend fun generateStudyMaterial(
        courseTitle: String,
        courseText: String,
        onProgress: (String) -> Unit
    ): Result<StudyGenerationResult> = withContext(Dispatchers.IO) {
        try {
            val trimmedText = courseText.trim()
            if (trimmedText.isBlank() || trimmedText.length < 30) {
                return@withContext Result.failure(
                    IllegalArgumentException("Le document contient trop peu de texte pour générer du matériel pédagogique.")
                )
            }

            val config = configProvider?.invoke() ?: AiConfig()

            // Optimized chunk size for fast single-call analysis (up to ~35k chars / 7k words)
            val chunkSize = 35000
            val chunks = splitIntoChunks(trimmedText, chunkSize)

            if (chunks.size == 1) {
                onProgress("Génération du résumé, flashcards et QCM avec l'IA...")
                val result = executeWithRetry(maxAttempts = 2) {
                    generateForChunk(config, courseTitle, chunks[0], isFullDoc = true)
                }
                val validQuiz = QuizValidator.filterValidQuestions(result.quizQuestions)
                val finalResult = result.copy(quizQuestions = validQuiz)
                return@withContext Result.success(finalResult)
            } else {
                // Multi-chunk processing in parallel with rate-limiting Semaphore
                onProgress("Analyse accélérée de ${chunks.size} sections du document...")
                val semaphore = Semaphore(2)
                val chunkResults = coroutineScope {
                    chunks.mapIndexed { index, chunk ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                executeWithRetry(maxAttempts = 2) {
                                    generateForChunk(
                                        config = config,
                                        courseTitle = "$courseTitle (Partie ${index + 1}/${chunks.size})",
                                        courseText = chunk,
                                        isFullDoc = false
                                    )
                                }
                            }
                        }
                    }.awaitAll()
                }

                val allFlashcards = mutableListOf<GeneratedFlashcard>()
                val allQuizQuestions = mutableListOf<GeneratedQuizQuestion>()
                val allKeyPoints = mutableListOf<String>()
                val allMnemonicTips = mutableListOf<String>()
                val chunkSummaries = mutableListOf<String>()

                chunkResults.forEachIndexed { idx, chunkResult ->
                    val sectionPrefix = "### Section ${idx + 1}\n"
                    chunkSummaries.add(sectionPrefix + chunkResult.summary)
                    allKeyPoints.addAll(chunkResult.keyPoints)
                    allMnemonicTips.addAll(chunkResult.mnemonicTips)
                    allFlashcards.addAll(chunkResult.flashcards)
                    allQuizQuestions.addAll(chunkResult.quizQuestions)
                }

                onProgress("Finalisation du matériel pédagogique...")
                val combinedSummary = chunkSummaries.filter { it.isNotBlank() }.joinToString("\n\n")
                val distinctKeyPoints = allKeyPoints.distinctBy { it.trim().lowercase() }.take(12)
                val distinctMnemonicTips = allMnemonicTips.distinctBy { it.trim().lowercase() }.take(6)
                val distinctFlashcards = deduplicateFlashcards(allFlashcards)
                val distinctValidQuizQuestions = deduplicateAndValidateQuiz(allQuizQuestions)

                return@withContext Result.success(
                    StudyGenerationResult(
                        summary = combinedSummary,
                        keyPoints = distinctKeyPoints,
                        flashcards = distinctFlashcards,
                        quizQuestions = distinctValidQuizQuestions,
                        mnemonicTips = distinctMnemonicTips
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(mapUserFacingException(e))
        }
    }

    internal suspend fun <T> executeWithRetry(
        maxAttempts: Int = 2,
        initialDelayMs: Long = 500L,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Throwable? = null

        for (attempt in 1..maxAttempts) {
            try {
                return block()
            } catch (e: Throwable) {
                lastException = e
                val isTransient = isTransientError(e)

                if (attempt < maxAttempts && isTransient) {
                    delay(currentDelay)
                    currentDelay = (currentDelay * 2).coerceAtMost(3000L)
                } else {
                    break
                }
            }
        }

        throw lastException ?: Exception("Erreur inconnue lors de l'appel IA")
    }

    internal fun isTransientError(throwable: Throwable): Boolean {
        val msg = throwable.message?.lowercase() ?: ""
        return throwable is IOException ||
                msg.contains("429") ||
                msg.contains("quota") ||
                msg.contains("resource_exhausted") ||
                msg.contains("unavailable") ||
                msg.contains("timeout") ||
                msg.contains("deadline") ||
                msg.contains("503") ||
                msg.contains("502") ||
                msg.contains("500")
    }

    internal fun mapUserFacingException(throwable: Throwable): Throwable {
        val msg = throwable.message?.lowercase() ?: ""
        return when {
            msg.contains("429") || msg.contains("quota") || msg.contains("resource_exhausted") ->
                IllegalStateException("Quota d'IA temporairement atteint. Veuillez patienter une minute avant de réessayer.", throwable)
            msg.contains("401") || msg.contains("unauthorized") || msg.contains("invalid api key") || msg.contains("api_key") ->
                IllegalStateException("Clé API invalide ou non configurée. Rendez-vous dans votre Profil pour vérifier votre clé.", throwable)
            throwable is IOException || msg.contains("network") || msg.contains("timeout") || msg.contains("unavailable") || msg.contains("connect") ->
                IllegalStateException("Problème de connexion avec le service IA. Vérifiez votre accès Internet.", throwable)
            msg.contains("json") || msg.contains("parsing") ->
                IllegalStateException("La réponse de l'IA n'a pas pu être structurée. Veuillez réessayer.", throwable)
            else -> throwable
        }
    }

    private suspend fun generateForChunk(
        config: AiConfig,
        courseTitle: String,
        courseText: String,
        isFullDoc: Boolean
    ): StudyGenerationResult {
        val targetFlashcardsCount = if (isFullDoc) "6 à 10" else "4 à 6"
        val targetQuizCount = if (isFullDoc) "4 à 6" else "2 à 4"

        val prompt = """
            Tu es un ingénieur pédagogique et un professeur universitaire.
            Analyse le texte de cours ci-dessous intitulé "$courseTitle" et génère un matériel de révision structuré en français.
            
            Format de réponse attendu : Un objet JSON STRICT (sans texte introductif ni markdown) :
            {
              "summary": "Résumé clair et pédagogique du cours (2 à 4 paragraphes structurés)",
              "keyPoints": [
                "Point clé essentiel 1",
                "Point clé essentiel 2",
                "Point clé essentiel 3"
              ],
              "mnemonicTips": [
                "Astuce mnémotechnique concrète pour retenir une notion"
              ],
              "flashcards": [
                {
                  "question": "Question atomique et précise",
                  "answer": "Réponse concise et exacte",
                  "explanation": "Brève explication"
                }
              ],
              "quizQuestions": [
                {
                  "question": "Question de QCM",
                  "options": ["Option 1", "Option 2", "Option 3", "Option 4"],
                  "correctAnswer": "Option 1",
                  "explanation": "Pourquoi cette réponse est correcte"
                }
              ]
            }

            Règles strictes :
            1. Base-toi uniquement sur le cours fourni.
            2. Génère $targetFlashcardsCount flashcards précises.
            3. Génère $targetQuizCount QCM comportant exactement 4 options distinctes et une bonne réponse identique à l'une des 4 options.
            4. Réponds UNIQUEMENT en JSON valide.

            TEXTE DU COURS :
            $courseText
        """.trimIndent()

        val rawText = openAiClient.generateChatCompletion(
            baseUrl = config.baseUrl,
            apiKey = config.apiKey,
            modelName = config.modelName,
            prompt = prompt,
            temperature = 0.2
        )

        return parseJsonResponse(rawText)
    }

    private fun deduplicateFlashcards(cards: List<GeneratedFlashcard>): List<GeneratedFlashcard> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<GeneratedFlashcard>()
        for (card in cards) {
            val key = card.question.trim().lowercase()
            if (key.length > 3 && seen.add(key)) {
                result.add(card)
            }
        }
        return result
    }

    private fun deduplicateAndValidateQuiz(questions: List<GeneratedQuizQuestion>): List<GeneratedQuizQuestion> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<GeneratedQuizQuestion>()
        for (q in questions) {
            val key = q.question.trim().lowercase()
            if (key.length > 3 && seen.add(key)) {
                val validation = QuizValidator.validateQuestion(q)
                if (validation.isValid) {
                    result.add(q)
                }
            }
        }
        return result
    }

    internal fun parseJsonResponse(rawText: String): StudyGenerationResult {
        android.util.Log.d("LearnSyncAI", "longueur réponse: ${rawText.length}")
        val extractedJson = extractJson(rawText)
        android.util.Log.d("LearnSyncAI", "JSON extrait: length=${extractedJson.length}")

        val jsonObject = try {
            JSONObject(extractedJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Format JSON invalide retourné par l'IA : ${e.message}", e)
        }

        // 1. Summary
        val summary = jsonObject.optString("summary", "")
            .ifBlank { jsonObject.optString("resume", "") }
            .ifBlank { jsonObject.optString("résumé", "") }
            .ifBlank { jsonObject.optString("synthese", "") }
            .ifBlank { jsonObject.optString("synthèse", "") }
            .ifBlank { jsonObject.optString("overview", "") }
            .ifBlank { jsonObject.optString("content", "") }
            .trim()

        if (summary.isBlank()) {
            throw IllegalArgumentException("La réponse de l'IA ne contient pas de résumé valide.")
        }

        // 2. Key Points
        val keyPointsArray = jsonObject.optJSONArray("keyPoints")
            ?: jsonObject.optJSONArray("key_points")
            ?: jsonObject.optJSONArray("pointsCles")
            ?: jsonObject.optJSONArray("points_cles")
            ?: jsonObject.optJSONArray("points_clés")
            ?: jsonObject.optJSONArray("notionsCles")
            ?: jsonObject.optJSONArray("notions_clés")
            ?: jsonObject.optJSONArray("points")
            ?: jsonObject.optJSONArray("key_concepts")
        val keyPoints = keyPointsArray?.toStringList() ?: emptyList()

        // 3. Mnemonic Tips
        val mnemonicArray = jsonObject.optJSONArray("mnemonicTips")
            ?: jsonObject.optJSONArray("mnemonic_tips")
            ?: jsonObject.optJSONArray("astuces")
            ?: jsonObject.optJSONArray("astucesMnemoniques")
            ?: jsonObject.optJSONArray("astuces_mnemoniques")
            ?: jsonObject.optJSONArray("tips")
            ?: jsonObject.optJSONArray("mnemonics")
        val mnemonicTips = mnemonicArray?.toStringList() ?: emptyList()

        // 4. Flashcards
        val flashcardsArray = jsonObject.optJSONArray("flashcards")
            ?: jsonObject.optJSONArray("flash_cards")
            ?: jsonObject.optJSONArray("cards")
            ?: jsonObject.optJSONArray("cartes")
            ?: jsonObject.optJSONArray("flashcard_list")
        val flashcards = mutableListOf<GeneratedFlashcard>()
        if (flashcardsArray != null) {
            for (i in 0 until flashcardsArray.length()) {
                val obj = flashcardsArray.optJSONObject(i) ?: continue
                val question = obj.optString("question", "")
                    .ifBlank { obj.optString("q", "") }
                    .ifBlank { obj.optString("prompt", "") }
                    .ifBlank { obj.optString("front", "") }
                    .trim()
                val answer = obj.optString("answer", "")
                    .ifBlank { obj.optString("a", "") }
                    .ifBlank { obj.optString("reponse", "") }
                    .ifBlank { obj.optString("réponse", "") }
                    .ifBlank { obj.optString("back", "") }
                    .trim()
                val explanation = obj.optString("explanation", "")
                    .ifBlank { obj.optString("explication", "") }
                    .ifBlank { obj.optString("details", "") }
                    .trim()

                if (question.isNotBlank() && answer.isNotBlank()) {
                    flashcards.add(GeneratedFlashcard(question, answer, explanation))
                }
            }
        }

        // 5. Quiz Questions
        val quizArray = jsonObject.optJSONArray("quizQuestions")
            ?: jsonObject.optJSONArray("quiz_questions")
            ?: jsonObject.optJSONArray("quiz")
            ?: jsonObject.optJSONArray("qcm")
            ?: jsonObject.optJSONArray("questions")
            ?: jsonObject.optJSONArray("mcq")
        val quizQuestions = mutableListOf<GeneratedQuizQuestion>()
        if (quizArray != null) {
            for (i in 0 until quizArray.length()) {
                val obj = quizArray.optJSONObject(i) ?: continue
                val question = obj.optString("question", "")
                    .ifBlank { obj.optString("q", "") }
                    .ifBlank { obj.optString("prompt", "") }
                    .trim()

                val rawOptions = mutableListOf<String>()
                val optionsJsonArray = obj.optJSONArray("options")
                    ?: obj.optJSONArray("choices")
                    ?: obj.optJSONArray("propositions")
                    ?: obj.optJSONArray("answers")
                    ?: obj.optJSONArray("choix")
                if (optionsJsonArray != null) {
                    rawOptions.addAll(optionsJsonArray.toStringList())
                } else {
                    val optionsObj = obj.optJSONObject("options")
                        ?: obj.optJSONObject("choices")
                        ?: obj.optJSONObject("propositions")
                    if (optionsObj != null) {
                        val keys = listOf("A", "B", "C", "D", "a", "b", "c", "d", "1", "2", "3", "4")
                        for (k in keys) {
                            val v = optionsObj.optString(k, "").trim()
                            if (v.isNotBlank()) rawOptions.add(v)
                        }
                    }
                }

                val rawCorrectAnswer = obj.optString("correctAnswer", "")
                    .ifBlank { obj.optString("correct_answer", "") }
                    .ifBlank { obj.optString("bonneReponse", "") }
                    .ifBlank { obj.optString("bonne_reponse", "") }
                    .ifBlank { obj.optString("reponseCorrecte", "") }
                    .ifBlank { obj.optString("reponse_correcte", "") }
                    .ifBlank { obj.optString("answer", "") }
                    .ifBlank { obj.optString("correct_option", "") }
                    .ifBlank { obj.optString("correct", "") }
                    .trim()

                val explanation = obj.optString("explanation", "")
                    .ifBlank { obj.optString("explication", "") }
                    .ifBlank { obj.optString("rationale", "") }
                    .trim()

                val normalizedQuiz = normalizeQuizQuestion(question, rawOptions, rawCorrectAnswer, explanation)
                if (normalizedQuiz != null) {
                    quizQuestions.add(normalizedQuiz)
                }
            }
        }

        android.util.Log.d("LearnSyncAI", "summary length: ${summary.length}")
        android.util.Log.d("LearnSyncAI", "keyPoints count: ${keyPoints.size}")
        android.util.Log.d("LearnSyncAI", "flashcards count: ${flashcards.size}")
        android.util.Log.d("LearnSyncAI", "quiz count: ${quizQuestions.size}")

        return StudyGenerationResult(
            summary = summary,
            keyPoints = keyPoints,
            flashcards = flashcards,
            quizQuestions = quizQuestions,
            mnemonicTips = mnemonicTips
        )
    }

    internal fun normalizeQuizQuestion(
        question: String,
        rawOptions: List<String>,
        rawCorrectAnswer: String,
        explanation: String
    ): GeneratedQuizQuestion? {
        if (question.isBlank() || rawOptions.isEmpty() || rawCorrectAnswer.isBlank()) return null

        val prefixRegex = Regex("^(?:[A-Da-d1-4][.)\\-\\s]+|[•\\-*]\\s*)")
        val cleanedOptions = rawOptions.map { it.replace(prefixRegex, "").trim() }.filter { it.isNotBlank() }

        if (cleanedOptions.size < 4) return null

        var normalizedCorrectAnswer = rawCorrectAnswer.replace(prefixRegex, "").trim()

        val letterIndex = when (rawCorrectAnswer.trim().uppercase()) {
            "A", "1" -> 0
            "B", "2" -> 1
            "C", "3" -> 2
            "D", "4" -> 3
            else -> -1
        }
        if (letterIndex in rawOptions.indices) {
            normalizedCorrectAnswer = cleanedOptions.getOrElse(letterIndex) { rawOptions[letterIndex] }
        } else {
            val match = cleanedOptions.find { it.equals(normalizedCorrectAnswer, ignoreCase = true) }
                ?: rawOptions.find { it.equals(rawCorrectAnswer, ignoreCase = true) }?.let { it.replace(prefixRegex, "").trim() }
            if (match != null) {
                normalizedCorrectAnswer = match
            }
        }

        val distinctOptions = cleanedOptions.distinctBy { it.lowercase() }.toMutableList()
        if (!distinctOptions.any { it.equals(normalizedCorrectAnswer, ignoreCase = true) }) {
            distinctOptions.add(0, normalizedCorrectAnswer)
        }

        if (distinctOptions.size < 4) return null

        val finalOptions = if (distinctOptions.size > 4) {
            val correctOpt = distinctOptions.find { it.equals(normalizedCorrectAnswer, ignoreCase = true) } ?: normalizedCorrectAnswer
            val others = distinctOptions.filter { !it.equals(correctOpt, ignoreCase = true) }.take(3)
            listOf(correctOpt) + others
        } else {
            distinctOptions
        }

        val finalCorrectAnswer = finalOptions.find { it.equals(normalizedCorrectAnswer, ignoreCase = true) } ?: normalizedCorrectAnswer

        val candidate = GeneratedQuizQuestion(
            question = question,
            options = finalOptions,
            correctAnswer = finalCorrectAnswer,
            explanation = explanation
        )

        return if (QuizValidator.validateQuestion(candidate).isValid) candidate else null
    }

    internal fun extractJson(rawText: String): String {
        val trimmed = rawText.trim()
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1)
        }

        val cleaned = trimmed
            .replace(Regex("^```(?:json|JSON)?\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("```\\s*$", RegexOption.MULTILINE), "")
            .trim()

        val cbFirst = cleaned.indexOf('{')
        val cbLast = cleaned.lastIndexOf('}')
        if (cbFirst != -1 && cbLast != -1 && cbLast > cbFirst) {
            return cleaned.substring(cbFirst, cbLast + 1)
        }

        return cleaned
    }

    private fun splitIntoChunks(text: String, maxChunkSize: Int): List<String> {
        if (text.length <= maxChunkSize) return listOf(text)

        val chunks = mutableListOf<String>()
        val paragraphs = text.split("\n\n")
        var currentChunk = StringBuilder()

        for (paragraph in paragraphs) {
            if (currentChunk.length + paragraph.length + 2 > maxChunkSize && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString().trim())
                currentChunk = StringBuilder()
            }
            if (paragraph.length > maxChunkSize) {
                val sentences = paragraph.split(". ")
                for (sentence in sentences) {
                    if (currentChunk.length + sentence.length + 2 > maxChunkSize && currentChunk.isNotEmpty()) {
                        chunks.add(currentChunk.toString().trim())
                        currentChunk = StringBuilder()
                    }
                    currentChunk.append(sentence).append(". ")
                }
            } else {
                currentChunk.append(paragraph).append("\n\n")
            }
        }

        if (currentChunk.isNotBlank()) {
            chunks.add(currentChunk.toString().trim())
        }

        return if (chunks.isEmpty()) listOf(text) else chunks
    }

    private fun JSONArray.toStringList(): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until this.length()) {
            val str = this.optString(i, "").trim()
            if (str.isNotBlank()) {
                list.add(str)
            }
        }
        return list
    }
}

