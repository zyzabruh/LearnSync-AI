package com.learnsyncai

import com.learnsyncai.data.repository.toDomain
import com.learnsyncai.data.repository.toEntity
import com.learnsyncai.domain.model.Course
import com.learnsyncai.domain.model.Flashcard
import com.learnsyncai.domain.model.QuizQuestion
import com.learnsyncai.domain.model.ReviewLog
import com.learnsyncai.domain.usecase.SpacedRepetition
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class LearnSyncTest {

    @Test
    fun testSpacedRepetitionAgainRating() {
        val card = Flashcard(
            id = "c1",
            courseId = "course1",
            question = "Qu'est-ce que le polymorphisme ?",
            answer = "La capacité d'un objet à prendre plusieurs formes.",
            explanation = "Principe clé de la POO.",
            difficulty = 5.0f,
            box = 3,
            dueDate = System.currentTimeMillis(),
            interval = 14,
            easeFactor = 5.0f,
            repetitions = 4,
            lapses = 1,
            lastReviewedAt = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14),
            createdAt = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        )

        val result = SpacedRepetition.calculateReview(card, rating = 1, responseTimeMs = 1200L)
        // "Again" = étape d'apprentissage : intervalle 0, carte re-due dans 10 minutes
        assertEquals(0, result.newInterval)
        assertEquals(1, result.updatedCard.box)
        assertEquals(0, result.updatedCard.repetitions)
        assertEquals(2, result.updatedCard.lapses)
        assertTrue(result.updatedCard.easeFactor < 2.0f)
        assertTrue(result.updatedCard.dueDate <= System.currentTimeMillis() + SpacedRepetition.AGAIN_RELEARN_DELAY_MS)
    }

    @Test
    fun testSpacedRepetitionGoodRatingProgression() {
        val card = Flashcard(
            id = "c2",
            courseId = "course1",
            question = "Question 1",
            answer = "Reponse 1",
            explanation = "Exp",
            difficulty = 5.0f,
            box = 1,
            dueDate = System.currentTimeMillis(),
            interval = 0,
            easeFactor = 1.0f,
            repetitions = 0,
            lapses = 0,
            lastReviewedAt = null,
            createdAt = System.currentTimeMillis()
        )

        // First repetition with rating 3 (Good) -> initial stability ~3.1 days
        val result1 = SpacedRepetition.calculateReview(card, rating = 3, responseTimeMs = 800L)
        assertEquals(1, result1.updatedCard.repetitions)
        assertEquals(2, result1.updatedCard.box)
        assertTrue(result1.newInterval >= 2)

        // Second repetition with rating 3 (Good) -> stability grows
        val cardAfterDelay = result1.updatedCard.copy(
            lastReviewedAt = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(result1.newInterval.toLong())
        )
        val result2 = SpacedRepetition.calculateReview(cardAfterDelay, rating = 3, responseTimeMs = 600L)
        assertEquals(2, result2.updatedCard.repetitions)
        assertTrue(result2.stability > result1.stability)
        assertTrue(result2.newInterval >= result1.newInterval)
    }

    @Test
    fun testSpacedRepetitionEasyRatingBonus() {
        val card = Flashcard(
            id = "c3",
            courseId = "course1",
            question = "Question Easy",
            answer = "Reponse Easy",
            explanation = "Exp",
            difficulty = 5.0f,
            box = 1,
            dueDate = System.currentTimeMillis(),
            interval = 0,
            easeFactor = 1.0f,
            repetitions = 0,
            lapses = 0,
            lastReviewedAt = null,
            createdAt = System.currentTimeMillis()
        )

        val result = SpacedRepetition.calculateReview(card, rating = 4, responseTimeMs = 400L)
        assertTrue(result.newInterval >= 4)
        assertEquals(3, result.updatedCard.box)
        assertTrue(result.updatedCard.easeFactor > 10.0f)
    }


    @Test
    fun testStreakCalculationConsecutiveDays() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayMillis = today.atStartOfDay(zone).toInstant().toEpochMilli() + 3600000L // 1am today
        val yesterdayMillis = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 3600000L // 1am yesterday
        val twoDaysAgoMillis = today.minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli() + 3600000L // 1am 2 days ago

        val logs = listOf(
            ReviewLog("l1", "f1", todayMillis, 3, 1, 6, 500L),
            ReviewLog("l2", "f2", todayMillis + 10000L, 4, 1, 10, 400L), // Same day review
            ReviewLog("l3", "f3", yesterdayMillis, 3, 0, 1, 600L),
            ReviewLog("l4", "f4", twoDaysAgoMillis, 3, 0, 1, 700L)
        )

        val streak = SpacedRepetition.calculateStreak(logs, zone)
        assertEquals(3, streak)
    }

    @Test
    fun testStreakBrokenWhenGapExists() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val threeDaysAgoMillis = today.minusDays(3).atStartOfDay(zone).toInstant().toEpochMilli()

        val logs = listOf(
            ReviewLog("l1", "f1", threeDaysAgoMillis, 3, 1, 6, 500L)
        )

        val streak = SpacedRepetition.calculateStreak(logs, zone)
        assertEquals(0, streak)
    }

    @Test
    fun testCardCategorization() {
        val now = System.currentTimeMillis()
        val day = TimeUnit.DAYS.toMillis(1)

        val overdueCard = Flashcard("1", "c", "Q1", "A1", "", 2.5f, 1, now - (3 * day), 1, 2.5f, 1, 0, null, now)
        val dueCard = Flashcard("2", "c", "Q2", "A2", "", 2.5f, 1, now - (1000), 1, 2.5f, 1, 0, null, now)
        val futureCard = Flashcard("3", "c", "Q3", "A3", "", 2.5f, 1, now + (5 * day), 5, 2.5f, 1, 0, null, now)

        val categories = SpacedRepetition.categorizeCards(listOf(overdueCard, dueCard, futureCard))
        assertEquals(2, categories.dueCards.size)
        assertEquals(1, categories.overdueCards.size)
        assertEquals(1, categories.futureCards.size)
    }

    @Test
    fun testQuizQuestionEntityMapping() {
        val original = QuizQuestion(
            id = "q1",
            courseId = "c1",
            question = "Quelle est la capitale de la France ?",
            options = listOf("Paris", "Lyon", "Marseille", "Bordeaux"),
            correctAnswer = "Paris",
            explanation = "Paris est la capitale depuis des siècles.",
            difficulty = "facile"
        )

        val entity = original.toEntity()
        val restored = entity.toDomain()

        assertEquals(original.id, restored.id)
        assertEquals(original.question, restored.question)
        assertEquals(4, restored.options.size)
        assertEquals("Paris", restored.options[0])
        assertEquals("Paris", restored.correctAnswer)
    }

    @Test
    fun testFsrsDeterministicInitialReviewValues() {
        val baseCard = Flashcard(
            id = "test_init",
            courseId = "c1",
            question = "Q",
            answer = "A",
            explanation = "",
            difficulty = 5.0f,
            box = 1,
            dueDate = 1000000L,
            interval = 0,
            easeFactor = 1.0f,
            repetitions = 0,
            lapses = 0,
            lastReviewedAt = null,
            createdAt = 1000000L
        )

        val againResult = SpacedRepetition.calculateReview(baseCard, rating = SpacedRepetition.RATING_AGAIN, currentTime = 1000000L)
        assertEquals(0.4f, againResult.stability, 0.001f)
        assertEquals(8.5f, againResult.difficulty, 0.001f)
        assertEquals(0, againResult.updatedCard.repetitions)
        assertEquals(1, againResult.updatedCard.lapses)
        // "Again" = étape d'apprentissage : intervalle 0 + re-due dans 10 minutes
        assertEquals(0, againResult.newInterval)
        assertEquals(1000000L + SpacedRepetition.AGAIN_RELEARN_DELAY_MS, againResult.updatedCard.dueDate)

        val hardResult = SpacedRepetition.calculateReview(baseCard, rating = SpacedRepetition.RATING_HARD, currentTime = 1000000L)
        assertEquals(1.2f, hardResult.stability, 0.001f)
        assertEquals(7.0f, hardResult.difficulty, 0.001f)
        assertEquals(1, hardResult.updatedCard.repetitions)
        assertEquals(0, hardResult.updatedCard.lapses)

        val goodResult = SpacedRepetition.calculateReview(baseCard, rating = SpacedRepetition.RATING_GOOD, currentTime = 1000000L)
        assertEquals(3.1f, goodResult.stability, 0.001f)
        assertEquals(5.5f, goodResult.difficulty, 0.001f)
        assertEquals(1, goodResult.updatedCard.repetitions)
        assertEquals(0, goodResult.updatedCard.lapses)

        val easyResult = SpacedRepetition.calculateReview(baseCard, rating = SpacedRepetition.RATING_EASY, currentTime = 1000000L)
        assertEquals(15.5f, easyResult.stability, 0.001f)
        assertEquals(3.0f, easyResult.difficulty, 0.001f)
        assertEquals(1, easyResult.updatedCard.repetitions)
        assertEquals(0, easyResult.updatedCard.lapses)
    }

    @Test
    fun testFsrsDeterministicRetrievabilityCurve() {
        val stability = 10.0f
        // R(0, S) = 1.0
        val r0 = SpacedRepetition.calculateRetrievability(0.0f, stability)
        assertEquals(1.0f, r0, 0.0001f)

        // R(10, 10) targeting 0.81 (1 / (1 + 19/81)) = 81/100 = 0.81
        val rS = SpacedRepetition.calculateRetrievability(10.0f, stability)
        assertEquals(0.81f, rS, 0.01f)

        // R decreases monotonically with elapsed time
        val r20 = SpacedRepetition.calculateRetrievability(20.0f, stability)
        assertTrue(r20 < rS)
        assertTrue(r20 > 0.0f)
    }

    @Test
    fun testQuizValidatorValidatesStrictly() {
        val validItem = com.learnsyncai.domain.model.GeneratedQuizQuestion(
            question = "Quel langage pour Android ?",
            options = listOf("Kotlin", "Swift", "PHP", "Ruby"),
            correctAnswer = "Kotlin",
            explanation = "Kotlin est le langage officiel recommandé."
        )
        val validList = com.learnsyncai.domain.usecase.QuizValidator.filterValidQuestions(listOf(validItem))
        assertEquals(1, validList.size)

        // Invalid: only 3 options
        val invalidOptionsCount = com.learnsyncai.domain.model.GeneratedQuizQuestion(
            question = "Q2 ?",
            options = listOf("A", "B", "C"),
            correctAnswer = "A",
            explanation = "Exp"
        )
        assertEquals(0, com.learnsyncai.domain.usecase.QuizValidator.filterValidQuestions(listOf(invalidOptionsCount)).size)

        // Invalid: duplicates
        val duplicateOptions = com.learnsyncai.domain.model.GeneratedQuizQuestion(
            question = "Q3 ?",
            options = listOf("A", "B", "A", "C"),
            correctAnswer = "A",
            explanation = "Exp"
        )
        assertEquals(0, com.learnsyncai.domain.usecase.QuizValidator.filterValidQuestions(listOf(duplicateOptions)).size)

        // Invalid: correct answer not in options
        val wrongAnswer = com.learnsyncai.domain.model.GeneratedQuizQuestion(
            question = "Q4 ?",
            options = listOf("A", "B", "C", "D"),
            correctAnswer = "Z",
            explanation = "Exp"
        )
        assertEquals(0, com.learnsyncai.domain.usecase.QuizValidator.filterValidQuestions(listOf(wrongAnswer)).size)
    }

    @Test
    fun testCalculateStreak() {
        val now = System.currentTimeMillis()
        val oneDayMs = TimeUnit.DAYS.toMillis(1)
        val logs = listOf(
            ReviewLog(id = "1", flashcardId = "c1", reviewedAt = now, rating = 3, previousInterval = 0, newInterval = 1, responseTime = 500L),
            ReviewLog(id = "2", flashcardId = "c1", reviewedAt = now - oneDayMs, rating = 3, previousInterval = 0, newInterval = 1, responseTime = 500L),
            ReviewLog(id = "3", flashcardId = "c1", reviewedAt = now - (oneDayMs * 2), rating = 3, previousInterval = 0, newInterval = 1, responseTime = 500L)
        )
        val streak = SpacedRepetition.calculateStreak(logs)
        assertTrue(streak >= 1)
    }

}

