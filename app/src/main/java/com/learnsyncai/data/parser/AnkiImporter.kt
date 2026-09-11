package com.learnsyncai.data.parser

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Carte importée d'un paquet Anki (.apkg). */
data class ImportedAnkiCard(val question: String, val answer: String)

/**
 * Import de paquets Anki (.apkg) sans dépendance externe : un .apkg est un
 * zip contenant collection.anki2 (SQLite) + médias (ignorés ici).
 * Question = 1er champ, réponse = 2e champ, HTML nettoyé, cloze Anki
 * ({{c1::…}}) converti au format {{…}} de l'app.
 */
object AnkiImporter {
    const val MAX_CARDS = 2000
    private val CLOZE_PATTERN = Regex("\\{\\{c\\d+::(.*?)\\}\\}")
    private val TAG_PATTERN = Regex("<[^>]+>")

    fun importApkg(context: Context, uri: Uri, maxCards: Int = MAX_CARDS): Pair<String, List<ImportedAnkiCard>> {
        val tmpDir = File(context.cacheDir, "anki_import").apply { mkdirs() }
        val apkgFile = File(tmpDir, "import.apkg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            apkgFile.outputStream().use { out -> input.copyTo(out) }
        } ?: throw IllegalArgumentException("Impossible de lire le paquet Anki.")
        ZipFile(apkgFile).use { zip ->
            var dbEntry: ZipEntry? = null
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val candidate = entries.nextElement()
                if (!candidate.isDirectory && candidate.name.endsWith(".anki2")) {
                    dbEntry = candidate
                    break
                }
            }
            val entry = dbEntry
                ?: throw IllegalStateException("Paquet Anki invalide (collection introuvable).")
            val dbFile = File(tmpDir, "collection.anki2")
            zip.getInputStream(entry).use { input ->
                dbFile.outputStream().use { out -> input.copyTo(out) }
            }
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            try {
                return readDeckName(db) to readCards(db, maxCards)
            } finally {
                try { db.close() } catch (_: Exception) { }
                dbFile.delete()
            }
        }
    }

    private fun readDeckName(db: SQLiteDatabase): String = try {
        db.rawQuery("SELECT decks FROM col LIMIT 1", null)?.use { cursor ->
            if (!cursor.moveToFirst()) return ""
            val decks = JSONObject(cursor.getString(0))
            val names = mutableListOf<String>()
            val keys = decks.keys()
            while (keys.hasNext()) {
                val deck = decks.optJSONObject(keys.next()) ?: continue
                names.add(deck.optString("name", ""))
            }
            names.firstOrNull { it.isNotBlank() && !it.equals("Default", ignoreCase = true) }
                ?: names.firstOrNull { it.isNotBlank() }.orEmpty()
        } ?: ""
    } catch (_: Exception) {
        ""
    }

    private fun readCards(db: SQLiteDatabase, maxCards: Int): List<ImportedAnkiCard> {
        val cards = mutableListOf<ImportedAnkiCard>()
        val seen = mutableSetOf<String>()
        db.rawQuery("SELECT flds FROM notes", null)?.use { cursor ->
            val fldsIndex = cursor.getColumnIndex("flds")
            while (cursor.moveToNext() && cards.size < maxCards) {
                val raw = if (fldsIndex >= 0) cursor.getString(fldsIndex).orEmpty() else ""
                // Champs Anki separes par le caractere US (unit separator, code 31).
                val fields = raw.split(31.toChar().toString())
                val question = convertCloze(stripHtml(fields.getOrElse(0) { "" })).trim()
                val answer = convertCloze(stripHtml(fields.getOrElse(1) { "" })).trim()
                if (question.length < 2 || answer.isEmpty()) continue
                if (seen.add(question.lowercase())) {
                    cards.add(ImportedAnkiCard(question.take(600), answer.take(1200)))
                }
            }
        }
        return cards
    }

    internal fun convertCloze(text: String): String =
        CLOZE_PATTERN.replace(text, "{{$1}}")

    internal fun stripHtml(html: String): String {
        var text = html
            .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<(br|/p|/div|/li|/h[1-6])[^>]*>"), "\n")
        text = TAG_PATTERN.replace(text, " ")
        text = text.replace("[sound:", " [audio ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        return text.replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n\\s*\\n+"), "\n\n")
            .trim()
    }
}
