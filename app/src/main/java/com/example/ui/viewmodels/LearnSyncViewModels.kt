package com.example.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiRepositoryImpl
import com.example.data.database.LearnSyncDatabase
import com.example.data.parser.DocumentParser
import com.example.data.repository.*
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
    private val reviewRepo = ReviewRepositoryImpl(db.reviewLogDao())
    private val prefsRepo = PreferencesRepositoryImpl(db.userPreferencesDao())
    private val aiRepo = AiRepositoryImpl()
    private val documentParser = DocumentParser(application)

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

    sealed interface UiState {
        object Idle : UiState
        object Loading : UiState
        data class Success(val message: String) : UiState
        data class Error(val message: String) : UiState
    }

    fun importCourse(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
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
                _uiState.value = UiState.Success("Cours importé avec succès !")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Erreur d'import : ${e.localizedMessage}")
            }
        }
    }

    fun generateMaterial(course: Course) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            // Update status to generating
            courseRepo.insertCourse(course.copy(generationStatus = "GENERATING", updatedAt = System.currentTimeMillis()))

            val result = aiRepo.generateStudyMaterial(course.title, course.extractedText)
            result.fold(
                onSuccess = { genResult ->
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

                    courseRepo.insertCourse(course.copy(generationStatus = "COMPLETED", progress = 100f, updatedAt = System.currentTimeMillis()))
                    _uiState.value = UiState.Success("Matériel généré avec succès !")
                },
                onFailure = { err ->
                    courseRepo.insertCourse(course.copy(generationStatus = "ERROR", updatedAt = System.currentTimeMillis()))
                    _uiState.value = UiState.Error("Erreur IA : ${err.localizedMessage ?: "Échec de génération"}")
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
            courseRepo.deleteCourse(courseId)
        }
    }

    fun updatePreferences(prefs: UserPreferences) {
        viewModelScope.launch {
            prefsRepo.updatePreferences(prefs)
        }
    }

    fun clearState() {
        _uiState.value = UiState.Idle
    }
}
