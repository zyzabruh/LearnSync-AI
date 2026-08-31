package com.learnsyncai.data.ai

import com.learnsyncai.domain.model.GeneratedFlashcard
import com.learnsyncai.domain.model.GeneratedQuizQuestion
import com.learnsyncai.domain.model.StudyGenerationResult
import kotlin.math.min
import kotlin.random.Random

/**
 * Générateur 100% local, sans API : transforme le texte du cours en flashcards
 * et QCM de secours (patterns "X est Y", cloze deletion). Qualité inférieure à
 * l'IA, mais l'app reste utilisable sans clé API.
 */
class OfflineMaterialGenerator {

    fun generate(courseTitle: String, courseText: String, flashcardsTarget: Int, quizTarget: Int): StudyGenerationResult {
        val sentences = courseText
            .replace(Regex("\\s+"), " ")
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.length in 25..400 }
        if (sentences.isEmpty()) {
            throw IllegalStateException("Le document ne contient pas assez de texte exploitable pour la génération hors-ligne.")
        }

        val summary = buildSummary(courseTitle, sentences)
        val keyPoints = buildKeyPoints(sentences)
        val flashcards = buildFlashcards(sentences, flashcardsTarget)
        val quizQuestions = buildQuiz(sentences, quizTarget, flashcardsTarget)

        return StudyGenerationResult(
            summary = summary,
            keyPoints = keyPoints,
            flashcards = flashcards,
            quizQuestions = quizQuestions,
            mnemonicTips = emptyList()
        )
    }

    private fun buildSummary(courseTitle: String, sentences: List<String>): String {
        val sb = StringBuilder()
        sb.append("Synthèse hors-ligne de « $courseTitle » (générée localement, sans IA).\n\n")
        var length = 0
        for (s in sentences) {
            if (length + s.length > 1200) break
            sb.append(s).append(" ")
            length += s.length
        }
        return sb.toString().trim()
    }

    private fun buildKeyPoints(sentences: List<String>): List<String> {
        return sentences.take(12)
    }

    private fun buildFlashcards(sentences: List<String>, target: Int): List<GeneratedFlashcard> {
        val cards = mutableListOf<GeneratedFlashcard>()
        val seenQuestions = mutableSetOf<String>()

        // Pattern 1 : définitions ("X est/sont/désigne/signifie/correspond à Y")
        val definitionRegex = Regex("^(.{3,80}?)\\s+(?:est|sont|désigne|désignent|signifie|correspondent? à)\\s+(.{15,250})$", RegexOption.IGNORE_CASE)
        for (s in sentences) {
            if (cards.size >= target) break
            val m = definitionRegex.find(s) ?: continue
            val term = m.groupValues[1].trim().trimEnd(',', ';')
            val definition = m.groupValues[2].trim()
            val question = "Qu'est-ce que « $term » ?"
            if (question.lowercase() in seenQuestions || term.length < 4) continue
            seenQuestions.add(question.lowercase())
            cards.add(GeneratedFlashcard(question = question, answer = definition, explanation = "Extrait du cours."))
        }

        // Pattern 2 : phrases courtes -> "Vrai ou Faux" reformulé en Q/R directe
        for (s in sentences) {
            if (cards.size >= target) break
            if (s.length !in 40..180) continue
            val question = "Énoncez une information clé du cours commençant par : « ${s.take(40)}… »"
            if (question.lowercase() in seenQuestions) continue
            seenQuestions.add(question.lowercase())
            cards.add(GeneratedFlashcard(question = question, answer = s, explanation = "Mémorisation de phrase clé."))
        }

        return cards
    }

    private fun buildQuiz(sentences: List<String>, quizTarget: Int, cardPoolSize: Int): List<GeneratedQuizQuestion> {
        val quizzes = mutableListOf<GeneratedQuizQuestion>()
        val seenQuestions = mutableSetOf<String>()

        // Termes candidats comme bonnes réponses / distracteurs
        val stopWords = setOf("leurs", "jusqu", "celle", "celui", "ensuite", "toujours", "possible", "exemple", "déjà", "autre", "entre", "après", "avant", "quand", "comme", "ainsi", "donc", "aussi", "toute", "tous", "cette", "être", "avoir", "fait", "peut", "plus", "moins", "très", "chez", "lors", "puis")
        val terms = Regex("[A-Za-zÀ-ÿ]{6,}").findAll(sentences.joinToString(" "))
            .map { it.value }
            .filter { it.lowercase() !in stopWords }
            .groupBy { it.lowercase() }
            .map { it.value.first() }
            .toList()
        if (terms.size < 8) return quizzes

        val random = Random(sentences.hashCode())
        for (s in sentences) {
            if (quizzes.size >= quizTarget) break
            val termInSentence = terms.filter { s.contains(it, ignoreCase = true) }
                .maxByOrNull { it.length } ?: continue
            val question = "Quel terme complète cette phrase ? « ${s.replace(termInSentence, "______")} »"
            val qKey = question.lowercase()
            if (qKey in seenQuestions) continue
            seenQuestions.add(qKey)

            val distractors = terms.filter { !it.equals(termInSentence, ignoreCase = true) }
                .shuffled(random)
                .take(3)
            if (distractors.size < 3) continue

            val options = (distractors + termInSentence).shuffled(random)
            quizzes.add(
                GeneratedQuizQuestion(
                    question = question,
                    options = options,
                    correctAnswer = termInSentence,
                    explanation = "Phrase issue du cours."
                )
            )
        }

        // Question de vérification de compréhension en secours
        if (quizzes.isEmpty() && cardPoolSize > 0) {
            val s = sentences.first()
            quizzes.add(
                GeneratedQuizQuestion(
                    question = "Lequel de ces énoncés figure dans le cours ?",
                    options = (sentences.drop(1).take(3).map { it.take(80) } + s.take(80)).shuffled(),
                    correctAnswer = s.take(80),
                    explanation = "Reconnaissance d'un énoncé du cours."
                )
            )
        }

        // Nettoyage : garantir 4 options distinctes
        return quizzes.filter { q ->
            q.options.size == 4 && q.options.distinct().size == 4 && q.options.contains(q.correctAnswer)
        }.let { it.subList(0, min(quizTarget, it.size)) }
    }
}
