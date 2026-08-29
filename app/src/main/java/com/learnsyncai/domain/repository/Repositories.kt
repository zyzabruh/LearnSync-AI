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
    suspend fun logReview(log: ReviewLog)
    suspend fun insertReviewLogs(logs: List<ReviewLog>)
}

interface AiRepository {
    suspend fun generateStudyMaterial(
        courseTitle: String,
        courseText: String,
        onProgress: (String) -> Unit = {}
    ): Result<StudyGenerationResult>
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

