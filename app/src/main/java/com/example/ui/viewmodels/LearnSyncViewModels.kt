package com.example.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiRepositoryImpl
import com.example.data.database.LearnSyncDatabase
import com.example.data.parser.DocumentParser
import com.example.data.repository.*
import com.example.data.sync.CalendarHelper
import com.example.data.sync.FirestoreSyncManager
import com.example.data.sync.ReviewNotificationWorker
import com.example.domain.model.*
import com.example.domain.usecase.SpacedRepetition
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class LearnSyncViewModel(application: Application) : AndroidViewModel(application) {
    private val db = LearnSyncDatabase.getDatabase(application)
    private val courseRepo = CourseRepositoryImpl(db.courseDao(), db)
    private val studyMaterialRepo = StudyMaterialRepositoryImpl(db.studyMaterialDao())
    private val flashcardRepo = FlashcardRepositoryImpl(db.flashcardDao())
    private val quizRepo = QuizRepositoryImpl(db.quizQuestionDao())
    private val reviewRepo = ReviewRepositoryImpl(db.reviewLogDao())
    private val prefsRepo = PreferencesRepositoryImpl(db.userPreferencesDao())
    private val calendarRepo = CalendarEventRepositoryImpl(db.calendarEventDao())
    private val aiRepo = AiRepositoryImpl()
    private val documentParser = DocumentParser(application)
    private val firestoreSyncManager = FirestoreSyncManager()

    init {
        // Initialize daily background reminder if enabled
        viewModelScope.launch {
            try {
                val prefs = prefsRepo.getPreferences().firstOrNull()
                if (prefs != null && prefs.notificationsEnabled) {
                    ReviewNotificationWorker.scheduleDailyReminder(application, prefs.reminderTime)
                }
            } catch (_: Throwable) {
                // Gracefully ignore if WorkManager is not ready or constrained
            }
        }
    }

    val courses: StateFlow<List<Course>> = courseRepo.getAllCourses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dueFlashcards: StateFlow<List<Flashcard>> = flashcardRepo.getDueFlashcards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFlashcards: StateFlow<List<Flashcard>> = flashcardRepo.getAllFlashcards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reviewLogs: StateFlow<List<ReviewLog>> = reviewRepo.getAllReviewLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val preferences: StateFlow<UserPreferences> = prefsRepo.getPreferences()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences(true, 10, "08:00", "system", "fr"))

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _generationProgress = MutableStateFlow<String>("")
    val generationProgress: StateFlow<String> = _generationProgress.asStateFlow()

    sealed interface UiState {
        object Idle : UiState
        data class Loading(val message: String = "Chargement...") : UiState
        data class Success(val message: String) : UiState
        data class Error(val message: String) : UiState
    }

    fun getMaterialsForCourse(courseId: String): Flow<List<StudyMaterial>> =
        studyMaterialRepo.getMaterialsForCourse(courseId)

    fun getFlashcardsForCourse(courseId: String): Flow<List<Flashcard>> =
        flashcardRepo.getFlashcardsForCourse(courseId)

    fun getDueFlashcardsForCourse(courseId: String): Flow<List<Flashcard>> =
        flashcardRepo.getDueFlashcardsForCourse(courseId)

    fun getQuizQuestionsForCourse(courseId: String): Flow<List<QuizQuestion>> =
        quizRepo.getQuizQuestionsForCourse(courseId)

    fun getCalendarEventsForCourse(courseId: String): Flow<List<CalendarEvent>> =
        calendarRepo.getEventsForCourse(courseId)

    fun importCourse(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading("Extraction du document...")
            try {
                val parseResult = documentParser.parseDocument(uri, fileName)
                val courseId = UUID.randomUUID().toString()
                val course = Course(
                    id = courseId,
                    title = parseResult.title,
                    description = "Importé depuis $fileName (${parseResult.pageCount} pages)",
                    sourceFileName = fileName,
                    sourceFileUri = uri.toString(),
                    extractedText = parseResult.text,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    progress = 0f,
                    color = "#3B82F6",
                    generationStatus = "NONE"
                )
                courseRepo.insertCourse(course)

                // Optional cloud file upload in background
                launch {
                    try {
                        firestoreSyncManager.uploadCourseDocument(uri, courseId, fileName, getApplication())
                    } catch (_: Exception) {}
                }

                _uiState.value = UiState.Success("Cours importé avec succès (${parseResult.pageCount} pages)")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Erreur d'import : ${e.localizedMessage}")
            }
        }
    }

    fun generateMaterial(course: Course) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading("Génération pédagogique en cours...")
            _generationProgress.value = "Démarrage de l'analyse IA..."
            
            // Mark course as GENERATING
            val updatedCourseGenerating = course.copy(
                generationStatus = "GENERATING",
                updatedAt = System.currentTimeMillis()
            )
            courseRepo.insertCourse(updatedCourseGenerating)

            val result = aiRepo.generateStudyMaterial(
                courseTitle = course.title,
                courseText = course.extractedText,
                onProgress = { progressText ->
                    _generationProgress.value = progressText
                }
            )

            result.fold(
                onSuccess = { genResult ->
                    // Study material
                    val materialId = UUID.randomUUID().toString()
                    val material = StudyMaterial(
                        id = materialId,
                        courseId = course.id,
                        summary = genResult.summary,
                        keyPoints = genResult.keyPoints,
                        mnemonicTips = genResult.mnemonicTips,
                        generatedAt = System.currentTimeMillis(),
                        version = 1
                    )

                    // Flashcards with FSRS defaults
                    val flashcards = genResult.flashcards.map {
                        Flashcard(
                            id = UUID.randomUUID().toString(),
                            courseId = course.id,
                            question = it.question,
                            answer = it.answer,
                            explanation = it.explanation,
                            difficulty = 5.0f,
                            box = 1,
                            dueDate = System.currentTimeMillis(),
                            interval = 0,
                            easeFactor = 1.0f,
                            repetitions = 0,
                            lapses = 0,
                            lastReviewedAt = null,
                            createdAt = System.currentTimeMillis()
                        )
                    }

                    // Quiz questions
                    val quizQuestions = genResult.quizQuestions.map {
                        QuizQuestion(
                            id = UUID.randomUUID().toString(),
                            courseId = course.id,
                            question = it.question,
                            options = it.options,
                            correctAnswer = it.correctAnswer,
                            explanation = it.explanation,
                            difficulty = "medium"
                        )
                    }

                    val completedCourse = course.copy(
                        generationStatus = "COMPLETED",
                        progress = 100f,
                        updatedAt = System.currentTimeMillis()
                    )

                    // Atomic replacement in a single Room transaction
                    courseRepo.replaceCourseContentAtomically(
                        course = completedCourse,
                        material = material,
                        flashcards = flashcards,
                        quizQuestions = quizQuestions
                    )

                    _uiState.value = UiState.Success("Matériel généré : ${flashcards.size} flashcards et ${quizQuestions.size} QCM créés !")
                },
                onFailure = { err ->
                    courseRepo.insertCourse(
                        course.copy(
                            generationStatus = "ERROR",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    _uiState.value = UiState.Error("Échec de la génération : ${err.localizedMessage ?: "Erreur inconnue"}")
                }
            )
        }
    }

    fun reviewCard(card: Flashcard, rating: Int, responseTimeMs: Long) {
        viewModelScope.launch {
            val reviewResult = SpacedRepetition.calculateReview(card, rating, responseTimeMs)
            flashcardRepo.updateFlashcard(reviewResult.updatedCard)

            val log = ReviewLog(
                id = UUID.randomUUID().toString(),
                flashcardId = card.id,
                reviewedAt = System.currentTimeMillis(),
                rating = rating,
                previousInterval = card.interval,
                newInterval = reviewResult.newInterval,
                responseTime = responseTimeMs
            )
            reviewRepo.logReview(log)
        }
    }

    fun deleteCourse(courseId: String) {
        viewModelScope.launch {
            try {
                // Cascading delete handles sub-entities in Room
                courseRepo.deleteCourse(courseId)
                // Clean up in cloud replica
                launch {
                    firestoreSyncManager.deleteCourseInCloud(courseId)
                }
                _uiState.value = UiState.Success("Cours supprimé avec succès.")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Erreur lors de la suppression : ${e.localizedMessage}")
            }
        }
    }

    fun updatePreferences(prefs: UserPreferences) {
        viewModelScope.launch {
            prefsRepo.updatePreferences(prefs)
            if (prefs.notificationsEnabled) {
                ReviewNotificationWorker.scheduleDailyReminder(getApplication(), prefs.reminderTime)
            } else {
                ReviewNotificationWorker.cancelDailyReminder(getApplication())
            }
        }
    }

    fun syncToCalendar() {
        viewModelScope.launch {
            try {
                val currentCourses = courses.value
                val due = dueFlashcards.value
                val count = CalendarHelper.syncReviewsToCalendar(
                    context = getApplication(),
                    courses = currentCourses,
                    dueCards = due,
                    calendarEventDao = db.calendarEventDao()
                )
                if (count > 0) {
                    _uiState.value = UiState.Success("$count sessions de révision synchronisées avec votre calendrier !")
                } else {
                    _uiState.value = UiState.Success("Votre calendrier est déjà à jour.")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Erreur lors de la synchronisation calendrier : ${e.localizedMessage}")
            }
        }
    }

    fun syncWithCloud() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading("Synchronisation avec Firebase...")
            try {
                val currentCourses = courses.value
                val currentFlashcards = allFlashcards.value
                val currentLogs = reviewLogs.value

                // 1. Sync Up local data
                val res1 = firestoreSyncManager.syncUpCourses(currentCourses)
                val res2 = firestoreSyncManager.syncUpFlashcards(currentFlashcards)
                val res3 = firestoreSyncManager.syncUpReviewLogs(currentLogs)

                // 2. Sync Down remote data if any
                val remoteCoursesResult = firestoreSyncManager.fetchRemoteCourses()
                if (remoteCoursesResult.isSuccess) {
                    val remoteCourses = remoteCoursesResult.getOrNull() ?: emptyList()
                    val localCourseIds = currentCourses.map { it.id }.toSet()
                    for (remoteCourse in remoteCourses) {
                        if (!localCourseIds.contains(remoteCourse.id)) {
                            courseRepo.insertCourse(remoteCourse)
                        }
                    }
                }

                val remoteCardsResult = firestoreSyncManager.fetchRemoteFlashcards()
                if (remoteCardsResult.isSuccess) {
                    val remoteCards = remoteCardsResult.getOrNull() ?: emptyList()
                    val localCardIds = currentFlashcards.map { it.id }.toSet()
                    for (remoteCard in remoteCards) {
                        if (!localCardIds.contains(remoteCard.id)) {
                            flashcardRepo.insertFlashcard(remoteCard)
                        }
                    }
                }

                if (res1.isSuccess && res2.isSuccess && res3.isSuccess) {
                    _uiState.value = UiState.Success("Synchronisation cloud bidirectionnelle terminée !")
                } else {
                    val error = res1.exceptionOrNull() ?: res2.exceptionOrNull() ?: res3.exceptionOrNull()
                    _uiState.value = UiState.Error("Erreur Cloud : ${error?.localizedMessage ?: "Vérifiez votre connexion"}")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Échec de la synchronisation : ${e.localizedMessage}")
            }
        }
    }

    fun clearState() {
        _uiState.value = UiState.Idle
    }
}

