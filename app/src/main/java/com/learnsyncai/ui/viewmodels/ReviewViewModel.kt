package com.learnsyncai.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.learnsyncai.data.widget.DueCardsWidgetProvider
import com.learnsyncai.domain.model.Flashcard
import com.learnsyncai.domain.model.ReviewLog
import com.learnsyncai.domain.model.ReviewSession
import com.learnsyncai.domain.usecase.SpacedRepetition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Session de révision : file de cartes, notation FSRS (transactionnelle),
 * traçage des sessions en base et rafraîchissement du widget.
 */
class ReviewViewModel(application: Application) : AndroidViewModel(application) {
    // Câblage délégué au conteneur d'injection de l'Application.
    private val container = (application as com.learnsyncai.LearnSyncApplication).container
    private val flashcardRepo = container.flashcardRepository
    private val reviewRepo = container.reviewRepository

    val dueFlashcards: StateFlow<List<Flashcard>> = flashcardRepo.getDueFlashcards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reviewLogs: StateFlow<List<ReviewLog>> = reviewRepo.getAllReviewLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * File de la session de révision en cours : null = aucune session active
     * (écran de choix), liste vide = session terminée. La file vit dans le
     * ViewModel : quitter l'écran met la session en pause, y revenir la reprend
     * dans le même ordre aléatoire.
     */
    private val _reviewQueue = MutableStateFlow<List<Flashcard>?>(null)
    val reviewQueue: StateFlow<List<Flashcard>?> = _reviewQueue.asStateFlow()

    /** Session Room en cours (null = pas de session, ou session héritée d'un crash). */
    private var currentSessionId: String? = null

    fun getDueFlashcardsForCourse(courseId: String): kotlinx.coroutines.flow.Flow<List<Flashcard>> =
        flashcardRepo.getDueFlashcardsForCourse(courseId)

    /** Démarre une session mélangée sur les cartes fournies (limit = 20, 30... ou null = tout). */
    fun startReviewSession(cards: List<Flashcard>, limit: Int? = null) {
        val shuffled = cards.distinctBy { it.id }.shuffled()
        _reviewQueue.value = if (limit != null) shuffled.take(limit) else shuffled

        // Trace la session en base (durée réelle + volume) pour stats et calendrier.
        val session = ReviewSession(
            id = UUID.randomUUID().toString(),
            courseId = cards.map { it.courseId }.distinct().singleOrNull(),
            startedAt = System.currentTimeMillis(),
            endedAt = null,
            cardsReviewed = 0
        )
        currentSessionId = session.id
        viewModelScope.launch {
            runCatching { reviewRepo.insertSession(session) }
        }
    }

    fun endReviewSession() {
        _reviewQueue.value = null
        val sessionId = currentSessionId
        currentSessionId = null
        if (sessionId != null) {
            viewModelScope.launch {
                runCatching { reviewRepo.endSession(sessionId, System.currentTimeMillis()) }
            }
        }
    }

    /**
     * Notation atomique d'une carte : état FSRS + log (+ compteur de session)
     * dans une seule transaction Room, puis rafraîchissement du widget.
     */
    fun rateCurrentCard(card: Flashcard, rating: Int, responseTimeMs: Long) {
        val sessionId = currentSessionId
        viewModelScope.launch {
            val reviewResult = SpacedRepetition.calculateReview(card, rating, responseTimeMs)
            val log = ReviewLog(
                id = UUID.randomUUID().toString(),
                flashcardId = card.id,
                courseId = card.courseId,
                reviewedAt = System.currentTimeMillis(),
                rating = rating,
                previousInterval = card.interval,
                newInterval = reviewResult.newInterval,
                responseTime = responseTimeMs
            )
            try {
                reviewRepo.rateCardAtomically(reviewResult.updatedCard, log, sessionId)
            } catch (e: Exception) {
                android.util.Log.e("LearnSyncAI", "Notation atomique échouée pour la carte ${card.id}", e)
            }
            // Rafraîchit le widget (nombre de cartes dues)
            DueCardsWidgetProvider.updateAll(getApplication())
        }
        _reviewQueue.update { queue ->
            queue?.let { q ->
                val rest = q.dropWhile { it.id == card.id }
                if (rating == SpacedRepetition.RATING_AGAIN) rest + card else rest
            }
        }
    }
}
