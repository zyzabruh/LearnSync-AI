package com.example.data.repository

import com.example.data.database.*
import com.example.domain.model.*
import com.example.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CourseRepositoryImpl(private val courseDao: CourseDao) : CourseRepository {
    override fun getAllCourses(): Flow<List<Course>> =
        courseDao.getAllCourses().map { list -> list.map { it.toDomain() } }

    override suspend fun getCourseById(courseId: String): Course? =
        courseDao.getCourseById(courseId)?.toDomain()

    override suspend fun insertCourse(course: Course) =
        courseDao.insertCourse(course.toEntity())

    override suspend fun deleteCourse(courseId: String) =
        courseDao.deleteCourseById(courseId)
}

class StudyMaterialRepositoryImpl(private val studyMaterialDao: StudyMaterialDao) : StudyMaterialRepository {
    override fun getMaterialsForCourse(courseId: String): Flow<List<StudyMaterial>> =
        studyMaterialDao.getMaterialsForCourse(courseId).map { list -> list.map { it.toDomain() } }

    override suspend fun insertMaterial(material: StudyMaterial) =
        studyMaterialDao.insertMaterial(material.toEntity())
}

class FlashcardRepositoryImpl(private val flashcardDao: FlashcardDao) : FlashcardRepository {
    override fun getFlashcardsForCourse(courseId: String): Flow<List<Flashcard>> =
        flashcardDao.getFlashcardsForCourse(courseId).map { list -> list.map { it.toDomain() } }

    override fun getDueFlashcards(): Flow<List<Flashcard>> =
        flashcardDao.getDueFlashcards(System.currentTimeMillis()).map { list -> list.map { it.toDomain() } }

    override fun getDueFlashcardsForCourse(courseId: String): Flow<List<Flashcard>> =
        flashcardDao.getDueFlashcardsForCourse(courseId, System.currentTimeMillis()).map { list -> list.map { it.toDomain() } }

    override fun getAllFlashcards(): Flow<List<Flashcard>> =
        flashcardDao.getAllFlashcards().map { list -> list.map { it.toDomain() } }

    override suspend fun insertFlashcard(flashcard: Flashcard) =
        flashcardDao.insertFlashcard(flashcard.toEntity())

    override suspend fun insertFlashcards(flashcards: List<Flashcard>) =
        flashcardDao.insertFlashcards(flashcards.map { it.toEntity() })

    override suspend fun updateFlashcard(flashcard: Flashcard) =
        flashcardDao.updateFlashcard(flashcard.toEntity())
}

class ReviewRepositoryImpl(private val reviewLogDao: ReviewLogDao) : ReviewRepository {
    override fun getAllReviewLogs(): Flow<List<ReviewLog>> =
        reviewLogDao.getAllReviewLogs().map { list -> list.map { it.toDomain() } }

    override fun getReviewLogsSince(startTime: Long): Flow<List<ReviewLog>> =
        reviewLogDao.getReviewLogsSince(startTime).map { list -> list.map { it.toDomain() } }

    override suspend fun logReview(log: ReviewLog) =
        reviewLogDao.insertReviewLog(log.toEntity())
}

class PreferencesRepositoryImpl(private val prefsDao: UserPreferencesDao) : PreferencesRepository {
    override fun getPreferences(): Flow<UserPreferences> =
        prefsDao.getPreferences().map { it?.toDomain() ?: UserPreferences(true, 10, "08:00", "system", "fr") }

    override suspend fun updatePreferences(preferences: UserPreferences) =
        prefsDao.insertPreferences(preferences.toEntity())
}

// Mappers
fun CourseEntity.toDomain() = Course(id, title, description, sourceFileName, sourceFileUri, extractedText, createdAt, updatedAt, progress, color, generationStatus)
fun Course.toEntity() = CourseEntity(id, title, description, sourceFileName, sourceFileUri, extractedText, createdAt, updatedAt, progress, color, generationStatus)

fun StudyMaterialEntity.toDomain() = StudyMaterial(id, courseId, summary, keyPoints.split("||"), mnemonicTips.split("||"), generatedAt, version)
fun StudyMaterial.toEntity() = StudyMaterialEntity(id, courseId, summary, keyPoints.joinToString("||"), mnemonicTips.joinToString("||"), generatedAt, version)

fun FlashcardEntity.toDomain() = Flashcard(id, courseId, question, answer, explanation, difficulty, box, dueDate, interval, easeFactor, repetitions, lapses, lastReviewedAt, createdAt)
fun Flashcard.toEntity() = FlashcardEntity(id, courseId, question, answer, explanation, difficulty, box, dueDate, interval, easeFactor, repetitions, lapses, lastReviewedAt, createdAt)

fun ReviewLogEntity.toDomain() = ReviewLog(id, flashcardId, reviewedAt, rating, previousInterval, newInterval, responseTime)
fun ReviewLog.toEntity() = ReviewLogEntity(id, flashcardId, reviewedAt, rating, previousInterval, newInterval, responseTime)

fun UserPreferencesEntity.toDomain() = UserPreferences(notificationsEnabled, dailyGoal, reminderTime, theme, language)
fun UserPreferences.toEntity() = UserPreferencesEntity(1, notificationsEnabled, dailyGoal, reminderTime, theme, language)
