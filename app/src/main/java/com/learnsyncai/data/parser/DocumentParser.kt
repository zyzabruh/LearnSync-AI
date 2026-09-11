package com.learnsyncai.data.parser

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Entrée d'arbre du sommaire (outline) d'un PDF, issue de PdfBox. */
data class OutlineEntry(
    val title: String,
    val pageIndex: Int,
    val children: List<OutlineEntry> = emptyList()
)

data class ParseResult(
    val title: String,
    val text: String,
    val pageCount: Int,
    val isScanOrEmpty: Boolean = false,
    val outline: List<OutlineEntry> = emptyList()
)

class ScannedPdfException(
    val uri: Uri,
    val displayName: String,
    val pageCount: Int
) : IllegalStateException(
    "Ce PDF semble être un scan sans couche de texte. Vous pouvez lancer l'OCR pour l'importer."
)

class DocumentParser(private val context: Context) {

    init {
        try {
            PDFBoxResourceLoader.init(context)
        } catch (_: Exception) {}
    }

    fun parseDocument(uri: Uri, fileName: String): ParseResult {
        val resolvedName = resolveDisplayName(uri) ?: fileName
        val extension = resolvedName.substringAfterLast('.', "").lowercase()
        val magic = readMagicBytes(uri)
        return when {
            magic.startsWith("%PDF-") || extension == "pdf" -> parsePdf(uri, resolvedName)
            extension == "docx" -> parseDocx(uri, resolvedName)
            extension == "pptx" -> parsePptx(uri, resolvedName)
            magic.startsWith("PK") -> detectAndParseOffice(uri, resolvedName)
            else -> parseTxt(uri, resolvedName)
        }
    }

    private fun detectAndParseOffice(uri: Uri, fileName: String): ParseResult {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Impossible d'ouvrir le fichier : $fileName")
        val firstEntry = ZipInputStream(inputStream).use { zip ->
            generateSequence { zip.nextEntry }.firstOrNull { it.name == "word/document.xml" || it.name.startsWith("ppt/slides/slide") }?.name
        }
        return if (firstEntry == "word/document.xml") {
            parseDocx(uri, fileName)
        } else if (firstEntry != null) {
            parsePptx(uri, fileName)
        } else {
            parseTxt(uri, fileName)
        }
    }

    private fun parsePptx(uri: Uri, fileName: String): ParseResult {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Impossible d'ouvrir le fichier PPTX : $fileName")

        val slideTexts = mutableListOf<Pair<Int, String>>()
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        try { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Exception) {}
        try { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) } catch (_: Exception) {}
        try { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) } catch (_: Exception) {}
        try { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) } catch (_: Exception) {}

        ZipInputStream(inputStream).use { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                val slideMatch = Regex("^ppt/slides/slide(\\d+)\\.xml$").find(entry.name)
                if (slideMatch != null) {
                    val slideNumber = slideMatch.groupValues[1].toIntOrNull() ?: 0
                    val builder = factory.newDocumentBuilder()
                    val doc = builder.parse(zipStream)
                    val nodes = doc.getElementsByTagName("a:t")
                    val sb = StringBuilder()
                    for (i in 0 until nodes.length) {
                        sb.append(nodes.item(i).textContent).append(" ")
                    }
                    val text = sb.toString().trim()
                    if (text.isNotBlank()) {
                        slideTexts.add(slideNumber to text)
                    }
                }
                entry = zipStream.nextEntry
            }
        }

        val extractedText = slideTexts.sortedBy { it.first }
            .joinToString("\n\n") { (index, text) -> "Diapositive $index : $text" }
            .trim()

        if (extractedText.isBlank() || extractedText.count { it.isLetterOrDigit() } < 20) {
            throw IllegalStateException("Le fichier PPTX est vide ou ne contient aucun texte exploitable (présentation sans texte ?).")
        }

        val title = fileName.substringBeforeLast('.')
        return ParseResult(title = title, text = extractedText, pageCount = slideTexts.size)
    }

    fun parseWebUrl(url: String): ParseResult {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw IllegalArgumentException("L'URL doit commencer par http:// ou https://")
        }

        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) LearnSyncAI/1.0")

        val html = try {
            connection.inputStream.use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
        if (html.isBlank()) throw IllegalStateException("La page web est vide ou inaccessible.")

        val title = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)?.trim()
            ?.let { cleanHtmlEntities(it) }
            ?: url.substringAfter("://").substringBefore('/')

        val text = html
            .replace(Regex("(?is)<(script|style|noscript|svg|head)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<(br|/p|/div|/h[1-6]|/li)[^>]*>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .let { cleanHtmlEntities(it) }
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n\\s*\\n+"), "\n\n")
            .trim()

        if (text.count { it.isLetterOrDigit() } < 50) {
            throw IllegalStateException("Impossible d'extraire du texte lisible de cette page (page dynamique ou protégée ?).")
        }

        return ParseResult(title = title, text = text, pageCount = 1)
    }

    private fun cleanHtmlEntities(value: String): String = value
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")

    private fun resolveDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    } catch (_: Exception) { null }

    private fun readMagicBytes(uri: Uri): String = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(5)
            val read = stream.read(buffer)
            if (read > 0) String(buffer, 0, read, StandardCharsets.US_ASCII) else ""
        } ?: ""
    } catch (_: Exception) { "" }

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
                val alphanumericCount = extractedText.count { it.isLetterOrDigit() }
                if (alphanumericCount < 20) {
                    throw ScannedPdfException(uri, fileName, pageCount)
                }

                ParseResult(
                    title = title,
                    text = extractedText,
                    pageCount = if (pageCount > 0) pageCount else 1,
                    isScanOrEmpty = false,
                    outline = extractOutline(document, pageCount)
                )
            }
        }
    }

    /**
     * Texte d'une page (mode « texte » du lecteur : sélection au doigt et
     * création de notes depuis un passage). Extraction paresseuse par page
     * pour ne pas paralyser l'ouverture des gros PDF.
     */
    fun extractPageText(file: java.io.File, pageIndex: Int): String {
        if (!file.exists()) return ""
        return try {
            PDDocument.load(file).use { document ->
                if (pageIndex < 0 || pageIndex >= document.numberOfPages) return ""
                val stripper = PDFTextStripper()
                stripper.sortByPosition = true
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1
                stripper.getText(document).trim()
            }
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Boîtes de surlignage (coordonnées 0..1, origine en haut à gauche) pour
     * un passage sur une page : positions réelles du texte via PdfBox
     * (TextPosition), appariées par mots significatifs pour tolérer les
     * différences d'espacement. Max 8 boîtes (regroupées par ligne).
     */
    fun findTextRects(file: java.io.File, pageIndex: Int, query: String): List<android.graphics.RectF> {
        if (!file.exists() || query.isBlank()) return emptyList()
        return try {
            PDDocument.load(file).use { document ->
                if (pageIndex < 0 || pageIndex >= document.numberOfPages) return emptyList()
                val page = document.getPage(pageIndex)
                val pageW = page.cropBox.width
                val pageH = page.cropBox.height
                if (pageW <= 0f || pageH <= 0f) return emptyList()
                val chars = mutableListOf<TextChar>()
                val stripper = object : PDFTextStripper() {
                    override fun processTextPosition(text: TextPosition) {
                        super.processTextPosition(text)
                        val glyph = text.unicode
                        if (!glyph.isBlank()) {
                            chars.add(TextChar(glyph, text.xDirAdj, text.yDirAdj, text.widthDirAdj, text.heightDir))
                        }
                    }
                }
                stripper.sortByPosition = true
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1
                stripper.getText(document)
                matchTextSpan(chars, query, pageW, pageH)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private data class TextChar(val ch: String, val x: Float, val y: Float, val w: Float, val h: Float)

    private fun matchTextSpan(
        chars: List<TextChar>,
        query: String,
        pageW: Float,
        pageH: Float
    ): List<android.graphics.RectF> {
        if (chars.isEmpty()) return emptyList()
        // Texte normalisé (espaces repliés) + index d'origine de chaque caractère.
        val norm = StringBuilder()
        val origin = mutableListOf<Int>()
        chars.forEachIndexed { i, c ->
            if (c.ch.isBlank()) {
                if (norm.isNotEmpty() && norm.last() != ' ') {
                    norm.append(' ')
                    origin.add(i)
                }
            } else {
                norm.append(c.ch)
                origin.add(i)
            }
        }
        val hay = norm.toString()
        if (hay.length < 4) return emptyList()
        val words = query.replace(Regex("\\s+"), " ").trim()
            .split(" ").filter { it.length >= 4 }.take(8)
        if (words.isEmpty()) return emptyList()
        // Occurrences (max 10/mot) de chaque mot significatif.
        val occs = words.map { w ->
            val list = mutableListOf<Int>()
            var i = hay.indexOf(w, 0, ignoreCase = true)
            while (i >= 0 && list.size < 10) {
                list.add(i)
                i = hay.indexOf(w, i + 1, ignoreCase = true)
            }
            list
        }
        // Meilleure grappe : autour de chaque occurrence du 1er mot, compte
        // les autres mots dans une fenêtre proportionnelle à la requête.
        val span = (query.length * 2).coerceIn(60, 600)
        var bestStart = -1
        var bestEnd = -1
        var bestScore = if (words.size == 1) 1 else 2
        for (anchor in occs[0]) {
            var end = anchor + words[0].length
            var score = 1
            for (k in 1 until words.size) {
                val near = occs[k].firstOrNull { it in anchor - 20..anchor + span }
                if (near != null) {
                    score++
                    end = maxOf(end, near + words[k].length)
                }
            }
            if (score > bestScore || (score == bestScore && bestStart < 0)) {
                bestScore = score
                bestStart = anchor
                bestEnd = end.coerceAtMost(hay.length)
            }
        }
        if (bestStart < 0) return emptyList()
        // Boîtes par ligne (regroupement vertical), origine convertie en haut-gauche.
        val spanChars = origin.subList(bestStart, bestEnd).map { chars[it] }
        val lines = mutableListOf<MutableList<TextChar>>()
        for (c in spanChars.sortedWith(compareBy({ it.y }, { it.x }))) {
            val line = lines.lastOrNull()
            val refY = line?.map { it.y + it.h / 2f }?.average() ?: Double.NaN
            if (line == null || kotlin.math.abs(c.y + c.h / 2f - refY) > c.h.coerceAtLeast(4f)) {
                lines.add(mutableListOf(c))
            } else {
                line.add(c)
            }
        }
        return lines.take(8).mapNotNull { line ->
            val x0 = line.minOf { it.x }.coerceAtLeast(0f)
            val x1 = line.maxOf { it.x + it.w }
            val yTopPdf = line.minOf { it.y }
            val yBotPdf = line.maxOf { it.y + it.h }
            android.graphics.RectF(
                x0 / pageW,
                1f - yBotPdf / pageH,
                (x1 / pageW).coerceAtMost(1f),
                (1f - yTopPdf / pageH).coerceAtMost(1f)
            )
        }.filter { it.width() > 0.005f && it.height() > 0.002f }
    }

    private fun extractOutline(document: PDDocument, pageCount: Int): List<OutlineEntry> {
        val root: PDOutlineNode = try {
            document.documentCatalog.documentOutline
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        return try {
            root.children().map { node -> buildOutlineEntry(node, document, pageCount) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildOutlineEntry(node: PDOutlineItem, document: PDDocument, pageCount: Int): OutlineEntry {
        val pageIndex = resolvePageIndex(node, document, pageCount)
        val children = try {
            node.children().map { child -> buildOutlineEntry(child, document, pageCount) }
        } catch (_: Exception) {
            emptyList()
        }
        return OutlineEntry(
            title = try { node.title } catch (_: Exception) { "" },
            pageIndex = pageIndex,
            children = children
        )
    }

    private fun resolvePageIndex(item: PDOutlineItem, document: PDDocument, pageCount: Int): Int {
        if (pageCount <= 0) return 0
        return try {
            resolveDestinationToPage(item.destination, document)?.let {
                return it.coerceIn(0, pageCount - 1)
            }
            val action = try { item.action } catch (_: Exception) { null }
            if (action is PDActionGoTo) {
                resolveDestinationToPage(action.destination, document)?.let {
                    return it.coerceIn(0, pageCount - 1)
                }
            }
            0
        } catch (_: Exception) {
            0
        }
    }

    private fun resolveDestinationToPage(dest: PDDestination?, document: PDDocument): Int? {
        if (dest == null) return null
        return try {
            when (dest) {
                is PDPageDestination -> {
                    val page = try { dest.page } catch (_: Exception) { null } ?: return null
                    pageIndexOf(document, page)
                }
                is PDNamedDestination -> {
                    val resolved = try {
                        document.documentCatalog.findNamedDestinationPage(dest)
                    } catch (_: Exception) { null } ?: return null
                    resolveDestinationToPage(resolved, document)
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun pageIndexOf(document: PDDocument, page: PDPage): Int? {
        return try {
            val index = document.pages.indexOf(page)
            if (index >= 0) index else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTxt(uri: Uri, fileName: String): ParseResult {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Impossible d'ouvrir le fichier texte : $fileName")

        val text = try {
            val utf8 = String(bytes, StandardCharsets.UTF_8)
            if (utf8.contains('\uFFFD')) String(bytes, Charset.forName("ISO-8859-1")) else utf8
        } catch (_: Exception) {
            String(bytes, StandardCharsets.UTF_8)
        }.trim()

        if (text.isBlank() || text.count { it.isLetterOrDigit() } < 5) {
            throw IllegalStateException("Le fichier texte est vide ou ne contient aucun contenu lisible.")
        }

        val controlChars = text.count { it.code < 32 && it != '\n' && it != '\r' && it != '\t' }
        val looksLikeRawPdf = text.contains("endstream") || text.contains("FlateDecode") || text.contains("%PDF-")
        if (controlChars > text.length / 100 || looksLikeRawPdf) {
            throw IllegalStateException("Le fichier importé est un document binaire (PDF ou DOCX) qui n'a pas pu être décodé en texte. Réessayez de l'importer depuis sa source d'origine.")
        }

        val title = fileName.substringBeforeLast('.')
        return ParseResult(title = title, text = text, pageCount = 1)
    }

    private fun parseDocx(uri: Uri, fileName: String): ParseResult {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Impossible d'ouvrir le fichier DOCX : $fileName")

        val stringBuilder = StringBuilder()
        val maxEntries = 1000
        var entryCount = 0

        ZipInputStream(inputStream).use { zipStream ->
            var entry = zipStream.nextEntry
            var entryCount = 0
            while (entry != null && entryCount < maxEntries) {
                entryCount++
                if (entry.name == "word/document.xml") {
                    val factory = DocumentBuilderFactory.newInstance().apply {
                        isNamespaceAware = true
                        isXIncludeAware = false
                        isExpandEntityReferences = false
                    }
                    try { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Exception) {}
                    try { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) } catch (_: Exception) {}
                    try { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) } catch (_: Exception) {}
                    try { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) } catch (_: Exception) {}

                    val builder = factory.newDocumentBuilder()
                    val doc = builder.parse(zipStream)
                    val nodeList = doc.getElementsByTagName("w:t")
                    for (i in 0 until nodeList.length) {
                        stringBuilder.append(nodeList.item(i).textContent).append(" ")
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