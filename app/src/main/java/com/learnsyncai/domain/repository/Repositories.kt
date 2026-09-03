package com.learnsyncai.domain.repository

import com.learnsyncai.domain.model.*
import kotlinx.coroutines.flow.Flow

interface CourseRepository {
    fun getAllCourses(): Flow<List<Course>>
    suspend fun getCourseById(courseId: String): Course?
    suspend fun insertCourse(course: Course)
    suspend fun insertCourses(courses: List<Course>)
    suspend fun deleteCourse(courseId: String)
    suspend fun replaceCourseContentAtomically(
        course: Course,
        material: StudyMaterial,
        flashcards: List<Flashcard>,
        quizQuestions: List<QuizQuestion>
    )
}

interface CalendarEventRepository {
    fun getEventsForCourse(courseId: String): Flow<List<CalendarEvent>>
    fun getAllCalendarEvents(): Flow<List<CalendarEvent>>
    suspend fun insertEvent(event: CalendarEvent)
    suspend fun insertEvents(events: List<CalendarEvent>)
    suspend fun deleteEvent(id: String)
    suspend fun deleteEventsForCourse(courseId: String)
}


interface StudyMaterialRepository {
    fun getMaterialsForCourse(courseId: String): Flow<List<StudyMaterial>>
    fun getAllMaterials(): Flow<List<StudyMaterial>>
    suspend fun getLatestVersionForCourse(courseId: String): Int
    suspend fun getLatestMaterialForCourse(courseId: String): StudyMaterial?
    suspend fun insertMaterial(material: StudyMaterial)
    suspend fun insertMaterials(materials: List<StudyMaterial>)
    suspend fun deleteMaterialsForCourse(courseId: String)
    suspend fun deleteMaterialById(materialId: String)
}

interface FlashcardRepository {
    fun getFlashcardsForCourse(courseId: String): Flow<List<Flashcard>>
    fun getDueFlashcards(): Flow<List<Flashcard>>
    fun getDueFlashcardsForCourse(courseId: String): Flow<List<Flashcard>>
    fun getAllFlashcards(): Flow<List<Flashcard>>
    suspend fun insertFlashcard(flashcard: Flashcard)
    suspend fun insertFlashcards(flashcards: List<Flashcard>)
    suspend fun updateFlashcard(flashcard: Flashcard)
    suspend fun deleteFlashcard(cardId: String)
    suspend fun deleteFlashcardsForCourse(courseId: String)
}

interface QuizRepository {
    fun getQuizQuestionsForCourse(courseId: String): Flow<List<QuizQuestion>>
    fun getAllQuizQuestions(): Flow<List<QuizQuestion>>
    suspend fun insertQuizQuestion(question: QuizQuestion)
    suspend fun insertQuizQuestions(questions: List<QuizQuestion>)
    suspend fun deleteQuizQuestion(questionId: String)
    suspend fun deleteQuizQuestionsForCourse(courseId: String)
}

interface ReviewRepository {
    fun getAllReviewLogs(): Flow<List<ReviewLog>>
    fun getReviewLogsSince(startTime: Long): Flow<List<ReviewLog>>
    fun getReviewLogsForCourse(courseId: String): Flow<List<ReviewLog>>
    suspend fun logReview(log: ReviewLog)
    suspend fun insertReviewLogs(logs: List<ReviewLog>)

    fun getAllSessions(): Flow<List<ReviewSession>>
    fun getSessionsSince(startTime: Long): Flow<List<ReviewSession>>
    suspend fun insertSession(session: ReviewSession)
    suspend fun updateSession(session: ReviewSession)
    suspend fun endSession(sessionId: String, endedAt: Long)

    /**
     * Notation atomique : mise à jour FSRS de la carte + insertion du log
     * (+ compteur de session) dans une seule transaction Room.
     */
    suspend fun rateCardAtomically(updatedCard: Flashcard, log: ReviewLog, sessionId: String?)
}

interface AiRepository {
    suspend fun generateStudyMaterial(
        courseTitle: String,
        courseText: String,
        language: String = "auto",
        onProgress: (String) -> Unit = {}
    ): Result<StudyGenerationResult>

    /**
     * Génère du contenu SUPPLÉMENTAIRE (flashcards + QCM) sans supprimer
     * l'existant : les questions déjà présentes sont passées en liste
     * d'exclusion pour éviter les doublons.
     */
    suspend fun generateAdditionalPractice(
        courseTitle: String,
        courseText: String,
        existingFlashcardQuestions: List<String>,
        existingQuizQuestions: List<String>,
        language: String = "auto",
        onProgress: (String) -> Unit = {}
    ): Result<Pair<List<GeneratedFlashcard>, List<GeneratedQuizQuestion>>>
}

interface PreferencesRepository {
    fun getPreferences(): Flow<UserPreferences>
    suspend fun getPreferencesSync(): UserPreferences
    suspend fun updatePreferences(preferences: UserPreferences)
}

interface AiProfileRepository {
    fun getAllProfiles(): Flow<List<AiProfile>>
    suspend fun getActiveProfile(): AiProfile?
    suspend fun insertProfile(profile: AiProfile)
    suspend fun updateProfile(profile: AiProfile)
    suspend fun deleteProfile(profileId: String)
    suspend fun setActiveProfile(profileId: String)
}

interface TombstoneRepository {
    suspend fun record(entityType: String, entityId: String)
    suspend fun recordAll(tombstones: List<Tombstone>)
    suspend fun getAll(): List<Tombstone>
    suspend fun getIds(entityType: String): List<String>
}

