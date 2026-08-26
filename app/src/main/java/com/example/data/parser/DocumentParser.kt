package com.example.data.parser

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class ParseResult(
    val title: String,
    val text: String,
    val pageCount: Int,
    val isScanOrEmpty: Boolean = false
)

class DocumentParser(private val context: Context) {

    init {
        try {
            PDFBoxResourceLoader.init(context)
        } catch (_: Exception) {}
    }

    fun parseDocument(uri: Uri, fileName: String): ParseResult {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "pdf" -> parsePdf(uri, fileName)
            "docx" -> parseDocx(uri, fileName)
            "txt" -> parseTxt(uri, fileName)
            else -> parseTxt(uri, fileName)
        }
    }

    private fun parsePdf(uri: Uri, fileName: String): ParseResult {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Impossible d'ouvrir le fichier PDF : $fileName")

        return inputStream.use { stream ->
            val document = PDDocument.load(stream)
            val pageCount = document.numberOfPages
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            val extractedText = stripper.getText(document).trim()
            document.close()

            val title = fileName.substringBeforeLast('.')
            
            // Validate if text is genuinely present (not just whitespace or unparseable chars)
            val alphanumericCount = extractedText.count { it.isLetterOrDigit() }
            if (alphanumericCount < 20) {
                throw IllegalStateException(
                    "Ce document PDF ne contient aucun texte sélectionnable. Il semble s'agir d'un scan ou d'images scannées sans couche de texte. Veuillez importer un document textuel ou appliquer un OCR."
                )
            }

            ParseResult(
                title = title,
                text = extractedText,
                pageCount = if (pageCount > 0) pageCount else 1,
                isScanOrEmpty = false
            )
        }
    }

    private fun parseTxt(uri: Uri, fileName: String): ParseResult {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Impossible d'ouvrir le fichier texte : $fileName")

        // Try UTF-8 first, then fallback to ISO-8859-1 (common for French accents in older files)
        val text = try {
            val utf8 = String(bytes, StandardCharsets.UTF_8)
            if (utf8.contains('\uFFFD')) {
                String(bytes, Charset.forName("ISO-8859-1"))
            } else {
                utf8
            }
        } catch (_: Exception) {
            String(bytes, StandardCharsets.UTF_8)
        }.trim()

        if (text.isBlank() || text.count { it.isLetterOrDigit() } < 5) {
            throw IllegalStateException("Le fichier texte est vide ou ne contient aucun contenu lisible.")
        }

        val title = fileName.substringBeforeLast('.')
        return ParseResult(title = title, text = text, pageCount = 1)
    }

    private fun parseDocx(uri: Uri, fileName: String): ParseResult {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Impossible d'ouvrir le fichier DOCX : $fileName")

        val stringBuilder = StringBuilder()
        ZipInputStream(inputStream).use { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val factory = DocumentBuilderFactory.newInstance()
                    // Disable external entity resolution for security
                    try {
                        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                    } catch (_: Exception) {}
                    
                    val builder = factory.newDocumentBuilder()
                    val doc = builder.parse(zipStream)
                    val nodeList = doc.getElementsByTagName("w:t")
                    for (i in 0 until nodeList.length) {
                        val node = nodeList.item(i)
                        stringBuilder.append(node.textContent).append(" ")
                    }
                    break
                }
                entry = zipStream.nextEntry
            }
        }

        val extractedText = stringBuilder.toString().trim()
        if (extractedText.isBlank() || extractedText.count { it.isLetterOrDigit() } < 20) {
            throw IllegalStateException("Le fichier DOCX est vide ou ne contient aucun texte exploitable.")
        }

        val title = fileName.substringBeforeLast('.')
        return ParseResult(title = title, text = extractedText, pageCount = 1)
    }
}
