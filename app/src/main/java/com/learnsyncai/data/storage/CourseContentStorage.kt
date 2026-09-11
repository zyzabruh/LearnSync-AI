package com.learnsyncai.data.storage

import android.content.Context
import com.learnsyncai.data.parser.OutlineEntry
import com.learnsyncai.domain.model.InkStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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

    suspend fun saveOutlineForCourse(courseId: String, outline: List<OutlineEntry>) {
        withContext(Dispatchers.IO) {
            val sanitizedId = sanitizeCourseId(courseId)
            val file = File(coursesDir, "$sanitizedId.outline.json")
            file.writeText(outlineToJson(outline).toString(), Charsets.UTF_8)
        }
    }

    suspend fun getOutlineForCourse(courseId: String): List<OutlineEntry> {
        return withContext(Dispatchers.IO) {
            val sanitizedId = sanitizeCourseId(courseId)
            val file = File(coursesDir, "$sanitizedId.outline.json")
            if (file.exists()) {
                try {
                    outlineFromJson(JSONArray(file.readText(Charsets.UTF_8)))
                } catch (e: Exception) {
                    android.util.Log.w("LearnSyncAI", "Outline illisible pour le cours $courseId : ${e.message}")
                    emptyList()
                }
            } else {
                emptyList()
            }
        }
    }

    private fun outlineToJson(entries: List<OutlineEntry>): JSONArray {
        val array = JSONArray()
        for (entry in entries) {
            array.put(
                JSONObject().apply {
                    put("title", entry.title)
                    put("pageIndex", entry.pageIndex)
                    put("children", outlineToJson(entry.children))
                }
            )
        }
        return array
    }

    private fun outlineFromJson(array: JSONArray): List<OutlineEntry> {
        val result = mutableListOf<OutlineEntry>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val children = obj.optJSONArray("children")
            result.add(
                OutlineEntry(
                    title = obj.optString("title", ""),
                    pageIndex = obj.optInt("pageIndex", 0),
                    children = if (children != null) outlineFromJson(children) else emptyList()
                )
            )
        }
        return result
    }

    // ==================== ENCRE LIBRE (surlignage au doigt) ====================

    suspend fun saveInkStrokes(courseId: String, strokes: List<InkStroke>) {
        withContext(Dispatchers.IO) {
            val file = File(coursesDir, "${sanitizeCourseId(courseId)}.ink.json")
            val array = JSONArray()
            for (s in strokes) {
                val pts = JSONArray()
                for (p in s.points) pts.put(p.toDouble())
                array.put(
                    JSONObject().apply {
                        put("page", s.page)
                        put("color", s.color)
                        put("points", pts)
                    }
                )
            }
            file.writeText(array.toString(), Charsets.UTF_8)
        }
    }

    suspend fun getInkStrokes(courseId: String): List<InkStroke> {
        return withContext(Dispatchers.IO) {
            val file = File(coursesDir, "${sanitizeCourseId(courseId)}.ink.json")
            if (!file.exists()) return@withContext emptyList()
            try {
                val array = JSONArray(file.readText(Charsets.UTF_8))
                List(array.length()) { i ->
                    val obj = array.getJSONObject(i)
                    val pts = obj.optJSONArray("points")
                    InkStroke(
                        page = obj.optInt("page", 0),
                        color = obj.optLong("color", -256L),
                        points = if (pts != null) List(pts.length()) { j -> pts.optDouble(j, 0.0).toFloat() } else emptyList()
                    )
                }.filter { it.points.size >= 4 }
            } catch (e: Exception) {
                android.util.Log.w("LearnSyncAI", "Encre illisible pour le cours $courseId : ${e.message}")
                emptyList()
            }
        }
    }

    suspend fun deleteInkStrokes(courseId: String) {
        withContext(Dispatchers.IO) {
            try {
                File(coursesDir, "${sanitizeCourseId(courseId)}.ink.json").delete()
            } catch (e: Exception) {
                android.util.Log.w("LearnSyncAI", "Nettoyage de l'encre impossible : ${e.message}")
            }
        }
    }

    suspend fun deleteOutlineForCourse(courseId: String) {
        withContext(Dispatchers.IO) {
            try {
                File(coursesDir, "${sanitizeCourseId(courseId)}.outline.json").delete()
            } catch (e: Exception) {
                android.util.Log.w("LearnSyncAI", "Nettoyage du sommaire impossible : ${e.message}")
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
