package com.learnsyncai

import android.content.Context
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.learnsyncai.data.database.LearnSyncDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Teste MIGRATION_10_11 sur une base v10 reconstruite à la main (le schéma v10
 * n'a jamais été exporté, exportSchema était à false) : backfill de courseId
 * depuis les flashcards, survie des logs à la suppression d'une carte (plus de
 * FK CASCADE), et création de review_sessions / tombstones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration10To11Test {

    private fun openV10Database(): SupportSQLiteDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // base en mémoire
            .callback(object : SupportSQLiteOpenHelper.Callback(10) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Schéma v10 exact des trois tables impliquées dans la migration.
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `courses` (
                            `id` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `description` TEXT NOT NULL,
                            `sourceFileName` TEXT NOT NULL,
                            `sourceFileUri` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            `progress` REAL NOT NULL,
                            `color` TEXT NOT NULL,
                            `generationStatus` TEXT NOT NULL,
                            `tag` TEXT NOT NULL,
                            `language` TEXT NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `flashcards` (
                            `id` TEXT NOT NULL,
                            `courseId` TEXT NOT NULL,
                            `question` TEXT NOT NULL,
                            `answer` TEXT NOT NULL,
                            `explanation` TEXT NOT NULL,
                            `difficulty` REAL NOT NULL,
                            `box` INTEGER NOT NULL,
                            `dueDate` INTEGER NOT NULL,
                            `interval` INTEGER NOT NULL,
                            `easeFactor` REAL NOT NULL,
                            `repetitions` INTEGER NOT NULL,
                            `lapses` INTEGER NOT NULL,
                            `lastReviewedAt` INTEGER,
                            `createdAt` INTEGER NOT NULL,
                            PRIMARY KEY(`id`),
                            FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """.trimIndent())
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcards_courseId` ON `flashcards` (`courseId`)")
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `review_logs` (
                            `id` TEXT NOT NULL,
                            `flashcardId` TEXT NOT NULL,
                            `reviewedAt` INTEGER NOT NULL,
                            `rating` INTEGER NOT NULL,
                            `previousInterval` INTEGER NOT NULL,
                            `newInterval` INTEGER NOT NULL,
                            `responseTime` INTEGER NOT NULL,
                            PRIMARY KEY(`id`),
                            FOREIGN KEY(`flashcardId`) REFERENCES `flashcards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """.trimIndent())
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_logs_flashcardId` ON `review_logs` (`flashcardId`)")
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `user_preferences` (
                            `id` INTEGER NOT NULL PRIMARY KEY,
                            `notificationsEnabled` INTEGER NOT NULL,
                            `dailyGoal` INTEGER NOT NULL,
                            `reminderTime` TEXT NOT NULL,
                            `theme` TEXT NOT NULL,
                            `language` TEXT NOT NULL,
                            `aiProvider` TEXT NOT NULL,
                            `aiBaseUrl` TEXT NOT NULL,
                            `aiApiKey` TEXT NOT NULL,
                            `aiModelName` TEXT NOT NULL,
                            `flashcardsMode` TEXT NOT NULL,
                            `flashcardsCustomCount` INTEGER NOT NULL,
                            `quizMode` TEXT NOT NULL,
                            `quizCustomCount` INTEGER NOT NULL,
                            `mnemonicTipsMode` TEXT NOT NULL,
                            `mnemonicTipsCustomCount` INTEGER NOT NULL,
                            `autoTtsEnabled` INTEGER NOT NULL
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }

    private fun insertV10Data(db: SupportSQLiteDatabase) {
        db.execSQL("""
            INSERT INTO courses VALUES ('course-1', 'Titre', 'Desc', 'f.pdf', 'uri', 0, 0, 0.0, '#000', 'COMPLETED', '', 'auto')
        """.trimIndent())
        db.execSQL("""
            INSERT INTO flashcards VALUES ('card-1', 'course-1', 'Q', 'A', 'E', 5.0, 1, 0, 1, 2.5, 1, 0, NULL, 0)
        """.trimIndent())
        // Deux logs : l'un rattachable à la carte, l'autre orphelin (carte déjà supprimée en v10).
        db.execSQL("INSERT INTO review_logs VALUES ('log-1', 'card-1', 100, 3, 1, 6, 500)")
        db.execSQL("INSERT INTO review_logs VALUES ('log-orphan', 'card-gone', 200, 2, 1, 2, 300)")
    }

    private fun logRows(db: SupportSQLiteDatabase): Map<String, Pair<String, String>> {
        val cursor: Cursor = db.query("SELECT id, courseId, flashcardId FROM review_logs")
        val rows = mutableMapOf<String, Pair<String, String>>()
        while (cursor.moveToNext()) {
            rows[cursor.getString(0)] = cursor.getString(1) to cursor.getString(2)
        }
        cursor.close()
        return rows
    }

    @Test
    fun migrationBackfillsCourseIdAndDetachesLogsFromCards() {
        val db = openV10Database()
        insertV10Data(db)
        db.execSQL("PRAGMA foreign_keys = ON")

        LearnSyncDatabase.MIGRATION_10_11.migrate(db)

        val rows = logRows(db)
        assertEquals(2, rows.size)
        assertEquals("course-1", rows["log-1"]?.first)
        assertEquals("card-1", rows["log-1"]?.second)
        // Log orphelin : pas de flashcard à rattacher -> courseId vide, mais conservé.
        assertEquals("", rows["log-orphan"]?.first)

        // Plus de FK : supprimer la carte ne doit plus effacer l'historique.
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM flashcards WHERE id = 'card-1'")
        val rowsAfterDelete = logRows(db)
        assertTrue(rowsAfterDelete.containsKey("log-1"))

        db.close()
    }

    @Test
    fun migration11To12AddsCalendarPreferences() {
        val db = openV10Database()
        insertV10Data(db)
        LearnSyncDatabase.MIGRATION_10_11.migrate(db)
        LearnSyncDatabase.MIGRATION_11_12.migrate(db)

        db.query("PRAGMA table_info(user_preferences)").use { cursor ->
            val columns = mutableSetOf<String>()
            while (cursor.moveToNext()) columns += cursor.getString(1)
            assertTrue(columns.contains("calendarHorizonDays"))
            assertTrue(columns.contains("calendarStartTime"))
            assertTrue(columns.contains("calendarDurationMinutes"))
            assertTrue(columns.contains("calendarReminderMinutes"))
        }
        db.close()
    }

    @Test
    fun migrationCreatesReviewSessionsAndTombstonesTables() {
        val db = openV10Database()
        insertV10Data(db)

        LearnSyncDatabase.MIGRATION_10_11.migrate(db)

        db.execSQL("""
            INSERT INTO review_sessions VALUES ('s-1', 'course-1', 1000, 2000, 12)
        """.trimIndent())
        db.execSQL("INSERT INTO review_sessions VALUES ('s-2', NULL, 3000, NULL, 0)")
        db.execSQL("INSERT INTO tombstones VALUES ('FLASHCARD', 'card-9', 1234)")

        db.query("SELECT id, courseId, cardsReviewed FROM review_sessions WHERE id = 's-1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("s-1", c.getString(0))
            assertEquals("course-1", c.getString(1))
            assertEquals(12, c.getInt(2))
        }
        db.query("SELECT courseId FROM review_sessions WHERE id = 's-2'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.isNull(0))
        }
        db.query("SELECT entityType, entityId FROM tombstones").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("FLASHCARD", c.getString(0))
            assertEquals("card-9", c.getString(1))
        }

        db.close()
    }
}
