package com.learnsyncai.data.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CourseContentStorage(private val context: Context) {
    private val coursesDir: File
        get() = File(context.filesDir, "courses").apply { mkdirs() }

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
                    ""
                }
            } else {
                ""
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
