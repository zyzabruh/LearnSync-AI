package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY updatedAt DESC")
    fun getAllCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE id = :courseId")
    suspend fun getCourseById(courseId: String): CourseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CourseEntity)

    @Delete
    suspend fun deleteCourse(course: CourseEntity)

    @Query("DELETE FROM courses WHERE id = :courseId")
    suspend fun deleteCourseById(courseId: String)
}

@Dao
interface StudyMaterialDao {
    @Query("SELECT * FROM study_materials WHERE courseId = :courseId ORDER BY version DESC")
    fun getMaterialsForCourse(courseId: String): Flow<List<StudyMaterialEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterial(material: StudyMaterialEntity)

    @Query("DELETE FROM study_materials WHERE courseId = :courseId")
    suspend fun deleteMaterialsForCourse(courseId: String)
}

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards WHERE courseId = :courseId")
    fun getFlashcardsForCourse(courseId: String): Flow<List<FlashcardEntity>>

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

    @Query("DELETE FROM flashcards WHERE courseId = :courseId")
    suspend fun deleteFlashcardsForCourse(courseId: String)
}

@Dao
interface QuizQuestionDao {
    @Query("SELECT * FROM quiz_questions WHERE courseId = :courseId")
    fun getQuizQuestionsForCourse(courseId: String): Flow<List<QuizQuestionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizQuestions(questions: List<QuizQuestionEntity>)

    @Query("DELETE FROM quiz_questions WHERE courseId = :courseId")
    suspend fun deleteQuizQuestionsForCourse(courseId: String)
}

@Dao
interface ReviewLogDao {
    @Query("SELECT * FROM review_logs ORDER BY reviewedAt DESC")
    fun getAllReviewLogs(): Flow<List<ReviewLogEntity>>

    @Query("SELECT * FROM review_logs WHERE reviewedAt >= :startTime")
    fun getReviewLogsSince(startTime: Long): Flow<List<ReviewLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviewLog(log: ReviewLogEntity)
}

@Dao
interface UserPreferencesDao {
    @Query("SELECT * FROM user_preferences WHERE id = 1")
    fun getPreferences(): Flow<UserPreferencesEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreferences(preferences: UserPreferencesEntity)
}
