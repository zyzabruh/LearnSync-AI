package com.example.data.ai

import com.example.domain.model.GeneratedFlashcard
import com.example.domain.model.GeneratedQuizQuestion
import com.example.domain.model.StudyGenerationResult
import com.example.domain.repository.AiRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AiRepositoryImpl : AiRepository {

    override suspend fun generateStudyMaterial(courseTitle: String, courseText: String): Result<StudyGenerationResult> = withContext(Dispatchers.IO) {
        try {
            val model = Firebase.ai.generativeModel("gemini-3.5-flash")
            val prompt = """
                Tu es un ingénieur pédagogique et un expert en révision. Analyse le cours suivant intitulé "$courseTitle" et fournis un JSON strict respectant exactement ce format, sans texte additionnel autour du JSON :
                {
                  "summary": "Résumé concis et précis du cours en français",
                  "keyPoints": ["Point clé 1", "Point clé 2", "Point clé 3"],
                  "mnemonicTips": ["Astuce mnémotechnique 1"],
                  "flashcards": [
                    {
                      "question": "Question atomique testant une seule notion",
                      "answer": "Réponse précise",
                      "explanation": "Explication pédagogique"
                    }
                  ],
                  "quizQuestions": [
                    {
                      "question": "Question QCM",
                      "options": ["Option A", "Option B", "Option C", "Option D"],
                      "correctAnswer": "Option A",
                      "explanation": "Pourquoi cette réponse"
                    }
                  ]
                }
                
                Contraintes importantes (Protection contre les hallucinations) :
                - Utilise UNIQUEMENT le contenu fourni dans le cours ci-dessous.
                - N'invente aucun fait ni aucune information externe.
                - Reste fidèle au document.
                - Si le cours est trop court ou vide, base-toi sur le texte fourni.
                
                Texte du cours :
                $courseText
            """.trimIndent()

            val response = model.generateContent(prompt)
            val rawText = response.text ?: throw IllegalStateException("Réponse vide de l'IA")
            
            val cleanedJson = rawText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val jsonObject = JSONObject(cleanedJson)
            val summary = jsonObject.optString("summary", "Résumé non disponible")
            
            val keyPointsJson = jsonObject.optJSONArray("keyPoints")
            val keyPoints = mutableListOf<String>()
            if (keyPointsJson != null) {
                for (i in 0 until keyPointsJson.length()) {
                    keyPoints.add(keyPointsJson.getString(i))
                }
            }

            val mnemonicJson = jsonObject.optJSONArray("mnemonicTips")
            val mnemonicTips = mutableListOf<String>()
            if (mnemonicJson != null) {
                for (i in 0 until mnemonicJson.length()) {
                    mnemonicTips.add(mnemonicJson.getString(i))
                }
            }

            val flashcardsJson = jsonObject.optJSONArray("flashcards")
            val flashcards = mutableListOf<GeneratedFlashcard>()
            if (flashcardsJson != null) {
                for (i in 0 until flashcardsJson.length()) {
                    val obj = flashcardsJson.getJSONObject(i)
                    flashcards.add(
                        GeneratedFlashcard(
                            question = obj.optString("question"),
                            answer = obj.optString("answer"),
                            explanation = obj.optString("explanation")
                        )
                    )
                }
            }

            val quizJson = jsonObject.optJSONArray("quizQuestions")
            val quizQuestions = mutableListOf<GeneratedQuizQuestion>()
            if (quizJson != null) {
                for (i in 0 until quizJson.length()) {
                    val obj = quizJson.getJSONObject(i)
                    val optsJson = obj.getJSONArray("options")
                    val opts = mutableListOf<String>()
                    for (j in 0 until optsJson.length()) {
                        opts.add(optsJson.getString(j))
                    }
                    quizQuestions.add(
                        GeneratedQuizQuestion(
                            question = obj.optString("question"),
                            options = opts,
                            correctAnswer = obj.optString("correctAnswer"),
                            explanation = obj.optString("explanation")
                        )
                    )
                }
            }

            Result.success(
                StudyGenerationResult(
                    summary = summary,
                    keyPoints = keyPoints,
                    flashcards = flashcards,
                    quizQuestions = quizQuestions,
                    mnemonicTips = mnemonicTips
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
