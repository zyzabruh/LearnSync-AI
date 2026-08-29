package com.learnsyncai.data.ai

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiParsingTest {

    private val repository = AiRepositoryImpl()

    @Test
    fun testSuccessfulJsonParsing() {
        val jsonPayload = """
            {
              "summary": "Introduction détaillée à la thermodynamique classique et aux principes de transfert d'énergie.",
              "keyPoints": [
                "Premier principe : conservation de l'énergie",
                "Deuxième principe : augmentation de l'entropie",
                "Troisième principe : zéro absolu"
              ],
              "mnemonicTips": [
                "Pour les principes : Conserve (1), Augmente (2), Gèle (3)"
              ],
              "flashcards": [
                {
                  "question": "Quel est le premier principe de la thermodynamique ?",
                  "answer": "La conservation de l'énergie (l'énergie totale d'un système isolé reste constante).",
                  "explanation": "L'énergie ne peut être ni créée ni détruite, seulement transformée."
                },
                {
                  "question": "Qu'indique le deuxième principe ?",
                  "answer": "L'entropie d'un système isolé augmente lors d'une transformation irréversible.",
                  "explanation": "Il définit le sens d'évolution des transformations spontanées."
                }
              ],
              "quizQuestions": [
                {
                  "question": "Quelle grandeur physique caractérise le désordre d'un système thermodynamique ?",
                  "options": ["L'entropie", "L'enthalpie", "La température", "La pression"],
                  "correctAnswer": "L'entropie",
                  "explanation": "L'entropie S mesure le degré de désordre microscopique."
                },
                {
                  "question": "Quelle est l'unité de la température absolue dans le SI ?",
                  "options": ["A. Kelvin", "B. Celsius", "C. Fahrenheit", "D. Rankine"],
                  "correctAnswer": "A",
                  "explanation": "Le Kelvin (K) est l'unité de base de la température thermodynamique."
                }
              ]
            }
        """.trimIndent()

        val result = repository.parseJsonResponse(jsonPayload)

        assertEquals("Introduction détaillée à la thermodynamique classique et aux principes de transfert d'énergie.", result.summary)
        assertEquals(3, result.keyPoints.size)
        assertEquals(1, result.mnemonicTips.size)
        assertEquals(2, result.flashcards.size)
        assertEquals(2, result.quizQuestions.size)

        // Check first flashcard
        assertEquals("Quel est le premier principe de la thermodynamique ?", result.flashcards[0].question)
        assertEquals("La conservation de l'énergie (l'énergie totale d'un système isolé reste constante).", result.flashcards[0].answer)

        // Check QCM normalization
        val secondQuiz = result.quizQuestions[1]
        assertEquals("Quelle est l'unité de la température absolue dans le SI ?", secondQuiz.question)
        assertEquals(listOf("Kelvin", "Celsius", "Fahrenheit", "Rankine"), secondQuiz.options)
        assertEquals("Kelvin", secondQuiz.correctAnswer)
    }

    @Test
    fun testJsonWithMarkdownCodeBlockAndSurroundingText() {
        val rawResponse = """
            Bonjour ! Voici le matériel de révision structuré que vous avez demandé :
            ```json
            {
              "summary": "Résumé de l'anatomie cardiaque.",
              "keyPoints": ["Le cœur comporte 4 cavités", "La circulation est double"],
              "mnemonicTips": ["Oreillettes en haut, Ventricules en bas"],
              "flashcards": [
                {
                  "question": "Combien de cavités possède le cœur humain ?",
                  "answer": "4 cavités (2 oreillettes et 2 ventricules).",
                  "explanation": "Deux cavités à gauche et deux à droite."
                }
              ],
              "quizQuestions": [
                {
                  "question": "Par quelle cavité le sang oxygéné quitte-t-il le cœur ?",
                  "options": ["Ventricule gauche", "Oreillette droite", "Ventricule droit", "Oreillette gauche"],
                  "correctAnswer": "Ventricule gauche",
                  "explanation": "Le ventricule gauche propulse le sang dans l'aorte."
                }
              ]
            }
            ```
            J'espère que ce résumé vous aidera dans vos révisions !
        """.trimIndent()

        val result = repository.parseJsonResponse(rawResponse)

        assertEquals("Résumé de l'anatomie cardiaque.", result.summary)
        assertEquals(2, result.keyPoints.size)
        assertEquals(1, result.flashcards.size)
        assertEquals(1, result.quizQuestions.size)
    }

    @Test
    fun testMalformedJsonThrowsException() {
        val nonJson = "Désolé, je ne peux pas traiter ce document car il est vide ou inintelligible."

        var caught: IllegalArgumentException? = null
        try {
            repository.parseJsonResponse(nonJson)
        } catch (e: IllegalArgumentException) {
            caught = e
        }

        assertNotNull("Une exception doit être levée en cas de réponse non-JSON", caught)
        assertTrue(caught!!.message?.contains("Format JSON invalide") == true || caught.message?.contains("résumé valide") == true)
    }

    @Test
    fun testMissingSummaryThrowsException() {
        val jsonWithoutSummary = """
            {
              "keyPoints": ["Point 1", "Point 2"],
              "flashcards": [],
              "quizQuestions": []
            }
        """.trimIndent()

        var caught: IllegalArgumentException? = null
        try {
            repository.parseJsonResponse(jsonWithoutSummary)
        } catch (e: IllegalArgumentException) {
            caught = e
        }

        assertNotNull("Une exception doit être levée si le résumé est manquant", caught)
        assertTrue(caught!!.message?.contains("résumé valide") == true)
    }
}
