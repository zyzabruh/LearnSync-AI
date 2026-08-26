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
    private val courseRepo = CourseRepositoryImpl(db.courseDao())
    private val studyMaterialRepo = StudyMaterialRepositoryImpl(db.studyMaterialDao())
    private val flashcardRepo = FlashcardRepositoryImpl(db.flashcardDao())
    private val quizRepo = QuizRepositoryImpl(db.quizQuestionDao())
    private val reviewRepo = ReviewRepositoryImpl(db.reviewLogDao())
    private val prefsRepo = PreferencesRepositoryImpl(db.userPreferencesDao())
    private val aiRepo = AiRepositoryImpl()
    private val documentParser = DocumentParser(application)
    private val firestoreSyncManager = FirestoreSyncManager()

    init {
        // Initialize daily background reminder if enabled
        ReviewNotificationWorker.scheduleDailyReminder(application)
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

    fun getQuizQuestionsForCourse(courseId: String): Flow<List<QuizQuestion>> =
        quizRepo.getQuizQuestionsForCourse(courseId)

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
            courseRepo.insertCourse(
                course.copy(
                    generationStatus = "GENERATING",
                    updatedAt = System.currentTimeMillis()
                )
            )

            val result = aiRepo.generateStudyMaterial(
                courseTitle = course.title,
                courseText = course.extractedText,
                onProgress = { progressText ->
                    _generationProgress.value = progressText
                }
            )

            result.fold(
                onSuccess = { genResult ->
                    // Atomic replacement: Delete old generated materials, flashcards, and quizzes for this course
                    studyMaterialRepo.deleteMaterialsForCourse(course.id)
                    flashcardRepo.deleteFlashcardsForCourse(course.id)
                    quizRepo.deleteQuizQuestionsForCourse(course.id)

                    // Insert study material
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
                    studyMaterialRepo.insertMaterial(material)

                    // Insert flashcards
                    val flashcards = genResult.flashcards.map {
                        Flashcard(
                            id = UUID.randomUUID().toString(),
                            courseId = course.id,
                            question = it.question,
                            answer = it.answer,
                            explanation = it.explanation,
                            difficulty = 2.5f,
                            box = 1,
                            dueDate = System.currentTimeMillis(),
                            interval = 0,
                            easeFactor = 2.5f,
                            repetitions = 0,
                            lapses = 0,
                            lastReviewedAt = null,
                            createdAt = System.currentTimeMillis()
                        )
                    }
                    flashcardRepo.insertFlashcards(flashcards)

                    // Insert quiz questions
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
                    quizRepo.insertQuizQuestions(quizQuestions)

                    // Update course status to COMPLETED
                    courseRepo.insertCourse(
                        course.copy(
                            generationStatus = "COMPLETED",
                            progress = 100f,
                            updatedAt = System.currentTimeMillis()
                        )
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
                _uiState.value = UiState.Success("Cours supprimé avec succès.")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Erreur lors de la suppression : ${e.localizedMessage}")
            }
        }
    }

    fun updatePreferences(prefs: UserPreferences) {
        viewModelScope.launch {
            prefsRepo.updatePreferences(prefs)
        }
    }

    fun syncToCalendar() {
        viewModelScope.launch {
            try {
                val currentCourses = courses.value
                val due = dueFlashcards.value
                val count = CalendarHelper.syncReviewsToCalendar(
                    getApplication(),
                    currentCourses,
                    due
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

                val res1 = firestoreSyncManager.syncUpCourses(currentCourses)
                val res2 = firestoreSyncManager.syncUpFlashcards(currentFlashcards)
                val res3 = firestoreSyncManager.syncUpReviewLogs(currentLogs)

                if (res1.isSuccess && res2.isSuccess && res3.isSuccess) {
                    _uiState.value = UiState.Success("Synchronisation cloud terminée avec succès !")
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
