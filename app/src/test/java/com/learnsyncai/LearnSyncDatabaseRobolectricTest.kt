package com.learnsyncai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.learnsyncai.data.database.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LearnSyncDatabaseRobolectricTest {

    private lateinit var db: LearnSyncDatabase
    private lateinit var courseDao: CourseDao
    private lateinit var materialDao: StudyMaterialDao
    private lateinit var flashcardDao: FlashcardDao
    private lateinit var quizDao: QuizQuestionDao
    private lateinit var calendarDao: CalendarEventDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LearnSyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        courseDao = db.courseDao()
        materialDao = db.studyMaterialDao()
        flashcardDao = db.flashcardDao()
        quizDao = db.quizQuestionDao()
        calendarDao = db.calendarEventDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testCascadeDeletionOnCourseDelete() = runBlocking {
        val courseId = "course-test-1"
        val course = CourseEntity(
            id = courseId,
            title = "Algorithmique Avancée",
            description = "Structures de données",
            sourceFileName = "algo.pdf",
            sourceFileUri = "content://algo.pdf",
            extractedText = "Arbres binaires et graphes",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            progress = 50f,
            color = "#3B82F6",
            generationStatus = "COMPLETED"
        )
        courseDao.insertCourse(course)

        val material = StudyMaterialEntity(
            id = "mat-1",
            courseId = courseId,
            summary = "Résumé des graphes",
            keyPoints = """["Dijkstra","A*"]""",
            mnemonicTips = """["Astuce memoire"]""",
            generatedAt = System.currentTimeMillis(),
            version = 1
        )
        materialDao.insertMaterial(material)

        val flashcard = FlashcardEntity(
            id = "card-1",
            courseId = courseId,
            question = "Complexité de Dijkstra ?",
            answer = "O(E log V)",
            explanation = "Avec tas binaire",
            difficulty = 5.0f,
            box = 1,
            dueDate = System.currentTimeMillis() - 1000,
            interval = 1,
            easeFactor = 2.5f,
            repetitions = 1,
            lapses = 0,
            lastReviewedAt = null,
            createdAt = System.currentTimeMillis()
        )
        flashcardDao.insertFlashcard(flashcard)

        val quiz = QuizQuestionEntity(
            id = "quiz-1",
            courseId = courseId,
            question = "Quel algorithme pour le plus court chemin ?",
            options = """["Dijkstra","Kruskal","Prim","DFS"]""",
            correctAnswer = "Dijkstra",
            explanation = "Dijkstra calcule les plus courts chemins.",
            difficulty = "medium"
        )
        quizDao.insertQuizQuestions(listOf(quiz))

        val calendarEvent = CalendarEventEntity(
            id = "cal-1",
            courseId = courseId,
            title = "Révision Algorithmique",
            scheduledDate = System.currentTimeMillis(),
            androidEventId = 12345L,
            updatedAt = System.currentTimeMillis()
        )
        calendarDao.insertEvent(calendarEvent)

        // Verify inserted
        assertEquals(1, courseDao.getAllCourses().first().size)
        assertEquals(1, materialDao.getMaterialsForCourse(courseId).first().size)
        assertEquals(1, flashcardDao.getFlashcardsForCourse(courseId).first().size)
        assertEquals(1, quizDao.getQuizQuestionsForCourse(courseId).first().size)
        assertEquals(1, calendarDao.getEventsForCourse(courseId).first().size)

        // Delete Course
        courseDao.deleteCourse(course)

        // Verify Cascade deletion
        assertEquals(0, courseDao.getAllCourses().first().size)
        assertEquals(0, materialDao.getMaterialsForCourse(courseId).first().size)
        assertEquals(0, flashcardDao.getFlashcardsForCourse(courseId).first().size)
        assertEquals(0, quizDao.getQuizQuestionsForCourse(courseId).first().size)
        assertEquals(0, calendarDao.getEventsForCourse(courseId).first().size)
    }

    @Test
    fun testDueFlashcardsOnlyReturned() = runBlocking {
        val courseId = "course-due-test"
        val course = CourseEntity(courseId, "Title", "Desc", "f.pdf", "uri", "txt", 0L, 0L, 0f, "#000", "COMPLETED")
        courseDao.insertCourse(course)

        val now = 1_000_000L
        val overdueCard = FlashcardEntity("c1", courseId, "Q1", "A1", "E1", 5f, 1, now - 1000, 1, 2.5f, 0, 0, null, 0L)
        val dueCard = FlashcardEntity("c2", courseId, "Q2", "A2", "E2", 5f, 1, now, 1, 2.5f, 0, 0, null, 0L)
        val futureCard = FlashcardEntity("c3", courseId, "Q3", "A3", "E3", 5f, 1, now + 100_000, 1, 2.5f, 0, 0, null, 0L)

        flashcardDao.insertFlashcards(listOf(overdueCard, dueCard, futureCard))

        val dueForCourse = flashcardDao.getDueFlashcardsForCourse(courseId, now).first()
        assertEquals(2, dueForCourse.size)
        assertTrue(dueForCourse.any { it.id == "c1" })
        assertTrue(dueForCourse.any { it.id == "c2" })
        assertFalse(dueForCourse.any { it.id == "c3" })
    }

    @Test
    fun testStudyMaterialVersionTracking() = runBlocking {
        val courseId = "course-ver-test"
        val course = CourseEntity(courseId, "Title", "Desc", "f.pdf", "uri", "txt", 0L, 0L, 0f, "#000", "COMPLETED")
        courseDao.insertCourse(course)

        assertEquals(0, materialDao.getLatestVersionForCourse(courseId) ?: 0)

        val v1 = StudyMaterialEntity("m1", courseId, "Summary v1", "[]", "[]", 1000L, 1)
        materialDao.insertMaterial(v1)
        assertEquals(1, materialDao.getLatestVersionForCourse(courseId))

        val v2 = StudyMaterialEntity("m2", courseId, "Summary v2", "[]", "[]", 2000L, 2)
        materialDao.insertMaterial(v2)
        assertEquals(2, materialDao.getLatestVersionForCourse(courseId))

        val v3 = StudyMaterialEntity("m3", courseId, "Summary v3", "[]", "[]", 3000L, 3)
        materialDao.insertMaterial(v3)
        assertEquals(3, materialDao.getLatestVersionForCourse(courseId))
    }
}
