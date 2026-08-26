package com.example

import com.example.data.repository.toDomain
import com.example.data.repository.toEntity
import com.example.domain.model.Course
import com.example.domain.model.Flashcard
import com.example.domain.model.QuizQuestion
import com.example.domain.model.ReviewLog
import com.example.domain.usecase.SpacedRepetition
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
            difficulty = 2.5f,
            box = 3,
            dueDate = System.currentTimeMillis(),
            interval = 14,
            easeFactor = 2.5f,
            repetitions = 4,
            lapses = 1,
            lastReviewedAt = null,
            createdAt = System.currentTimeMillis()
        )

        val result = SpacedRepetition.calculateReview(card, rating = 1, responseTimeMs = 1200L)
        assertEquals(1, result.newInterval)
        assertEquals(1, result.updatedCard.box)
        assertEquals(0, result.updatedCard.repetitions)
        assertEquals(2, result.updatedCard.lapses)
        assertEquals(2.3f, result.updatedCard.easeFactor, 0.01f)
    }

    @Test
    fun testSpacedRepetitionGoodRatingProgression() {
        val card = Flashcard(
            id = "c2",
            courseId = "course1",
            question = "Question 1",
            answer = "Reponse 1",
            explanation = "Exp",
            difficulty = 2.5f,
            box = 1,
            dueDate = System.currentTimeMillis(),
            interval = 1,
            easeFactor = 2.5f,
            repetitions = 1,
            lapses = 0,
            lastReviewedAt = null,
            createdAt = System.currentTimeMillis()
        )

        // Second repetition with rating 3 (Good) -> interval becomes 6
        val result1 = SpacedRepetition.calculateReview(card, rating = 3, responseTimeMs = 800L)
        assertEquals(6, result1.newInterval)
        assertEquals(2, result1.updatedCard.repetitions)
        assertEquals(2, result1.updatedCard.box)

        // Third repetition with rating 3 (Good) -> interval becomes 6 * 2.5 = 15
        val result2 = SpacedRepetition.calculateReview(result1.updatedCard, rating = 3, responseTimeMs = 600L)
        assertEquals(15, result2.newInterval)
        assertEquals(3, result2.updatedCard.repetitions)
        assertEquals(3, result2.updatedCard.box)
    }

    @Test
    fun testSpacedRepetitionEasyRatingBonus() {
        val card = Flashcard(
            id = "c3",
            courseId = "course1",
            question = "Question Easy",
            answer = "Reponse Easy",
            explanation = "Exp",
            difficulty = 2.5f,
            box = 1,
            dueDate = System.currentTimeMillis(),
            interval = 0,
            easeFactor = 2.5f,
            repetitions = 0,
            lapses = 0,
            lastReviewedAt = null,
            createdAt = System.currentTimeMillis()
        )

        val result = SpacedRepetition.calculateReview(card, rating = 4, responseTimeMs = 400L)
        assertEquals(4, result.newInterval)
        assertEquals(3, result.updatedCard.box)
        assertEquals(2.65f, result.updatedCard.easeFactor, 0.01f)
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
}
