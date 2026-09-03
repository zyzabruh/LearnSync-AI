package com.learnsyncai.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.learnsyncai.data.sync.CalendarHelper
import com.learnsyncai.data.sync.FirestoreSyncManager
import com.learnsyncai.domain.model.Tombstone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Synchronisation bidirectionnelle avec Firestore et export vers le
 * calendrier Android.
 */
class SyncViewModel(application: Application) : AndroidViewModel(application) {
    // Câblage délégué au conteneur d'injection de l'Application.
    private val container = (application as com.learnsyncai.LearnSyncApplication).container
    private val courseRepo = container.courseRepository
    private val studyMaterialRepo = container.studyMaterialRepository
    private val flashcardRepo = container.flashcardRepository
    private val quizRepo = container.quizRepository
    private val reviewRepo = container.reviewRepository
    private val prefsRepo = container.preferencesRepository
    private val tombstoneRepo = container.tombstoneRepository
    private val firestoreSyncManager = container.firestoreSyncManager

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun getCalendarEventsForCourse(courseId: String): kotlinx.coroutines.flow.Flow<List<com.learnsyncai.domain.model.CalendarEvent>> =
        container.calendarEventRepository.getEventsForCourse(courseId)

    fun syncToCalendar() {
        viewModelScope.launch {
            try {
                // État local frais (les StateFlow UI peuvent être en retard d'une émission).
                val currentCourses = courseRepo.getAllCourses().firstOrNull() ?: emptyList()
                val allCards = flashcardRepo.getAllFlashcards().firstOrNull() ?: emptyList()
                val preferences = prefsRepo.getPreferencesSync()
                val count = CalendarHelper.syncReviewsToCalendar(
                    context = getApplication(),
                    courses = currentCourses,
                    allCards = allCards,
                    preferences = preferences,
                    calendarEventDao = container.database.calendarEventDao()
                )
                if (count > 0) {
                    _uiState.value = UiState.Success("$count sessions de révision synchronisées avec votre calendrier !")
                } else {
                    _uiState.value = UiState.Success("Votre calendrier est déjà à jour.")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Erreur lors de la synchronisation calendrier : ${e.localizedMessage}")
            }
        }
    }

    fun syncWithCloud() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading("Synchronisation complète avec Firebase...")
            try {
                // 0. Tombstones locaux : propagés vers le cloud, et filtre
                // anti-résurrection pour la sync descendante.
                var localTombstones = tombstoneRepo.getAll()

                // 1. DOWN : appliquer d'abord les suppressions distantes en local
                // (chaque suppression enregistre son tombstone, donc se repropage).
                val remoteDeleted = firestoreSyncManager.fetchRemoteDeletedIds()
                    .getOrDefault(emptyMap())
                val remoteDeletionsApplied = remoteDeleted.values.any { it.isNotEmpty() }
                for (courseId in remoteDeleted[Tombstone.TYPE_COURSE].orEmpty()) {
                    courseRepo.deleteCourse(courseId)
                }
                for (materialId in remoteDeleted[Tombstone.TYPE_STUDY_MATERIAL].orEmpty()) {
                    studyMaterialRepo.deleteMaterialById(materialId)
                }
                for (cardId in remoteDeleted[Tombstone.TYPE_FLASHCARD].orEmpty()) {
                    flashcardRepo.deleteFlashcard(cardId)
                }
                for (quizId in remoteDeleted[Tombstone.TYPE_QUIZ_QUESTION].orEmpty()) {
                    quizRepo.deleteQuizQuestion(quizId)
                }
                if (remoteDeletionsApplied) {
                    localTombstones = tombstoneRepo.getAll()
                }

                // 2. État local frais (les StateFlow UI peuvent ne pas avoir
                // encore ré-émis après les suppressions ci-dessus).
                val currentCourses = courseRepo.getAllCourses().firstOrNull() ?: emptyList()
                val currentFlashcards = flashcardRepo.getAllFlashcards().firstOrNull() ?: emptyList()
                val currentLogs = reviewRepo.getAllReviewLogs().firstOrNull() ?: emptyList()
                val currentMaterials = studyMaterialRepo.getAllMaterials().firstOrNull() ?: emptyList()
                val currentQuizQuestions = quizRepo.getAllQuizQuestions().firstOrNull() ?: emptyList()

                // 3. Fetch remote data (DOWN) — les docs marqués deletedAt sont exclus
                val remoteCoursesResult = firestoreSyncManager.fetchRemoteCourses()
                val remoteMaterialsResult = firestoreSyncManager.fetchRemoteMaterials()
                val remoteCardsResult = firestoreSyncManager.fetchRemoteFlashcards()
                val remoteQuizResult = firestoreSyncManager.fetchRemoteQuizQuestions()
                val remoteLogsResult = firestoreSyncManager.fetchRemoteReviewLogs()
                val remotePrefsResult = firestoreSyncManager.fetchRemotePreferences()

                // 4. Reconcile Courses with timestamp conflict resolution
                val localCourseMap = currentCourses.associateBy { it.id }.toMutableMap()
                for (remote in remoteCoursesResult.getOrDefault(emptyList())) {
                    val local = localCourseMap[remote.id]
                    if (local == null || remote.updatedAt > local.updatedAt) {
                        // Remote is missing locally or newer -> update local
                        courseRepo.insertCourse(remote)
                        localCourseMap[remote.id] = remote
                    }
                }

                // 5. Reconcile StudyMaterials
                val localMaterialMap = currentMaterials.associateBy { it.id }.toMutableMap()
                for (remote in remoteMaterialsResult.getOrDefault(emptyList())) {
                    val local = localMaterialMap[remote.id]
                    if (local == null || remote.generatedAt > local.generatedAt || remote.version > local.version) {
                        studyMaterialRepo.insertMaterial(remote)
                        localMaterialMap[remote.id] = remote
                    }
                }

                // 6. Reconcile Flashcards
                val localCardMap = currentFlashcards.associateBy { it.id }.toMutableMap()
                for (remote in remoteCardsResult.getOrDefault(emptyList())) {
                    val local = localCardMap[remote.id]
                    val remoteTime = remote.lastReviewedAt ?: remote.createdAt
                    val isLocalNewer = local != null && (local.lastReviewedAt ?: local.createdAt) >= remoteTime
                    if (!isLocalNewer) {
                        flashcardRepo.insertFlashcard(remote)
                        localCardMap[remote.id] = remote
                    }
                }

                // 7. Reconcile Quiz Questions
                val localQuizMap = currentQuizQuestions.associateBy { it.id }.toMutableMap()
                val newQuizQuestions = remoteQuizResult.getOrDefault(emptyList()).filter { it.id !in localQuizMap }
                if (newQuizQuestions.isNotEmpty()) {
                    quizRepo.insertQuizQuestions(newQuizQuestions)
                    localQuizMap.putAll(newQuizQuestions.associateBy { it.id })
                }

                // 8. Reconcile Review Logs — en filtrant les logs de cartes/cours
                // supprimés (sans quoi ils reviendraient à chaque sync).
                val tombstonedCardIds = localTombstones.filter { it.entityType == Tombstone.TYPE_FLASHCARD }.map { it.entityId }.toSet()
                val tombstonedCourseIds = localTombstones.filter { it.entityType == Tombstone.TYPE_COURSE }.map { it.entityId }.toSet()
                val localLogMap = currentLogs.associateBy { it.id }.toMutableMap()
                val newReviewLogs = remoteLogsResult.getOrDefault(emptyList())
                    .filter { it.id !in localLogMap }
                    .filter { it.flashcardId !in tombstonedCardIds && it.courseId !in tombstonedCourseIds }
                if (newReviewLogs.isNotEmpty()) {
                    reviewRepo.insertReviewLogs(newReviewLogs)
                    localLogMap.putAll(newReviewLogs.associateBy { it.id })
                }

                // 9. Reconcile User Preferences (keeping local AI config intact!)
                //    et mémorise le résultat fusionné : c'est LUI qu'on upload,
                //    sinon le cloud est écrasé par les anciennes valeurs.
                var mergedPrefs = prefsRepo.getPreferencesSync()
                if (remotePrefsResult.isSuccess) {
                    val remotePrefs = remotePrefsResult.getOrNull()
                    if (remotePrefs != null) {
                        mergedPrefs = mergedPrefs.copy(
                            notificationsEnabled = remotePrefs.notificationsEnabled,
                            dailyGoal = remotePrefs.dailyGoal,
                            reminderTime = remotePrefs.reminderTime,
                            theme = remotePrefs.theme,
                            language = remotePrefs.language,
                            calendarHorizonDays = remotePrefs.calendarHorizonDays,
                            calendarStartTime = remotePrefs.calendarStartTime,
                            calendarDurationMinutes = remotePrefs.calendarDurationMinutes,
                            calendarReminderMinutes = remotePrefs.calendarReminderMinutes
                        )
                        prefsRepo.updatePreferences(mergedPrefs)
                    }
                }

                // 10. Sync Up newest combined state to Firestore (UP)
                val upCourses = localCourseMap.values.toList()
                val upMaterials = localMaterialMap.values.toList()
                val upCards = localCardMap.values.toList()
                val upQuiz = localQuizMap.values.toList()
                val upLogs = localLogMap.values.toList()

                val res1 = firestoreSyncManager.syncUpCourses(upCourses)
                val res2 = firestoreSyncManager.syncUpMaterials(upMaterials)
                val res3 = firestoreSyncManager.syncUpFlashcards(upCards)
                val res4 = firestoreSyncManager.syncUpQuizQuestions(upQuiz)
                val res5 = firestoreSyncManager.syncUpReviewLogs(upLogs)
                val res6 = firestoreSyncManager.syncUpPreferences(mergedPrefs)
                val res7 = firestoreSyncManager.markDeletedInCloud(localTombstones)

                if (res1.isSuccess && res2.isSuccess && res3.isSuccess && res4.isSuccess && res5.isSuccess && res6.isSuccess && res7.isSuccess) {
                    _uiState.value = UiState.Success("Synchronisation bidirectionnelle terminée avec succès (${upCourses.size} cours, ${upCards.size} flashcards) !")
                } else {
                    val error = res1.exceptionOrNull() ?: res2.exceptionOrNull() ?: res3.exceptionOrNull() ?: res4.exceptionOrNull() ?: res5.exceptionOrNull() ?: res6.exceptionOrNull() ?: res7.exceptionOrNull()
                    _uiState.value = UiState.Error("Erreur Cloud : ${error?.localizedMessage ?: "Vérifiez votre connexion"}")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Échec de la synchronisation : ${e.localizedMessage}")
            }
        }
    }

    fun clearState() {
        _uiState.value = UiState.Idle
    }
}
