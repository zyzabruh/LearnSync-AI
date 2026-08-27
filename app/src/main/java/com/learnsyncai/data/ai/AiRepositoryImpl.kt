package com.learnsyncai.data.ai

import com.learnsyncai.domain.model.GeneratedFlashcard
import com.learnsyncai.domain.model.GeneratedQuizQuestion
import com.learnsyncai.domain.model.StudyGenerationResult
import com.learnsyncai.domain.repository.AiRepository
import com.learnsyncai.domain.usecase.QuizValidator
import com.google.firebase.Firebase
import com.google.firebase.ai.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class AiRepositoryImpl : AiRepository {

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

            val chunkSize = 8000
            val chunks = splitIntoChunks(trimmedText, chunkSize)

            if (chunks.size == 1) {
                onProgress("Génération du résumé, flashcards et QCM avec l'IA...")
                val result = executeWithRetry(maxAttempts = 3) {
                    generateForChunk(courseTitle, chunks[0], isFullDoc = true)
                }
                QuizValidator.validateAllOrThrow(result.quizQuestions)
                return@withContext Result.success(result)
            } else {
                // Multi-chunk processing
                val allFlashcards = mutableListOf<GeneratedFlashcard>()
                val allQuizQuestions = mutableListOf<GeneratedQuizQuestion>()
                val allKeyPoints = mutableListOf<String>()
                val allMnemonicTips = mutableListOf<String>()
                val chunkSummaries = mutableListOf<String>()

                chunks.forEachIndexed { index, chunk ->
                    onProgress("Analyse de la section ${index + 1} sur ${chunks.size}...")
                    val chunkResult = executeWithRetry(maxAttempts = 3) {
                        generateForChunk(
                            courseTitle = "$courseTitle (Section ${index + 1}/${chunks.size})",
                            courseText = chunk,
                            isFullDoc = false
                        )
                    }
                    chunkSummaries.add(chunkResult.summary)
                    allKeyPoints.addAll(chunkResult.keyPoints)
                    allMnemonicTips.addAll(chunkResult.mnemonicTips)
                    allFlashcards.addAll(chunkResult.flashcards)
                    allQuizQuestions.addAll(chunkResult.quizQuestions)
                }

                onProgress("Consolidation et synthèse globale par l'IA...")
                val consolidatedSynthesis = executeWithRetry(maxAttempts = 2) {
                    consolidateSectionsWithAi(
                        courseTitle = courseTitle,
                        sectionSummaries = chunkSummaries,
                        allKeyPoints = allKeyPoints,
                        allMnemonicTips = allMnemonicTips
                    )
                }

                // Intelligent deduplication of flashcards and quiz questions
                val distinctFlashcards = deduplicateFlashcards(allFlashcards)
                val distinctValidQuizQuestions = deduplicateAndValidateQuiz(allQuizQuestions)

                QuizValidator.validateAllOrThrow(distinctValidQuizQuestions)

                return@withContext Result.success(
                    StudyGenerationResult(
                        summary = consolidatedSynthesis.first,
                        keyPoints = consolidatedSynthesis.second,
                        flashcards = distinctFlashcards,
                        quizQuestions = distinctValidQuizQuestions,
                        mnemonicTips = consolidatedSynthesis.third
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(mapUserFacingException(e))
        }
    }

    internal suspend fun <T> executeWithRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 1000L,
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
                    currentDelay = (currentDelay * 2).coerceAtMost(6000L)
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
                msg.contains("deadline")
    }

    internal fun mapUserFacingException(throwable: Throwable): Throwable {
        val msg = throwable.message?.lowercase() ?: ""
        return when {
            msg.contains("429") || msg.contains("quota") || msg.contains("resource_exhausted") ->
                IllegalStateException("Quota d'IA temporairement atteint. Veuillez patienter une minute avant de réessayer.", throwable)
            throwable is IOException || msg.contains("network") || msg.contains("timeout") || msg.contains("unavailable") ->
                IllegalStateException("Problème de connexion avec le service IA. Vérifiez votre accès Internet.", throwable)
            msg.contains("json") || msg.contains("parsing") ->
                IllegalStateException("Le document n'a pas pu être structuré par l'IA. Essayez avec un document plus concis.", throwable)
            else -> throwable
        }
    }

    private suspend fun generateForChunk(
        courseTitle: String,
        courseText: String,
        isFullDoc: Boolean
    ): StudyGenerationResult {
        val model = Firebase.ai.generativeModel("gemini-2.5-flash")

        val targetFlashcardsCount = if (isFullDoc) "6 à 12" else "3 à 5"
        val targetQuizCount = if (isFullDoc) "4 à 8" else "2 à 4"

        val prompt = """
            Tu es un ingénieur pédagogique de haut niveau et un tuteur universitaire bienveillant.
            Analyse le texte de cours ci-dessous intitulé "$courseTitle" et génère un matériel de révision structuré en français.
            
            Format de réponse attendu : Un objet JSON STRICT respectant exactement ce schéma (aucun texte, markdown ou balise avant ou après le JSON) :
            {
              "summary": "Résumé clair, pédagogique et fidèle au contenu",
              "keyPoints": [
                "Point clé essentiel 1",
                "Point clé essentiel 2",
                "Point clé essentiel 3"
              ],
              "mnemonicTips": [
                "Astuce mnémotechnique concrète pour retenir une formule ou un concept"
              ],
              "flashcards": [
                {
                  "question": "Question atomique et précise testant un concept clé",
                  "answer": "Réponse exacte et concise",
                  "explanation": "Brève explication clarifiant le concept"
                }
              ],
              "quizQuestions": [
                {
                  "question": "Question à choix multiples",
                  "options": ["Option 1", "Option 2", "Option 3", "Option 4"],
                  "correctAnswer": "Option 1 (la réponse correcte exacte)",
                  "explanation": "Pourquoi cette réponse est correcte"
                }
              ]
            }

            Règles strictes et anti-hallucination :
            1. Base-toi EXCLUSIVEMENT sur les informations fournies dans le texte du cours ci-dessous.
            2. N'invente aucun fait, date, formule ou nom non présent dans le texte.
            3. Génère $targetFlashcardsCount flashcards atomiques et pertinentes.
            4. Génère $targetQuizCount questions de QCM avec exactement 4 options distinctes et 1 seule bonne réponse (qui doit être rigoureusement identique à l'une des 4 options).
            5. Si une notion est ambiguë dans le document, privilégie ce qui est textuellement écrit.

            TEXTE DU COURS :
            $courseText
        """.trimIndent()

        val response = model.generateContent(prompt)
        val rawText = response.text ?: throw IllegalStateException("Réponse vide de l'IA.")

        return parseJsonResponse(rawText)
    }

    /**
     * Second-pass AI consolidation to synthesize multiple section summaries into a single executive summary.
     */
    private suspend fun consolidateSectionsWithAi(
        courseTitle: String,
        sectionSummaries: List<String>,
        allKeyPoints: List<String>,
        allMnemonicTips: List<String>
    ): Triple<String, List<String>, List<String>> {
        val model = Firebase.ai.generativeModel("gemini-2.5-flash")

        val prompt = """
            Tu es un expert pédagogique. Tu as analysé plusieurs sections d'un cours intitulé "$courseTitle".
            Voici les résumés et points clés intermédiaires extraits de chaque section :

            RÉSUMÉS DES SECTIONS :
            ${sectionSummaries.joinToString("\n\n---\n\n")}

            POINTS CLÉS EXTRAITS :
            ${allKeyPoints.joinToString("\n• ")}

            ASTUCES MNÉMOTECHNIQUES :
            ${allMnemonicTips.joinToString("\n• ")}

            TÂCHE :
            Produis une synthèse globale unifiée et harmonieuse en JSON STRICT :
            {
              "summary": "Synthèse globale rédigée et structurée couvrant l'ensemble du cours",
              "keyPoints": ["Top 5 à 10 points clés consolidés et non redondants"],
              "mnemonicTips": ["Top 3 à 5 meilleures astuces mnémotechniques uniques"]
            }
        """.trimIndent()

        return try {
            val response = model.generateContent(prompt)
            val rawText = response.text ?: ""
            val cleaned = cleanJsonString(rawText)
            val json = JSONObject(cleaned)

            val summary = json.optString("summary", sectionSummaries.joinToString("\n\n"))
            val keyPoints = json.optJSONArray("keyPoints")?.toStringList() ?: allKeyPoints.distinct()
            val mnemonicTips = json.optJSONArray("mnemonicTips")?.toStringList() ?: allMnemonicTips.distinct()

            Triple(summary, keyPoints, mnemonicTips)
        } catch (_: Exception) {
            // Fallback gracefully to non-redundant merge
            Triple(
                sectionSummaries.filter { it.isNotBlank() }.joinToString("\n\n"),
                allKeyPoints.distinctBy { it.trim().lowercase() },
                allMnemonicTips.distinctBy { it.trim().lowercase() }
            )
        }
    }

    private fun deduplicateFlashcards(cards: List<GeneratedFlashcard>): List<GeneratedFlashcard> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<GeneratedFlashcard>()
        for (card in cards) {
            val key = card.question.trim().lowercase()
            if (key.length > 5 && seen.add(key)) {
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
            if (key.length > 5 && seen.add(key)) {
                val validation = QuizValidator.validateQuestion(q)
                if (validation.isValid) {
                    result.add(q)
                }
            }
        }
        return result
    }

    private fun parseJsonResponse(rawText: String): StudyGenerationResult {
        val finalJsonString = cleanJsonString(rawText)
        val jsonObject = JSONObject(finalJsonString)
        val summary = jsonObject.optString("summary", "Résumé non disponible.")

        val keyPoints = jsonObject.optJSONArray("keyPoints")?.toStringList() ?: emptyList()
        val mnemonicTips = jsonObject.optJSONArray("mnemonicTips")?.toStringList() ?: emptyList()

        val flashcards = mutableListOf<GeneratedFlashcard>()
        val flashcardsArray = jsonObject.optJSONArray("flashcards")
        if (flashcardsArray != null) {
            for (i in 0 until flashcardsArray.length()) {
                val obj = flashcardsArray.optJSONObject(i) ?: continue
                val question = obj.optString("question").trim()
                val answer = obj.optString("answer").trim()
                val explanation = obj.optString("explanation").trim()
                if (question.isNotBlank() && answer.isNotBlank()) {
                    flashcards.add(GeneratedFlashcard(question, answer, explanation))
                }
            }
        }

        val quizQuestions = mutableListOf<GeneratedQuizQuestion>()
        val quizArray = jsonObject.optJSONArray("quizQuestions")
        if (quizArray != null) {
            for (i in 0 until quizArray.length()) {
                val obj = quizArray.optJSONObject(i) ?: continue
                val question = obj.optString("question").trim()
                val optionsArray = obj.optJSONArray("options")
                val options = optionsArray?.toStringList() ?: emptyList()
                val correctAnswer = obj.optString("correctAnswer").trim()
                val explanation = obj.optString("explanation").trim()

                if (question.isNotBlank() && options.isNotEmpty() && correctAnswer.isNotBlank()) {
                    val quizCandidate = GeneratedQuizQuestion(question, options, correctAnswer, explanation)
                    if (QuizValidator.validateQuestion(quizCandidate).isValid) {
                        quizQuestions.add(quizCandidate)
                    }
                }
            }
        }

        return StudyGenerationResult(
            summary = summary,
            keyPoints = keyPoints,
            flashcards = flashcards,
            quizQuestions = quizQuestions,
            mnemonicTips = mnemonicTips
        )
    }

    private fun cleanJsonString(rawText: String): String {
        val cleanedJson = rawText.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val jsonStartIndex = cleanedJson.indexOf('{')
        val jsonEndIndex = cleanedJson.lastIndexOf('}')
        return if (jsonStartIndex != -1 && jsonEndIndex != -1 && jsonEndIndex > jsonStartIndex) {
            cleanedJson.substring(jsonStartIndex, jsonEndIndex + 1)
        } else {
            cleanedJson
        }
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

