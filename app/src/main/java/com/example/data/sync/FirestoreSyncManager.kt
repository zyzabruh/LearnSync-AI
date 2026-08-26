package com.example.data.sync

import com.example.domain.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirestoreSyncManager {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    suspend fun syncUpCourses(courses: List<Course>): Result<Unit> {
        val uid = getCurrentUserId() ?: return Result.failure(IllegalStateException("Utilisateur non connecté"))
        return try {
            val batch = firestore.batch()
            for (course in courses) {
                val docRef = firestore.collection("users").document(uid).collection("courses").document(course.id)
                val map = hashMapOf(
                    "id" to course.id,
                    "title" to course.title,
                    "description" to course.description,
                    "sourceFileName" to course.sourceFileName,
                    "sourceFileUri" to course.sourceFileUri,
                    "extractedText" to course.extractedText,
                    "createdAt" to course.createdAt,
                    "updatedAt" to course.updatedAt,
                    "progress" to course.progress,
                    "color" to course.color,
                    "generationStatus" to course.generationStatus
                )
                batch.set(docRef, map, SetOptions.merge())
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncUpFlashcards(flashcards: List<Flashcard>): Result<Unit> {
        val uid = getCurrentUserId() ?: return Result.failure(IllegalStateException("Utilisateur non connecté"))
        return try {
            val batch = firestore.batch()
            for (card in flashcards) {
                val docRef = firestore.collection("users").document(uid).collection("flashcards").document(card.id)
                val map = hashMapOf(
                    "id" to card.id,
                    "courseId" to card.courseId,
                    "question" to card.question,
                    "answer" to card.answer,
                    "explanation" to card.explanation,
                    "difficulty" to card.difficulty,
                    "box" to card.box,
                    "dueDate" to card.dueDate,
                    "interval" to card.interval,
                    "easeFactor" to card.easeFactor,
                    "repetitions" to card.repetitions,
                    "lapses" to card.lapses,
                    "lastReviewedAt" to card.lastReviewedAt,
                    "createdAt" to card.createdAt
                )
                batch.set(docRef, map, SetOptions.merge())
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncUpReviewLogs(logs: List<ReviewLog>): Result<Unit> {
        val uid = getCurrentUserId() ?: return Result.failure(IllegalStateException("Utilisateur non connecté"))
        return try {
            val batch = firestore.batch()
            for (log in logs) {
                val docRef = firestore.collection("users").document(uid).collection("review_logs").document(log.id)
                val map = hashMapOf(
                    "id" to log.id,
                    "flashcardId" to log.flashcardId,
                    "reviewedAt" to log.reviewedAt,
                    "rating" to log.rating,
                    "previousInterval" to log.previousInterval,
                    "newInterval" to log.newInterval,
                    "responseTime" to log.responseTime
                )
                batch.set(docRef, map, SetOptions.merge())
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
