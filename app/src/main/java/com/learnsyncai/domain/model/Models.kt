package com.learnsyncai.domain.model

data class Course(
    val id: String,
    val title: String,
    val description: String,
    val sourceFileName: String,
    val sourceFileUri: String,
    val createdAt: Long,
    val updatedAt: Long,
    val progress: Float,
    val color: String,
    val generationStatus: String
)

data class StudyMaterial(
    val id: String,
    val courseId: String,
    val summary: String,
    val keyPoints: List<String>,
    val mnemonicTips: List<String>,
    val generatedAt: Long,
    val version: Int
)

data class Flashcard(
    val id: String,
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

data class QuizQuestion(
    val id: String,
    val courseId: String,
    val question: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String,
    val difficulty: String
)

data class ReviewLog(
    val id: String,
    val flashcardId: String,
    val reviewedAt: Long,
    val rating: Int,
    val previousInterval: Int,
    val newInterval: Int,
    val responseTime: Long
)

data class UserPreferences(
    val notificationsEnabled: Boolean,
    val dailyGoal: Int,
    val reminderTime: String,
    val theme: String,
    val language: String,
    val aiProvider: String = "GEMINI",
    val aiBaseUrl: String = "https://generativelanguage.googleapis.com/v1beta/openai",
    val aiApiKey: String = "",
    val aiModelName: String = "gemini-2.0-flash"
)

data class StudyGenerationResult(
    val summary: String,
    val keyPoints: List<String>,
    val flashcards: List<GeneratedFlashcard>,
    val quizQuestions: List<GeneratedQuizQuestion>,
    val mnemonicTips: List<String>
)

data class GeneratedFlashcard(
    val question: String,
    val answer: String,
    val explanation: String
)

data class GeneratedQuizQuestion(
    val question: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String
)

data class CalendarEvent(
    val id: String,
    val courseId: String,
    val title: String,
    val scheduledDate: Long,
    val androidEventId: Long?,
    val updatedAt: Long
)

data class AiProfile(
    val id: String,
    val name: String,
    val provider: String, // "GEMINI", "OPENROUTER", "NVIDIA_NIM", "OPENCODE_ZEN", "CUSTOM"
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
    val isActive: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)


