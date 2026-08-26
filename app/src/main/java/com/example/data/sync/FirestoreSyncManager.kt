package com.example.data.sync

import android.content.Context
import android.net.Uri
import com.example.domain.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class FirestoreSyncManager {

    private val firestore: FirebaseFirestore?
        get() = FirebaseHelper.getFirestore()

    private val auth: FirebaseAuth?
        get() = FirebaseHelper.getAuth()

    private val storage: FirebaseStorage?
        get() = FirebaseHelper.getStorage()

    fun getCurrentUserId(): String? = auth?.currentUser?.uid

    // ==================== STORAGE: PDF / DOCX SYNC ====================

    suspend fun uploadCourseDocument(
        uri: Uri,
        courseId: String,
        fileName: String,
        context: Context
    ): Result<String> {
        val st = storage ?: return Result.failure(
            IllegalStateException("Firebase Storage non configuré.")
        )
        val uid = getCurrentUserId() ?: return Result.failure(
            IllegalStateException("Authentification requise pour stocker des documents dans le Cloud.")
        )
        return try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(IllegalArgumentException("Impossible de lire le fichier local : $fileName"))

            val storageRef = st.reference.child("users/$uid/courses/$courseId/$fileName")
            storageRef.putStream(stream).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== FIRESTORE: SYNC UP ====================

    suspend fun syncUpCourses(courses: List<Course>): Result<Unit> {
        val fs = firestore ?: return Result.failure(
            IllegalStateException("Firebase Firestore n'est pas configuré ou non initialisé.")
        )
        val uid = getCurrentUserId() ?: return Result.failure(
            IllegalStateException("Veuillez vous connecter avec Firebase pour synchroniser vos cours dans le Cloud.")
        )
        return try {
            val batch = fs.batch()
            for (course in courses) {
                val docRef = fs.collection("users").document(uid).collection("courses").document(course.id)
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

    suspend fun syncUpMaterials(materials: List<StudyMaterial>): Result<Unit> {
        val fs = firestore ?: return Result.failure(
            IllegalStateException("Firebase Firestore n'est pas configuré.")
        )
        val uid = getCurrentUserId() ?: return Result.failure(
            IllegalStateException("Veuillez vous connecter pour synchroniser les synthèses.")
        )
        return try {
            val batch = fs.batch()
            for (mat in materials) {
                val docRef = fs.collection("users").document(uid).collection("study_materials").document(mat.id)
                val map = hashMapOf(
                    "id" to mat.id,
                    "courseId" to mat.courseId,
                    "summary" to mat.summary,
                    "keyPoints" to mat.keyPoints,
                    "mnemonicTips" to mat.mnemonicTips,
                    "generatedAt" to mat.generatedAt,
                    "version" to mat.version
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
        val fs = firestore ?: return Result.failure(
            IllegalStateException("Firebase Firestore n'est pas configuré.")
        )
        val uid = getCurrentUserId() ?: return Result.failure(
            IllegalStateException("Veuillez vous connecter avec Firebase pour synchroniser vos flashcards.")
        )
        return try {
            val batch = fs.batch()
            for (card in flashcards) {
                val docRef = fs.collection("users").document(uid).collection("flashcards").document(card.id)
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
        val fs = firestore ?: return Result.failure(
            IllegalStateException("Firebase Firestore n'est pas configuré.")
        )
        val uid = getCurrentUserId() ?: return Result.failure(
            IllegalStateException("Veuillez vous connecter avec Firebase pour synchroniser l'historique de révision.")
        )
        return try {
            val batch = fs.batch()
            for (log in logs) {
                val docRef = fs.collection("users").document(uid).collection("review_logs").document(log.id)
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

    // ==================== FIRESTORE: SYNC DOWN (CLOUD REPLICA -> ROOM) ====================

    suspend fun fetchRemoteCourses(): Result<List<Course>> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firebase Firestore non configuré."))
        val uid = getCurrentUserId() ?: return Result.failure(IllegalStateException("Non authentifié."))
        return try {
            val snapshot = fs.collection("users").document(uid).collection("courses").get().await()
            val courses = snapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val title = doc.getString("title") ?: return@mapNotNull null
                val description = doc.getString("description") ?: ""
                val sourceFileName = doc.getString("sourceFileName") ?: ""
                val sourceFileUri = doc.getString("sourceFileUri") ?: ""
                val extractedText = doc.getString("extractedText") ?: ""
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                val progress = doc.getDouble("progress")?.toFloat() ?: 0f
                val color = doc.getString("color") ?: "#4F46E5"
                val generationStatus = doc.getString("generationStatus") ?: "COMPLETED"

                Course(id, title, description, sourceFileName, sourceFileUri, extractedText, createdAt, updatedAt, progress, color, generationStatus)
            }
            Result.success(courses)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRemoteFlashcards(): Result<List<Flashcard>> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firebase Firestore non configuré."))
        val uid = getCurrentUserId() ?: return Result.failure(IllegalStateException("Non authentifié."))
        return try {
            val snapshot = fs.collection("users").document(uid).collection("flashcards").get().await()
            val flashcards = snapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val courseId = doc.getString("courseId") ?: return@mapNotNull null
                val question = doc.getString("question") ?: ""
                val answer = doc.getString("answer") ?: ""
                val explanation = doc.getString("explanation") ?: ""
                val difficulty = doc.getDouble("difficulty")?.toFloat() ?: 5.0f
                val box = doc.getLong("box")?.toInt() ?: 1
                val dueDate = doc.getLong("dueDate") ?: System.currentTimeMillis()
                val interval = doc.getLong("interval")?.toInt() ?: 1
                val easeFactor = doc.getDouble("easeFactor")?.toFloat() ?: 2.5f
                val repetitions = doc.getLong("repetitions")?.toInt() ?: 0
                val lapses = doc.getLong("lapses")?.toInt() ?: 0
                val lastReviewedAt = doc.getLong("lastReviewedAt")
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()

                Flashcard(id, courseId, question, answer, explanation, difficulty, box, dueDate, interval, easeFactor, repetitions, lapses, lastReviewedAt, createdAt)
            }
            Result.success(flashcards)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCourseInCloud(courseId: String): Result<Unit> {
        val fs = firestore ?: return Result.success(Unit)
        val uid = getCurrentUserId() ?: return Result.success(Unit)
        return try {
            fs.collection("users").document(uid).collection("courses").document(courseId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

