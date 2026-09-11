package com.learnsyncai.data.storage

import android.content.Context
import com.hermes_tools.json.gson.Gson
import com.learnsyncai.data.parser.OutlineEntry
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

    /** Lecture pour la génération : distingue document absent et erreur de lecture. */
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
            if (file.exists()) file.delete()
        }
    }

    // ==================== FICHIER D'ORIGINE (ouverture in-app) ====================

    /** Conserve une copie locale du fichier importé pour pouvoir l'ouvrir depuis l'application. */
    suspend fun saveOriginalFile(courseId: String, fileName: String, input: java.io.InputStream): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val dir = File(coursesDir, "originals/${sanitizeCourseId(courseId)}").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val target = File(dir, sanitizeFileName(fileName).ifBlank { "document" })
                input.use { src -> target.outputStream().use { dst -> src.copyTo(dst) } }
                true
            } catch (e: Exception) {
                android.util.Log.w("LearnSyncAI", "Copie locale du document impossible : ${e.message}")
                false
            }
        }
    }

    /** Copie locale du fichier d'origine, ou null si absente. */
    fun getOriginalFile(courseId: String): File? {
        val dir = File(coursesDir, "originals/${sanitizeCourseId(courseId)}")
        return dir.listFiles()?.firstOrNull { it.isFile }
    }

    suspend fun deleteOriginalFiles(courseId: String) {
        withContext(Dispatchers.IO) {
            try {
                File(coursesDir, "originals/${sanitizeCourseId(courseId)}").deleteRecursively()
            } catch (e: Exception) {
                android.util.Log.w("LearnSyncAI", "Nettoyage du document local impossible : ${e.message}")
            }
        }
    }

    suspend fun exists(courseId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val sanitizedId = sanitizeCourseId(courseId)
            File(coursesDir, "$sanitizedId.txt").exists()
        }
    }

    // ==================== SOMMAIRE PDF (outline) ====================

    private val gson = Gson()

    suspend fun saveOutlineForCourse(courseId: String, outline: List<OutlineEntry>) {
        withContext(Dispatchers.IO) {
            val sanitizedId = sanitizeCourseId(courseId)
            val file = File(coursesDir, "$sanitizedId.outline.json")
            file.writeText(gson.toJson(outline), Charsets.UTF_8)
        }
    }

    suspend fun getOutlineForCourse(courseId: String): List<OutlineEntry> {
        return withContext(Dispatchers.IO) {
            val sanitizedId = sanitizeCourseId(courseId)
            val file = File(coursesDir, "$sanitizedId.outline.json")
            if (file.exists()) {
                try {
                    gson.fromJson(file.readText(Charsets.UTF_8), Array<OutlineEntry>::class.java).toList()
                } catch (e: Exception) {
                    android.util.Log.w("LearnSyncAI", "Outline illisible pour le cours $courseId : ${e.message}")
                    emptyList()
                }
            } else {
                emptyList()
            }
        }
    }

    private fun sanitizeCourseId(courseId: String): String {
        return courseId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }

    private fun sanitizeFileName(fileName: String): String {
        return fileName.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^a-zA-Z0-9._-]"), "_").takeLast(120)
    }
}
