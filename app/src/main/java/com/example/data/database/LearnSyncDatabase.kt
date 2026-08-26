package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CourseEntity::class,
        StudyMaterialEntity::class,
        FlashcardEntity::class,
        QuizQuestionEntity::class,
        ReviewLogEntity::class,
        UserPreferencesEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LearnSyncDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun studyMaterialDao(): StudyMaterialDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun quizQuestionDao(): QuizQuestionDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun userPreferencesDao(): UserPreferencesDao

    companion object {
        @Volatile
        private var INSTANCE: LearnSyncDatabase? = null

        fun getDatabase(context: Context): LearnSyncDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LearnSyncDatabase::class.java,
                    "learn_sync_database"
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
