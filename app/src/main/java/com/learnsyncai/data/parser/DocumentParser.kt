package com.learnsyncai.data.parser

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
        // Le nom transmis par l'UI vient souvent de lastPathSegment (ex. "msf:26"),
        // sans extension : on interroge le ContentResolver pour le vrai nom.
        val resolvedName = resolveDisplayName(uri) ?: fileName
        val extension = resolvedName.substringAfterLast('.', "").lowercase()

        // La signature binaire est plus fiable que l'extension : un PDF choisi
        // via SAF peut arriver avec un nom sans ".pdf" et être sinon lu comme texte.
        val magic = readMagicBytes(uri)
        return when {
            magic.startsWith("%PDF-") || extension == "pdf" -> parsePdf(uri, resolvedName)
            magic.startsWith("PK") || extension == "docx" -> parseDocx(uri, resolvedName)
            else -> parseTxt(uri, resolvedName)
        }
    }

    private fun resolveDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    } catch (_: Exception) {
        null
    }

    private fun readMagicBytes(uri: Uri): String = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(5)
            val read = stream.read(buffer)
            if (read > 0) String(buffer, 0, read, StandardCharsets.US_ASCII) else ""
        } ?: ""
    } catch (_: Exception) {
        ""
    }

    private fun parsePdf(uri: Uri, fileName: String): ParseResult {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Impossible d'ouvrir le fichier PDF : $fileName")

        return inputStream.use { stream ->
            PDDocument.load(stream).use { document ->
                val pageCount = document.numberOfPages
                val stripper = PDFTextStripper()
                stripper.sortByPosition = true
                val extractedText = stripper.getText(document).trim()

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

        // Filet de sécurité : un binaire (PDF/DOCX non reconnu) décodé en texte
        // contient des caractères de contrôle et des marqueurs PDF — on refuse.
        val controlChars = text.count { it.code < 32 && it != '\n' && it != '\r' && it != '\t' }
        val looksLikeRawPdf = text.contains("endstream") || text.contains("FlateDecode") || text.contains("%PDF-")
        if (controlChars > text.length / 100 || looksLikeRawPdf) {
            throw IllegalStateException(
                "Le fichier importé est un document binaire (PDF ou DOCX) qui n'a pas pu être décodé en texte. Réessayez de l'importer depuis sa source d'origine."
            )
        }

        val title = fileName.substringBeforeLast('.')
        return ParseResult(title = title, text = text, pageCount = 1)
    }

    private fun parseDocx(uri: Uri, fileName: String): ParseResult {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Impossible d'ouvrir le fichier DOCX : $fileName")

        val stringBuilder = StringBuilder()
        val maxEntries = 1000
        val maxTotalBytes = 50 * 1024 * 1024L // 50MB protection against zip bombs
        var totalBytesRead = 0L
        var entryCount = 0

        ZipInputStream(inputStream).use { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                entryCount++
                if (entryCount > maxEntries) {
                    throw SecurityException("Fichier DOCX corrompu ou suspect (dépassement du nombre maximal d'entrées).")
                }

                if (entry.name == "word/document.xml") {
                    val factory = DocumentBuilderFactory.newInstance().apply {
                        isNamespaceAware = true
                        isXIncludeAware = false
                        isExpandEntityReferences = false
                    }

                    // Security: Disable external DTDs and entities against XXE injection
                    try {
                        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                    } catch (_: Exception) {}
                    try {
                        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
                    } catch (_: Exception) {}
                    try {
                        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                    } catch (_: Exception) {}
                    try {
                        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
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
