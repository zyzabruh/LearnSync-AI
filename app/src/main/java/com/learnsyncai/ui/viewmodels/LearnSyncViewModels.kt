package com.learnsyncai.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.learnsyncai.data.ai.AiRepositoryImpl
import com.learnsyncai.data.ai.OfflineMaterialGenerator
import com.learnsyncai.data.database.LearnSyncDatabase
import com.learnsyncai.data.parser.DocumentParser
import com.learnsyncai.data.repository.*
import com.learnsyncai.data.sync.CalendarHelper
import com.learnsyncai.data.sync.FirestoreSyncManager
import com.learnsyncai.data.sync.GenerationNotifier
import com.learnsyncai.data.sync.ReviewNotificationWorker
import com.learnsyncai.data.widget.DueCardsWidgetProvider
import com.learnsyncai.domain.model.*
import com.learnsyncai.domain.usecase.QuizValidator
import com.learnsyncai.domain.usecase.SpacedRepetition
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Levée quand le téléchargement d'un modèle échoue pour cause de licence
 * non acceptée (HTTP 401/403) : [pageUrl] pointe vers la page Hugging Face
 * du modèle où accepter la licence.
 */
class LicenseRequiredException(val pageUrl: String) : IllegalStateException(
    "Licence non acceptée : ce modèle Google exige d'accepter sa licence sur Hugging Face (gratuit, avec un compte)."
)

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
    private val localLlmClient = com.learnsyncai.data.ai.LocalLlmClient(application)
    private val aiRepo = AiRepositoryImpl(
        openAiClient = openAiClient,
        localLlmClient = localLlmClient,
        configProvider = {
            val active = aiProfileRepo.getActiveProfile()
            if (active != null) {
                com.learnsyncai.data.ai.AiConfig(
                    baseUrl = active.baseUrl,
                    apiKey = active.apiKey,
                    modelName = active.modelName,
                    isLocal = active.provider == "LOCAL_GEMMA"
                )
            } else {
                val currentPrefs = prefsRepo.getPreferencesSync()
                com.learnsyncai.data.ai.AiConfig(
                    baseUrl = currentPrefs.aiBaseUrl,
                    apiKey = currentPrefs.aiApiKey,
                    modelName = currentPrefs.aiModelName
                )
            }
        },
        preferencesProvider = {
            prefsRepo.getPreferencesSync()
        }
    )
    private val documentParser = DocumentParser(application)
    private val firestoreSyncManager = FirestoreSyncManager()
    private val courseContentStorage = com.learnsyncai.data.storage.CourseContentStorage(application)
    private val offlineMaterialGenerator = OfflineMaterialGenerator()

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

        // Reset stale GENERATING status from a previous session (app killed mid-generation)
        viewModelScope.launch {
            try {
                courseRepo.getAllCourses().firstOrNull()
                    ?.filter { it.generationStatus == "GENERATING" }
                    ?.forEach { stale ->
                        courseRepo.insertCourse(
                            stale.copy(generationStatus = "ERROR", updatedAt = System.currentTimeMillis())
                        )
                    }
            } catch (_: Throwable) {}
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences.DEFAULT)

    val allQuizQuestions: StateFlow<List<QuizQuestion>> = quizRepo.getAllQuizQuestions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMaterials: StateFlow<List<StudyMaterial>> = studyMaterialRepo.getAllMaterials()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasValidAiConfig: StateFlow<Boolean> = combine(
        aiProfileRepo.getAllProfiles(),
        prefsRepo.getPreferences()
    ) { list, prefs ->
        val active = list.find { it.isActive }
        if (active?.provider == "LOCAL_GEMMA") {
            // Modèle local : valide si un fichier modèle est configuré
            active.baseUrl.isNotBlank()
        } else {
            val key = active?.apiKey ?: prefs.aiApiKey
            val baseUrl = active?.baseUrl ?: prefs.aiBaseUrl
            key.isNotBlank() || baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1")
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

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

    fun importCourseFromUrl(url: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading("Téléchargement de la page...")
            try {
                val parsed = documentParser.parseWebUrl(url.trim())
                val courseId = UUID.randomUUID().toString()
                courseContentStorage.saveExtractedText(courseId, parsed.text)
                val course = Course(
                    id = courseId,
                    title = parsed.title,
                    description = "Importé depuis $url",
                    sourceFileName = parsed.title,
                    sourceFileUri = url,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    progress = 0f,
                    color = "#3B82F6",
                    generationStatus = "NONE"
                )
                courseRepo.insertCourse(course)
                _uiState.value = UiState.Success("Page web importée : « ${parsed.title} »")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Erreur d'import web : ${e.localizedMessage}")
            }
        }
    }

    fun getCoursePreview(courseId: String): Flow<String> = flow {
        val text = courseContentStorage.readExtractedText(courseId)
        val preview = if (text.isBlank()) "" else text.take(600).trim() + if (text.length > 600) "…" else ""
        emit(preview)
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    fun exportCourseToCsv(uri: Uri, courseId: String) {
        viewModelScope.launch {
            try {
                val cards = flashcardRepo.getFlashcardsForCourse(courseId).firstOrNull() ?: emptyList()
                if (cards.isEmpty()) {
                    _uiState.value = UiState.Error("Aucune flashcard à exporter pour ce cours.")
                    return@launch
                }
                val csv = buildString {
                    append("Question,Answer,Explanation,Tags\n")
                    cards.forEach { card ->
                        append(csvEscape(card.question)).append(",")
                        append(csvEscape(card.answer)).append(",")
                        append(csvEscape(card.explanation)).append(",")
                        append(csvEscape("learnsync-ai"))
                        append("\n")
                    }
                }
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(csv.toByteArray(Charsets.UTF_8))
                }
                _uiState.value = UiState.Success("Export réussi : ${cards.size} cartes au format Anki/CSV.")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Erreur d'export : ${e.localizedMessage}")
            }
        }
    }

    private fun csvEscape(value: String): String {
        val v = value.replace("\n", " ").replace("\r", " ")
        return if (v.contains('"') || v.contains(',')) "\"${v.replace("\"", "\"\"")}\"" else v
    }

    fun updateCourseTag(courseId: String, tag: String) {
        viewModelScope.launch {
            val course = courseRepo.getCourseById(courseId) ?: return@launch
            courseRepo.insertCourse(course.copy(tag = tag.trim(), updatedAt = System.currentTimeMillis()))
            _uiState.value = if (tag.isBlank()) UiState.Success("Étiquette retirée.") else UiState.Success("Étiquette « ${tag.trim()} » appliquée.")
        }
    }

    fun generateMaterial(course: Course) {
        viewModelScope.launch {
            val activeProfile = aiProfileRepo.getActiveProfile()
            val apiKey = activeProfile?.apiKey ?: prefsRepo.getPreferencesSync().aiApiKey
            val baseUrl = activeProfile?.baseUrl ?: prefsRepo.getPreferencesSync().aiBaseUrl
            val isLocal = baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1")
            val isLocalModel = activeProfile?.provider == "LOCAL_GEMMA"

            if (apiKey.isBlank() && !isLocal && !isLocalModel) {
                generateOffline(course)
                return@launch
            }

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
                    val message = persistGenerationResult(course, genResult, "IA")
                    _uiState.value = UiState.Success(message)
                    _generationProgress.value = ""
                },
                onFailure = { err ->
                    courseRepo.insertCourse(
                        course.copy(
                            generationStatus = "ERROR",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    _uiState.value = UiState.Error("Échec de la génération : ${err.localizedMessage ?: "Erreur inconnue"}")
                    _generationProgress.value = ""
                    GenerationNotifier.notifyDone(
                        context = getApplication(),
                        courseTitle = course.title,
                        success = false,
                        detail = err.localizedMessage ?: "Erreur inconnue"
                    )
                }
            )
        }
    }

    /**
     * Mode hors-ligne : aucune clé API configurée. Génère un contenu de secours
     * localement (patterns définitions + cloze deletion), sans appel réseau.
     */
    private suspend fun generateOffline(course: Course) {
        _uiState.value = UiState.Loading("Génération hors-ligne en cours...")
        _generationProgress.value = "Génération locale (sans IA)..."

        courseRepo.insertCourse(
            course.copy(generationStatus = "GENERATING", updatedAt = System.currentTimeMillis())
        )

        try {
            val courseText = courseContentStorage.readExtractedText(course.id)
            val prefs = prefsRepo.getPreferencesSync()
            val flashcardsTarget = if (prefs.flashcardsMode == "custom") prefs.flashcardsCustomCount else 8
            val quizTarget = if (prefs.quizMode == "custom") prefs.quizCustomCount else 5

            val genResult = offlineMaterialGenerator.generate(course.title, courseText, flashcardsTarget, quizTarget)
            val message = persistGenerationResult(course, genResult, "hors-ligne")

            _uiState.value = UiState.Success(message)
            _generationProgress.value = ""
        } catch (e: Exception) {
            courseRepo.insertCourse(
                course.copy(generationStatus = "ERROR", updatedAt = System.currentTimeMillis())
            )
            _uiState.value = UiState.Error("Génération hors-ligne impossible : ${e.localizedMessage}")
            _generationProgress.value = ""
            GenerationNotifier.notifyDone(getApplication(), course.title, false, e.localizedMessage ?: "Erreur inconnue")
        }
    }

    private suspend fun persistGenerationResult(
        course: Course,
        genResult: com.learnsyncai.domain.model.StudyGenerationResult,
        sourceLabel: String
    ): String {
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
            newFlashcard(
                courseId = course.id,
                question = it.question,
                answer = it.answer,
                explanation = it.explanation
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

        android.util.Log.d("LearnSyncAI", "insertion Room ($sourceLabel): courseId=${course.id}, materialId=$materialId, flashcardsCount=${flashcards.size}, quizCount=${quizQuestions.size}")

        // Atomic replacement in a single Room transaction
        courseRepo.replaceCourseContentAtomically(
            course = completedCourse,
            material = material,
            flashcards = flashcards,
            quizQuestions = quizQuestions
        )

        val detail = "${flashcards.size} flashcards et ${quizQuestions.size} QCM créés"
        GenerationNotifier.notifyDone(
            context = getApplication(),
            courseTitle = course.title,
            success = true,
            detail = detail
        )

        return "Matériel v$nextVersion ($sourceLabel) : $detail !"
    }

    private fun newFlashcard(courseId: String, question: String, answer: String, explanation: String) = Flashcard(
        id = UUID.randomUUID().toString(),
        courseId = courseId,
        question = question,
        answer = answer,
        explanation = explanation,
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

            // Rafraîchit le widget (nombre de cartes dues)
            DueCardsWidgetProvider.updateAll(getApplication())
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
                for (remote in remoteCoursesResult.getOrDefault(emptyList())) {
                    val local = localCourseMap[remote.id]
                    if (local == null || remote.updatedAt > local.updatedAt) {
                        // Remote is missing locally or newer -> update local
                        courseRepo.insertCourse(remote)
                        localCourseMap[remote.id] = remote
                    }
                }

                // 3. Reconcile StudyMaterials
                val localMaterialMap = currentMaterials.associateBy { it.id }.toMutableMap()
                for (remote in remoteMaterialsResult.getOrDefault(emptyList())) {
                    val local = localMaterialMap[remote.id]
                    if (local == null || remote.generatedAt > local.generatedAt || remote.version > local.version) {
                        studyMaterialRepo.insertMaterial(remote)
                        localMaterialMap[remote.id] = remote
                    }
                }

                // 4. Reconcile Flashcards
                val localCardMap = currentFlashcards.associateBy { it.id }.toMutableMap()
                for (remote in remoteCardsResult.getOrDefault(emptyList())) {
                    val local = localCardMap[remote.id]
                    val remoteTime = remote.lastReviewedAt ?: remote.createdAt
                    val isLocalNewer = local != null && (local.lastReviewedAt ?: local.createdAt) >= remoteTime
                    if (!isLocalNewer) {
                        flashcardRepo.insertFlashcard(remote)
                        localCardMap[remote.id] = remote
                    }
                }

                // 5. Reconcile Quiz Questions
                val localQuizMap = currentQuizQuestions.associateBy { it.id }.toMutableMap()
                val newQuizQuestions = remoteQuizResult.getOrDefault(emptyList()).filter { it.id !in localQuizMap }
                if (newQuizQuestions.isNotEmpty()) {
                    quizRepo.insertQuizQuestions(newQuizQuestions)
                    localQuizMap.putAll(newQuizQuestions.associateBy { it.id })
                }

                // 6. Reconcile Review Logs
                val localLogMap = currentLogs.associateBy { it.id }.toMutableMap()
                val newReviewLogs = remoteLogsResult.getOrDefault(emptyList()).filter { it.id !in localLogMap }
                if (newReviewLogs.isNotEmpty()) {
                    reviewRepo.insertReviewLogs(newReviewLogs)
                    localLogMap.putAll(newReviewLogs.associateBy { it.id })
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
        // Profil local : baseUrl est le chemin du fichier modèle importé
        if (baseUrl.startsWith("/") && (baseUrl.endsWith(".task") || baseUrl.endsWith(".litertlm") || baseUrl.endsWith(".bin"))) {
            return runCatching {
                val file = java.io.File(baseUrl)
                if (!file.exists()) throw IllegalStateException("Fichier modèle introuvable sur l'appareil.")
                val sizeMb = file.length() / (1024 * 1024)
                if (sizeMb < 200) throw IllegalStateException("Fichier trop petit (${sizeMb} Mo) : ce n'est probablement pas un modèle Gemma valide.")
                "Modèle local prêt : ${file.name} (${"%.1f".format(sizeMb / 1024f)} Go)."
            }
        }
        return openAiClient.testConnection(baseUrl, apiKey, modelName)
    }

    /**
     * Importe un fichier modèle Gemma (.task / .litertlm) dans le stockage privé
     * de l'app et renvoie son chemin (utilisé comme baseUrl du profil local).
     */
    suspend fun importLocalGemmaModel(uri: Uri): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val resolver = getApplication<Application>().contentResolver
            val fileName = queryDisplayName(uri) ?: "gemma-model.task"
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9 ._\\-]"), "_")
            val modelsDir = java.io.File(getApplication<Application>().filesDir, "models").apply { mkdirs() }
            val dest = java.io.File(modelsDir, safeName)

            resolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Impossible de lire le fichier sélectionné.")

            val sizeMb = dest.length() / (1024 * 1024)
            if (sizeMb < 200) {
                dest.delete()
                throw IllegalStateException("Le fichier importé ne semble pas être un modèle (${sizeMb} Mo).")
            }
            dest.absolutePath
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    // --- Gestion des Profils IA ---

    // Progression du téléchargement d'un modèle Gemma : null = inactif,
    // -1 = taille totale inconnue (indéterminé), sinon 0..1
    private val _modelDownloadProgress = MutableStateFlow<Float?>(null)
    val modelDownloadProgress: StateFlow<Float?> = _modelDownloadProgress.asStateFlow()

    /**
     * Télécharge directement un modèle Gemma depuis Hugging Face dans le
     * stockage privé de l'app et renvoie son chemin (utilisé comme baseUrl
     * du profil local). Écrit d'abord dans un fichier .part puis renomme.
     */
    fun downloadGemmaModel(url: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            _modelDownloadProgress.value = 0f
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val fileName = url.substringAfterLast('/').ifBlank { "gemma-model.task" }
                    val modelsDir = java.io.File(getApplication<Application>().filesDir, "models").apply { mkdirs() }
                    val dest = java.io.File(modelsDir, fileName)
                    val tmp = java.io.File(modelsDir, "$fileName.part")

                    val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000
                    try {
                        val code = connection.responseCode
                        if (code == 401 || code == 403) {
                            val pageUrl = url.substringBefore("/resolve/")
                            throw LicenseRequiredException(pageUrl)
                        }
                        if (code != 200) {
                            throw IllegalStateException("Téléchargement impossible (HTTP $code).")
                        }
                        val total = connection.contentLengthLong
                        connection.inputStream.use { input ->
                            tmp.outputStream().use { output ->
                                val buffer = ByteArray(1 shl 16)
                                var read: Int
                                var done = 0L
                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                    done += read
                                    _modelDownloadProgress.value = if (total > 0) done.toFloat() / total else -1f
                                }
                            }
                        }
                    } finally {
                        connection.disconnect()
                    }

                    if (tmp.length() < 200L * 1024 * 1024) {
                        tmp.delete()
                        throw IllegalStateException("Le fichier téléchargé est trop petit pour être un modèle valide — réessayez ou importez-le manuellement.")
                    }
                    if (!tmp.renameTo(dest)) {
                        tmp.copyTo(dest, overwrite = true)
                        tmp.delete()
                    }
                    dest.absolutePath
                }
            }
            _modelDownloadProgress.value = null
            onResult(result)
        }
    }

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
            val card = newFlashcard(
                courseId = courseId,
                question = question.trim(),
                answer = answer.trim(),
                explanation = explanation.trim()
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
                _uiState.value = UiState.Error(validation.error ?: "Format de QCM invalide (4 options distinctes requises).")
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

