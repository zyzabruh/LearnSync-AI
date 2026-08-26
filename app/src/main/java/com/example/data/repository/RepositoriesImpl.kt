package com.example.data.repository

import com.example.data.database.*
import com.example.domain.model.*
import com.example.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

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

    override suspend fun deleteMaterialsForCourse(courseId: String) =
        studyMaterialDao.deleteMaterialsForCourse(courseId)
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

    override suspend fun deleteFlashcardsForCourse(courseId: String) =
        flashcardDao.deleteFlashcardsForCourse(courseId)
}

class QuizRepositoryImpl(private val quizQuestionDao: QuizQuestionDao) : QuizRepository {
    override fun getQuizQuestionsForCourse(courseId: String): Flow<List<QuizQuestion>> =
        quizQuestionDao.getQuizQuestionsForCourse(courseId).map { list -> list.map { it.toDomain() } }

    override suspend fun insertQuizQuestions(questions: List<QuizQuestion>) =
        quizQuestionDao.insertQuizQuestions(questions.map { it.toEntity() })

    override suspend fun deleteQuizQuestionsForCourse(courseId: String) =
        quizQuestionDao.deleteQuizQuestionsForCourse(courseId)
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

fun StudyMaterialEntity.toDomain() = StudyMaterial(
    id = id,
    courseId = courseId,
    summary = summary,
    keyPoints = if (keyPoints.isBlank()) emptyList() else keyPoints.split("||").filter { it.isNotBlank() },
    mnemonicTips = if (mnemonicTips.isBlank()) emptyList() else mnemonicTips.split("||").filter { it.isNotBlank() },
    generatedAt = generatedAt,
    version = version
)
fun StudyMaterial.toEntity() = StudyMaterialEntity(
    id = id,
    courseId = courseId,
    summary = summary,
    keyPoints = keyPoints.joinToString("||"),
    mnemonicTips = mnemonicTips.joinToString("||"),
    generatedAt = generatedAt,
    version = version
)

fun FlashcardEntity.toDomain() = Flashcard(id, courseId, question, answer, explanation, difficulty, box, dueDate, interval, easeFactor, repetitions, lapses, lastReviewedAt, createdAt)
fun Flashcard.toEntity() = FlashcardEntity(id, courseId, question, answer, explanation, difficulty, box, dueDate, interval, easeFactor, repetitions, lapses, lastReviewedAt, createdAt)

fun QuizQuestionEntity.toDomain(): QuizQuestion {
    val optList = mutableListOf<String>()
    try {
        val jsonArray = JSONArray(options)
        for (i in 0 until jsonArray.length()) {
            optList.add(jsonArray.getString(i))
        }
    } catch (_: Exception) {
        optList.addAll(options.split("||").filter { it.isNotBlank() })
    }
    return QuizQuestion(id, courseId, question, optList, correctAnswer, explanation, difficulty)
}

fun QuizQuestion.toEntity(): QuizQuestionEntity {
    val jsonArray = JSONArray()
    options.forEach { jsonArray.put(it) }
    return QuizQuestionEntity(id, courseId, question, jsonArray.toString(), correctAnswer, explanation, difficulty)
}

fun ReviewLogEntity.toDomain() = ReviewLog(id, flashcardId, reviewedAt, rating, previousInterval, newInterval, responseTime)
fun ReviewLog.toEntity() = ReviewLogEntity(id, flashcardId, reviewedAt, rating, previousInterval, newInterval, responseTime)

fun UserPreferencesEntity.toDomain() = UserPreferences(notificationsEnabled, dailyGoal, reminderTime, theme, language)
fun UserPreferences.toEntity() = UserPreferencesEntity(1, notificationsEnabled, dailyGoal, reminderTime, theme, language)
