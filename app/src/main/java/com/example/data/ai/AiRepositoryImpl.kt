package com.example.data.ai

import com.example.domain.model.GeneratedFlashcard
import com.example.domain.model.GeneratedQuizQuestion
import com.example.domain.model.StudyGenerationResult
import com.example.domain.repository.AiRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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
                val result = generateForChunk(courseTitle, chunks[0], isFullDoc = true)
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
                    val chunkResult = generateForChunk(
                        courseTitle = "$courseTitle (Section ${index + 1})",
                        courseText = chunk,
                        isFullDoc = false
                    )
                    chunkSummaries.add(chunkResult.summary)
                    allKeyPoints.addAll(chunkResult.keyPoints)
                    allMnemonicTips.addAll(chunkResult.mnemonicTips)
                    allFlashcards.addAll(chunkResult.flashcards)
                    allQuizQuestions.addAll(chunkResult.quizQuestions)
                }

                onProgress("Finalisation et consolidation du matériel...")
                val combinedSummary = chunkSummaries.filter { it.isNotBlank() }.joinToString("\n\n")
                
                // Deduplicate flashcards by normalized question
                val distinctFlashcards = allFlashcards.distinctBy { it.question.trim().lowercase() }
                val distinctQuizQuestions = allQuizQuestions.distinctBy { it.question.trim().lowercase() }
                val distinctKeyPoints = allKeyPoints.distinctBy { it.trim().lowercase() }
                val distinctMnemonicTips = allMnemonicTips.distinctBy { it.trim().lowercase() }

                return@withContext Result.success(
                    StudyGenerationResult(
                        summary = combinedSummary,
                        keyPoints = distinctKeyPoints,
                        flashcards = distinctFlashcards,
                        quizQuestions = distinctQuizQuestions,
                        mnemonicTips = distinctMnemonicTips
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
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
            4. Génère $targetQuizCount questions de QCM avec exactement 4 options distinctes et 1 seule bonne réponse (qui doit être présente dans les options).
            5. Si une notion est ambiguë dans le document, privilégie ce qui est textuellement écrit.

            TEXTE DU COURS :
            $courseText
        """.trimIndent()

        val response = model.generateContent(prompt)
        val rawText = response.text ?: throw IllegalStateException("Réponse vide de l'IA.")

        return parseJsonResponse(rawText)
    }

    private fun parseJsonResponse(rawText: String): StudyGenerationResult {
        val cleanedJson = rawText.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        // Extract JSON substring if surrounded by extra text
        val jsonStartIndex = cleanedJson.indexOf('{')
        val jsonEndIndex = cleanedJson.lastIndexOf('}')
        val finalJsonString = if (jsonStartIndex != -1 && jsonEndIndex != -1 && jsonEndIndex > jsonStartIndex) {
            cleanedJson.substring(jsonStartIndex, jsonEndIndex + 1)
        } else {
            cleanedJson
        }

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
                    // Ensure correctAnswer is in options list
                    val finalOptions = if (options.contains(correctAnswer)) {
                        options
                    } else {
                        options + correctAnswer
                    }
                    quizQuestions.add(GeneratedQuizQuestion(question, finalOptions, correctAnswer, explanation))
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
                // Slicing long single paragraph by sentences
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
