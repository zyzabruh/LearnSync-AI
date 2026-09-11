package com.learnsyncai.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.learnsyncai.data.widget.DueCardsWidgetProvider
import com.learnsyncai.domain.model.Flashcard
import com.learnsyncai.domain.model.ReviewItem
import com.learnsyncai.domain.model.ReviewLog
import com.learnsyncai.domain.model.ReviewSession
import com.learnsyncai.domain.usecase.ReviewQueue
import com.learnsyncai.domain.usecase.SpacedRepetition
import com.learnsyncai.tts.TtsController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Session de révision : file de cartes, notation FSRS (transactionnelle),
 * traçage des sessions en base, lecture vocale (TtsController) et
 * rafraîchissement du widget.
 */
class ReviewViewModel(application: Application) : AndroidViewModel(application) {
    // Câblage délégué au conteneur d'injection de l'Application.
    private val container = (application as com.learnsyncai.LearnSyncApplication).container
    private val flashcardRepo = container.flashcardRepository
    private val reviewRepo = container.reviewRepository
    private val prefsRepo = container.preferencesRepository
    private val aiRepo = container.aiRepository

    /** Lecture vocale possédée par ce ViewModel (moteur initialisé paresseusement). */
    private val ttsController by lazy { TtsController(getApplication()) }

    /** Dernière carte dont la question a été lue automatiquement (anti-doublon). */
    private var lastAutoSpokenCardId: String? = null

    /** Explication IA de la carte courante (null = aucune demandée). */
    private val _explanation = MutableStateFlow<String?>(null)
    val explanation: StateFlow<String?> = _explanation.asStateFlow()
    private val _explaining = MutableStateFlow(false)
    val explaining: StateFlow<Boolean> = _explaining.asStateFlow()
    private var lastExplainedCardId: String? = null

    /**
     * File de la session de révision en cours : null = aucune session active
     * (écran de choix), liste vide = session terminée. Chaque carte est
     * expansée en items (sens inversé, occultations cloze) puis mélangée.
     * Quitter l'écran met la session en pause, y revenir la reprend.
     */
    private val _reviewQueue = MutableStateFlow<List<ReviewItem>?>(null)
    val reviewQueue: StateFlow<List<ReviewItem>?> = _reviewQueue.asStateFlow()

    init {
        // Lecture vocale automatique de la question à chaque nouvelle carte
        // en tête de file (option autoTtsEnabled), arrêt dès qu'il n'y a plus
        // de session active.
        viewModelScope.launch {
            combine(_reviewQueue, prefsRepo.getPreferences()) { queue, prefs ->
                queue to prefs.autoTtsEnabled
            }.collect { (queue, autoTtsEnabled) ->
                val headCard = queue?.firstOrNull()?.card
                if (headCard == null) {
                    ttsController.stop()
                    lastAutoSpokenCardId = null
                } else if (autoTtsEnabled && headCard.id != lastAutoSpokenCardId) {
                    lastAutoSpokenCardId = headCard.id
                    ttsController.speak(headCard.question, utteranceId = "auto_question")
                }
            }
        }
    }

    val dueFlashcards: StateFlow<List<Flashcard>> = flashcardRepo.getDueFlashcards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reviewLogs: StateFlow<List<ReviewLog>> = reviewRepo.getAllReviewLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reviewSessions: StateFlow<List<ReviewSession>> = reviewRepo.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Session Room en cours (null = pas de session, ou session héritée d'un crash). */
    private var currentSessionId: String? = null

    fun getDueFlashcardsForCourse(courseId: String): kotlinx.coroutines.flow.Flow<List<Flashcard>> =
        flashcardRepo.getDueFlashcardsForCourse(courseId)

    /** Démarre une session mélangée sur les cartes fournies (limit = 20, 30... ou null = tout). */
    fun startReviewSession(cards: List<Flashcard>, limit: Int? = null, shuffle: Boolean = true) {
        _lastRating.value = null
        _explanation.value = null
        val expanded = ReviewQueue.expand(cards, shuffle)
        _reviewQueue.value = if (limit != null) expanded.take(limit) else expanded

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
        _lastRating.value = null
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
    fun rateCurrentCard(item: ReviewItem, rating: Int, responseTimeMs: Long) {
        _explanation.value = null
        val card = item.card
        val sessionId = currentSessionId
        // Calcul pur synchrone : la carte précédente + le log sont connus
        // avant l'écriture, ce qui rend l'annulation possible.
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
        _lastRating.value = LastRating(
            previousItem = item,
            logId = log.id,
            sessionId = sessionId
        )
        viewModelScope.launch {
            try {
                reviewRepo.rateCardAtomically(reviewResult.updatedCard, log, sessionId)
            } catch (e: Exception) {
                android.util.Log.e("LearnSyncAI", "Notation atomique échouée pour la carte ${card.id}", e)
                _lastRating.value = null
            }
            // Rafraîchit le widget (nombre de cartes dues)
            DueCardsWidgetProvider.updateAll(getApplication())
        }
        grantReviewXp()
        _reviewQueue.update { queue ->
            queue?.let { q ->
                val rest = if (q.isNotEmpty()) q.drop(1) else q
                if (rating == SpacedRepetition.RATING_AGAIN) rest + item else rest
            }
        }
    }

    /** Dernière notation annulable (null = rien à annuler). */
    private val _lastRating = MutableStateFlow<LastRating?>(null)
    val canUndo: StateFlow<Boolean> = _lastRating
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Annule la dernière notation : restaure l'item en tête de file,
     * supprime le log et décrémente le compteur de session.
     */
    fun undoLastRating() {
        val last = _lastRating.value ?: return
        _lastRating.value = null
        viewModelScope.launch {
            try {
                reviewRepo.undoRateAtomically(last.previousItem.card, last.logId, last.sessionId)
            } catch (e: Exception) {
                android.util.Log.e("LearnSyncAI", "Annulation échouée pour la carte ${last.previousItem.card.id}", e)
            }
            DueCardsWidgetProvider.updateAll(getApplication())
        }
        _reviewQueue.update { queue ->
            queue?.let { q -> listOf(last.previousItem) + q.filterNot { it.key() == last.previousItem.key() } }
        }
    }

    /** Retire une carte de la file (report/suspension depuis la session). */
    fun removeCardFromQueue(cardId: String) {
        _lastRating.value = null
        _reviewQueue.update { queue -> queue?.filterNot { it.card.id == cardId } }
    }

    /**
     * Session « Lacunes » : cartes dues triées par priorité (oublis,
     * dernier échec, temps de réponse élevé), sans mélange pour traiter
     * le plus urgent d'abord.
     */
    fun startGapSession(cards: List<Flashcard>, logs: List<ReviewLog>, limit: Int = 15) {
        val lastRatingByCard = logs.groupBy { it.flashcardId }
            .mapValues { (_, entries) -> entries.maxByOrNull { it.reviewedAt } }
        val ranked = cards.sortedByDescending { card ->
            val last = lastRatingByCard[card.id]
            card.lapses * 100 +
                (if (last?.rating == SpacedRepetition.RATING_AGAIN) 50 else 0) +
                (if ((last?.responseTime ?: 0L) > 30_000L) 10 else 0) +
                (if (card.interval == 0) 5 else 0)
        }
        _lastRating.value = null
        _reviewQueue.value = ReviewQueue.expand(ranked.take(limit.coerceAtLeast(1)), shuffle = false)
        val session = ReviewSession(
            id = UUID.randomUUID().toString(),
            courseId = ranked.map { it.courseId }.distinct().singleOrNull(),
            startedAt = System.currentTimeMillis(),
            endedAt = null,
            cardsReviewed = 0
        )
        currentSessionId = session.id
        viewModelScope.launch {
            runCatching { reviewRepo.insertSession(session) }
        }
    }

    /** Compte les cartes dues relevant d'une session Lacunes (oublis ou échec récent). */
    fun gapCount(cards: List<Flashcard>, logs: List<ReviewLog>): Int {
        val lastRatingByCard = logs.groupBy { it.flashcardId }
            .mapValues { (_, entries) -> entries.maxByOrNull { it.reviewedAt }?.rating }
        return cards.count { card ->
            card.lapses >= 2 || lastRatingByCard[card.id] == SpacedRepetition.RATING_AGAIN
        }
    }

    /**
     * Cartes éligibles à une session « Examen » : non suspendues et dues
     * avant la date butoir, triées par échéance croissante (vide sans date future).
     */
    fun examEligible(cards: List<Flashcard>, examDate: Long): List<Flashcard> {
        if (examDate <= System.currentTimeMillis()) return emptyList()
        return cards.filter { !it.suspended && it.dueDate <= examDate }.sortedBy { it.dueDate }
    }

    /** Session « Examen » : la liste éligible, sans mélange (urgent d'abord). */
    fun startExamSession(cards: List<Flashcard>, limit: Int = 30) {
        val ranked = cards.sortedBy { it.dueDate }
        if (ranked.isEmpty()) return
        _lastRating.value = null
        _explanation.value = null
        _reviewQueue.value = ReviewQueue.expand(ranked.take(limit.coerceAtLeast(1)), shuffle = false)
        val session = ReviewSession(
            id = UUID.randomUUID().toString(),
            courseId = ranked.map { it.courseId }.distinct().singleOrNull(),
            startedAt = System.currentTimeMillis(),
            endedAt = null,
            cardsReviewed = 0
        )
        currentSessionId = session.id
        viewModelScope.launch {
            runCatching { reviewRepo.insertSession(session) }
        }
    }

    /**
     * Explication IA de la carte (style RemNote) : affichée puis persistée
     * sur la carte (file + base). Appels redondants ignorés.
     */
    fun explainCard(card: Flashcard, question: String, answer: String) {
        if (_explaining.value && lastExplainedCardId == card.id) return
        if (_explanation.value != null && lastExplainedCardId == card.id) return
        lastExplainedCardId = card.id
        _explanation.value = null
        viewModelScope.launch {
            _explaining.value = true
            try {
                aiRepo.explainCard(question, answer, card.sourceExcerpt).onSuccess { text ->
                    _explanation.value = text
                    runCatching { flashcardRepo.updateFlashcard(card.copy(explanation = text)) }
                    refreshQueueExplanation(card.id, text)
                }.onFailure {
                    _explanation.value = null
                }
            } finally {
                _explaining.value = false
            }
        }
    }

    fun clearExplanation() {
        _explanation.value = null
    }

    /** Met à jour l'explication d'une carte dans la file après génération IA. */
    fun refreshQueueExplanation(cardId: String, explanation: String) {
        _reviewQueue.update { queue -> queue?.map { if (it.card.id == cardId) it.copy(card = it.card.copy(explanation = explanation)) else it } }
    }

    /** Met à jour le texte d'une carte dans la file après correction. */
    fun refreshQueueCard(cardId: String, question: String, answer: String) {
        _reviewQueue.update { queue ->
            queue?.map { if (it.card.id == cardId) it.copy(card = it.card.copy(question = question, answer = answer)) else it }
        }
    }

    /** Lit une question à voix haute (bouton de l'écran de révision). */
    fun speakQuestion(text: String) {
        ttsController.speak(text, utteranceId = "question")
    }

    /** Lit une réponse à voix haute (bouton de l'écran de révision). */
    fun speakAnswer(text: String) {
        ttsController.speak(text, utteranceId = "answer")
    }

    /** +2 XP par carte notée (gamification, sans bloquer la notation). */
    private fun grantReviewXp() {
        viewModelScope.launch {
            try {
                val prefs = prefsRepo.getPreferencesSync()
                prefsRepo.updatePreferences(prefs.copy(xp = prefs.xp + 2))
            } catch (_: Exception) { }
        }
    }

    override fun onCleared() {
        ttsController.release()
        super.onCleared()
    }

    /** État minimal pour annuler exactement la dernière notation. */
    private data class LastRating(
        val previousItem: ReviewItem,
        val logId: String,
        val sessionId: String?
    )
}
