package com.learnsyncai.domain.usecase

import com.learnsyncai.domain.model.Flashcard
import com.learnsyncai.domain.model.ReviewLog
import com.learnsyncai.domain.model.ReviewSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Implementation of the Free Spaced Repetition Scheduler (FSRS v4) algorithm.
 *
 * Core State Variables:
 * - Stability (S, stored in easeFactor): Memory stability in days (time for retrievability to fall to 90%).
 * - Difficulty (D, stored in difficulty): Intrinsic item complexity scaled from 1.0 to 10.0.
 * - Retrievability (R): Probability of recalling the card after elapsed time t.
 *
 * Ratings:
 * 1 = Again: Complete failure of recall (Lapse)
 * 2 = Hard: Successful recall with significant difficulty / hesitation
 * 3 = Good: Normal recall with expected effort
 * 4 = Easy: Effortless recall with immediate confidence
 */
object SpacedRepetition {

    const val RATING_AGAIN = 1
    const val RATING_HARD = 2
    const val RATING_GOOD = 3
    const val RATING_EASY = 4

    // Étape d'apprentissage après "Again" (comme Anki) : la carte est re-due
    // 10 minutes plus tard et réapparaît en fin de session courante, au lieu
    // de disparaître pendant 1 jour.
    const val AGAIN_RELEARN_DELAY_MS = 10L * 60L * 1000L

    // FSRS default stability anchors (days) for initial review
    private val INITIAL_STABILITY = mapOf(
        RATING_AGAIN to 0.4f,
        RATING_HARD to 1.2f,
        RATING_GOOD to 3.1f,
        RATING_EASY to 15.5f
    )

    // Initial difficulty per rating
    private val INITIAL_DIFFICULTY = mapOf(
        RATING_AGAIN to 8.5f,
        RATING_HARD to 7.0f,
        RATING_GOOD to 5.5f,
        RATING_EASY to 3.0f
    )

    // FSRS retrievability decay factor (19/81)
    private const val FACTOR = 19.0f / 81.0f

    // Target retention rate (90%)
    private const val REQUESTED_RETENTION = 0.90f

    data class ReviewResult(
        val updatedCard: Flashcard,
        val newInterval: Int,
        val stability: Float,
        val difficulty: Float,
        val retrievability: Float
    )

    /**
     * Calculates the new FSRS state for a card following a review.
     *
     * @param card The flashcard being reviewed.
     * @param rating Rating between 1 (Again) and 4 (Easy).
     * @param responseTimeMs Elapsed response time in milliseconds.
     * @param currentTime Timestamp of the review (defaults to now).
     */
    fun calculateReview(
        card: Flashcard,
        rating: Int,
        responseTimeMs: Long = 0L,
        currentTime: Long = System.currentTimeMillis()
    ): ReviewResult {
        val validRating = rating.coerceIn(RATING_AGAIN, RATING_EASY)
        val dayInMillis = TimeUnit.DAYS.toMillis(1)

        val isFirstReview = card.repetitions == 0 && card.lastReviewedAt == null

        val currentStability = if (card.easeFactor > 0f) card.easeFactor else 2.5f
        val currentDifficulty = if (card.difficulty in 1.0f..10.0f) card.difficulty else 5.0f

        val elapsedDays: Float = if (isFirstReview || card.lastReviewedAt == null) {
            0f
        } else {
            val diff = currentTime - card.lastReviewedAt
            max(0.1f, diff.toFloat() / dayInMillis.toFloat())
        }

        val retrievability: Float = if (isFirstReview) {
            1.0f
        } else {
            calculateRetrievability(elapsedDays, currentStability)
        }

        val newDifficulty: Float
        val newStability: Float
        var repetitions = card.repetitions
        var lapses = card.lapses

        if (isFirstReview) {
            // First learning step
            newStability = INITIAL_STABILITY[validRating] ?: 2.5f
            newDifficulty = INITIAL_DIFFICULTY[validRating] ?: 5.0f
            if (validRating == RATING_AGAIN) {
                lapses += 1
                repetitions = 0
            } else {
                repetitions = 1
            }
        } else {
            // Review phase
            newDifficulty = calculateNextDifficulty(currentDifficulty, validRating)

            if (validRating == RATING_AGAIN) {
                lapses += 1
                repetitions = 0
                newStability = calculateLapseStability(newDifficulty, currentStability, retrievability)
            } else {
                repetitions += 1
                newStability = calculateRecallStability(
                    difficulty = newDifficulty,
                    stability = currentStability,
                    retrievability = retrievability,
                    rating = validRating
                )
            }
        }

        // "Again" : intervalle 0 + due dans 10 minutes (étape d'apprentissage).
        // Sinon, interval calculé depuis la nouvelle stabilité.
        val newInterval = if (validRating == RATING_AGAIN) 0 else calculateInterval(newStability, validRating)
        val dueDate = if (validRating == RATING_AGAIN) {
            currentTime + AGAIN_RELEARN_DELAY_MS
        } else {
            currentTime + (newInterval.toLong() * dayInMillis)
        }
        val newBox = mapStabilityToBox(newStability)

        val updatedCard = card.copy(
            interval = newInterval,
            dueDate = dueDate,
            easeFactor = newStability,
            difficulty = newDifficulty,
            repetitions = repetitions,
            lapses = lapses,
            box = newBox,
            lastReviewedAt = currentTime
        )

        return ReviewResult(
            updatedCard = updatedCard,
            newInterval = newInterval,
            stability = newStability,
            difficulty = newDifficulty,
            retrievability = retrievability
        )
    }

    /**
     * Power-law memory retrievability formula: R(t, S) = (1 + FACTOR * t / S)^(-1)
     */
    fun calculateRetrievability(elapsedDays: Float, stability: Float): Float {
        if (stability <= 0f) return 0f
        val base = 1.0f + FACTOR * (elapsedDays / stability)
        return (1.0f / base).coerceIn(0.0f, 1.0f)
    }

    /**
     * Mean reversion difficulty calculation:
     * D' = D - w6 * (G - 3)
     * D_new = w7 * D0(3) + (1 - w7) * D'
     */
    private fun calculateNextDifficulty(currentDifficulty: Float, rating: Int): Float {
        val delta = -0.6f * (rating - 3)
        val rawD = currentDifficulty + delta
        val meanReverted = (0.15f * 5.5f) + (0.85f * rawD)
        return meanReverted.coerceIn(1.0f, 10.0f)
    }

    /**
     * Stability update after successful recall (Hard, Good, Easy).
     */
    private fun calculateRecallStability(
        difficulty: Float,
        stability: Float,
        retrievability: Float,
        rating: Int
    ): Float {
        val hardPenalty = if (rating == RATING_HARD) 0.65f else 1.0f
        val easyBonus = if (rating == RATING_EASY) 1.35f else 1.0f

        val growthModifier = exp(1.5f) * (11.0f - difficulty) *
                (stability.pow(-0.15f)) *
                (exp(1.0f * (1.0f - retrievability)) - 1.0f) *
                hardPenalty * easyBonus

        val newS = stability * (1.0f + max(0.1f, growthModifier))
        return when (rating) {
            RATING_HARD -> max(stability * 1.05f, newS)
            RATING_GOOD -> max(stability * 1.4f, newS)
            RATING_EASY -> max(stability * 2.0f, newS)
            else -> newS
        }
    }

    /**
     * Post-lapse stability calculation after 'Again' rating.
     */
    private fun calculateLapseStability(
        difficulty: Float,
        stability: Float,
        retrievability: Float
    ): Float {
        val sLapse = 0.5f * (difficulty.pow(-0.3f)) *
                (((stability + 1.0f).pow(0.2f)) - 1.0f) *
                exp(0.4f * (1.0f - retrievability))
        return max(0.3f, sLapse)
    }

    /**
     * Converts stability to interval in days targeting 90% retention.
     */
    private fun calculateInterval(stability: Float, rating: Int): Int {
        if (rating == RATING_AGAIN) {
            return 1
        }
        val targetInterval = (stability * (1.0f / FACTOR) * ((1.0f / REQUESTED_RETENTION) - 1.0f)).roundToInt()
        val baseInterval = max(1, targetInterval)
        return when (rating) {
            RATING_HARD -> max(1, (baseInterval * 0.8f).roundToInt())
            RATING_GOOD -> max(2, baseInterval)
            RATING_EASY -> max(4, (baseInterval * 1.3f).roundToInt())
            else -> baseInterval
        }
    }

    /**
     * Maps stability (days) to a Leitner-like box level (1 to 5) for gamification and visual progression.
     */
    fun mapStabilityToBox(stability: Float): Int {
        return when {
            stability < 2.0f -> 1
            stability < 7.0f -> 2
            stability < 21.0f -> 3
            stability < 60.0f -> 4
            else -> 5
        }
    }

    /**
     * Forecast future review dates by repeatedly simulating a Good rating.
     *
     * The current due date is used as the first simulated review. Overdue cards
     * are simulated from now, while cards due later keep their scheduled date.
     * The returned dates are only the subsequent due dates, not the current one.
     */
    fun forecastSchedule(
        card: Flashcard,
        horizonDays: Int,
        currentTime: Long = System.currentTimeMillis()
    ): List<Long> {
        if (horizonDays <= 0) return emptyList()

        val horizonEnd = currentTime + TimeUnit.DAYS.toMillis(horizonDays.toLong())
        var simulatedCard = card
        var reviewAt = max(card.dueDate, currentTime)
        val forecastDates = mutableListOf<Long>()

        while (reviewAt <= horizonEnd) {
            val result = calculateReview(
                card = simulatedCard,
                rating = RATING_GOOD,
                currentTime = reviewAt
            )
            val nextDue = result.updatedCard.dueDate

            if (nextDue <= reviewAt || nextDue > horizonEnd) break

            forecastDates += nextDue
            simulatedCard = result.updatedCard
            reviewAt = nextDue
        }

        return forecastDates
    }

    /**
     * Sum actual session durations, including a currently open session.
     * Sessions with invalid or negative bounds are ignored.
     */
    fun calculateStudyDurationMs(
        sessions: List<ReviewSession>,
        currentTime: Long = System.currentTimeMillis()
    ): Long = sessions.sumOf { session ->
        val end = session.endedAt ?: currentTime
        (end - session.startedAt).coerceAtLeast(0L)
    }

    /**
     * Calculate consecutive active days streak using java.time.LocalDate.
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
                    dueToday.add(card)
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

