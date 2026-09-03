package com.learnsyncai.data.sync

import com.learnsyncai.domain.model.Tombstone
import com.learnsyncai.domain.repository.CourseRepository
import com.learnsyncai.domain.repository.FlashcardRepository
import com.learnsyncai.domain.repository.PreferencesRepository
import com.learnsyncai.domain.repository.QuizRepository
import com.learnsyncai.domain.repository.ReviewRepository
import com.learnsyncai.domain.repository.StudyMaterialRepository
import com.learnsyncai.domain.repository.SyncStatusRepository
import com.learnsyncai.domain.repository.TombstoneRepository
import kotlinx.coroutines.flow.firstOrNull

/** Shared cloud reconciliation used by UI actions and WorkManager. */
class CloudSyncCoordinator(
    private val courseRepo: CourseRepository,
    private val studyMaterialRepo: StudyMaterialRepository,
    private val flashcardRepo: FlashcardRepository,
    private val quizRepo: QuizRepository,
    private val reviewRepo: ReviewRepository,
    private val prefsRepo: PreferencesRepository,
    private val tombstoneRepo: TombstoneRepository,
    private val syncStatusRepo: SyncStatusRepository,
    private val firestoreSyncManager: FirestoreSyncManager
) {
    suspend fun sync(): Result<String> {
        syncStatusRepo.markPending()
        return try {
            var localTombstones = tombstoneRepo.getAll()
            val remoteDeleted = firestoreSyncManager.fetchRemoteDeletedIds().getOrThrow()
            val remoteDeletionsApplied = remoteDeleted.values.any { it.isNotEmpty() }

            remoteDeleted[Tombstone.TYPE_COURSE].orEmpty().forEach { courseRepo.deleteCourse(it) }
            remoteDeleted[Tombstone.TYPE_STUDY_MATERIAL].orEmpty().forEach { studyMaterialRepo.deleteMaterialById(it) }
            remoteDeleted[Tombstone.TYPE_FLASHCARD].orEmpty().forEach { flashcardRepo.deleteFlashcard(it) }
            remoteDeleted[Tombstone.TYPE_QUIZ_QUESTION].orEmpty().forEach { quizRepo.deleteQuizQuestion(it) }
            if (remoteDeletionsApplied) localTombstones = tombstoneRepo.getAll()

            val tombstonedCourses = localTombstones
                .filter { it.entityType == Tombstone.TYPE_COURSE }
                .map { it.entityId }
                .toSet()
            val tombstonedCards = localTombstones
                .filter { it.entityType == Tombstone.TYPE_FLASHCARD }
                .map { it.entityId }
                .toSet()
            val tombstonedMaterials = localTombstones
                .filter { it.entityType == Tombstone.TYPE_STUDY_MATERIAL }
                .map { it.entityId }
                .toSet()
            val tombstonedQuiz = localTombstones
                .filter { it.entityType == Tombstone.TYPE_QUIZ_QUESTION }
                .map { it.entityId }
                .toSet()

            val localCourses = courseRepo.getAllCourses().firstOrNull().orEmpty()
            val localCards = flashcardRepo.getAllFlashcards().firstOrNull().orEmpty()
                .filterNot { it.id in tombstonedCards }
            val localLogs = reviewRepo.getAllReviewLogs().firstOrNull().orEmpty()
            val localMaterials = studyMaterialRepo.getAllMaterials().firstOrNull().orEmpty()
                .filterNot { it.id in tombstonedMaterials }
            val localQuiz = quizRepo.getAllQuizQuestions().firstOrNull().orEmpty()
                .filterNot { it.id in tombstonedQuiz }

            val remoteCourses = firestoreSyncManager.fetchRemoteCourses().getOrThrow()
            val remoteMaterials = firestoreSyncManager.fetchRemoteMaterials().getOrThrow()
            val remoteCards = firestoreSyncManager.fetchRemoteFlashcards().getOrThrow()
            val remoteQuiz = firestoreSyncManager.fetchRemoteQuizQuestions().getOrThrow()
            val remoteLogs = firestoreSyncManager.fetchRemoteReviewLogs().getOrThrow()
            val remotePrefs = firestoreSyncManager.fetchRemotePreferences().getOrThrow()

            val courses = localCourses.associateBy { it.id }.toMutableMap()
            remoteCourses.filter { it.id !in tombstonedCourses }.forEach { remote ->
                val local = courses[remote.id]
                if (local == null || remote.updatedAt > local.updatedAt) {
                    courseRepo.insertCourse(remote)
                    courses[remote.id] = remote
                }
            }

            val materials = localMaterials.associateBy { it.id }.toMutableMap()
            remoteMaterials.filter { it.id !in tombstonedMaterials && it.courseId !in tombstonedCourses }.forEach { remote ->
                val local = materials[remote.id]
                if (local == null || remote.generatedAt > local.generatedAt || remote.version > local.version) {
                    studyMaterialRepo.insertMaterial(remote)
                    materials[remote.id] = remote
                }
            }

            val cards = localCards.associateBy { it.id }.toMutableMap()
            remoteCards.filter { it.id !in tombstonedCards && it.courseId !in tombstonedCourses }.forEach { remote ->
                val local = cards[remote.id]
                val remoteTime = remote.lastReviewedAt ?: remote.createdAt
                val localTime = local?.lastReviewedAt ?: local?.createdAt
                if (local == null || localTime == null || localTime < remoteTime) {
                    flashcardRepo.insertFlashcard(remote)
                    cards[remote.id] = remote
                }
            }

            val quizzes = localQuiz.associateBy { it.id }.toMutableMap()
            remoteQuiz.filter { it.id !in quizzes && it.id !in tombstonedQuiz && it.courseId !in tombstonedCourses }.forEach { remote ->
                quizRepo.insertQuizQuestion(remote)
                quizzes[remote.id] = remote
            }

            val logs = localLogs.associateBy { it.id }.toMutableMap()
            remoteLogs
                .filter { it.id !in logs }
                .filter { it.flashcardId !in tombstonedCards && it.courseId !in tombstonedCourses }
                .forEach { remote ->
                    reviewRepo.logReview(remote)
                    logs[remote.id] = remote
                }

            var mergedPrefs = prefsRepo.getPreferencesSync()
            remotePrefs?.let { remote ->
                mergedPrefs = mergedPrefs.copy(
                    notificationsEnabled = remote.notificationsEnabled,
                    dailyGoal = remote.dailyGoal,
                    reminderTime = remote.reminderTime,
                    theme = remote.theme,
                    language = remote.language,
                    calendarHorizonDays = remote.calendarHorizonDays,
                    calendarStartTime = remote.calendarStartTime,
                    calendarDurationMinutes = remote.calendarDurationMinutes,
                    calendarReminderMinutes = remote.calendarReminderMinutes,
                    periodicSyncEnabled = remote.periodicSyncEnabled
                )
                prefsRepo.updatePreferences(mergedPrefs)
            }

            firestoreSyncManager.syncUpCourses(courses.values.toList()).getOrThrow()
            firestoreSyncManager.syncUpMaterials(materials.values.toList()).getOrThrow()
            firestoreSyncManager.syncUpFlashcards(cards.values.toList()).getOrThrow()
            firestoreSyncManager.syncUpQuizQuestions(quizzes.values.toList()).getOrThrow()
            firestoreSyncManager.syncUpReviewLogs(logs.values.toList()).getOrThrow()
            firestoreSyncManager.syncUpPreferences(mergedPrefs).getOrThrow()
            firestoreSyncManager.markDeletedInCloud(localTombstones).getOrThrow()

            val message = "Synchronisation terminée (${courses.size} cours, ${cards.size} flashcards)."
            syncStatusRepo.markSuccess()
            Result.success(message)
        } catch (error: Exception) {
            syncStatusRepo.markFailure(error.localizedMessage ?: "Erreur de synchronisation")
            Result.failure(error)
        }
    }
}
