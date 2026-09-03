package com.learnsyncai.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.learnsyncai.data.sync.CalendarHelper
import com.learnsyncai.domain.model.CalendarEvent
import com.learnsyncai.domain.model.SyncStatus
import com.learnsyncai.domain.repository.SyncStatusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

/** Synchronisation Firestore et export des échéances vers le calendrier Android. */
class SyncViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as com.learnsyncai.LearnSyncApplication).container
    private val courseRepo = container.courseRepository
    private val flashcardRepo = container.flashcardRepository
    private val prefsRepo = container.preferencesRepository
    private val syncStatusRepo: SyncStatusRepository = container.syncStatusRepository

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val syncStatus: StateFlow<SyncStatus> = syncStatusRepo.getStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus())

    fun getCalendarEventsForCourse(courseId: String): kotlinx.coroutines.flow.Flow<List<CalendarEvent>> =
        container.calendarEventRepository.getEventsForCourse(courseId)

    fun syncToCalendar() {
        viewModelScope.launch {
            try {
                val currentCourses = courseRepo.getAllCourses().firstOrNull().orEmpty()
                val allCards = flashcardRepo.getAllFlashcards().firstOrNull().orEmpty()
                val preferences = prefsRepo.getPreferencesSync()
                val count = CalendarHelper.syncReviewsToCalendar(
                    context = getApplication(),
                    courses = currentCourses,
                    allCards = allCards,
                    preferences = preferences,
                    calendarEventDao = container.database.calendarEventDao()
                )
                _uiState.value = if (count > 0) {
                    UiState.Success("$count sessions de révision synchronisées avec votre calendrier !")
                } else {
                    UiState.Success("Votre calendrier est déjà à jour.")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Erreur lors de la synchronisation calendrier : ${e.localizedMessage}")
            }
        }
    }

    fun syncWithCloud() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading("Synchronisation complète avec Firebase...")
            val result = container.cloudSyncCoordinator.sync()
            _uiState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error("Échec de la synchronisation : ${it.localizedMessage ?: "Erreur inconnue"}") }
            )
        }
    }

    fun clearState() {
        _uiState.value = UiState.Idle
    }
}
