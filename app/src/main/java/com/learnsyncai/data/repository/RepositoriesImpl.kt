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
        courseDao.getAllCourses().map { list ->
            val domainList = list.map { it.toDomain() }
            android.util.Log.d("LearnSyncAI", "données émises par les Flow: type=Course, count=${domainList.size}")
            domainList
        }

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


class StudyMaterialRepositoryImpl(
    private val studyMaterialDao: StudyMaterialDao,
    private val tombstoneDao: TombstoneDao? = null
) : StudyMaterialRepository {
    override fun getMaterialsForCourse(courseId: String): Flow<List<StudyMaterial>> =
        studyMaterialDao.getMaterialsForCourse(courseId).map { list ->
            val domainList = list.map { it.toDomain() }
            android.util.Log.d("LearnSyncAI", "données émises par les Flow: type=StudyMaterial, courseId=$courseId, count=${domainList.size}")
            domainList
        }

    override fun getAllMaterials(): Flow<List<StudyMaterial>> =
        studyMaterialDao.getAllMaterials().map { list -> list.map { it.toDomain() } }

    override suspend fun getLatestVersionForCourse(courseId: String): Int =
        studyMaterialDao.getLatestVersionForCourse(courseId) ?: 0

    override suspend fun getLatestMaterialForCourse(courseId: String): StudyMaterial? =
        studyMaterialDao.getLatestMaterialForCourse(courseId)?.toDomain()

    override suspend fun insertMaterial(material: StudyMaterial) =
        studyMaterialDao.insertMaterial(material.toEntity())

    override suspend fun insertMaterials(materials: List<StudyMaterial>) =
        studyMaterialDao.insertMaterials(materials.map { it.toEntity() })

    override suspend fun deleteMaterialsForCourse(courseId: String) =
        studyMaterialDao.deleteMaterialsForCourse(courseId)

    override suspend fun deleteMaterialById(materialId: String) {
        tombstoneDao?.insertTombstone(
            TombstoneEntity(Tombstone.TYPE_STUDY_MATERIAL, materialId, System.currentTimeMillis())
        )
        studyMaterialDao.deleteMaterialById(materialId)
    }
}

class FlashcardRepositoryImpl(
    private val flashcardDao: FlashcardDao,
    private val tombstoneDao: TombstoneDao? = null
) : FlashcardRepository {
    override fun getFlashcardsForCourse(courseId: String): Flow<List<Flashcard>> =
        flashcardDao.getFlashcardsForCourse(courseId).map { list ->
            val domainList = list.map { it.toDomain() }
            android.util.Log.d("LearnSyncAI", "données émises par les Flow: type=Flashcard, courseId=$courseId, count=${domainList.size}")
            domainList
        }

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

    override suspend fun deleteFlashcard(cardId: String) {
        tombstoneDao?.insertTombstone(
            TombstoneEntity(Tombstone.TYPE_FLASHCARD, cardId, System.currentTimeMillis())
        )
        flashcardDao.deleteFlashcardById(cardId)
    }

    override suspend fun deleteFlashcardsForCourse(courseId: String) =
        flashcardDao.deleteFlashcardsForCourse(courseId)
}

class QuizRepositoryImpl(
    private val quizQuestionDao: QuizQuestionDao,
    private val tombstoneDao: TombstoneDao? = null
) : QuizRepository {
    override fun getQuizQuestionsForCourse(courseId: String): Flow<List<QuizQuestion>> =
        quizQuestionDao.getQuizQuestionsForCourse(courseId).map { list ->
            val domainList = list.map { it.toDomain() }
            android.util.Log.d("LearnSyncAI", "données émises par les Flow: type=QuizQuestion, courseId=$courseId, count=${domainList.size}")
            domainList
        }

    override fun getAllQuizQuestions(): Flow<List<QuizQuestion>> =
        quizQuestionDao.getAllQuizQuestions().map { list -> list.map { it.toDomain() } }

    override suspend fun insertQuizQuestion(question: QuizQuestion) =
        quizQuestionDao.insertQuizQuestion(question.toEntity())

    override suspend fun insertQuizQuestions(questions: List<QuizQuestion>) =
        quizQuestionDao.insertQuizQuestions(questions.map { it.toEntity() })

    override suspend fun deleteQuizQuestion(questionId: String) {
        tombstoneDao?.insertTombstone(
            TombstoneEntity(Tombstone.TYPE_QUIZ_QUESTION, questionId, System.currentTimeMillis())
        )
        quizQuestionDao.deleteQuizQuestionById(questionId)
    }

    override suspend fun deleteQuizQuestionsForCourse(courseId: String) =
        quizQuestionDao.deleteQuizQuestionsForCourse(courseId)
}

class ReviewRepositoryImpl(
    private val reviewLogDao: ReviewLogDao,
    private val reviewSessionDao: ReviewSessionDao? = null
) : ReviewRepository {
    override fun getAllReviewLogs(): Flow<List<ReviewLog>> =
        reviewLogDao.getAllReviewLogs().map { list -> list.map { it.toDomain() } }

    override fun getReviewLogsSince(startTime: Long): Flow<List<ReviewLog>> =
        reviewLogDao.getReviewLogsSince(startTime).map { list -> list.map { it.toDomain() } }

    override fun getReviewLogsForCourse(courseId: String): Flow<List<ReviewLog>> =
        reviewLogDao.getReviewLogsForCourse(courseId).map { list -> list.map { it.toDomain() } }

    override suspend fun logReview(log: ReviewLog) =
        reviewLogDao.insertReviewLog(log.toEntity())

    override suspend fun insertReviewLogs(logs: List<ReviewLog>) =
        reviewLogDao.insertReviewLogs(logs.map { it.toEntity() })

    override fun getAllSessions(): Flow<List<ReviewSession>> =
        (reviewSessionDao ?: throw IllegalStateException("reviewSessionDao requis pour les sessions"))
            .getAllSessions().map { list -> list.map { it.toDomain() } }

    override fun getSessionsSince(startTime: Long): Flow<List<ReviewSession>> =
        (reviewSessionDao ?: throw IllegalStateException("reviewSessionDao requis pour les sessions"))
            .getSessionsSince(startTime).map { list -> list.map { it.toDomain() } }

    override suspend fun insertSession(session: ReviewSession) =
        reviewSessionDao?.insertSession(session.toEntity()) ?: Unit

    override suspend fun updateSession(session: ReviewSession) =
        reviewSessionDao?.updateSession(session.toEntity()) ?: Unit
}

class PreferencesRepositoryImpl(private val prefsDao: UserPreferencesDao) : PreferencesRepository {
    override fun getPreferences(): Flow<UserPreferences> =
        prefsDao.getPreferences().map { it?.toDomain() ?: UserPreferences.DEFAULT }

    override suspend fun getPreferencesSync(): UserPreferences =
        prefsDao.getPreferencesSync()?.toDomain() ?: UserPreferences.DEFAULT

    override suspend fun updatePreferences(preferences: UserPreferences) =
        prefsDao.insertPreferences(preferences.toEntity())
}

class AiProfileRepositoryImpl(private val aiProfileDao: AiProfileDao) : AiProfileRepository {
    override fun getAllProfiles(): Flow<List<AiProfile>> =
        aiProfileDao.getAllProfiles().map { list -> list.map { it.toDomain() } }

    override suspend fun getActiveProfile(): AiProfile? =
        aiProfileDao.getActiveProfile()?.toDomain()

    override suspend fun insertProfile(profile: AiProfile) =
        aiProfileDao.insertProfile(profile.toEntity())

    override suspend fun updateProfile(profile: AiProfile) =
        aiProfileDao.updateProfile(profile.toEntity())

    override suspend fun deleteProfile(profileId: String) =
        aiProfileDao.deleteProfile(profileId)

    override suspend fun setActiveProfile(profileId: String) =
        aiProfileDao.setActiveProfile(profileId)
}

class TombstoneRepositoryImpl(private val tombstoneDao: TombstoneDao) : TombstoneRepository {
    override suspend fun record(entityType: String, entityId: String) =
        tombstoneDao.insertTombstone(TombstoneEntity(entityType, entityId, System.currentTimeMillis()))

    override suspend fun recordAll(tombstones: List<Tombstone>) =
        tombstoneDao.insertTombstones(tombstones.map { it.toEntity() })

    override suspend fun getAll(): List<Tombstone> =
        tombstoneDao.getAllTombstones().map { it.toDomain() }

    override suspend fun getIds(entityType: String): List<String> =
        tombstoneDao.getTombstonedIds(entityType)
}

// Mappers
fun CourseEntity.toDomain() = Course(id, title, description, sourceFileName, sourceFileUri, createdAt, updatedAt, progress, color, generationStatus, tag, language)
fun Course.toEntity() = CourseEntity(id, title, description, sourceFileName, sourceFileUri, createdAt, updatedAt, progress, color, generationStatus, tag, language)

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

fun ReviewLogEntity.toDomain() = ReviewLog(id, flashcardId, reviewedAt, rating, previousInterval, newInterval, responseTime, courseId)
fun ReviewLog.toEntity() = ReviewLogEntity(id, courseId, flashcardId, reviewedAt, rating, previousInterval, newInterval, responseTime)

fun ReviewSessionEntity.toDomain() = ReviewSession(id, courseId, startedAt, endedAt, cardsReviewed)
fun ReviewSession.toEntity() = ReviewSessionEntity(id, courseId, startedAt, endedAt, cardsReviewed)

fun UserPreferencesEntity.toDomain() = UserPreferences(notificationsEnabled, dailyGoal, reminderTime, theme, language, aiProvider, aiBaseUrl, aiApiKey, aiModelName, flashcardsMode, flashcardsCustomCount, quizMode, quizCustomCount, mnemonicTipsMode, mnemonicTipsCustomCount, autoTtsEnabled)
fun UserPreferences.toEntity() = UserPreferencesEntity(1, notificationsEnabled, dailyGoal, reminderTime, theme, language, aiProvider, aiBaseUrl, aiApiKey, aiModelName, flashcardsMode, flashcardsCustomCount, quizMode, quizCustomCount, mnemonicTipsMode, mnemonicTipsCustomCount, autoTtsEnabled)

fun CalendarEventEntity.toDomain() = CalendarEvent(id, courseId, title, scheduledDate, androidEventId, updatedAt)
fun CalendarEvent.toEntity() = CalendarEventEntity(id, courseId, title, scheduledDate, androidEventId, updatedAt)

fun AiProfileEntity.toDomain() = AiProfile(id, name, provider, baseUrl, apiKey, modelName, isActive, createdAt)
fun AiProfile.toEntity() = AiProfileEntity(id, name, provider, baseUrl, apiKey, modelName, isActive, createdAt)

fun TombstoneEntity.toDomain() = Tombstone(entityType, entityId, deletedAt)
fun Tombstone.toEntity() = TombstoneEntity(entityType, entityId, deletedAt)


