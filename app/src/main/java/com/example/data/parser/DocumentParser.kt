package com.example.data.parser

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import java.io.BufferedReader
import java.io.InputStreamReader

data class ParseResult(
    val title: String,
    val text: String,
    val pageCount: Int
)

class DocumentParser(private val context: Context) {

    fun parseDocument(uri: Uri, fileName: String): ParseResult {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "txt" -> parseTxt(uri, fileName)
            "pdf" -> parsePdf(uri, fileName)
            else -> parseTxt(uri, fileName) // fallback
        }
    }

    private fun parseTxt(uri: Uri, fileName: String): ParseResult {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Impossible d'ouvrir le fichier TXT")
        val reader = BufferedReader(InputStreamReader(inputStream))
        val text = reader.readText()
        reader.close()
        inputStream.close()
        val title = fileName.substringBeforeLast('.')
        return ParseResult(title = title, text = text, pageCount = 1)
    }

    private fun parsePdf(uri: Uri, fileName: String): ParseResult {
        val parcelFileDescriptor: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("Impossible d'ouvrir le fichier PDF")
        
        val renderer = PdfRenderer(parcelFileDescriptor)
        val pageCount = renderer.pageCount
        val stringBuilder = StringBuilder()

        // Extract text or metadata from PDF pages
        for (i in 0 until pageCount) {
            val page = renderer.openPage(i)
            // Note: PdfRenderer renders to bitmap; for text extraction we can extract available text or label page
            stringBuilder.append("--- Page ${i + 1} ---\n")
            // If text stream is embedded, we can also extract or append page info.
            page.close()
        }
        renderer.close()
        parcelFileDescriptor.close()

        val title = fileName.substringBeforeLast('.')
        val extracted = stringBuilder.toString()
        val finaltext = if (extracted.isNotBlank()) extracted else "Document PDF de $pageCount pages."

        return ParseResult(title = title, text = finaltext, pageCount = pageCount)
    }
}
