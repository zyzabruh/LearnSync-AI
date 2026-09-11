package com.learnsyncai.data.parser

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Aller-retour Anki : export .apkg puis réimport (deck, cartes, cloze). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnkiRoundTripTest {

    @Test
    fun exportThenImportRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cards = listOf(
            "Capitale de la France ?" to "Paris",
            "La {{photosynthèse}} produit du glucose." to "La photosynthèse produit du glucose."
        )
        val bytes = AnkiExporter.exportDeck(context, "Deck Test", cards)
        assertTrue(bytes.size > 1000)

        val tmp = File(context.cacheDir, "roundtrip.apkg")
        tmp.writeBytes(bytes)
        val (deckName, imported) = AnkiImporter.importApkg(context, Uri.fromFile(tmp))
        tmp.delete()

        assertEquals("Deck Test", deckName)
        assertEquals(2, imported.size)
        assertTrue(imported.any { it.question.contains("Capitale") && it.answer.contains("Paris") })
        val cloze = imported.first { it.question.contains("photosynth") }
        assertTrue(cloze.question.contains("{{photosynthèse}}"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun exportEmptyDeckFails() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        AnkiExporter.exportDeck(context, "Vide", listOf("" to ""))
    }
}
