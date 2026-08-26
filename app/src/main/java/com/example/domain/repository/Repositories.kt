package com.example.domain.repository

import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow

interface CourseRepository {
    fun getAllCourses(): Flow<List<Course>>
    suspend fun getCourseById(courseId: String): Course?
    suspend fun insertCourse(course: Course)
    suspend fun deleteCourse(courseId: String)
}

interface StudyMaterialRepository {
    fun getMaterialsForCourse(courseId: String): Flow<List<StudyMaterial>>
    suspend fun insertMaterial(material: StudyMaterial)
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
    suspend fun deleteFlashcardsForCourse(courseId: String)
}

interface QuizRepository {
    fun getQuizQuestionsForCourse(courseId: String): Flow<List<QuizQuestion>>
    suspend fun insertQuizQuestions(questions: List<QuizQuestion>)
    suspend fun deleteQuizQuestionsForCourse(courseId: String)
}

interface ReviewRepository {
    fun getAllReviewLogs(): Flow<List<ReviewLog>>
    fun getReviewLogsSince(startTime: Long): Flow<List<ReviewLog>>
    suspend fun logReview(log: ReviewLog)
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
    suspend fun updatePreferences(preferences: UserPreferences)
}
