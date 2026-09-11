package com.learnsyncai.data.storage

import androidx.test.core.app.ApplicationProvider
import com.learnsyncai.domain.model.InkStroke
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Encre libre : persistance JSON des traits par cours. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InkStorageTest {

    private fun storage(): CourseContentStorage =
        CourseContentStorage(ApplicationProvider.getApplicationContext())

    @Test
    fun inkRoundTrip() = runBlocking {
        val strokes = listOf(
            InkStroke(page = 2, color = -256L, points = listOf(0.1f, 0.2f, 0.3f, 0.4f)),
            InkStroke(page = 2, color = -16711936L, points = listOf(0.5f, 0.5f, 0.6f, 0.7f))
        )
        storage().saveInkStrokes("cours-ink", strokes)
        val loaded = storage().getInkStrokes("cours-ink")
        assertEquals(2, loaded.size)
        assertEquals(2, loaded[0].page)
        assertEquals(-256L, loaded[0].color)
        assertEquals(strokes[0].points, loaded[0].points)
    }

    @Test
    fun inkMissingIsEmpty() = runBlocking {
        assertTrue(storage().getInkStrokes("cours-inexistant").isEmpty())
    }

    @Test
    fun inkDeleteClears() = runBlocking {
        val s = storage()
        s.saveInkStrokes("cours-del", listOf(InkStroke(0, -256L, listOf(0f, 0f, 1f, 1f))))
        assertEquals(1, s.getInkStrokes("cours-del").size)
        s.deleteInkStrokes("cours-del")
        assertTrue(s.getInkStrokes("cours-del").isEmpty())
    }
}
