package com.learnsyncai.data.parser

import org.junit.Assert.*
import org.junit.Test

/** Import Anki : conversion cloze et nettoyage HTML (parties pures). */
class AnkiImporterTest {

    @Test
    fun clozeAnkiConvertedToAppFormat() {
        assertEquals(
            "La {{photosynthèse}} produit du {{glucose}}.",
            AnkiImporter.convertCloze("La {{c1::photosynthèse}} produit du {{c2::glucose}}.")
        )
    }

    @Test
    fun htmlStrippedAndEntitiesDecoded() {
        val raw = "<div>La <b>mitose</b> comporte plusieurs phases.</div><br>Voir&nbsp;schéma &amp; légende."
        val clean = AnkiImporter.stripHtml(raw)
        assertTrue(clean.contains("La mitose comporte plusieurs phases."))
        assertTrue(clean.contains("Voir schéma & légende."))
        assertFalse(clean.contains("<"))
    }

    @Test
    fun soundMarkersKeptAsText() {
        val clean = AnkiImporter.stripHtml("Mot [sound:prononciation.mp3]")
        assertTrue(clean.contains("[audio prononciation.mp3]"))
    }
}
