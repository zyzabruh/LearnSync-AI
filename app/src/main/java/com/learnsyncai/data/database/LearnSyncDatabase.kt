package com.learnsyncai.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.learnsyncai.domain.model.Tombstone

@Database(
    entities = [
        CourseEntity::class,
        StudyMaterialEntity::class,
        FlashcardEntity::class,
        QuizQuestionEntity::class,
        ReviewLogEntity::class,
        ReviewSessionEntity::class,
        UserPreferencesEntity::class,
        CalendarEventEntity::class,
        AiProfileEntity::class,
        TombstoneEntity::class
    ],
    version = 11,
    exportSchema = true
)
abstract class LearnSyncDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun studyMaterialDao(): StudyMaterialDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun quizQuestionDao(): QuizQuestionDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun reviewSessionDao(): ReviewSessionDao
    abstract fun userPreferencesDao(): UserPreferencesDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun aiProfileDao(): AiProfileDao
    abstract fun tombstoneDao(): TombstoneDao

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

        // Report de l'état FSRS : une carte régénérée posant la même question
        // (casse/espacements ignorés) reprend la stabilité, la difficulté et
        // l'échéance de l'ancienne, au lieu de repartir de zéro. L'historique
        // (review_logs) survit de toute façon : plus de FK CASCADE.
        val previousByQuestion = flashcardDao()
            .getFlashcardsForCourseSync(course.id)
            .associateBy { normalizeQuestion(it.question) }
        val withCarriedState = flashcards.map { card ->
            val previous = previousByQuestion[normalizeQuestion(card.question)]
            if (previous != null && previous.repetitions > 0) card.copy(
                difficulty = previous.difficulty,
                box = previous.box,
                dueDate = previous.dueDate,
                interval = previous.interval,
                easeFactor = previous.easeFactor,
                repetitions = previous.repetitions,
                lapses = previous.lapses,
                lastReviewedAt = previous.lastReviewedAt
            ) else card
        }

        // Les entités remplacées partent en tombstone : sans cela, la sync
        // réinsérerait l'ancien contenu depuis Firestore (doublons).
        val now = System.currentTimeMillis()
        tombstoneDao().insertTombstones(
            studyMaterialDao().getMaterialIdsForCourse(course.id).map {
                TombstoneEntity(Tombstone.TYPE_STUDY_MATERIAL, it, now)
            } + flashcardDao().getFlashcardsForCourseSync(course.id).map {
                TombstoneEntity(Tombstone.TYPE_FLASHCARD, it.id, now)
            } + quizQuestionDao().getQuizQuestionIdsForCourse(course.id).map {
                TombstoneEntity(Tombstone.TYPE_QUIZ_QUESTION, it, now)
            }
        )

        studyMaterialDao().deleteMaterialsForCourse(course.id)
        flashcardDao().deleteFlashcardsForCourse(course.id)
        quizQuestionDao().deleteQuizQuestionsForCourse(course.id)

        studyMaterialDao().insertMaterial(material)
        flashcardDao().insertFlashcards(withCarriedState)
        quizQuestionDao().insertQuizQuestions(quizQuestions)

        android.util.Log.d("LearnSyncAI", "commit Room: courseId=${course.id}")
    }

    /**
     * Notation d'une carte en une seule transaction : l'état FSRS, le log de
     * révision et le compteur de session sont écrits ensemble — un crash
     * entre deux ne peut plus laisser un état incohérent.
     */
    @Transaction
    open suspend fun rateCardAtomically(
        updatedCard: FlashcardEntity,
        log: ReviewLogEntity,
        sessionId: String?
    ) {
        flashcardDao().updateFlashcard(updatedCard)
        reviewLogDao().insertReviewLog(log)
        if (sessionId != null) {
            reviewSessionDao().incrementCardsReviewed(sessionId)
        }
    }

    @Transaction
    open suspend fun deleteCourseAtomically(courseId: String) {
        // Tombstones pour le cours et tout son contenu : la sync descendante
        // et les autres appareils doivent apprendre la suppression.
        val now = System.currentTimeMillis()
        tombstoneDao().insertTombstones(
            listOf(TombstoneEntity(Tombstone.TYPE_COURSE, courseId, now)) +
                studyMaterialDao().getMaterialIdsForCourse(courseId).map {
                    TombstoneEntity(Tombstone.TYPE_STUDY_MATERIAL, it, now)
                } + flashcardDao().getFlashcardsForCourseSync(courseId).map {
                    TombstoneEntity(Tombstone.TYPE_FLASHCARD, it.id, now)
                } + quizQuestionDao().getQuizQuestionIdsForCourse(courseId).map {
                    TombstoneEntity(Tombstone.TYPE_QUIZ_QUESTION, it, now)
                }
        )
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

        /**
         * v11 : review_logs détaché des flashcards (plus de FK CASCADE →
         * l'historique survit aux régénérations), colonne courseId backfillée
         * depuis les flashcards existantes ; tables review_sessions et
         * tombstones ajoutées.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `_new_review_logs` (
                        `id` TEXT NOT NULL,
                        `courseId` TEXT NOT NULL,
                        `flashcardId` TEXT NOT NULL,
                        `reviewedAt` INTEGER NOT NULL,
                        `rating` INTEGER NOT NULL,
                        `previousInterval` INTEGER NOT NULL,
                        `newInterval` INTEGER NOT NULL,
                        `responseTime` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `_new_review_logs` (`id`, `courseId`, `flashcardId`, `reviewedAt`, `rating`, `previousInterval`, `newInterval`, `responseTime`)
                    SELECT r.`id`, COALESCE(f.`courseId`, ''), r.`flashcardId`, r.`reviewedAt`, r.`rating`, r.`previousInterval`, r.`newInterval`, r.`responseTime`
                    FROM `review_logs` r LEFT JOIN `flashcards` f ON r.`flashcardId` = f.`id`
                """.trimIndent())
                db.execSQL("DROP TABLE `review_logs`")
                db.execSQL("ALTER TABLE `_new_review_logs` RENAME TO `review_logs`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_logs_flashcardId` ON `review_logs` (`flashcardId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_logs_courseId` ON `review_logs` (`courseId`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `review_sessions` (
                        `id` TEXT NOT NULL,
                        `courseId` TEXT,
                        `startedAt` INTEGER NOT NULL,
                        `endedAt` INTEGER,
                        `cardsReviewed` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_sessions_courseId` ON `review_sessions` (`courseId`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tombstones` (
                        `entityType` TEXT NOT NULL,
                        `entityId` TEXT NOT NULL,
                        `deletedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`entityType`, `entityId`)
                    )
                """.trimIndent())
            }
        }

        /** Clé de rapprochement des questions entre deux générations (casse/espacements ignorés). */
        internal fun normalizeQuestion(question: String): String =
            question.trim().lowercase().replace(Regex("\\s+"), " ")

        fun getDatabase(context: Context): LearnSyncDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LearnSyncDatabase::class.java,
                    "learn_sync_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

