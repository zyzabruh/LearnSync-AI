package com.learnsyncai.data.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CourseContentStorage(private val context: Context) {
    private val coursesDir: File
        get() = File(context.filesDir, "courses").apply { mkdirs() }

    /** Résultat typé de la lecture du texte extrait d'un document. */
    sealed interface ExtractedText {
        data class Available(val text: String) : ExtractedText
        data object Missing : ExtractedText
        data class ReadError(val detail: String) : ExtractedText
    }

    suspend fun saveExtractedText(courseId: String, text: String) {
        withContext(Dispatchers.IO) {
            val sanitizedId = sanitizeCourseId(courseId)
            val file = File(coursesDir, "$sanitizedId.txt")
            file.writeText(text, Charsets.UTF_8)
        }
    }

    suspend fun readExtractedText(courseId: String): String {
        return withContext(Dispatchers.IO) {
            val sanitizedId = sanitizeCourseId(courseId)
            val file = File(coursesDir, "$sanitizedId.txt")
            if (file.exists()) {
                try {
                    file.readText(Charsets.UTF_8)
                } catch (e: Exception) {
                    android.util.Log.w("LearnSyncAI", "Texte extrait illisible pour le cours $courseId : ${e.message}")
                    ""
                }
            } else {
                ""
            }
        }
    }

    /**
     * Lecture pour la génération : distingue document absent et erreur de
     * lecture, afin d'interrompre la génération avec un message clair au lieu
     * de produire du contenu à partir d'un texte vide.
     */
    suspend fun readExtractedTextChecked(courseId: String): ExtractedText {
        return withContext(Dispatchers.IO) {
            val file = File(coursesDir, "${sanitizeCourseId(courseId)}.txt")
            if (!file.exists()) {
                ExtractedText.Missing
            } else {
                try {
                    val text = file.readText(Charsets.UTF_8)
                    if (text.isBlank()) ExtractedText.Missing else ExtractedText.Available(text)
                } catch (e: Exception) {
                    ExtractedText.ReadError(e.message ?: "lecture impossible")
                }
            }
        }
    }

    suspend fun deleteExtractedText(courseId: String) {
        withContext(Dispatchers.IO) {
            val sanitizedId = sanitizeCourseId(courseId)
            val file = File(coursesDir, "$sanitizedId.txt")
            if (file.exists()) {
                file.delete()
            }
        }
    }

    suspend fun exists(courseId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val sanitizedId = sanitizeCourseId(courseId)
            val file = File(coursesDir, "$sanitizedId.txt")
            file.exists()
        }
    }

    private fun sanitizeCourseId(courseId: String): String {
        return courseId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }
}
