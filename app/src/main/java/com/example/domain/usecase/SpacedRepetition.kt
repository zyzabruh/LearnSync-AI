package com.example.domain.usecase

import com.example.domain.model.Flashcard
import com.example.domain.model.ReviewLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object SpacedRepetition {

    data class ReviewResult(
        val updatedCard: Flashcard,
        val newInterval: Int
    )

    /**
     * Calculate spaced repetition schedule using the SM-2 / Leitner hybrid algorithm.
     * Rating:
     * 1: Again (Incorrect / forgot)
     * 2: Hard (Correct with hesitation)
     * 3: Good (Correct with normal effort)
     * 4: Easy (Perfect recall)
     */
    fun calculateReview(card: Flashcard, rating: Int, responseTimeMs: Long): ReviewResult {
        val now = System.currentTimeMillis()
        val dayInMillis = TimeUnit.DAYS.toMillis(1)

        var interval = card.interval
        var easeFactor = card.easeFactor
        var repetitions = card.repetitions
        var lapses = card.lapses
        var box = card.box

        when (rating) {
            1 -> { // Again
                lapses += 1
                repetitions = 0
                interval = 1
                box = 1
                easeFactor = (easeFactor - 0.2f).coerceAtLeast(1.3f)
            }
            2 -> { // Hard
                repetitions += 1
                interval = if (interval <= 1) 1 else (interval * 1.2f).toInt()
                box = (box + 1).coerceAtMost(5)
                easeFactor = (easeFactor - 0.15f).coerceAtLeast(1.3f)
            }
            3 -> { // Good
                repetitions += 1
                interval = when (repetitions) {
                    1 -> 1
                    2 -> 6
                    else -> (interval * easeFactor).toInt().coerceAtLeast(interval + 1)
                }
                box = (box + 1).coerceAtMost(5)
            }
            4 -> { // Easy
                repetitions += 1
                interval = when (repetitions) {
                    1 -> 4
                    2 -> 10
                    else -> (interval * easeFactor * 1.3f).toInt().coerceAtLeast(interval + 2)
                }
                box = (box + 2).coerceAtMost(5)
                easeFactor += 0.15f
            }
            else -> {
                interval = 1
            }
        }

        val dueDate = now + (interval.toLong() * dayInMillis)

        val updated = card.copy(
            interval = interval,
            dueDate = dueDate,
            easeFactor = easeFactor,
            repetitions = repetitions,
            lapses = lapses,
            box = box,
            lastReviewedAt = now
        )

        return ReviewResult(updatedCard = updated, newInterval = interval)
    }

    /**
     * Calculate consecutive active days streak using java.time.LocalDate.
     * Handles multiple reviews on same day, gaps, today/yesterday start.
     */
    fun calculateStreak(reviewLogs: List<ReviewLog>, zoneId: ZoneId = ZoneId.systemDefault()): Int {
        if (reviewLogs.isEmpty()) return 0

        val daysWithReviews: Set<LocalDate> = reviewLogs.map { log ->
            Instant.ofEpochMilli(log.reviewedAt).atZone(zoneId).toLocalDate()
        }.toSet()

        val today = LocalDate.now(zoneId)
        val yesterday = today.minusDays(1)

        val hasReviewToday = daysWithReviews.contains(today)
        val hasReviewYesterday = daysWithReviews.contains(yesterday)

        if (!hasReviewToday && !hasReviewYesterday) {
            return 0
        }

        var streak = 0
        var checkDate = if (hasReviewToday) today else yesterday

        while (daysWithReviews.contains(checkDate)) {
            streak++
            checkDate = checkDate.minusDays(1)
        }

        return streak
    }

    /**
     * Categorize flashcards by urgency:
     * - Overdue: dueDate < today's start
     * - Due Today: dueDate <= now
     * - Future: dueDate > now
     */
    fun categorizeCards(cards: List<Flashcard>, zoneId: ZoneId = ZoneId.systemDefault()): CardCategories {
        val now = System.currentTimeMillis()
        val todayStart = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val overdue = mutableListOf<Flashcard>()
        val dueToday = mutableListOf<Flashcard>()
        val future = mutableListOf<Flashcard>()

        for (card in cards) {
            when {
                card.dueDate < todayStart -> {
                    overdue.add(card)
                    dueToday.add(card) // Overdue is also due today
                }
                card.dueDate <= now -> {
                    dueToday.add(card)
                }
                else -> {
                    future.add(card)
                }
            }
        }

        return CardCategories(
            dueCards = dueToday.distinctBy { it.id },
            overdueCards = overdue,
            futureCards = future
        )
    }

    data class CardCategories(
        val dueCards: List<Flashcard>,
        val overdueCards: List<Flashcard>,
        val futureCards: List<Flashcard>
    )
}
