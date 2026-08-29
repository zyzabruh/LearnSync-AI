package com.learnsyncai.data.repository

import com.learnsyncai.data.database.*
import com.learnsyncai.domain.model.*
import com.learnsyncai.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

class CourseRepositoryImpl(
    private val courseDao: CourseDao,
    private val db: LearnSyncDatabase? = null
) : CourseRepository {
    override fun getAllCourses(): Flow<List<Course>> =
        courseDao.getAllCourses().map { list -> list.map { it.toDomain() } }

    override suspend fun getCourseById(courseId: String): Course? =
        courseDao.getCourseById(courseId)?.toDomain()

    override suspend fun insertCourse(course: Course) =
        courseDao.insertCourse(course.toEntity())

    override suspend fun insertCourses(courses: List<Course>) =
        courseDao.insertCourses(courses.map { it.toEntity() })

    override suspend fun deleteCourse(courseId: String) {
        if (db != null) {
            db.deleteCourseAtomically(courseId)
        } else {
            courseDao.deleteCourseById(courseId)
        }
    }

    override suspend fun replaceCourseContentAtomically(
        course: Course,
        material: StudyMaterial,
        flashcards: List<Flashcard>,
        quizQuestions: List<QuizQuestion>
    ) {
        if (db != null) {
            db.replaceCourseContentAtomically(
                course = course.toEntity(),
                material = material.toEntity(),
                flashcards = flashcards.map { it.toEntity() },
                quizQuestions = quizQuestions.map { it.toEntity() }
            )
        } else {
            // Fallback for isolated DAO tests
            courseDao.insertCourse(course.toEntity())
        }
    }
}

class CalendarEventRepositoryImpl(private val calendarEventDao: CalendarEventDao) : CalendarEventRepository {
    override fun getEventsForCourse(courseId: String): Flow<List<CalendarEvent>> =
        calendarEventDao.getEventsForCourse(courseId).map { list -> list.map { it.toDomain() } }

    override fun getAllCalendarEvents(): Flow<List<CalendarEvent>> =
        calendarEventDao.getAllCalendarEvents().map { list -> list.map { it.toDomain() } }

    override suspend fun insertEvent(event: CalendarEvent) =
        calendarEventDao.insertEvent(event.toEntity())

    override suspend fun insertEvents(events: List<CalendarEvent>) =
        calendarEventDao.insertEvents(events.map { it.toEntity() })

    override suspend fun deleteEvent(id: String) =
        calendarEventDao.deleteEvent(id)

    override suspend fun deleteEventsForCourse(courseId: String) =
        calendarEventDao.deleteEventsForCourse(courseId)
}


class StudyMaterialRepositoryImpl(private val studyMaterialDao: StudyMaterialDao) : StudyMaterialRepository {
    override fun getMaterialsForCourse(courseId: String): Flow<List<StudyMaterial>> =
        studyMaterialDao.getMaterialsForCourse(courseId).map { list -> list.map { it.toDomain() } }

    override fun getAllMaterials(): Flow<List<StudyMaterial>> =
        studyMaterialDao.getAllMaterials().map { list -> list.map { it.toDomain() } }

    override suspend fun getLatestVersionForCourse(courseId: String): Int =
        studyMaterialDao.getLatestVersionForCourse(courseId) ?: 0

    override suspend fun insertMaterial(material: StudyMaterial) =
        studyMaterialDao.insertMaterial(material.toEntity())

    override suspend fun insertMaterials(materials: List<StudyMaterial>) =
        studyMaterialDao.insertMaterials(materials.map { it.toEntity() })

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

    override fun getAllQuizQuestions(): Flow<List<QuizQuestion>> =
        quizQuestionDao.getAllQuizQuestions().map { list -> list.map { it.toDomain() } }

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

    override suspend fun insertReviewLogs(logs: List<ReviewLog>) =
        reviewLogDao.insertReviewLogs(logs.map { it.toEntity() })
}

class PreferencesRepositoryImpl(private val prefsDao: UserPreferencesDao) : PreferencesRepository {
    override fun getPreferences(): Flow<UserPreferences> =
        prefsDao.getPreferences().map { it?.toDomain() ?: UserPreferences(true, 10, "08:00", "system", "fr") }

    override suspend fun getPreferencesSync(): UserPreferences =
        prefsDao.getPreferencesSync()?.toDomain() ?: UserPreferences(true, 10, "08:00", "system", "fr")

    override suspend fun updatePreferences(preferences: UserPreferences) =
        prefsDao.insertPreferences(preferences.toEntity())
}

// Mappers
fun CourseEntity.toDomain() = Course(id, title, description, sourceFileName, sourceFileUri, createdAt, updatedAt, progress, color, generationStatus)
fun Course.toEntity() = CourseEntity(id, title, description, sourceFileName, sourceFileUri, createdAt, updatedAt, progress, color, generationStatus)

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
    if (options.contains("\u001F")) {
        optList.addAll(options.split("\u001F").filter { it.isNotBlank() })
    } else if (options.contains("||")) {
        optList.addAll(options.split("||").filter { it.isNotBlank() })
    } else if (options.startsWith("[") && options.endsWith("]")) {
        val trimmed = options.substring(1, options.length - 1)
        if (trimmed.isNotBlank()) {
            optList.addAll(trimmed.split(",").map { it.trim().trim('"', '\'') })
        }
    } else if (options.isNotBlank()) {
        optList.add(options)
    }
    return QuizQuestion(id, courseId, question, optList, correctAnswer, explanation, difficulty)
}

fun QuizQuestion.toEntity(): QuizQuestionEntity {
    return QuizQuestionEntity(id, courseId, question, options.joinToString("\u001F"), correctAnswer, explanation, difficulty)
}

fun ReviewLogEntity.toDomain() = ReviewLog(id, flashcardId, reviewedAt, rating, previousInterval, newInterval, responseTime)
fun ReviewLog.toEntity() = ReviewLogEntity(id, flashcardId, reviewedAt, rating, previousInterval, newInterval, responseTime)

fun UserPreferencesEntity.toDomain() = UserPreferences(notificationsEnabled, dailyGoal, reminderTime, theme, language)
fun UserPreferences.toEntity() = UserPreferencesEntity(1, notificationsEnabled, dailyGoal, reminderTime, theme, language)

fun CalendarEventEntity.toDomain() = CalendarEvent(id, courseId, title, scheduledDate, androidEventId, updatedAt)
fun CalendarEvent.toEntity() = CalendarEventEntity(id, courseId, title, scheduledDate, androidEventId, updatedAt)

