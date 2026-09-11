package com.learnsyncai.data.storage

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.learnsyncai.domain.model.Course
import java.io.File

/**
 * Ouvre le document source d'un cours dans le visualiseur du système
 * (lecteur PDF, images, bureautique…) via un intent ACTION_VIEW.
 *
 * Ordre de résolution : copie locale conservée à l'import, puis URI
 * persistée du sélecteur, puis URL distante (navigateur).
 */
object FileOpener {

    fun openCourseDocument(context: Context, storage: CourseContentStorage, course: Course): Result<Unit> {
        // 1. Copie locale (cas général, fonctionne même hors-ligne et après redémarrage).
        val local = storage.getOriginalFile(course.id)
        if (local != null && local.exists()) {
            return openFile(context, local)
        }
        // 2. URI du sélecteur système (si l'autorisation persistée est encore valide).
        val uriString = course.sourceFileUri
        if (uriString.startsWith("content://")) {
            return try {
                openUri(context, Uri.parse(uriString), guessMimeType(course.sourceFileName))
            } catch (e: SecurityException) {
                Result.failure(IllegalStateException("Le document d'origine n'est plus accessible (fichier déplacé ou autorisation expirée)."))
            }
        }
        // 3. URL distante (import web) : ouverture dans le navigateur.
        if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
            return openUri(context, Uri.parse(uriString), null)
        }
        return Result.failure(
            IllegalStateException("Aucune copie locale du document : réimportez le fichier pour pouvoir l'ouvrir.")
        )
    }

    private fun openFile(context: Context, file: File): Result<Unit> {
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            openUri(context, uri, guessMimeType(file.name))
        } catch (e: IllegalArgumentException) {
            Result.failure(IllegalStateException("Document inaccessible : ${e.message}"))
        }
    }

    private fun openUri(context: Context, uri: Uri, mimeType: String?): Result<Unit> {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // Le lancement peut venir d'un contexte applicatif (ViewModel).
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Ouvrir le document").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            Result.success(Unit)
        } catch (e: ActivityNotFoundException) {
            Result.failure(IllegalStateException("Aucune application installée pour ouvrir ce type de fichier."))
        } catch (e: SecurityException) {
            Result.failure(IllegalStateException("Ouverture refusée par le système : ${e.message}"))
        }
    }

    private fun guessMimeType(fileName: String): String? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }
}
