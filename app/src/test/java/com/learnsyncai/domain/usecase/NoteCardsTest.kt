package com.learnsyncai.domain.usecase

import org.junit.Assert.*
import org.junit.Test

/** Éditeur à blocs : une ligne marquée = une carte (1-tap). */
class NoteCardsTest {

    @Test
    fun forwardMarker() {
        val parsed = NoteCards.parseLine("Mitochondrie >> centrale énergétique")
        assertNotNull(parsed)
        assertEquals("Mitochondrie", parsed!!.question)
        assertEquals("centrale énergétique", parsed.answer)
        assertEquals(CardDirection.FORWARD, parsed.direction)
    }

    @Test
    fun bothMarker() {
        val parsed = NoteCards.parseLine("ATP <> énergie cellulaire")
        assertNotNull(parsed)
        assertEquals(CardDirection.BOTH, parsed!!.direction)
    }

    @Test
    fun clozePriorityOverMarkers() {
        val parsed = NoteCards.parseLine("La {{mitochondrie}} produit l'ATP >> énergie")
        assertNotNull(parsed)
        assertTrue(CardContent.hasClozes(parsed!!.question))
    }

    @Test
    fun plainLineIgnored() {
        assertNull(NoteCards.parseLine("Simple note sans marqueur"))
        assertNull(NoteCards.parseLine("AB"))
    }
}
