package com.learnsyncai.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.learnsyncai.data.ai.AiRepositoryImpl
import com.learnsyncai.data.database.LearnSyncDatabase
import com.learnsyncai.data.parser.DocumentParser
import com.learnsyncai.data.repository.*
import com.learnsyncai.data.sync.CalendarHelper
import com.learnsyncai.data.sync.FirestoreSyncManager
import com.learnsyncai.data.sync.ReviewNotificationWorker
import com.learnsyncai.domain.model.*
import com.learnsyncai.domain.usecase.SpacedRepetition
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
    private val aiProfileRepo = AiProfileRepositoryImpl(db.aiProfileDao())
    private val openAiClient = com.learnsyncai.data.ai.OpenAiCompatibleClient()
    private val aiRepo = AiRepositoryImpl(
        openAiClient = openAiClient,
        configProvider = {
            val active = aiProfileRepo.getActiveProfile()
            if (active != null) {
                com.learnsyncai.data.ai.AiConfig(
                    baseUrl = active.baseUrl,
                    apiKey = active.apiKey,
                    modelName = active.modelName
                )
            } else {
                val currentPrefs = prefsRepo.getPreferencesSync()
                com.learnsyncai.data.ai.AiConfig(
                    baseUrl = currentPrefs.aiBaseUrl,
                    apiKey = currentPrefs.aiApiKey,
                    modelName = currentPrefs.aiModelName
                )
            }
        }
    )
    private val documentParser = DocumentParser(application)
    private val firestoreSyncManager = FirestoreSyncManager()
    private val courseContentStorage = com.learnsyncai.data.storage.CourseContentStorage(application)

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

        // Seed initial AI profile if none exists
        viewModelScope.launch {
            try {
                val existing = aiProfileRepo.getAllProfiles().firstOrNull() ?: emptyList()
                if (existing.isEmpty()) {
                    val currentPrefs = prefsRepo.getPreferencesSync()
                    val defaultProfile = AiProfile(
                        id = UUID.randomUUID().toString(),
                        name = "Profil Principal",
                        provider = currentPrefs.aiProvider,
                        baseUrl = currentPrefs.aiBaseUrl,
                        apiKey = currentPrefs.aiApiKey,
                        modelName = currentPrefs.aiModelName,
                        isActive = true
                    )
                    aiProfileRepo.insertProfile(defaultProfile)
                }
            } catch (_: Throwable) {}
        }
    }

    val aiProfiles: StateFlow<List<AiProfile>> = aiProfileRepo.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeAiProfile: StateFlow<AiProfile?> = aiProfileRepo.getAllProfiles()
        .map { list -> list.find { it.isActive } ?: list.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
                courseContentStorage.saveExtractedText(courseId, parseResult.text)
                val course = Course(
                    id = courseId,
                    title = parseResult.title,
                    description = "Importé depuis $fileName (${parseResult.pageCount} pages)",
                    sourceFileName = fileName,
                    sourceFileUri = uri.toString(),
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

            val courseText = courseContentStorage.readExtractedText(course.id)
            val result = aiRepo.generateStudyMaterial(
                courseTitle = course.title,
                courseText = courseText,
                onProgress = { progressText ->
                    _generationProgress.value = progressText
                }
            )

            result.fold(
                onSuccess = { genResult ->
                    // Increment version from previous generations (1 -> 2 -> 3...)
                    val currentLatestVersion = studyMaterialRepo.getLatestVersionForCourse(course.id)
                    val nextVersion = currentLatestVersion + 1

                    // Study material
                    val materialId = UUID.randomUUID().toString()
                    val material = StudyMaterial(
                        id = materialId,
                        courseId = course.id,
                        summary = genResult.summary,
                        keyPoints = genResult.keyPoints,
                        mnemonicTips = genResult.mnemonicTips,
                        generatedAt = System.currentTimeMillis(),
                        version = nextVersion
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

                    _uiState.value = UiState.Success("Matériel v$nextVersion généré : ${flashcards.size} flashcards et ${quizQuestions.size} QCM créés !")
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
                // 1. Delete local extracted text file
                courseContentStorage.deleteExtractedText(courseId)
                // 2. Cascading delete handles sub-entities in Room
                courseRepo.deleteCourse(courseId)
                // 3. Clean up in cloud replica and storage
                launch {
                    firestoreSyncManager.deleteCourseInCloud(courseId)
                    firestoreSyncManager.deleteCourseFiles(courseId)
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
            _uiState.value = UiState.Loading("Synchronisation complète avec Firebase...")
            try {
                val currentCourses = courses.value
                val currentFlashcards = allFlashcards.value
                val currentLogs = reviewLogs.value
                val currentMaterials = studyMaterialRepo.getAllMaterials().firstOrNull() ?: emptyList()
                val currentQuizQuestions = quizRepo.getAllQuizQuestions().firstOrNull() ?: emptyList()
                val currentPrefs = prefsRepo.getPreferencesSync()

                // 1. Fetch remote data (DOWN)
                val remoteCoursesResult = firestoreSyncManager.fetchRemoteCourses()
                val remoteMaterialsResult = firestoreSyncManager.fetchRemoteMaterials()
                val remoteCardsResult = firestoreSyncManager.fetchRemoteFlashcards()
                val remoteQuizResult = firestoreSyncManager.fetchRemoteQuizQuestions()
                val remoteLogsResult = firestoreSyncManager.fetchRemoteReviewLogs()
                val remotePrefsResult = firestoreSyncManager.fetchRemotePreferences()

                // 2. Reconcile Courses with timestamp conflict resolution
                val localCourseMap = currentCourses.associateBy { it.id }.toMutableMap()
                if (remoteCoursesResult.isSuccess) {
                    val remoteCourses = remoteCoursesResult.getOrNull() ?: emptyList()
                    for (remote in remoteCourses) {
                        val local = localCourseMap[remote.id]
                        if (local == null) {
                            courseRepo.insertCourse(remote)
                            localCourseMap[remote.id] = remote
                        } else if (remote.updatedAt > local.updatedAt) {
                            // Remote is newer -> update local
                            courseRepo.insertCourse(remote)
                            localCourseMap[remote.id] = remote
                        }
                    }
                }

                // 3. Reconcile StudyMaterials
                val localMaterialMap = currentMaterials.associateBy { it.id }.toMutableMap()
                if (remoteMaterialsResult.isSuccess) {
                    val remoteMaterials = remoteMaterialsResult.getOrNull() ?: emptyList()
                    for (remote in remoteMaterials) {
                        val local = localMaterialMap[remote.id]
                        if (local == null) {
                            studyMaterialRepo.insertMaterial(remote)
                            localMaterialMap[remote.id] = remote
                        } else if (remote.generatedAt > local.generatedAt || remote.version > local.version) {
                            studyMaterialRepo.insertMaterial(remote)
                            localMaterialMap[remote.id] = remote
                        }
                    }
                }

                // 4. Reconcile Flashcards
                val localCardMap = currentFlashcards.associateBy { it.id }.toMutableMap()
                if (remoteCardsResult.isSuccess) {
                    val remoteCards = remoteCardsResult.getOrNull() ?: emptyList()
                    for (remote in remoteCards) {
                        val local = localCardMap[remote.id]
                        if (local == null) {
                            flashcardRepo.insertFlashcard(remote)
                            localCardMap[remote.id] = remote
                        } else {
                            val remoteTime = remote.lastReviewedAt ?: remote.createdAt
                            val localTime = local.lastReviewedAt ?: local.createdAt
                            if (remoteTime > localTime) {
                                flashcardRepo.insertFlashcard(remote)
                                localCardMap[remote.id] = remote
                            }
                        }
                    }
                }

                // 5. Reconcile Quiz Questions
                val localQuizMap = currentQuizQuestions.associateBy { it.id }.toMutableMap()
                if (remoteQuizResult.isSuccess) {
                    val remoteQuiz = remoteQuizResult.getOrNull() ?: emptyList()
                    val toInsert = mutableListOf<QuizQuestion>()
                    for (remote in remoteQuiz) {
                        if (!localQuizMap.containsKey(remote.id)) {
                            toInsert.add(remote)
                            localQuizMap[remote.id] = remote
                        }
                    }
                    if (toInsert.isNotEmpty()) {
                        quizRepo.insertQuizQuestions(toInsert)
                    }
                }

                // 6. Reconcile Review Logs
                val localLogMap = currentLogs.associateBy { it.id }.toMutableMap()
                if (remoteLogsResult.isSuccess) {
                    val remoteLogs = remoteLogsResult.getOrNull() ?: emptyList()
                    val toInsert = mutableListOf<ReviewLog>()
                    for (remote in remoteLogs) {
                        if (!localLogMap.containsKey(remote.id)) {
                            toInsert.add(remote)
                            localLogMap[remote.id] = remote
                        }
                    }
                    if (toInsert.isNotEmpty()) {
                        reviewRepo.insertReviewLogs(toInsert)
                    }
                }

                // 7. Reconcile User Preferences (keeping local AI config intact!)
                if (remotePrefsResult.isSuccess) {
                    val remotePrefs = remotePrefsResult.getOrNull()
                    if (remotePrefs != null) {
                        val currentLocalPrefs = prefsRepo.getPreferencesSync()
                        prefsRepo.updatePreferences(
                            currentLocalPrefs.copy(
                                notificationsEnabled = remotePrefs.notificationsEnabled,
                                dailyGoal = remotePrefs.dailyGoal,
                                reminderTime = remotePrefs.reminderTime,
                                theme = remotePrefs.theme,
                                language = remotePrefs.language
                            )
                        )
                    }
                }

                // 8. Sync Up newest combined state to Firestore (UP)
                val upCourses = localCourseMap.values.toList()
                val upMaterials = localMaterialMap.values.toList()
                val upCards = localCardMap.values.toList()
                val upQuiz = localQuizMap.values.toList()
                val upLogs = localLogMap.values.toList()

                val res1 = firestoreSyncManager.syncUpCourses(upCourses)
                val res2 = firestoreSyncManager.syncUpMaterials(upMaterials)
                val res3 = firestoreSyncManager.syncUpFlashcards(upCards)
                val res4 = firestoreSyncManager.syncUpQuizQuestions(upQuiz)
                val res5 = firestoreSyncManager.syncUpReviewLogs(upLogs)
                val res6 = firestoreSyncManager.syncUpPreferences(currentPrefs)

                if (res1.isSuccess && res2.isSuccess && res3.isSuccess && res4.isSuccess && res5.isSuccess && res6.isSuccess) {
                    _uiState.value = UiState.Success("Synchronisation bidirectionnelle terminée avec succès (${upCourses.size} cours, ${upCards.size} flashcards) !")
                } else {
                    val error = res1.exceptionOrNull() ?: res2.exceptionOrNull() ?: res3.exceptionOrNull() ?: res4.exceptionOrNull() ?: res5.exceptionOrNull() ?: res6.exceptionOrNull()
                    _uiState.value = UiState.Error("Erreur Cloud : ${error?.localizedMessage ?: "Vérifiez votre connexion"}")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Échec de la synchronisation : ${e.localizedMessage}")
            }
        }
    }

    suspend fun testAiConnection(baseUrl: String, apiKey: String, modelName: String): Result<String> {
        return openAiClient.testConnection(baseUrl, apiKey, modelName)
    }

    // --- Gestion des Profils IA ---
    fun addAiProfile(name: String, provider: String, baseUrl: String, apiKey: String, modelName: String, setAsActive: Boolean = true) {
        viewModelScope.launch {
            val newProfile = AiProfile(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { "Configuration IA" },
                provider = provider,
                baseUrl = baseUrl,
                apiKey = apiKey,
                modelName = modelName,
                isActive = setAsActive,
                createdAt = System.currentTimeMillis()
            )
            aiProfileRepo.insertProfile(newProfile)
            if (setAsActive) {
                aiProfileRepo.setActiveProfile(newProfile.id)
            }
            _uiState.value = UiState.Success("Profil IA « ${newProfile.name} » ajouté !")
        }
    }

    fun updateAiProfile(profile: AiProfile) {
        viewModelScope.launch {
            aiProfileRepo.updateProfile(profile)
            _uiState.value = UiState.Success("Profil IA mis à jour !")
        }
    }

    fun deleteAiProfile(profileId: String) {
        viewModelScope.launch {
            aiProfileRepo.deleteProfile(profileId)
            val remaining = aiProfileRepo.getAllProfiles().firstOrNull() ?: emptyList()
            if (remaining.isNotEmpty() && remaining.none { it.isActive }) {
                aiProfileRepo.setActiveProfile(remaining.first().id)
            }
            _uiState.value = UiState.Success("Profil IA supprimé.")
        }
    }

    fun setActiveAiProfile(profileId: String) {
        viewModelScope.launch {
            aiProfileRepo.setActiveProfile(profileId)
            _uiState.value = UiState.Success("Profil IA activé.")
        }
    }

    // --- Création Manuelle de Contenu Pédagogique ---
    fun addCustomFlashcard(courseId: String, question: String, answer: String, explanation: String = "") {
        viewModelScope.launch {
            if (question.isBlank() || answer.isBlank()) {
                _uiState.value = UiState.Error("La question et la réponse ne peuvent pas être vides.")
                return@launch
            }
            val card = Flashcard(
                id = UUID.randomUUID().toString(),
                courseId = courseId,
                question = question.trim(),
                answer = answer.trim(),
                explanation = explanation.trim(),
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
            flashcardRepo.insertFlashcard(card)
            _uiState.value = UiState.Success("Flashcard ajoutée avec succès !")
        }
    }

    fun deleteFlashcard(flashcardId: String) {
        viewModelScope.launch {
            flashcardRepo.deleteFlashcard(flashcardId)
            _uiState.value = UiState.Success("Flashcard supprimée.")
        }
    }

    fun addCustomQuizQuestion(
        courseId: String,
        question: String,
        options: List<String>,
        correctAnswer: String,
        explanation: String = ""
    ) {
        viewModelScope.launch {
            val cleanOptions = options.map { it.trim() }.filter { it.isNotBlank() }
            val candidate = GeneratedQuizQuestion(
                question = question.trim(),
                options = cleanOptions,
                correctAnswer = correctAnswer.trim(),
                explanation = explanation.trim()
            )
            val validation = QuizValidator.validateQuestion(candidate)
            if (!validation.isValid) {
                _uiState.value = UiState.Error(validation.errorMessage ?: "Format de QCM invalide (4 options distinctes requises).")
                return@launch
            }
            val quizQuestion = QuizQuestion(
                id = UUID.randomUUID().toString(),
                courseId = courseId,
                question = candidate.question,
                options = candidate.options,
                correctAnswer = candidate.correctAnswer,
                explanation = candidate.explanation,
                difficulty = "medium"
            )
            quizRepo.insertQuizQuestion(quizQuestion)
            _uiState.value = UiState.Success("Question de QCM ajoutée avec succès !")
        }
    }

    fun deleteQuizQuestion(quizQuestionId: String) {
        viewModelScope.launch {
            quizRepo.deleteQuizQuestion(quizQuestionId)
            _uiState.value = UiState.Success("QCM supprimé.")
        }
    }

    fun saveCustomSummary(courseId: String, summary: String) {
        viewModelScope.launch {
            val existing = studyMaterialRepo.getLatestMaterialForCourse(courseId)
            val updated = if (existing != null) {
                existing.copy(
                    summary = summary.trim(),
                    generatedAt = System.currentTimeMillis()
                )
            } else {
                StudyMaterial(
                    id = UUID.randomUUID().toString(),
                    courseId = courseId,
                    summary = summary.trim(),
                    keyPoints = emptyList(),
                    mnemonicTips = emptyList(),
                    generatedAt = System.currentTimeMillis(),
                    version = 1
                )
            }
            studyMaterialRepo.insertMaterial(updated)
            _uiState.value = UiState.Success("Résumé mis à jour !")
        }
    }

    fun addCustomKeyPoint(courseId: String, point: String) {
        viewModelScope.launch {
            if (point.isBlank()) return@launch
            val existing = studyMaterialRepo.getLatestMaterialForCourse(courseId)
            val currentKeyPoints = existing?.keyPoints?.toMutableList() ?: mutableListOf()
            currentKeyPoints.add(point.trim())
            val updated = if (existing != null) {
                existing.copy(keyPoints = currentKeyPoints.distinct())
            } else {
                StudyMaterial(
                    id = UUID.randomUUID().toString(),
                    courseId = courseId,
                    summary = "",
                    keyPoints = listOf(point.trim()),
                    mnemonicTips = emptyList(),
                    generatedAt = System.currentTimeMillis(),
                    version = 1
                )
            }
            studyMaterialRepo.insertMaterial(updated)
            _uiState.value = UiState.Success("Notion clé ajoutée !")
        }
    }

    fun removeCustomKeyPoint(courseId: String, point: String) {
        viewModelScope.launch {
            val existing = studyMaterialRepo.getLatestMaterialForCourse(courseId) ?: return@launch
            val updatedPoints = existing.keyPoints.filter { it != point }
            studyMaterialRepo.insertMaterial(existing.copy(keyPoints = updatedPoints))
            _uiState.value = UiState.Success("Notion clé supprimée.")
        }
    }

    fun clearState() {
        _uiState.value = UiState.Idle
    }
}

