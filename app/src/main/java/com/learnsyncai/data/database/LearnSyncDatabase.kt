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
        CalendarEventEntity::class,
        AiProfileEntity::class
    ],
    version = 10,
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
    abstract fun aiProfileDao(): AiProfileDao

    @Transaction
    open suspend fun replaceCourseContentAtomically(
        course: CourseEntity,
        material: StudyMaterialEntity,
        flashcards: List<FlashcardEntity>,
        quizQuestions: List<QuizQuestionEntity>
    ) {
        // Le cours d'abord (upsert, sans cascade) : les contenus insérés ensuite
        // satisfont leur clé étrangère vers la ligne déjà à jour.
        courseDao().insertCourse(course)

        studyMaterialDao().deleteMaterialsForCourse(course.id)
        flashcardDao().deleteFlashcardsForCourse(course.id)
        quizQuestionDao().deleteQuizQuestionsForCourse(course.id)

        studyMaterialDao().insertMaterial(material)
        flashcardDao().insertFlashcards(flashcards)
        quizQuestionDao().insertQuizQuestions(quizQuestions)

        android.util.Log.d("LearnSyncAI", "commit Room: courseId=${course.id}")
    }

    @Transaction
    open suspend fun deleteCourseAtomically(courseId: String) {
        studyMaterialDao().deleteMaterialsForCourse(courseId)
        flashcardDao().deleteFlashcardsForCourse(courseId)
        quizQuestionDao().deleteQuizQuestionsForCourse(courseId)
        calendarEventDao().deleteEventsForCourse(courseId)
        courseDao().deleteCourseById(courseId)
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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    val cursor = db.query("SELECT id, extractedText FROM courses")
                    val filesDir = java.io.File("/data/data/com.learnsyncai/files/courses")
                    filesDir.mkdirs()
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        val text = cursor.getString(1)
                        if (!text.isNullOrEmpty()) {
                            val sanitizedId = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                            val file = java.io.File(filesDir, "$sanitizedId.txt")
                            file.writeText(text, Charsets.UTF_8)
                        }
                    }
                    cursor.close()
                } catch (_: Exception) {}

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `courses_new` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `sourceFileName` TEXT NOT NULL,
                        `sourceFileUri` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `progress` REAL NOT NULL,
                        `color` TEXT NOT NULL,
                        `generationStatus` TEXT NOT NULL
                    )
                """)
                db.execSQL("""
                    INSERT INTO courses_new (id, title, description, sourceFileName, sourceFileUri, createdAt, updatedAt, progress, color, generationStatus)
                    SELECT id, title, description, sourceFileName, sourceFileUri, createdAt, updatedAt, progress, color, generationStatus FROM courses
                """)
                db.execSQL("DROP TABLE courses")
                db.execSQL("ALTER TABLE courses_new RENAME TO courses")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_preferences ADD COLUMN aiProvider TEXT NOT NULL DEFAULT 'GEMINI'")
                db.execSQL("ALTER TABLE user_preferences ADD COLUMN aiBaseUrl TEXT NOT NULL DEFAULT 'https://generativelanguage.googleapis.com/v1beta/openai'")
                db.execSQL("ALTER TABLE user_preferences ADD COLUMN aiApiKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE user_preferences ADD COLUMN aiModelName TEXT NOT NULL DEFAULT 'gemini-2.0-flash'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ai_profiles` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `provider` TEXT NOT NULL,
                        `baseUrl` TEXT NOT NULL,
                        `apiKey` TEXT NOT NULL,
                        `modelName` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_preferences ADD COLUMN flashcardsMode TEXT NOT NULL DEFAULT 'auto'")
                db.execSQL("ALTER TABLE user_preferences ADD COLUMN flashcardsCustomCount INTEGER NOT NULL DEFAULT 10")
                db.execSQL("ALTER TABLE user_preferences ADD COLUMN quizMode TEXT NOT NULL DEFAULT 'auto'")
                db.execSQL("ALTER TABLE user_preferences ADD COLUMN quizCustomCount INTEGER NOT NULL DEFAULT 5")
                db.execSQL("ALTER TABLE user_preferences ADD COLUMN mnemonicTipsMode TEXT NOT NULL DEFAULT 'auto'")
                db.execSQL("ALTER TABLE user_preferences ADD COLUMN mnemonicTipsCustomCount INTEGER NOT NULL DEFAULT 3")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN tag TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_preferences ADD COLUMN autoTtsEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN language TEXT NOT NULL DEFAULT 'auto'")
            }
        }

        fun getDatabase(context: Context): LearnSyncDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LearnSyncDatabase::class.java,
                    "learn_sync_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

