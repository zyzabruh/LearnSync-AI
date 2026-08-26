package com.example

import com.example.domain.model.Flashcard
import com.example.domain.model.ReviewLog
import com.example.domain.usecase.SpacedRepetition
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class LearnSyncTest {

    @Test
    fun testSpacedRepetitionAgain() {
        val card = Flashcard(
            id = "1",
            courseId = "c1",
            question = "What is X?",
            answer = "X is Y",
            explanation = "Exp",
            difficulty = 2.5f,
            box = 2,
            dueDate = System.currentTimeMillis(),
            interval = 10,
            easeFactor = 2.5f,
            repetitions = 3,
            lapses = 0,
            lastReviewedAt = null,
            createdAt = System.currentTimeMillis()
        )

        val result = SpacedRepetition.calculateReview(card, rating = 1, responseTimeMs = 1500L)
        assertEquals(1, result.updatedCard.interval)
        assertEquals(1, result.updatedCard.lapses)
        assertEquals(0, result.updatedCard.repetitions)
    }

    @Test
    fun testSpacedRepetitionEasy() {
        val card = Flashcard(
            id = "1",
            courseId = "c1",
            question = "What is X?",
            answer = "X is Y",
            explanation = "Exp",
            difficulty = 2.5f,
            box = 1,
            dueDate = System.currentTimeMillis(),
            interval = 2,
            easeFactor = 2.5f,
            repetitions = 1,
            lapses = 0,
            lastReviewedAt = null,
            createdAt = System.currentTimeMillis()
        )

        val result = SpacedRepetition.calculateReview(card, rating = 4, responseTimeMs = 1000L)
        assertTrue(result.newInterval > 2)
        assertEquals(2, result.updatedCard.repetitions)
    }

    @Test
    fun testStreakCalculationEmpty() {
        val streak = SpacedRepetition.calculateStreak(emptyList())
        assertEquals(0, streak)
    }
}
