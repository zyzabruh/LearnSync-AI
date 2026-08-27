package com.learnsyncai.domain.usecase

import com.learnsyncai.domain.model.GeneratedQuizQuestion

object QuizValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val error: String? = null
    )

    /**
     * Strict validation of a single QCM question:
     * 1. Non-empty question text
     * 2. Exactly 4 options
     * 3. All 4 options are non-empty
     * 4. All 4 options are strictly unique
     * 5. Non-empty correctAnswer
     * 6. correctAnswer matches exactly one of the 4 options
     */
    fun validateQuestion(question: GeneratedQuizQuestion): ValidationResult {
        if (question.question.isBlank()) {
            return ValidationResult(false, "Le libellé de la question ne peut pas être vide.")
        }
        if (question.options.size != 4) {
            return ValidationResult(false, "Le QCM doit comporter exactement 4 options (reçu : ${question.options.size}).")
        }
        if (question.options.any { it.isBlank() }) {
            return ValidationResult(false, "Toutes les options doivent contenir du texte valide.")
        }
        val distinctNormalized = question.options.map { it.trim().lowercase() }.distinct()
        if (distinctNormalized.size != 4) {
            return ValidationResult(false, "Les 4 options du QCM doivent être strictement distinctes et uniques.")
        }
        if (question.correctAnswer.isBlank()) {
            return ValidationResult(false, "La réponse correcte ne peut pas être vide.")
        }
        val matchesCorrectAnswer = question.options.any { it.trim().equals(question.correctAnswer.trim(), ignoreCase = true) }
        if (!matchesCorrectAnswer) {
            return ValidationResult(false, "La réponse correcte '${question.correctAnswer}' ne correspond à aucune des 4 options proposées.")
        }
        return ValidationResult(true)
    }

    /**
     * Validates all questions or throws IllegalArgumentException on first invalid question.
     */
    fun validateAllOrThrow(questions: List<GeneratedQuizQuestion>) {
        questions.forEachIndexed { index, q ->
            val res = validateQuestion(q)
            if (!res.isValid) {
                throw IllegalArgumentException("Question QCM invalide (index $index) : ${res.error}")
            }
        }
    }

    /**
     * Filters and returns only valid questions according to strict QCM criteria.
     */
    fun filterValidQuestions(questions: List<GeneratedQuizQuestion>): List<GeneratedQuizQuestion> {
        return questions.filter { validateQuestion(it).isValid }
    }
}
