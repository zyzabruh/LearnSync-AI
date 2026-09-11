package com.learnsyncai.data.parser

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Export d'un paquet Anki (.apkg) sans dépendance externe : base SQLite
 * collection.anki2 au schéma Anki (col/notes/cards + tables vides) + fichier
 * media, le tout zippé. Compatible import Anki Desktop / AnkiDroid.
 */
object AnkiExporter {

    /**
     * Construit le .apkg en mémoire : nom du paquet + paires (question, réponse).
     * Le cloze local {{…}} devient du cloze Anki {{cN::…}}.
     */
    fun exportDeck(context: Context, deckName: String, cards: List<Pair<String, String>>): ByteArray {
        val clean = cards.mapNotNull { (q, a) ->
            val question = q.trim()
            val answer = a.trim()
            if (question.length < 2 || answer.isEmpty()) null else question to answer
        }.distinctBy { it.first.lowercase() }.take(5000)
        require(clean.isNotEmpty()) { "Aucune carte à exporter." }

        val tmpDir = File(context.cacheDir, "anki_export").apply { mkdirs() }
        val dbFile = File(tmpDir, "collection_export.anki2")
        if (dbFile.exists()) dbFile.delete()
        val nowSec = System.currentTimeMillis() / 1000
        val safeDeck = deckName.trim().take(120).ifBlank { "LearnSync" }
        val modelId = 1000000000000L + (Math.random() * 899999999999L).toLong()

        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            createSchema(db)
            insertCol(db, nowSec, safeDeck, modelId)
            var nid = nowSec * 1000
            for ((question, answer) in clean) {
                nid += 7
                val qHtml = toHtml(toAnkiCloze(question))
                val aHtml = toHtml(toAnkiCloze(answer))
                val guid = newGuid()
                val flds = qHtml + fieldSep() + aHtml
                db.execSQL(
                    "INSERT INTO notes (id, guid, mid, mod, usn, tags, flds, sfld, csum, flags, data) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    arrayOf<Any>(nid, guid, modelId, nowSec, -1, "", flds, qHtml, checksum(qHtml), 0, "")
                )
                val cid = nid + 1
                db.execSQL(
                    "INSERT INTO cards (id, nid, did, ord, mod, usn, type, queue, due, ivl, factor, reps, lapses, left, odue, odid, flags, data) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    arrayOf<Any>(cid, nid, 1, 0, nowSec, -1, 0, 0, nid, 0, 0, 0, 0, 0, 0, 0, 0, "")
                )
            }
        } finally {
            try { db.close() } catch (_: Exception) { }
        }

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("collection.anki2"))
            dbFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("media"))
            zip.write("{}".toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        dbFile.delete()
        return out.toByteArray()
    }

    private fun fieldSep(): String = 31.toChar().toString()

    private fun toAnkiCloze(text: String): String {
        var n = 0
        return Regex("\\{\\{(.*?)\\}\\}").replace(text) { m ->
            n++
            "{{c" + n + "::" + m.groupValues[1] + "}}"
        }
    }

    private fun toHtml(text: String): String =
        text.trim().split(Regex("\\n+")).joinToString("<br>") { it.trim() }

    private fun newGuid(): String {
        val alphabet = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        val rnd = java.util.Random()
        return (1..10).map { alphabet[rnd.nextInt(alphabet.size)] }.joinToString("")
    }

    private fun checksum(sfld: String): Int {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(sfld.toByteArray(Charsets.UTF_8))
        return ((digest[0].toInt() and 0xFF) shl 24) or
            ((digest[1].toInt() and 0xFF) shl 16) or
            ((digest[2].toInt() and 0xFF) shl 8) or
            (digest[3].toInt() and 0xFF)
    }

    private fun createSchema(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE col (id INTEGER PRIMARY KEY, crt INTEGER NOT NULL, mod INTEGER NOT NULL, scm INTEGER NOT NULL, ver INTEGER NOT NULL, dty INTEGER NOT NULL, usn INTEGER NOT NULL, ls INTEGER NOT NULL, conf TEXT NOT NULL, models TEXT NOT NULL, decks TEXT NOT NULL, dconf TEXT NOT NULL, tags TEXT NOT NULL)")
        db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY, guid TEXT NOT NULL, mid INTEGER NOT NULL, mod INTEGER NOT NULL, usn INTEGER NOT NULL, tags TEXT NOT NULL, flds TEXT NOT NULL, sfld INTEGER NOT NULL, csum INTEGER NOT NULL, flags INTEGER NOT NULL, data TEXT NOT NULL)")
        db.execSQL("CREATE TABLE cards (id INTEGER PRIMARY KEY, nid INTEGER NOT NULL, did INTEGER NOT NULL, ord INTEGER NOT NULL, mod INTEGER NOT NULL, usn INTEGER NOT NULL, type INTEGER NOT NULL, queue INTEGER NOT NULL, due INTEGER NOT NULL, ivl INTEGER NOT NULL, factor INTEGER NOT NULL, reps INTEGER NOT NULL, lapses INTEGER NOT NULL, left INTEGER NOT NULL, odue INTEGER NOT NULL, odid INTEGER NOT NULL, flags INTEGER NOT NULL, data TEXT NOT NULL)")
        db.execSQL("CREATE TABLE revlog (id INTEGER PRIMARY KEY, cid INTEGER NOT NULL, usn INTEGER NOT NULL, ease INTEGER NOT NULL, ivl INTEGER NOT NULL, lastIvl INTEGER NOT NULL, factor INTEGER NOT NULL, time INTEGER NOT NULL, type INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE graves (usn INTEGER NOT NULL, type INTEGER NOT NULL, oid INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX ix_notes_usn ON notes (usn)")
        db.execSQL("CREATE INDEX ix_cards_usn ON cards (usn)")
        db.execSQL("CREATE INDEX ix_cards_nid ON cards (nid)")
        db.execSQL("CREATE INDEX ix_cards_sched ON cards (did, queue, due)")
        db.execSQL("CREATE INDEX ix_revlog_usn ON revlog (usn)")
        db.execSQL("CREATE INDEX ix_revlog_cid ON revlog (cid)")
    }

    private fun insertCol(db: SQLiteDatabase, nowSec: Long, deckName: String, modelId: Long) {
        val models = JSONObject().put(
            modelId.toString(),
            JSONObject().apply {
                put("id", modelId)
                put("name", "Basique")
                put("type", 0)
                put("mod", nowSec)
                put("usn", -1)
                put("sortf", 0)
                put("did", 1)
                put("tmpls", JSONArray().put(
                    JSONObject().apply {
                        put("name", "Carte 1")
                        put("ord", 0)
                        put("qfmt", "{{Recto}}")
                        put("afmt", "{{FrontSide}}\n\n<hr id=answer>\n\n{{Verso}}")
                        put("bqfmt", "")
                        put("bafmt", "")
                        put("bfont", "")
                        put("bsize", "")
                    }
                ))
                put("flds", JSONArray()
                    .put(fieldDef("Recto", 0))
                    .put(fieldDef("Verso", 1)))
                put("css", ".card { font-family: arial; font-size: 20px; text-align: center; color: black; background-color: white; }")
                put("latexPre", "\\documentclass[12pt]{article}\n\\usepackage{amssymb,amsmath}\n\\begin{document}\n")
                put("latexPost", "\\end{document}")
                put("req", JSONArray().put(JSONArray().put(0).put("any").put(JSONArray().put(0))))
            }
        )
        val decks = JSONObject().put(
            "1",
            JSONObject().apply {
                put("id", 1)
                put("mod", nowSec)
                put("name", deckName)
                put("usn", -1)
                put("desc", "Exporté depuis LearnSync AI")
                put("dyn", 0)
                put("collapsed", false)
            }
        )
        val dconf = JSONObject().put(
            "1",
            JSONObject().apply {
                put("id", 1)
                put("mod", nowSec)
                put("name", "Default")
                put("usn", -1)
            }
        )
        db.execSQL(
            "INSERT INTO col (id, crt, mod, scm, ver, dty, usn, ls, conf, models, decks, dconf, tags) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any>(
                1, nowSec, nowSec, System.currentTimeMillis(), 11, 0, -1, 0,
                "{}", models.toString(), decks.toString(), dconf.toString(), "{}"
            )
        )
    }

    private fun fieldDef(name: String, ord: Int): JSONObject =
        JSONObject().apply {
            put("name", name)
            put("ord", ord)
            put("font", "Arial")
            put("size", 20)
            put("media", JSONArray())
            put("rtl", false)
            put("sticky", false)
        }
}
