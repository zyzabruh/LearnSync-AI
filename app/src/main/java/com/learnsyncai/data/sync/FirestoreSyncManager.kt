package com.learnsyncai.data.sync

import android.content.Context
import android.net.Uri
import com.learnsyncai.domain.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import org.json.JSONArray

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

    // ==================== FIRESTORE: SYNC UP (WITH 400-CHUNK BATCHES) ====================

    suspend fun syncUpCourses(courses: List<Course>): Result<Unit> {
        val fs = firestore ?: return Result.failure(
            IllegalStateException("Firebase Firestore n'est pas configuré ou non initialisé.")
        )
        val uid = getCurrentUserId() ?: return Result.failure(
            IllegalStateException("Veuillez vous connecter avec Firebase pour synchroniser vos cours.")
        )
        return try {
            for (chunk in courses.chunked(400)) {
                val batch = fs.batch()
                for (course in chunk) {
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
            }
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
            for (chunk in materials.chunked(400)) {
                val batch = fs.batch()
                for (mat in chunk) {
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
            }
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
            for (chunk in flashcards.chunked(400)) {
                val batch = fs.batch()
                for (card in chunk) {
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
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncUpQuizQuestions(questions: List<QuizQuestion>): Result<Unit> {
        val fs = firestore ?: return Result.failure(
            IllegalStateException("Firebase Firestore n'est pas configuré.")
        )
        val uid = getCurrentUserId() ?: return Result.failure(
            IllegalStateException("Veuillez vous connecter avec Firebase pour synchroniser les QCM.")
        )
        return try {
            for (chunk in questions.chunked(400)) {
                val batch = fs.batch()
                for (q in chunk) {
                    val docRef = fs.collection("users").document(uid).collection("quiz_questions").document(q.id)
                    val map = hashMapOf(
                        "id" to q.id,
                        "courseId" to q.courseId,
                        "question" to q.question,
                        "options" to q.options,
                        "correctAnswer" to q.correctAnswer,
                        "explanation" to q.explanation,
                        "difficulty" to q.difficulty
                    )
                    batch.set(docRef, map, SetOptions.merge())
                }
                batch.commit().await()
            }
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
            for (chunk in logs.chunked(400)) {
                val batch = fs.batch()
                for (log in chunk) {
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
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncUpPreferences(prefs: UserPreferences): Result<Unit> {
        val fs = firestore ?: return Result.failure(
            IllegalStateException("Firebase Firestore n'est pas configuré.")
        )
        val uid = getCurrentUserId() ?: return Result.failure(
            IllegalStateException("Veuillez vous connecter pour synchroniser vos préférences.")
        )
        return try {
            val docRef = fs.collection("users").document(uid).collection("settings").document("user_preferences")
            val map = hashMapOf(
                "notificationsEnabled" to prefs.notificationsEnabled,
                "dailyGoal" to prefs.dailyGoal,
                "reminderTime" to prefs.reminderTime,
                "theme" to prefs.theme,
                "language" to prefs.language,
                "updatedAt" to System.currentTimeMillis()
            )
            docRef.set(map, SetOptions.merge()).await()
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

    suspend fun fetchRemoteMaterials(): Result<List<StudyMaterial>> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firebase Firestore non configuré."))
        val uid = getCurrentUserId() ?: return Result.failure(IllegalStateException("Non authentifié."))
        return try {
            val snapshot = fs.collection("users").document(uid).collection("study_materials").get().await()
            val materials = snapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val courseId = doc.getString("courseId") ?: return@mapNotNull null
                val summary = doc.getString("summary") ?: ""
                @Suppress("UNCHECKED_CAST")
                val keyPoints = (doc.get("keyPoints") as? List<String>) ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val mnemonicTips = (doc.get("mnemonicTips") as? List<String>) ?: emptyList()
                val generatedAt = doc.getLong("generatedAt") ?: System.currentTimeMillis()
                val version = doc.getLong("version")?.toInt() ?: 1

                StudyMaterial(id, courseId, summary, keyPoints, mnemonicTips, generatedAt, version)
            }
            Result.success(materials)
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

    suspend fun fetchRemoteQuizQuestions(): Result<List<QuizQuestion>> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firebase Firestore non configuré."))
        val uid = getCurrentUserId() ?: return Result.failure(IllegalStateException("Non authentifié."))
        return try {
            val snapshot = fs.collection("users").document(uid).collection("quiz_questions").get().await()
            val questions = snapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val courseId = doc.getString("courseId") ?: return@mapNotNull null
                val question = doc.getString("question") ?: ""
                @Suppress("UNCHECKED_CAST")
                val options = (doc.get("options") as? List<String>) ?: emptyList()
                val correctAnswer = doc.getString("correctAnswer") ?: ""
                val explanation = doc.getString("explanation") ?: ""
                val difficulty = doc.getString("difficulty") ?: "medium"

                QuizQuestion(id, courseId, question, options, correctAnswer, explanation, difficulty)
            }
            Result.success(questions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRemoteReviewLogs(): Result<List<ReviewLog>> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firebase Firestore non configuré."))
        val uid = getCurrentUserId() ?: return Result.failure(IllegalStateException("Non authentifié."))
        return try {
            val snapshot = fs.collection("users").document(uid).collection("review_logs").get().await()
            val logs = snapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val flashcardId = doc.getString("flashcardId") ?: return@mapNotNull null
                val reviewedAt = doc.getLong("reviewedAt") ?: System.currentTimeMillis()
                val rating = doc.getLong("rating")?.toInt() ?: 3
                val previousInterval = doc.getLong("previousInterval")?.toInt() ?: 0
                val newInterval = doc.getLong("newInterval")?.toInt() ?: 1
                val responseTime = doc.getLong("responseTime") ?: 0L

                ReviewLog(id, flashcardId, reviewedAt, rating, previousInterval, newInterval, responseTime)
            }
            Result.success(logs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRemotePreferences(): Result<UserPreferences?> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firebase Firestore non configuré."))
        val uid = getCurrentUserId() ?: return Result.failure(IllegalStateException("Non authentifié."))
        return try {
            val doc = fs.collection("users").document(uid).collection("settings").document("user_preferences").get().await()
            if (!doc.exists()) return Result.success(null)

            val notificationsEnabled = doc.getBoolean("notificationsEnabled") ?: true
            val dailyGoal = doc.getLong("dailyGoal")?.toInt() ?: 10
            val reminderTime = doc.getString("reminderTime") ?: "08:00"
            val theme = doc.getString("theme") ?: "system"
            val language = doc.getString("language") ?: "fr"

            Result.success(UserPreferences(notificationsEnabled, dailyGoal, reminderTime, theme, language))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCourseInCloud(courseId: String): Result<Unit> {
        val fs = firestore ?: return Result.success(Unit)
        val uid = getCurrentUserId() ?: return Result.success(Unit)
        return try {
            val batch = fs.batch()
            val userDoc = fs.collection("users").document(uid)

            // 1. Supprimer le cours
            batch.delete(userDoc.collection("courses").document(courseId))

            // 2. Trouver et supprimer en cascade toutes les flashcards du cours
            val flashcardsSnapshot = userDoc.collection("flashcards")
                .whereEqualTo("courseId", courseId)
                .get()
                .await()

            for (doc in flashcardsSnapshot.documents) {
                batch.delete(doc.reference)
            }

            // 3. Trouver et supprimer en cascade tous les quiz du cours
            val quizSnapshot = userDoc.collection("quiz_questions")
                .whereEqualTo("courseId", courseId)
                .get()
                .await()

            for (doc in quizSnapshot.documents) {
                batch.delete(doc.reference)
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
