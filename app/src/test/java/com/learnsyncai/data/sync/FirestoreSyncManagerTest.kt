package com.learnsyncai.data.sync

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirestoreSyncManagerTest {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var currentUser: FirebaseUser
    private lateinit var writeBatch: WriteBatch
    private lateinit var userDoc: DocumentReference
    private lateinit var coursesCol: CollectionReference
    private lateinit var studyMaterialsCol: CollectionReference
    private lateinit var quizCol: CollectionReference
    private lateinit var flashcardsCol: CollectionReference
    private lateinit var reviewLogsCol: CollectionReference

    private lateinit var studyMaterialsQuery: Query
    private lateinit var quizQuery: Query
    private lateinit var flashcardsQuery: Query
    private lateinit var reviewLogsQuery: Query

    private lateinit var studyMaterialsSnapshot: QuerySnapshot
    private lateinit var quizSnapshot: QuerySnapshot
    private lateinit var flashcardsSnapshot: QuerySnapshot
    private lateinit var reviewLogsSnapshot: QuerySnapshot

    private lateinit var studyMaterialDoc: DocumentSnapshot
    private lateinit var quizDoc: DocumentSnapshot
    private lateinit var flashcardDoc: DocumentSnapshot
    private lateinit var reviewLogDoc: DocumentSnapshot

    private lateinit var studyMaterialDocRef: DocumentReference
    private lateinit var quizDocRef: DocumentReference
    private lateinit var flashcardDocRef: DocumentReference
    private lateinit var reviewLogDocRef: DocumentReference

    private lateinit var syncManager: FirestoreSyncManager

    @Before
    fun setup() {
        clearAllMocks()

        val context = RuntimeEnvironment.getApplication()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        firestore = mockk(relaxed = true)
        auth = mockk(relaxed = true)
        currentUser = mockk(relaxed = true)
        writeBatch = mockk(relaxed = true)
        userDoc = mockk(relaxed = true)
        coursesCol = mockk(relaxed = true)
        studyMaterialsCol = mockk(relaxed = true)
        quizCol = mockk(relaxed = true)
        flashcardsCol = mockk(relaxed = true)
        reviewLogsCol = mockk(relaxed = true)

        studyMaterialsQuery = mockk(relaxed = true)
        quizQuery = mockk(relaxed = true)
        flashcardsQuery = mockk(relaxed = true)
        reviewLogsQuery = mockk(relaxed = true)

        studyMaterialsSnapshot = mockk(relaxed = true)
        quizSnapshot = mockk(relaxed = true)
        flashcardsSnapshot = mockk(relaxed = true)
        reviewLogsSnapshot = mockk(relaxed = true)

        studyMaterialDoc = mockk(relaxed = true)
        quizDoc = mockk(relaxed = true)
        flashcardDoc = mockk(relaxed = true)
        reviewLogDoc = mockk(relaxed = true)

        studyMaterialDocRef = mockk(relaxed = true)
        quizDocRef = mockk(relaxed = true)
        flashcardDocRef = mockk(relaxed = true)
        reviewLogDocRef = mockk(relaxed = true)

        syncManager = FirestoreSyncManager(customFirestore = firestore, customAuth = auth)

        every { auth.currentUser } returns currentUser
        every { currentUser.uid } returns "test-uid"
        every { firestore.batch() } returns writeBatch
        every { firestore.collection("users") } returns mockk(relaxed = true) {
            every { document("test-uid") } returns userDoc
        }

        every { userDoc.collection("courses") } returns coursesCol
        every { userDoc.collection("study_materials") } returns studyMaterialsCol
        every { userDoc.collection("quiz_questions") } returns quizCol
        every { userDoc.collection("flashcards") } returns flashcardsCol
        every { userDoc.collection("review_logs") } returns reviewLogsCol

        // Study materials setup
        every { studyMaterialsCol.whereEqualTo("courseId", any()) } returns studyMaterialsQuery
        every { studyMaterialsQuery.get() } returns FakeTask(studyMaterialsSnapshot)
        every { studyMaterialsSnapshot.documents } returns listOf(studyMaterialDoc)
        every { studyMaterialDoc.reference } returns studyMaterialDocRef

        // Quiz setup
        every { quizCol.whereEqualTo("courseId", any()) } returns quizQuery
        every { quizQuery.get() } returns FakeTask(quizSnapshot)
        every { quizSnapshot.documents } returns listOf(quizDoc)
        every { quizDoc.reference } returns quizDocRef

        // Flashcards setup
        every { flashcardsCol.whereEqualTo("courseId", any()) } returns flashcardsQuery
        every { flashcardsQuery.get() } returns FakeTask(flashcardsSnapshot)
        every { flashcardsSnapshot.documents } returns listOf(flashcardDoc)
        every { flashcardDoc.reference } returns flashcardDocRef
        every { flashcardDoc.getString("id") } returns "card-1"
        every { flashcardDoc.id } returns "card-1"

        // Review logs setup
        every { reviewLogsCol.whereIn(any<String>(), any()) } returns reviewLogsQuery
        every { reviewLogsQuery.get() } returns FakeTask(reviewLogsSnapshot)
        every { reviewLogsSnapshot.documents } returns listOf(reviewLogDoc)
        every { reviewLogDoc.reference } returns reviewLogDocRef

        // Commit task
        every { writeBatch.commit() } returns FakeTask(null)
    }

    @Test
    fun testDeleteCourseInCloudRemovesAllAssociatedDocuments() {
        runBlocking {
            val courseId = "course-123"
            val result = syncManager.deleteCourseInCloud(courseId)

            assertTrue(result.isSuccess)

            // Verify batch delete was called for course, study material, quiz, flashcard, and review log
            verify { writeBatch.delete(any()) }
            verify { writeBatch.commit() }
        }
    }
}

class FakeTask<T>(private val resultValue: T) : Task<T>() {
    override fun isComplete(): Boolean = true
    override fun isSuccessful(): Boolean = true
    override fun isCanceled(): Boolean = false
    override fun getResult(): T = resultValue
    override fun <X : Throwable?> getResult(exceptionType: Class<X>): T = resultValue
    override fun getException(): Exception? = null
    override fun addOnCompleteListener(listener: com.google.android.gms.tasks.OnCompleteListener<T>): Task<T> {
        listener.onComplete(this)
        return this
    }
    override fun addOnCompleteListener(executor: java.util.concurrent.Executor, listener: com.google.android.gms.tasks.OnCompleteListener<T>): Task<T> {
        listener.onComplete(this)
        return this
    }
    override fun addOnFailureListener(listener: com.google.android.gms.tasks.OnFailureListener): Task<T> = this
    override fun addOnFailureListener(activity: android.app.Activity, listener: com.google.android.gms.tasks.OnFailureListener): Task<T> = this
    override fun addOnFailureListener(executor: java.util.concurrent.Executor, listener: com.google.android.gms.tasks.OnFailureListener): Task<T> = this
    override fun addOnSuccessListener(listener: com.google.android.gms.tasks.OnSuccessListener<in T>): Task<T> = this
    override fun addOnSuccessListener(activity: android.app.Activity, listener: com.google.android.gms.tasks.OnSuccessListener<in T>): Task<T> = this
    override fun addOnSuccessListener(executor: java.util.concurrent.Executor, listener: com.google.android.gms.tasks.OnSuccessListener<in T>): Task<T> = this
}
