package com.learnsyncai.data.sync

import com.learnsyncai.domain.model.Course
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirestoreSyncManagerTest {

    @Test
    fun testUnauthenticatedDeleteGracefullySucceedsLocally() = runBlocking {
        val syncManager = FirestoreSyncManager(customFirestore = null, customAuth = null)
        val result = syncManager.deleteCourseInCloud("course-123")
        // Should succeed gracefully so local deletion is never blocked when not signed in
        assertTrue(result.isSuccess)
    }

    @Test
    fun testUnauthenticatedSyncUpReturnsFailure() = runBlocking {
        val syncManager = FirestoreSyncManager(customFirestore = null, customAuth = null)
        val result = syncManager.syncUpCourses(emptyList<Course>())
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.message ?: ""
        assertTrue(msg.contains("non initialisé") || msg.contains("Authentification requise") || msg.contains("connecter"))
    }

    @Test
    fun testUnauthenticatedSyncDownReturnsFailure() = runBlocking {
        val syncManager = FirestoreSyncManager(customFirestore = null, customAuth = null)
        val result = syncManager.fetchRemoteCourses()
        assertTrue(result.isFailure)
    }
}
