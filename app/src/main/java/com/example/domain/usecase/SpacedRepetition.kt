package com.example.domain.usecase

import com.example.domain.model.Flashcard
import java.util.concurrent.TimeUnit

object SpacedRepetition {

    data class ReviewResult(
        val updatedCard: Flashcard,
        val newInterval: Int
    )

    fun calculateReview(card: Flashcard, rating: Int, responseTimeMs: Long): ReviewResult {
        val now = System.currentTimeMillis()
        val dayInMillis = TimeUnit.DAYS.toMillis(1)

        var interval = card.interval
        var easeFactor = card.easeFactor
        var repetitions = card.repetitions
        var lapses = card.lapses
        var box = card.box

        // Rating: 1 = Again, 2 = Hard, 3 = Good, 4 = Easy
        when (rating) {
            1 -> { // Again
                lapses += 1
                repetitions = 0
                interval = 1
                box = 1
                easeFactor = maxOf(1.3f, easeFactor - 0.2f)
            }
            2 -> { // Hard
                interval = maxOf(1, (interval * 1.2f).toInt())
                box = minOf(5, box + 1)
                easeFactor = maxOf(1.3f, easeFactor - 0.15f)
                repetitions += 1
            }
            3 -> { // Good
                interval = if (interval == 0) 1 else (interval * easeFactor).toInt()
                box = minOf(5, box + 1)
                repetitions += 1
            }
            4 -> { // Easy
                interval = if (interval == 0) 4 else (interval * easeFactor * 1.3f).toInt()
                box = minOf(5, box + 2)
                easeFactor += 0.15f
                repetitions += 1
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

    fun calculateStreak(reviewLogs: List<com.example.domain.model.ReviewLog>): Int {
        if (reviewLogs.isEmpty()) return 0
        
        // Group by calendar days
        val daysWithReviews = reviewLogs.map { log ->
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = log.reviewedAt }
            // normalize to day start
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }.distinct().sortedDescending()

        if (daysWithReviews.isEmpty()) return 0

        val todayCal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val today = todayCal.timeInMillis
        val yesterday = today - TimeUnit.DAYS.toMillis(1)

        val latestReviewDay = daysWithReviews.first()
        if (latestReviewDay != today && latestReviewDay != yesterday) {
            return 0 // Streak broken if no review today or yesterday
        }

        var streak = 0
        var expectedDay = latestReviewDay
        for (day in daysWithReviews) {
            if (day == expectedDay) {
                streak++
                expectedDay -= TimeUnit.DAYS.toMillis(1)
            } else {
                break
            }
        }
        return streak
    }
}
