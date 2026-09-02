package com.learnsyncai.data.sync

import com.learnsyncai.domain.model.Course
import com.learnsyncai.domain.model.Tombstone
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
    fun testUnauthenticatedFetchDeletedIdsReturnsFailure() = runBlocking {
        val syncManager = FirestoreSyncManager(customFirestore = null, customAuth = null)
        val result = syncManager.fetchRemoteDeletedIds()
        assertTrue(result.isFailure)
    }

    @Test
    fun testUnauthenticatedMarkDeletedReturnsFailure() = runBlocking {
        val syncManager = FirestoreSyncManager(customFirestore = null, customAuth = null)
        val result = syncManager.markDeletedInCloud(listOf(Tombstone(Tombstone.TYPE_COURSE, "course-123")))
        assertTrue(result.isFailure)
    }

    @Test
    fun testMarkDeletedWithEmptyListSucceedsWithoutFirebase() = runBlocking {
        val syncManager = FirestoreSyncManager(customFirestore = null, customAuth = null)
        // Aucun tombstone : rien à propager, succès immédiat sans toucher à Firebase
        val result = syncManager.markDeletedInCloud(emptyList())
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
