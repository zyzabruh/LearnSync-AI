package com.learnsyncai.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY updatedAt DESC")
    fun getAllCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE id = :courseId")
    suspend fun getCourseById(courseId: String): CourseEntity?

    // @Upsert (UPDATE si la ligne existe) : contrairement à REPLACE, il ne supprime
    // pas la ligne existante, donc ne déclenche pas le CASCADE des contenus liés.
    @Upsert
    suspend fun insertCourse(course: CourseEntity)

    @Upsert
    suspend fun insertCourses(courses: List<CourseEntity>)

    @Delete
    suspend fun deleteCourse(course: CourseEntity)

    @Query("DELETE FROM courses WHERE id = :courseId")
    suspend fun deleteCourseById(courseId: String)
}

@Dao
interface StudyMaterialDao {
    @Query("SELECT * FROM study_materials WHERE courseId = :courseId ORDER BY version DESC")
    fun getMaterialsForCourse(courseId: String): Flow<List<StudyMaterialEntity>>

    @Query("SELECT id FROM study_materials WHERE courseId = :courseId")
    suspend fun getMaterialIdsForCourse(courseId: String): List<String>

    @Query("SELECT * FROM study_materials ORDER BY generatedAt DESC")
    fun getAllMaterials(): Flow<List<StudyMaterialEntity>>

    @Query("SELECT MAX(version) FROM study_materials WHERE courseId = :courseId")
    suspend fun getLatestVersionForCourse(courseId: String): Int?

    @Query("SELECT * FROM study_materials WHERE courseId = :courseId ORDER BY version DESC LIMIT 1")
    suspend fun getLatestMaterialForCourse(courseId: String): StudyMaterialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterial(material: StudyMaterialEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterials(materials: List<StudyMaterialEntity>)

    @Query("DELETE FROM study_materials WHERE courseId = :courseId")
    suspend fun deleteMaterialsForCourse(courseId: String)

    @Query("DELETE FROM study_materials WHERE id = :materialId")
    suspend fun deleteMaterialById(materialId: String)
}

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards WHERE courseId = :courseId")
    fun getFlashcardsForCourse(courseId: String): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE courseId = :courseId")
    suspend fun getFlashcardsForCourseSync(courseId: String): List<FlashcardEntity>

    @Query("SELECT * FROM flashcards WHERE dueDate <= :currentTime")
    fun getDueFlashcards(currentTime: Long): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE courseId = :courseId AND dueDate <= :currentTime")
    fun getDueFlashcardsForCourse(courseId: String, currentTime: Long): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards")
    fun getAllFlashcards(): Flow<List<FlashcardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcard(flashcard: FlashcardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcards(flashcards: List<FlashcardEntity>)

    @Update
    suspend fun updateFlashcard(flashcard: FlashcardEntity)

    @Query("DELETE FROM flashcards WHERE id = :id")
    suspend fun deleteFlashcardById(id: String)

    @Query("DELETE FROM flashcards WHERE courseId = :courseId")
    suspend fun deleteFlashcardsForCourse(courseId: String)
}

@Dao
interface QuizQuestionDao {
    @Query("SELECT * FROM quiz_questions WHERE courseId = :courseId")
    fun getQuizQuestionsForCourse(courseId: String): Flow<List<QuizQuestionEntity>>

    @Query("SELECT id FROM quiz_questions WHERE courseId = :courseId")
    suspend fun getQuizQuestionIdsForCourse(courseId: String): List<String>

    @Query("SELECT * FROM quiz_questions")
    fun getAllQuizQuestions(): Flow<List<QuizQuestionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizQuestion(question: QuizQuestionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizQuestions(questions: List<QuizQuestionEntity>)

    @Query("DELETE FROM quiz_questions WHERE id = :id")
    suspend fun deleteQuizQuestionById(id: String)

    @Query("DELETE FROM quiz_questions WHERE courseId = :courseId")
    suspend fun deleteQuizQuestionsForCourse(courseId: String)
}

@Dao
interface ReviewLogDao {
    @Query("SELECT * FROM review_logs ORDER BY reviewedAt DESC")
    fun getAllReviewLogs(): Flow<List<ReviewLogEntity>>

    @Query("SELECT * FROM review_logs WHERE reviewedAt >= :startTime")
    fun getReviewLogsSince(startTime: Long): Flow<List<ReviewLogEntity>>

    @Query("SELECT * FROM review_logs WHERE courseId = :courseId ORDER BY reviewedAt DESC")
    fun getReviewLogsForCourse(courseId: String): Flow<List<ReviewLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviewLog(log: ReviewLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviewLogs(logs: List<ReviewLogEntity>)
}

@Dao
interface ReviewSessionDao {
    @Query("SELECT * FROM review_sessions ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<ReviewSessionEntity>>

    @Query("SELECT * FROM review_sessions WHERE startedAt >= :startTime ORDER BY startedAt DESC")
    fun getSessionsSince(startTime: Long): Flow<List<ReviewSessionEntity>>

    @Query("SELECT * FROM review_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: String): ReviewSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ReviewSessionEntity)

    @Update
    suspend fun updateSession(session: ReviewSessionEntity)

    @Query("UPDATE review_sessions SET cardsReviewed = cardsReviewed + 1 WHERE id = :sessionId")
    suspend fun incrementCardsReviewed(sessionId: String)
}

@Dao
interface TombstoneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(tombstone: TombstoneEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstones(tombstones: List<TombstoneEntity>)

    @Query("SELECT * FROM tombstones")
    suspend fun getAllTombstones(): List<TombstoneEntity>

    @Query("SELECT entityId FROM tombstones WHERE entityType = :entityType")
    suspend fun getTombstonedIds(entityType: String): List<String>
}

@Dao
interface UserPreferencesDao {
    @Query("SELECT * FROM user_preferences WHERE id = 1")
    fun getPreferences(): Flow<UserPreferencesEntity?>

    @Query("SELECT * FROM user_preferences WHERE id = 1")
    suspend fun getPreferencesSync(): UserPreferencesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreferences(preferences: UserPreferencesEntity)
}

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events WHERE courseId = :courseId ORDER BY scheduledDate ASC")
    fun getEventsForCourse(courseId: String): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE courseId = :courseId ORDER BY scheduledDate ASC")
    suspend fun getEventsForCourseOnce(courseId: String): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events ORDER BY scheduledDate ASC")
    fun getAllCalendarEvents(): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events")
    suspend fun getAllCalendarEventsOnce(): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events WHERE courseId = :courseId AND scheduledDate = :scheduledDate LIMIT 1")
    suspend fun getEventForCourseAndDate(courseId: String, scheduledDate: Long): CalendarEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CalendarEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<CalendarEventEntity>)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteEvent(id: String)

    @Query("DELETE FROM calendar_events WHERE courseId = :courseId")
    suspend fun deleteEventsForCourse(courseId: String)
}

@Dao
interface SyncStatusDao {
    @Query("SELECT * FROM sync_status WHERE id = 1")
    fun getStatus(): Flow<SyncStatusEntity?>

    @Query("SELECT * FROM sync_status WHERE id = 1")
    suspend fun getStatusSync(): SyncStatusEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(status: SyncStatusEntity)
}

@Dao
interface AiProfileDao {
    @Query("SELECT * FROM ai_profiles ORDER BY createdAt ASC")
    fun getAllProfiles(): Flow<List<AiProfileEntity>>

    @Query("SELECT * FROM ai_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfile(): AiProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: AiProfileEntity)

    @Update
    suspend fun updateProfile(profile: AiProfileEntity)

    @Query("DELETE FROM ai_profiles WHERE id = :profileId")
    suspend fun deleteProfile(profileId: String)

    @Query("UPDATE ai_profiles SET isActive = CASE WHEN id = :profileId THEN 1 ELSE 0 END")
    suspend fun setActiveProfile(profileId: String)
}


