package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val sourceFileName: String,
    val sourceFileUri: String,
    val extractedText: String,
    val createdAt: Long,
    val updatedAt: Long,
    val progress: Float,
    val color: String,
    val generationStatus: String // "NONE", "GENERATING", "COMPLETED", "ERROR"
)

@Entity(tableName = "study_materials")
data class StudyMaterialEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val summary: String,
    val keyPoints: String, // stored as JSON string or delimited
    val mnemonicTips: String,
    val generatedAt: Long,
    val version: Int
)

@Entity(tableName = "flashcards")
data class FlashcardEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val question: String,
    val answer: String,
    val explanation: String,
    val difficulty: Float,
    val box: Int,
    val dueDate: Long,
    val interval: Int,
    val easeFactor: Float,
    val repetitions: Int,
    val lapses: Int,
    val lastReviewedAt: Long?,
    val createdAt: Long
)

@Entity(tableName = "quiz_questions")
data class QuizQuestionEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val question: String,
    val options: String, // JSON array string
    val correctAnswer: String,
    val explanation: String,
    val difficulty: String
)

@Entity(tableName = "review_logs")
data class ReviewLogEntity(
    @PrimaryKey val id: String,
    val flashcardId: String,
    val reviewedAt: Long,
    val rating: Int, // 1: Again, 2: Hard, 3: Good, 4: Easy
    val previousInterval: Int,
    val newInterval: Int,
    val responseTime: Long
)

@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @PrimaryKey val id: Int = 1,
    val notificationsEnabled: Boolean,
    val dailyGoal: Int,
    val reminderTime: String,
    val theme: String,
    val language: String
)
