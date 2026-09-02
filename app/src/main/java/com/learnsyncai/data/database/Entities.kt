package com.learnsyncai.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val sourceFileName: String,
    val sourceFileUri: String,
    val createdAt: Long,
    val updatedAt: Long,
    val progress: Float,
    val color: String,
    val generationStatus: String, // "NONE", "GENERATING", "COMPLETED", "ERROR"
    val tag: String = "",
    val language: String = "auto"
)

@Entity(
    tableName = "study_materials",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["courseId"])]
)
data class StudyMaterialEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val summary: String,
    val keyPoints: String, // stored as JSON string or delimited
    val mnemonicTips: String,
    val generatedAt: Long,
    val version: Int
)

@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["courseId"])]
)
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

@Entity(
    tableName = "quiz_questions",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["courseId"])]
)
data class QuizQuestionEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val question: String,
    val options: String, // JSON array string
    val correctAnswer: String,
    val explanation: String,
    val difficulty: String
)

// Pas de clé étrangère vers flashcards : l'historique de révision doit
// survivre à la suppression/régénération des cartes (streak et stats).
// Le rattachement au cours via courseId reste indexé.
@Entity(
    tableName = "review_logs",
    indices = [Index(value = ["flashcardId"]), Index(value = ["courseId"])]
)
data class ReviewLogEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val flashcardId: String,
    val reviewedAt: Long,
    val rating: Int, // 1: Again, 2: Hard, 3: Good, 4: Easy
    val previousInterval: Int,
    val newInterval: Int,
    val responseTime: Long
)

// Une session de révision réelle (durée complète, cartes vues) : permet
// des stats de temps d'étude fidèles et un historique calendrier.
@Entity(
    tableName = "review_sessions",
    indices = [Index(value = ["courseId"])]
)
data class ReviewSessionEntity(
    @PrimaryKey val id: String,
    val courseId: String?, // null = session tous cours confondus
    val startedAt: Long,
    val endedAt: Long?,
    val cardsReviewed: Int
)

// Mémorialise les suppressions locales pour les propager à Firestore
// (sinon les entités supprimées reviennent en doublon à la sync suivante).
@Entity(
    tableName = "tombstones",
    primaryKeys = ["entityType", "entityId"]
)
data class TombstoneEntity(
    val entityType: String, // "COURSE", "FLASHCARD", "QUIZ_QUESTION", "STUDY_MATERIAL"
    val entityId: String,
    val deletedAt: Long
)

@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @PrimaryKey val id: Int = 1,
    val notificationsEnabled: Boolean,
    val dailyGoal: Int,
    val reminderTime: String,
    val theme: String,
    val language: String,
    val aiProvider: String = "GEMINI",
    val aiBaseUrl: String = "https://generativelanguage.googleapis.com/v1beta/openai",
    val aiApiKey: String = "",
    val aiModelName: String = "gemini-2.5-flash",
    val flashcardsMode: String = "auto",
    val flashcardsCustomCount: Int = 10,
    val quizMode: String = "auto",
    val quizCustomCount: Int = 5,
    val mnemonicTipsMode: String = "auto",
    val mnemonicTipsCustomCount: Int = 3,
    val autoTtsEnabled: Boolean = false
)

@Entity(
    tableName = "calendar_events",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["courseId"])]
)
data class CalendarEventEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val title: String,
    val scheduledDate: Long,
    val androidEventId: Long?,
    val updatedAt: Long
)

@Entity(tableName = "ai_profiles")
data class AiProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val provider: String,
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
    val isActive: Boolean,
    val createdAt: Long
)


