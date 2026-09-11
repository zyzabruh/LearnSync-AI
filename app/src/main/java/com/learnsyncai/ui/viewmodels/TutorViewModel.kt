package com.learnsyncai.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.learnsyncai.domain.model.Flashcard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class TutorMessage(val role: String, val text: String) {
    companion object {
        const val USER = "user"
        const val TUTOR = "tutor"
    }
}

/**
 * Tuteur IA scopé à un cours : conversation éphémère (par session d'écran),
 * contexte = texte extrait du cours, création de carte depuis une réponse.
 */
class TutorViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as com.learnsyncai.LearnSyncApplication).container
    private val aiRepo = container.aiRepository
    private val flashcardRepo = container.flashcardRepository
    private val courseContentStorage = container.courseContentStorage
    private val courseRepo = container.courseRepository

    private val _messages = MutableStateFlow<List<TutorMessage>>(emptyList())
    val messages: StateFlow<List<TutorMessage>> = _messages.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun send(courseId: String, question: String) {
        val q = question.trim()
        if (q.isEmpty() || _sending.value) return
        // Historique capturé AVANT d'ajouter la question : sinon elle serait
        // dupliquée dans le prompt (historique « Élève : » + « QUESTION DE L'ÉLÈVE »).
        val priorHistory = _messages.value
            .takeLast(6)
            .map { it.role to it.text }
        _sending.value = true
        _error.value = null
        _messages.update { it + TutorMessage(TutorMessage.USER, q) }
        viewModelScope.launch {
            try {
                val course = courseRepo.getCourseById(courseId)
                val context = courseContentStorage.readExtractedText(courseId)
                val history = priorHistory
                val answer = aiRepo.tutorAsk(
                    courseTitle = course?.title ?: "Cours",
                    courseContext = context,
                    history = history,
                    question = q,
                    language = course?.language ?: "auto"
                ).getOrThrow()
                _messages.update { it + TutorMessage(TutorMessage.TUTOR, answer) }
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Réponse impossible."
            } finally {
                _sending.value = false
            }
        }
    }

    /** Crée une flashcard basique depuis une réponse du tuteur. */
    fun createCardFromAnswer(courseId: String, question: String, answer: String, onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val card = Flashcard(
                    id = UUID.randomUUID().toString(),
                    courseId = courseId,
                    question = question.trim().take(300),
                    answer = answer.trim().take(600),
                    explanation = "",
                    difficulty = 5.0f,
                    box = 1,
                    dueDate = System.currentTimeMillis(),
                    interval = 0,
                    easeFactor = 1.0f,
                    repetitions = 0,
                    lapses = 0,
                    lastReviewedAt = null,
                    createdAt = System.currentTimeMillis(),
                    sourceExcerpt = "Tuteur IA"
                )
                flashcardRepo.insertFlashcard(card)
                onDone("Carte créée !")
            } catch (e: Exception) {
                onDone("Création impossible : ${e.localizedMessage}")
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
