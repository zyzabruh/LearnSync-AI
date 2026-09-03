package com.learnsyncai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.learnsyncai.data.database.*
import com.learnsyncai.data.repository.ReviewRepositoryImpl
import com.learnsyncai.data.repository.toDomain
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
        val course = CourseEntity(courseId, "Title", "Desc", "f.pdf", "uri", 0L, 0L, 0f, "#000", "COMPLETED")
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
        val course = CourseEntity(courseId, "Title", "Desc", "f.pdf", "uri", 0L, 0L, 0f, "#000", "COMPLETED")
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

        val latest = materialDao.getLatestMaterialForCourse(courseId)
        assertEquals("m3", latest?.id)
        assertEquals("Summary v3", latest?.summary)
    }

    @Test
    fun testAiProfileDaoOperations() = runBlocking {
        val aiProfileDao = db.aiProfileDao()
        val p1 = AiProfileEntity("p1", "Gemini Direct", "GEMINI", "https://api.gemini", "key1", "gemini-2.0-flash", true, 100L)
        val p2 = AiProfileEntity("p2", "OpenRouter", "OPENROUTER", "https://openrouter.ai", "key2", "gpt-4o", false, 200L)

        aiProfileDao.insertProfile(p1)
        aiProfileDao.insertProfile(p2)

        val profiles = aiProfileDao.getAllProfiles().first()
        assertEquals(2, profiles.size)

        val active = aiProfileDao.getActiveProfile()
        assertNotNull(active)
        assertEquals("p1", active?.id)

        // Switch active
        aiProfileDao.setActiveProfile("p2")
        val newActive = aiProfileDao.getActiveProfile()
        assertEquals("p2", newActive?.id)

        // Delete profile
        aiProfileDao.deleteProfile("p1")
        val remaining = aiProfileDao.getAllProfiles().first()
        assertEquals(1, remaining.size)
        assertEquals("p2", remaining.first().id)
    }

    @Test
    fun testIndividualFlashcardAndQuizDeletion() = runBlocking {
        val courseId = "course-delete-test"
        val course = CourseEntity(courseId, "Title", "Desc", "f.pdf", "uri", 0L, 0L, 0f, "#000", "COMPLETED")
        courseDao.insertCourse(course)

        val card = FlashcardEntity("c-to-delete", courseId, "Q", "A", "E", 5f, 1, 0L, 1, 2.5f, 0, 0, null, 0L)
        flashcardDao.insertFlashcard(card)
        assertEquals(1, flashcardDao.getFlashcardsForCourse(courseId).first().size)

        flashcardDao.deleteFlashcardById("c-to-delete")
        assertEquals(0, flashcardDao.getFlashcardsForCourse(courseId).first().size)

        val quiz = QuizQuestionEntity("q-to-delete", courseId, "Question ?", "A\u001FB\u001FC\u001FD", "A", "Expl", "easy")
        quizDao.insertQuizQuestion(quiz)
        assertEquals(1, quizDao.getQuizQuestionsForCourse(courseId).first().size)

        quizDao.deleteQuizQuestionById("q-to-delete")
        assertEquals(0, quizDao.getQuizQuestionsForCourse(courseId).first().size)
    }

    @Test
    fun testReplaceCourseContentCarriesFsrsState() = runBlocking {
        val courseId = "course-regen-test"
        courseDao.insertCourse(CourseEntity(courseId, "Title", "Desc", "f.pdf", "uri", 0L, 0L, 0f, "#000", "COMPLETED"))

        // Carte déjà bien avancée dans FSRS (question avec casse/espacements irréguliers)
        val reviewedCard = FlashcardEntity(
            id = "old-card", courseId = courseId,
            question = "  Complexité de   dijkstra ? ", answer = "O(E log V)", explanation = "",
            difficulty = 4.2f, box = 3, dueDate = 123_456L, interval = 7, easeFactor = 2.6f,
            repetitions = 4, lapses = 1, lastReviewedAt = 999L, createdAt = 0L
        )
        flashcardDao.insertFlashcard(reviewedCard)

        val regenerated = listOf(
            // Même question (casse/espacements différents) : doit reprendre l'état FSRS
            FlashcardEntity(
                id = "new-uuid-1", courseId = courseId,
                question = "Complexité de Dijkstra ?", answer = "O(E log V)", explanation = "",
                difficulty = 5.0f, box = 1, dueDate = System.currentTimeMillis(), interval = 0,
                easeFactor = 1.0f, repetitions = 0, lapses = 0, lastReviewedAt = null, createdAt = 1L
            ),
            // Question inédite : doit rester aux valeurs par défaut
            FlashcardEntity(
                id = "new-uuid-2", courseId = courseId,
                question = "Question toute nouvelle", answer = "Réponse", explanation = "",
                difficulty = 5.0f, box = 1, dueDate = System.currentTimeMillis(), interval = 0,
                easeFactor = 1.0f, repetitions = 0, lapses = 0, lastReviewedAt = null, createdAt = 1L
            )
        )
        val material = StudyMaterialEntity("mat-regen", courseId, "S", "", "", 1L, 2)

        db.replaceCourseContentAtomically(
            CourseEntity(courseId, "Title", "Desc", "f.pdf", "uri", 0L, 1L, 100f, "#000", "COMPLETED"),
            material, regenerated, emptyList()
        )

        val cards = flashcardDao.getFlashcardsForCourseSync(courseId)
        assertEquals(2, cards.size)

        val carried = cards.first { it.id == "new-uuid-1" }
        assertEquals(4.2f, carried.difficulty)
        assertEquals(3, carried.box)
        assertEquals(123_456L, carried.dueDate)
        assertEquals(7, carried.interval)
        assertEquals(2.6f, carried.easeFactor)
        assertEquals(4, carried.repetitions)
        assertEquals(1, carried.lapses)
        assertEquals(999L, carried.lastReviewedAt)

        val fresh = cards.first { it.id == "new-uuid-2" }
        assertEquals(0, fresh.repetitions)
        assertEquals(5.0f, fresh.difficulty)
    }

    @Test
    fun testReviewLogsSurviveFlashcardDeletionAndRegeneration() = runBlocking {
        val courseId = "course-logs-test"
        courseDao.insertCourse(CourseEntity(courseId, "Title", "Desc", "f.pdf", "uri", 0L, 0L, 0f, "#000", "COMPLETED"))
        flashcardDao.insertFlashcard(
            FlashcardEntity("c-1", courseId, "Q", "A", "E", 5f, 1, 0L, 1, 2.5f, 2, 0, 500L, 0L)
        )
        val reviewDao = db.reviewLogDao()
        reviewDao.insertReviewLog(
            ReviewLogEntity("log-1", courseId, "c-1", 1000L, 3, 1, 6, 500L)
        )

        // Suppression directe d'une carte : le log doit rester (plus de FK CASCADE)
        flashcardDao.deleteFlashcardById("c-1")
        assertEquals(1, reviewDao.getAllReviewLogs().first().size)

        // Régénération complète du cours : le log doit toujours rester
        db.replaceCourseContentAtomically(
            CourseEntity(courseId, "Title", "Desc", "f.pdf", "uri", 0L, 1L, 100f, "#000", "COMPLETED"),
            StudyMaterialEntity("mat-logs", courseId, "S", "", "", 1L, 2),
            listOf(
                FlashcardEntity("c-regen", courseId, "Q régénérée", "A", "E", 5f, 1, 0L, 1, 2.5f, 0, 0, null, 1L)
            ),
            emptyList()
        )
        val logs = reviewDao.getAllReviewLogs().first()
        assertEquals(1, logs.size)
        assertEquals(courseId, logs.first().courseId)
        assertEquals(1, flashcardDao.getFlashcardsForCourseSync(courseId).size)
    }

    @Test
    fun testDeletionsRecordTombstones() = runBlocking {
        val tombstoneDao = db.tombstoneDao()
        val courseId = "course-tomb-test"
        courseDao.insertCourse(CourseEntity(courseId, "Title", "Desc", "f.pdf", "uri", 0L, 0L, 0f, "#000", "COMPLETED"))
        materialDao.insertMaterial(StudyMaterialEntity("mat-t1", courseId, "S", "", "", 1L, 1))
        flashcardDao.insertFlashcard(FlashcardEntity("c-t1", courseId, "Q", "A", "E", 5f, 1, 0L, 1, 2.5f, 0, 0, null, 0L))
        quizDao.insertQuizQuestion(QuizQuestionEntity("q-t1", courseId, "Q ?", "A\u001FB\u001FC\u001FD", "A", "", "easy"))

        // Suppression du cours : tombstones pour le cours ET tout son contenu
        db.deleteCourseAtomically(courseId)
        val allTombstones = tombstoneDao.getAllTombstones()
        val types = allTombstones.associate { it.entityId to it.entityType }
        assertEquals("COURSE", types[courseId])
        assertEquals("STUDY_MATERIAL", types["mat-t1"])
        assertEquals("FLASHCARD", types["c-t1"])
        assertEquals("QUIZ_QUESTION", types["q-t1"])

        // Régénération : l'ancien contenu part en tombstone, le nouveau pas
        courseDao.insertCourse(CourseEntity(courseId, "Title", "Desc", "f.pdf", "uri", 0L, 1L, 0f, "#000", "COMPLETED"))
        flashcardDao.insertFlashcard(FlashcardEntity("c-t2", courseId, "Q2", "A", "E", 5f, 1, 0L, 1, 2.5f, 0, 0, null, 1L))
        db.replaceCourseContentAtomically(
            CourseEntity(courseId, "Title", "Desc", "f.pdf", "uri", 0L, 2L, 100f, "#000", "COMPLETED"),
            StudyMaterialEntity("mat-t2", courseId, "S", "", "", 2L, 1),
            listOf(FlashcardEntity("c-t3", courseId, "Q3", "A", "E", 5f, 1, 0L, 1, 2.5f, 0, 0, null, 2L)),
            emptyList()
        )
        val flashcardTombs = tombstoneDao.getTombstonedIds("FLASHCARD")
        assertTrue(flashcardTombs.contains("c-t2"))
        assertTrue(flashcardTombs.contains("c-t1")) // du test précédent, conservé
        assertFalse(flashcardTombs.contains("c-t3")) // la nouvelle carte est vivante
        val materialTombs = tombstoneDao.getTombstonedIds("STUDY_MATERIAL")
        assertFalse(materialTombs.contains("mat-t2")) // la nouvelle matière est vivante
    }

    @Test
    fun testRateCardAtomicallyUpdatesCardLogAndSession() = runBlocking {
        val courseId = "course-rate-test"
        courseDao.insertCourse(CourseEntity(courseId, "Title", "Desc", "f.pdf", "uri", 0L, 0L, 0f, "#000", "COMPLETED"))
        flashcardDao.insertFlashcard(
            FlashcardEntity("c-r1", courseId, "Q", "A", "E", 5f, 1, 0L, 1, 2.5f, 0, 0, null, 0L)
        )
        val sessionDao = db.reviewSessionDao()
        sessionDao.insertSession(ReviewSessionEntity("s-1", courseId, 1000L, null, 0))

        val reviewRepo = ReviewRepositoryImpl(db.reviewLogDao(), sessionDao, flashcardDao, db)
        val original = flashcardDao.getFlashcardsForCourseSync(courseId).first()
        val updated = original.copy(
            difficulty = 4.0f, dueDate = 999_999L, interval = 6,
            easeFactor = 2.6f, repetitions = 1, lastReviewedAt = 123_456L
        )
        reviewRepo.rateCardAtomically(
            updated.toDomain(),
            com.learnsyncai.domain.model.ReviewLog(
                id = "log-r1", flashcardId = "c-r1", courseId = courseId,
                reviewedAt = 123_456L, rating = 3, previousInterval = 1, newInterval = 6, responseTime = 500L
            ),
            sessionId = "s-1"
        )
        // Deuxième notation dans la même session (re-queue "Again")
        reviewRepo.rateCardAtomically(
            updated.copy(repetitions = 2).toDomain(),
            com.learnsyncai.domain.model.ReviewLog(
                id = "log-r2", flashcardId = "c-r1", courseId = courseId,
                reviewedAt = 124_456L, rating = 1, previousInterval = 6, newInterval = 0, responseTime = 800L
            ),
            sessionId = "s-1"
        )

        val card = flashcardDao.getFlashcardsForCourseSync(courseId).first()
        assertEquals(2, card.repetitions)
        assertEquals(999_999L, card.dueDate)

        val logs = db.reviewLogDao().getAllReviewLogs().first()
        assertEquals(2, logs.size)
        assertEquals(courseId, logs[0].courseId)

        assertEquals(2, sessionDao.getSessionById("s-1")?.cardsReviewed)

        reviewRepo.endSession("s-1", 200_000L)
        val session = sessionDao.getSessionById("s-1")!!
        assertEquals(200_000L, session.endedAt)
        assertEquals(2, session.cardsReviewed)
    }
}

