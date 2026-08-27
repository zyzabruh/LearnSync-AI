package com.learnsyncai.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CourseEntity::class,
        StudyMaterialEntity::class,
        FlashcardEntity::class,
        QuizQuestionEntity::class,
        ReviewLogEntity::class,
        UserPreferencesEntity::class,
        CalendarEventEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class LearnSyncDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun studyMaterialDao(): StudyMaterialDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun quizQuestionDao(): QuizQuestionDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun userPreferencesDao(): UserPreferencesDao
    abstract fun calendarEventDao(): CalendarEventDao

    @Transaction
    open suspend fun replaceCourseContentAtomically(
        course: CourseEntity,
        material: StudyMaterialEntity,
        flashcards: List<FlashcardEntity>,
        quizQuestions: List<QuizQuestionEntity>
    ) {
        studyMaterialDao().deleteMaterialsForCourse(course.id)
        flashcardDao().deleteFlashcardsForCourse(course.id)
        quizQuestionDao().deleteQuizQuestionsForCourse(course.id)

        studyMaterialDao().insertMaterial(material)
        flashcardDao().insertFlashcards(flashcards)
        quizQuestionDao().insertQuizQuestions(quizQuestions)
        courseDao().insertCourse(course)
    }

    companion object {
        @Volatile
        private var INSTANCE: LearnSyncDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN generationStatus TEXT NOT NULL DEFAULT 'NONE'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `calendar_events` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `courseId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `scheduledDate` INTEGER NOT NULL,
                        `androidEventId` INTEGER,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_calendar_events_courseId` ON `calendar_events` (`courseId`)")
            }
        }

        fun getDatabase(context: Context): LearnSyncDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LearnSyncDatabase::class.java,
                    "learn_sync_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

