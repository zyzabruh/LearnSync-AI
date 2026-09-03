package com.learnsyncai.data.parser

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Bounded OCR for image-only PDFs, using the unbundled on-device ML Kit model. */
class PdfOcrService(private val context: Context) {
    suspend fun extractText(
        pdfUri: Uri,
        pageCount: Int,
        onProgress: (completedPages: Int, totalPages: Int) -> Unit = { _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(pageCount > 0) { "Le PDF ne contient aucune page." }
            val pages = pageCount.coerceAtMost(MAX_PAGES)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val inputStream = context.contentResolver.openInputStream(pdfUri)
                    ?: error("Impossible d'ouvrir le PDF sélectionné.")
                inputStream.use { stream ->
                    PDDocument.load(stream).use { document ->
                    val renderer = PDFRenderer(document)
                    val text = StringBuilder()
                    for (page in 0 until pages) {
                        coroutineContext.ensureActive()
                        val bitmap = renderer.renderImageWithDPI(page, RENDER_DPI)
                        try {
                            val image = InputImage.fromBitmap(bitmap, 0)
                            val result = Tasks.await(recognizer.process(image))
                            if (result.text.isNotBlank()) {
                                if (text.isNotEmpty()) text.append("\n\n")
                                text.append("Page ${page + 1} : ").append(result.text.trim())
                            }
                        } finally {
                            bitmap.recycle()
                        }
                        onProgress(page + 1, pages)
                    }
                    val extracted = text.toString().trim()
                    require(extracted.count { it.isLetterOrDigit() } >= MIN_TEXT_CHARACTERS) {
                        "L'OCR n'a pas trouvé suffisamment de texte lisible dans ce PDF."
                    }
                    extracted
                    }
                }
            } finally {
                recognizer.close()
            }
        }
    }

    companion object {
        const val MAX_PAGES = 50
        const val RENDER_DPI = 150f
        const val MIN_TEXT_CHARACTERS = 20
    }
}
