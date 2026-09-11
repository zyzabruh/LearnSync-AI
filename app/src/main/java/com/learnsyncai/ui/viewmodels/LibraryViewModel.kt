package com.learnsyncai.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.learnsyncai.data.parser.AnkiImporter
import com.learnsyncai.data.parser.DocumentParser
import com.learnsyncai.data.parser.OutlineEntry
import com.learnsyncai.data.parser.PageLink
import com.learnsyncai.data.parser.PageWord
import com.learnsyncai.data.parser.PdfSearchHit
import com.learnsyncai.data.parser.ScannedPdfException
import com.learnsyncai.data.sync.CloudSyncWorker
import com.learnsyncai.data.sync.FirestoreSyncManager
import com.learnsyncai.data.sync.GenerationNotifier
import com.learnsyncai.domain.model.*
import com.learnsyncai.domain.usecase.QuizValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Cours et contenu pédagogique : import (fichier / URL), génération IA et
 * hors-ligne, régénération, CRUD manuel (flashcards, QCM, résumé, notions
 * clés), export CSV, suppression de cours.
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    // Câblage délégué au conteneur d'injection de l'Application.
    private val container = (application as com.learnsyncai.LearnSyncApplication).container
    private val courseRepo = container.courseRepository
    private val studyMaterialRepo = container.studyMaterialRepository
    private val flashcardRepo = container.flashcardRepository
    private val quizRepo = container.quizRepository
    private val prefsRepo = container.preferencesRepository
    private val aiProfileRepo = container.aiProfileRepository
    private val tombstoneRepo = container.tombstoneRepository
    private val aiRepo = container.aiRepository
    private val noteRepo = container.noteRepository
    private val annotationRepo = container.annotationRepository
    private val mediaRepo = container.mediaRepository
    private val documentParser = container.documentParser
    private val pdfOcrService = container.pdfOcrService
    private val firestoreSyncManager = container.firestoreSyncManager
    private val courseContentStorage = container.courseContentStorage
    private val offlineMaterialGenerator = container.offlineMaterialGenerator

    init {
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
            } catch (e: Throwable) {
                android.util.Log.w("LearnSyncAI", "Reset des statuts GENERATING orphelins impossible : ${e.message}")
            }
        }
    }

    val courses: StateFlow<List<Course>> = courseRepo.getAllCourses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFlashcards: StateFlow<List<Flashcard>> = flashcardRepo.getAllFlashcards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private val _ocrRequest = MutableStateFlow<ScannedPdfException?>(null)
    val ocrRequest: StateFlow<ScannedPdfException?> = _ocrRequest.asStateFlow()

    private val _ocrProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val ocrProgress: StateFlow<Pair<Int, Int>?> = _ocrProgress.asStateFlow()
    private var ocrJob: Job? = null

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
                courseContentStorage.saveExtractedText(courseId, parseResult.text)
                // Sommaire du PDF (outline) : extrait par PdfBox, conservé pour l'affichage.
                courseContentStorage.saveOutlineForCourse(courseId, parseResult.outline)
                // Conserve une copie locale + l'accès persistant pour l'ouverture depuis l'app.
                persistImportForOpening(uri, courseId, fileName)
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
                    firestoreSyncManager.uploadCourseDocument(uri, courseId, fileName, getApplication())
                        .onFailure {
                            android.util.Log.w("LearnSyncAI", "Upload cloud du document différé : ${it.message}")
                        }
                }

                _uiState.value = UiState.Success("Cours importé avec succès (${parseResult.pageCount} pages)")
            } catch (e: ScannedPdfException) {
                _ocrRequest.value = e
                _uiState.value = UiState.Idle
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Erreur d'import : ${e.localizedMessage}")
            }
        }
    }

    fun runPdfOcr(request: ScannedPdfException) {
        ocrJob?.cancel()
        ocrJob = viewModelScope.launch {
            _ocrRequest.value = request
            _ocrProgress.value = 0 to request.pageCount.coerceAtMost(com.learnsyncai.data.parser.PdfOcrService.MAX_PAGES)
            _uiState.value = UiState.Loading("Préparation de l'OCR...")
            try {
                val result = pdfOcrService.extractText(request.uri, request.pageCount) { completed, total ->
                    _ocrProgress.value = completed to total
                    _uiState.value = UiState.Loading("OCR de la page $completed/$total...")
                }.getOrThrow()
                val courseId = UUID.randomUUID().toString()
                courseContentStorage.saveExtractedText(courseId, result)
                // Conserve une copie locale + l'accès persistant pour l'ouverture depuis l'app.
                persistImportForOpening(request.uri, courseId, request.displayName)
                val course = Course(
                    id = courseId,
                    title = request.displayName.substringBeforeLast('.'),
                    description = "Importé depuis ${request.displayName} (${request.pageCount} pages, OCR)",
                    sourceFileName = request.displayName,
                    sourceFileUri = request.uri.toString(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    progress = 0f,
                    color = "#3B82F6",
                    generationStatus = "NONE"
                )
                courseRepo.insertCourse(course)
                _ocrRequest.value = null
                _uiState.value = UiState.Success("PDF OCR importé avec succès (${request.pageCount} pages)")
                CloudSyncWorker.enqueueNow(getApplication())
            } catch (e: kotlinx.coroutines.CancellationException) {
                _uiState.value = UiState.Idle
                throw e
            } catch (e: Exception) {
                _uiState.value = UiState.Error("OCR impossible : ${e.localizedMessage ?: "modèle ML Kit indisponible"}")
            } finally {
                // Keep the request after an OCR error so the user can retry.
                _ocrProgress.value = null
            }
        }
    }

    fun cancelPdfOcr() {
        ocrJob?.cancel()
        ocrJob = null
        _ocrProgress.value = null
        _ocrRequest.value = null
        _uiState.value = UiState.Idle
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

    /**
     * Conserve l'accès au fichier importé pour l'ouverture depuis l'app :
     * permission persistante sur l'URI du sélecteur + copie locale (best effort,
     * l'import reste valide même si la copie échoue).
     */
    private suspend fun persistImportForOpening(uri: Uri, courseId: String, fileName: String) {
        val app = getApplication<Application>()
        try {
            app.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            android.util.Log.w("LearnSyncAI", "Permission persistante refusée pour $fileName : ${e.message}")
        }
        try {
            app.contentResolver.openInputStream(uri)?.use { input ->
                courseContentStorage.saveOriginalFile(courseId, fileName, input)
            }
        } catch (e: Exception) {
            android.util.Log.w("LearnSyncAI", "Copie locale différée pour $fileName : ${e.message}")
        }
    }

    /** Ouvre le document source du cours dans le visualiseur système. */
    fun openCourseDocument(courseId: String) {
        viewModelScope.launch {
            try {
                val course = courseRepo.getCourseById(courseId)
                    ?: return@launch.also { _uiState.value = UiState.Error("Cours introuvable.") }
                com.learnsyncai.data.storage.FileOpener
                    .openCourseDocument(getApplication(), courseContentStorage, course)
                    .onFailure { _uiState.value = UiState.Error(it.message ?: "Ouverture impossible.") }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Ouverture impossible : ${e.localizedMessage}")
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
            updateCourseTagsInternal(courseId, com.learnsyncai.domain.model.CourseTags.parse(tag))
        }
    }

    /** Remplace les étiquettes d'un cours (liste) et notifie. */
    fun updateCourseTags(courseId: String, tags: List<String>) {
        viewModelScope.launch {
            updateCourseTagsInternal(courseId, tags)
        }
    }

    private suspend fun updateCourseTagsInternal(courseId: String, tags: List<String>) {
        val course = courseRepo.getCourseById(courseId) ?: return
        val joined = com.learnsyncai.domain.model.CourseTags.join(tags)
        courseRepo.insertCourse(course.copy(tag = joined, updatedAt = System.currentTimeMillis()))
        _uiState.value = if (tags.isEmpty()) UiState.Success("Étiquettes retirées.")
        else UiState.Success("Étiquettes « $joined » appliquées.")
    }

    /** Déplace un cours dans un dossier (""/vide = sans dossier). */
    fun updateCourseFolder(courseId: String, folder: String) {
        viewModelScope.launch {
            val course = courseRepo.getCourseById(courseId) ?: return@launch
            val clean = folder.trim()
            courseRepo.insertCourse(course.copy(folder = clean, updatedAt = System.currentTimeMillis()))
            _uiState.value = if (clean.isBlank()) UiState.Success("Cours sorti du dossier.")
            else UiState.Success("Cours déplacé dans « $clean ».")
        }
    }

    /** Fixe la date d'examen d'un cours (0 = aucune) : pilote le compte à rebours. */
    fun updateExamDate(courseId: String, examDate: Long) {
        viewModelScope.launch {
            val course = courseRepo.getCourseById(courseId) ?: return@launch
            courseRepo.insertCourse(course.copy(examDate = examDate, updatedAt = System.currentTimeMillis()))
            _uiState.value = if (examDate <= 0L) UiState.Success("Date d'examen retirée.")
            else {
                val days = ((examDate - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0L)
                UiState.Success("Examen dans $days jour(s).")
            }
        }
    }

    /** Ajoute des points d'expérience (gamification, local-only). */
    fun addXp(amount: Int) {
        if (amount <= 0) return
        viewModelScope.launch {
            try {
                val prefs = prefsRepo.getPreferencesSync()
                prefsRepo.updatePreferences(prefs.copy(xp = prefs.xp + amount))
            } catch (_: Exception) { }
        }
    }

    /** Marque le cours en erreur et affiche un message clair à l'utilisateur. */
    private suspend fun markGenerationError(course: Course, message: String) {
        courseRepo.insertCourse(course.copy(generationStatus = "ERROR", updatedAt = System.currentTimeMillis()))
        _uiState.value = UiState.Error(message)
        _generationProgress.value = ""
    }

    /**
     * Lit le texte extrait du document pour la génération ; en cas de document
     * absent ou illisible, interrompt avec un message clair au lieu de générer
     * à partir d'un texte vide. Renvoie null si la génération doit s'arrêter.
     */
    private suspend fun readCourseTextForGeneration(course: Course): String? {
        return when (val result = courseContentStorage.readExtractedTextChecked(course.id)) {
            is com.learnsyncai.data.storage.CourseContentStorage.ExtractedText.Available -> result.text
            is com.learnsyncai.data.storage.CourseContentStorage.ExtractedText.Missing -> {
                markGenerationError(course, "Texte du document introuvable pour ce cours : réimporte le document avant de générer.")
                null
            }
            is com.learnsyncai.data.storage.CourseContentStorage.ExtractedText.ReadError -> {
                markGenerationError(course, "Texte du document illisible (${result.detail}) : réimporte le document avant de générer.")
                null
            }
        }
    }

    /** Générations IA suivies par cours : anti-double-clic + annulation. */
    private val generationJobs = mutableMapOf<String, Job>()

    /** Annule la génération en cours d'un cours (la fiche reste utilisable). */
    fun cancelGeneration(courseId: String) {
        generationJobs[courseId]?.cancel()
        generationJobs.remove(courseId)
        viewModelScope.launch {
            try {
                val course = courseRepo.getCourseById(courseId) ?: return@launch
                if (course.generationStatus == "GENERATING") {
                    courseRepo.insertCourse(course.copy(generationStatus = "NONE", updatedAt = System.currentTimeMillis()))
                }
                _generationProgress.value = ""
                _uiState.value = UiState.Success("Génération annulée.")
            } catch (_: Exception) { }
        }
    }

    fun generateMaterial(course: Course) {
        if (generationJobs[course.id]?.isActive == true) {
            _uiState.value = UiState.Success("Génération déjà en cours : tu peux naviguer, tu seras notifié.")
            return
        }
        val job = viewModelScope.launch {
            val activeProfile = aiProfileRepo.getActiveProfile()
            val apiKey = activeProfile?.apiKey ?: prefsRepo.getPreferencesSync().aiApiKey
            val baseUrl = activeProfile?.baseUrl ?: prefsRepo.getPreferencesSync().aiBaseUrl
            val isLocal = baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1")
            val isLocalModel = activeProfile?.provider == "LOCAL_GEMMA"

            if (apiKey.isBlank() && !isLocal && !isLocalModel) {
                generateOffline(course)
                return@launch
            }

            _generationProgress.value = "Démarrage de l'analyse IA..."

            // Mark course as GENERATING
            val updatedCourseGenerating = course.copy(
                generationStatus = "GENERATING",
                updatedAt = System.currentTimeMillis()
            )
            courseRepo.insertCourse(updatedCourseGenerating)

            val courseText = readCourseTextForGeneration(course) ?: return@launch
            val result = aiRepo.generateStudyMaterial(
                courseTitle = course.title,
                courseText = courseText,
                language = course.language,
                onProgress = { progressText ->
                    _generationProgress.value = progressText
                }
            )

            result.fold(
                onSuccess = { genResult ->
                    val message = persistGenerationResult(course, genResult, "IA")
                    GenerationNotifier.notifyDone(
                        context = getApplication(),
                        courseTitle = course.title,
                        success = true,
                        detail = message
                    )
                    _uiState.value = UiState.Success(message)
                    CloudSyncWorker.enqueueNow(getApplication())
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
        generationJobs[course.id] = job
        job.invokeOnCompletion { generationJobs.remove(course.id) }
    }

    /**
     * Génère du contenu SUPPLÉMENTAIRE pour un cours, sans supprimer
     * l'existant et sans doublons : les questions déjà en base sont envoyées
     * à l'IA en liste d'exclusion, et le résultat est re-filtré avant
     * insertion (les anciennes flashcards/QCM sont conservées).
     */
    fun generateMoreMaterial(course: Course) {
        if (generationJobs[course.id]?.isActive == true) {
            _uiState.value = UiState.Success("Génération déjà en cours : tu peux naviguer, tu seras notifié.")
            return
        }
        val job = viewModelScope.launch {
            val activeProfile = aiProfileRepo.getActiveProfile()
            val apiKey = activeProfile?.apiKey ?: prefsRepo.getPreferencesSync().aiApiKey
            val baseUrl = activeProfile?.baseUrl ?: prefsRepo.getPreferencesSync().aiBaseUrl
            val isLocalModel = activeProfile?.provider == "LOCAL_GEMMA"

            if (apiKey.isBlank() && !isLocalModel && !baseUrl.contains("localhost") && !baseUrl.contains("127.0.0.1")) {
                _uiState.value = UiState.Error("Configure une IA dans le Profil pour générer du contenu supplémentaire.")
                return@launch
            }

            _generationProgress.value = "Analyse des questions existantes..."
            courseRepo.insertCourse(
                course.copy(generationStatus = "GENERATING", updatedAt = System.currentTimeMillis())
            )

            try {
                val courseText = readCourseTextForGeneration(course) ?: return@launch
                val existingCards = flashcardRepo.getFlashcardsForCourse(course.id).firstOrNull() ?: emptyList()
                val existingQuiz = quizRepo.getQuizQuestionsForCourse(course.id).firstOrNull() ?: emptyList()

                val result = aiRepo.generateAdditionalPractice(
                    courseTitle = course.title,
                    courseText = courseText,
                    existingFlashcardQuestions = existingCards.map { it.question },
                    existingQuizQuestions = existingQuiz.map { it.question },
                    language = course.language,
                    onProgress = { _generationProgress.value = it }
                )

                result.fold(
                    onSuccess = { (cards, quiz) ->
                        // Garde-fou final : exclure toute question déjà en base
                        val existingKeys = (existingCards.map { it.question.trim().lowercase() } +
                                existingQuiz.map { it.question.trim().lowercase() }).toMutableSet()
                        val newCards = cards.filter { c -> c.question.trim().length > 3 && existingKeys.add(c.question.trim().lowercase()) }
                        val newQuiz = quiz.filter { q -> q.question.trim().length > 3 && existingKeys.add(q.question.trim().lowercase()) }

                        if (newCards.isEmpty() && newQuiz.isEmpty()) {
                            courseRepo.insertCourse(
                                course.copy(generationStatus = "COMPLETED", updatedAt = System.currentTimeMillis())
                            )
                            _uiState.value = UiState.Success("Aucune nouvelle question trouvée : le cours semble déjà bien couvert.")
                            _generationProgress.value = ""
                            return@fold
                        }

                        // Insertion NON destructive : les anciennes cartes restent en base
                        flashcardRepo.insertFlashcards(
                            newCards.map { newFlashcard(course.id, it.question, it.answer, it.explanation, sourceExcerpt = it.source) }
                        )
                        quizRepo.insertQuizQuestions(
                            newQuiz.map {
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
                        )
                        courseRepo.insertCourse(
                            course.copy(generationStatus = "COMPLETED", updatedAt = System.currentTimeMillis())
                        )

                        val detail = "+${newCards.size} flashcards et +${newQuiz.size} QCM ajoutés (contenu existant conservé)"
                        GenerationNotifier.notifyDone(
                            context = getApplication(),
                            courseTitle = course.title,
                            success = true,
                            detail = detail
                        )
                        _uiState.value = UiState.Success("$detail !")
                        _generationProgress.value = ""
                    },
                    onFailure = { err ->
                        courseRepo.insertCourse(
                            course.copy(generationStatus = "ERROR", updatedAt = System.currentTimeMillis())
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
            } catch (e: Exception) {
                courseRepo.insertCourse(
                    course.copy(generationStatus = "ERROR", updatedAt = System.currentTimeMillis())
                )
                _uiState.value = UiState.Error("Erreur : ${e.localizedMessage}")
                _generationProgress.value = ""
            }
        }
        generationJobs[course.id] = job
        job.invokeOnCompletion { generationJobs.remove(course.id) }
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
            val courseText = readCourseTextForGeneration(course) ?: return
            val prefs = prefsRepo.getPreferencesSync()
            val flashcardsTarget = if (prefs.flashcardsMode == "custom") prefs.flashcardsCustomCount else 8
            val quizTarget = if (prefs.quizMode == "custom") prefs.quizCustomCount else 5

            val genResult = offlineMaterialGenerator.generate(course.title, courseText, flashcardsTarget, quizTarget)
            val message = persistGenerationResult(course, genResult, "hors-ligne")

            _uiState.value = UiState.Success(message)
            CloudSyncWorker.enqueueNow(getApplication())
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
                explanation = it.explanation,
                sourceExcerpt = it.source
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

    private fun newFlashcard(
        courseId: String,
        question: String,
        answer: String,
        explanation: String,
        direction: String = com.learnsyncai.domain.model.CardDirection.FORWARD,
        typeAnswer: Boolean = false,
        sourceExcerpt: String = "",
        sourcePage: Int = -1
    ) = Flashcard(
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
        createdAt = System.currentTimeMillis(),
        cardType = com.learnsyncai.domain.usecase.CardContent.detectType(question),
        direction = direction,
        typeAnswer = typeAnswer,
        sourceExcerpt = sourceExcerpt,
        sourcePage = sourcePage
    )

    /** Change la langue de réponse IA d'un cours ("auto" = langue du document). */
    fun updateCourseLanguage(course: Course, language: String) {
        viewModelScope.launch {
            courseRepo.insertCourse(
                course.copy(language = language, updatedAt = System.currentTimeMillis())
            )
        }
    }

    fun deleteCourse(courseId: String) {
        viewModelScope.launch {
            try {
                // 1. Delete local extracted text file
                courseContentStorage.deleteExtractedText(courseId)
                courseContentStorage.deleteOriginalFiles(courseId)
                courseContentStorage.deleteInkStrokes(courseId)
                courseContentStorage.deleteOutlineForCourse(courseId)
                // 2. Cascading delete + tombstones (cours et contenus enfants)
                courseRepo.deleteCourse(courseId)
                // 3. Propage les suppressions vers le cloud en marqueurs deletedAt
                //    et nettoie les fichiers sources dans Storage (best effort).
                launch {
                    val markResult = firestoreSyncManager.markDeletedInCloud(tombstoneRepo.getAll())
                    if (markResult.isFailure) {
                        android.util.Log.w("LearnSyncAI", "Propagation cloud des suppressions différée : ${markResult.exceptionOrNull()?.message}")
                    }
                    val filesResult = firestoreSyncManager.deleteCourseFiles(courseId)
                    if (filesResult.isFailure) {
                        android.util.Log.w("LearnSyncAI", "Nettoyage Storage échoué : ${filesResult.exceptionOrNull()?.message}")
                    }
                }
                _uiState.value = UiState.Success("Cours supprimé avec succès.")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Erreur lors de la suppression : ${e.localizedMessage}")
            }
        }
    }

    // --- Création Manuelle de Contenu Pédagogique ---
    fun addCustomFlashcard(
        courseId: String,
        question: String,
        answer: String,
        explanation: String = "",
        direction: String = com.learnsyncai.domain.model.CardDirection.FORWARD,
        typeAnswer: Boolean = false
    ) {
        viewModelScope.launch {
            if (question.isBlank() || answer.isBlank()) {
                _uiState.value = UiState.Error("La question et la réponse ne peuvent pas être vides.")
                return@launch
            }
            val card = newFlashcard(
                courseId = courseId,
                question = question.trim(),
                answer = answer.trim(),
                explanation = explanation.trim(),
                direction = direction,
                typeAnswer = typeAnswer
            )
            flashcardRepo.insertFlashcard(card)
            _uiState.value = UiState.Success("Flashcard ajoutée avec succès !")
            addXp(5)
        }
    }

    fun deleteFlashcard(flashcardId: String) {
        viewModelScope.launch {
            flashcardRepo.deleteFlashcard(flashcardId)
            _uiState.value = UiState.Success("Flashcard supprimée.")
        }
    }

    /** Corrige question/réponse d'une carte sans toucher à son état FSRS. */
    fun updateFlashcardContent(
        card: Flashcard,
        question: String,
        answer: String,
        direction: String = card.direction,
        typeAnswer: Boolean = card.typeAnswer
    ) {
        viewModelScope.launch {
            if (question.isBlank() || answer.isBlank()) {
                _uiState.value = UiState.Error("La question et la réponse ne peuvent pas être vides.")
                return@launch
            }
            val type = com.learnsyncai.domain.usecase.CardContent.detectType(question)
            flashcardRepo.updateFlashcard(
                card.copy(
                    question = question.trim(),
                    answer = answer.trim(),
                    cardType = type,
                    direction = direction,
                    typeAnswer = typeAnswer
                )
            )
            _uiState.value = UiState.Success("Carte mise à jour.")
        }
    }

    /** Reporte une carte au lendemain matin (retirée des dues du jour). */
    fun postponeFlashcard(card: Flashcard) {
        viewModelScope.launch {
            val tomorrow = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            flashcardRepo.updateFlashcard(card.copy(dueDate = tomorrow))
            _uiState.value = UiState.Success("Carte reportée à demain.")
        }
    }

    /** Suspend (ou réactive) une carte : exclue des dues jusqu'à réactivation. */
    fun setFlashcardSuspended(card: Flashcard, suspended: Boolean) {
        viewModelScope.launch {
            flashcardRepo.updateFlashcard(card.copy(suspended = suspended))
            _uiState.value = UiState.Success(
                if (suspended) "Carte suspendue." else "Carte réactivée."
            )
        }
    }

    /**
     * Création directe depuis un surlignage (style RemNote) : question déjà
     * mise en forme ({{…}} pour un cloze), sans passer par le dialogue.
     */
    fun quickAddFlashcard(courseId: String, question: String, answer: String, sourceExcerpt: String) {
        viewModelScope.launch {
            val q = question.trim()
            val a = answer.trim().ifBlank { q.replace("{{", "").replace("}}", "") }
            if (q.length < 3 || a.isBlank()) {
                _uiState.value = UiState.Error("Sélection trop courte pour créer une carte.")
                return@launch
            }
            flashcardRepo.insertFlashcard(newFlashcard(courseId, q, a, "", sourceExcerpt = sourceExcerpt.trim()))
            _uiState.value = UiState.Success("Carte créée depuis la sélection !")
            addXp(5)
        }
    }

    /**
     * Génère 1 à 3 cartes IA depuis un extrait surligné du résumé
     * (surlignage → cartes, comme RemNote).
     */
    fun generateFlashcardsFromExcerpt(course: Course, excerpt: String, sourcePage: Int = -1) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading("Génération de cartes depuis la sélection...")
            try {
                val cards = aiRepo.generateFlashcardsFromExcerpt(excerpt, course.language)
                    .getOrThrow()
                    .filter { it.question.trim().length > 3 }
                if (cards.isEmpty()) {
                    _uiState.value = UiState.Error("L'IA n'a produit aucune carte pour cette sélection.")
                    return@launch
                }
                val existingKeys = flashcardRepo.getFlashcardsForCourse(course.id)
                    .firstOrNull()?.map { it.question.trim().lowercase() }
                    ?.toMutableSet() ?: mutableSetOf()
                val fresh = cards.filter { existingKeys.add(it.question.trim().lowercase()) }
                if (fresh.isEmpty()) {
                    _uiState.value = UiState.Success("Ces cartes existent déjà.")
                    return@launch
                }
                flashcardRepo.insertFlashcards(
                    fresh.map {
                        newFlashcard(
                            course.id, it.question, it.answer, it.explanation,
                            sourceExcerpt = it.source.ifBlank { excerpt.trim().take(200) },
                            sourcePage = sourcePage
                        )
                    }
                )
                _uiState.value = UiState.Success("${fresh.size} carte(s) créée(s) depuis la sélection !")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Génération impossible : ${e.localizedMessage}")
            }
        }
    }

    // --- Notes libres (style RemNote) : une note par cours, cartes via >>, <<, <>, ;;, ::, {{}} ---

    fun getNoteForCourse(courseId: String): Flow<CourseNote?> =
        noteRepo.getNoteForCourse(courseId)

    fun saveNote(courseId: String, content: String) {
        viewModelScope.launch {
            try {
                val existing = noteRepo.getNoteForCourse(courseId).firstOrNull()
                noteRepo.upsertNote(
                    (existing ?: CourseNote(
                        id = UUID.randomUUID().toString(),
                        courseId = courseId,
                        content = "",
                        updatedAt = 0L
                    )).copy(content = content, updatedAt = System.currentTimeMillis())
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Sauvegarde impossible : ${e.localizedMessage}")
            }
        }
    }

    /**
     * Convertit UNE ligne de note en flashcard (1-tap depuis l'éditeur à blocs).
     */
    fun convertSingleNoteLine(courseId: String, line: String) {
        viewModelScope.launch {
            try {
                val parsed = com.learnsyncai.domain.usecase.NoteCards.parseLine(line)
                    ?: return@launch.also {
                        _uiState.value = UiState.Error("Cette ligne ne contient aucune carte (>> , <<, <>, ;;, :: ou {{}}).")
                    }
                val existingKeys = flashcardRepo.getFlashcardsForCourse(courseId)
                    .firstOrNull()?.map { it.question.trim().lowercase() }
                    ?.toMutableSet() ?: mutableSetOf()
                if (!existingKeys.add(parsed.question.trim().lowercase())) {
                    _uiState.value = UiState.Success("Cette carte existe déjà.")
                    return@launch
                }
                flashcardRepo.insertFlashcard(
                    newFlashcard(
                        courseId, parsed.question, parsed.answer, "",
                        direction = parsed.direction,
                        typeAnswer = parsed.typeAnswer,
                        sourceExcerpt = "Note personnelle"
                    )
                )
                _uiState.value = UiState.Success("Carte créée depuis le bloc !")
                addXp(5)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Conversion impossible : ${e.localizedMessage}")
            }
        }
    }

    /** Convertit les lignes marquées des notes en flashcards (anti-doublons). */
    fun convertNotesToCards(courseId: String, content: String) {
        viewModelScope.launch {
            try {
                val parsed = com.learnsyncai.domain.usecase.NoteCards.parse(content)
                if (parsed.isEmpty()) {
                    _uiState.value = UiState.Error("Aucune carte détectée : utilisez >>, <<, <>, ;;, :: ou {{}}.")
                    return@launch
                }
                val existingKeys = flashcardRepo.getFlashcardsForCourse(courseId)
                    .firstOrNull()?.map { it.question.trim().lowercase() }
                    ?.toMutableSet() ?: mutableSetOf()
                val fresh = parsed.filter { existingKeys.add(it.question.trim().lowercase()) }
                if (fresh.isEmpty()) {
                    _uiState.value = UiState.Success("Ces cartes existent déjà.")
                    return@launch
                }
                flashcardRepo.insertFlashcards(
                    fresh.map {
                        newFlashcard(
                            courseId, it.question, it.answer, "",
                            direction = it.direction,
                            sourceExcerpt = "Note personnelle"
                        )
                    }
                )
                _uiState.value = UiState.Success("${fresh.size} carte(s) créée(s) depuis les notes !")
                addXp(5 * fresh.size)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Conversion impossible : ${e.localizedMessage}")
            }
        }
    }

    // --- Annotations PDF : notes par page + passages à retenir ---

    /** Copie locale du document source (pour le lecteur PDF intégré). */
    fun getLocalDocument(courseId: String): java.io.File? =
        courseContentStorage.getOriginalFile(courseId)

    /** Texte d'une page mémorisé en session (mode « texte » du lecteur PDF). */
    private val pageTextCache = mutableMapOf<String, String>()

    suspend fun getPageText(courseId: String, pageIndex: Int): String = withContext(Dispatchers.IO) {
        val key = "$courseId#$pageIndex"
        pageTextCache[key] ?: run {
            val file = courseContentStorage.getOriginalFile(courseId)
            val text = if (file != null && file.exists() && file.extension.lowercase() == "pdf") {
                try {
                    documentParser.extractPageText(file, pageIndex)
                } catch (_: Exception) {
                    ""
                }
            } else {
                ""
            }
            pageTextCache[key] = text
            text
        }
    }

    /** Mots d'une page mémorisés en session (sélection au doigt). */
    private val pageWordsCache = mutableMapOf<String, List<PageWord>>()

    /** Liens internes d'une page mémorisés en session (annotations Link). */
    private val pageLinksCache = mutableMapOf<String, List<PageLink>>()

    suspend fun getPageLinks(courseId: String, pageIndex: Int): List<PageLink> = withContext(Dispatchers.IO) {
        val key = "$courseId#$pageIndex"
        pageLinksCache[key] ?: run {
            val file = courseContentStorage.getOriginalFile(courseId)
            val links = if (file != null && file.exists() && file.extension.lowercase() == "pdf") {
                try {
                    documentParser.getPageLinks(file, pageIndex)
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            pageLinksCache[key] = links
            links
        }
    }
    suspend fun getPageWords(courseId: String, pageIndex: Int): List<PageWord> = withContext(Dispatchers.IO) {
        val key = "$courseId#$pageIndex"
        pageWordsCache[key] ?: run {
            val file = courseContentStorage.getOriginalFile(courseId)
            val words = if (file != null && file.exists() && file.extension.lowercase() == "pdf") {
                try {
                    documentParser.getPageWords(file, pageIndex)
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            pageWordsCache[key] = words
            words
        }
    }

    /** Boîtes de surlignage d'un passage « à retenir », mémorisées en session. */
    private val highlightCache = mutableMapOf<String, List<android.graphics.RectF>>()
    suspend fun getHighlightRectsForPage(courseId: String, page: Int, texts: List<String>): List<android.graphics.RectF> =
        withContext(Dispatchers.IO) {
            val key = "$courseId#$page#${texts.joinToString("|").take(200).hashCode()}"
            highlightCache[key] ?: run {
                val file = courseContentStorage.getOriginalFile(courseId)
                val queries = texts.map { it.trim() }.filter { it.length >= 4 }.take(3)
                val rects = if (file != null && file.exists() && file.extension.lowercase() == "pdf" && queries.isNotEmpty()) {
                    try {
                        documentParser.findTextRectsMulti(file, page, queries)
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
                highlightCache[key] = rects
                rects
            }
        }

    fun getAnnotationsForCourse(courseId: String): Flow<List<PdfAnnotation>> =
        annotationRepo.getAnnotationsForCourse(courseId)

    /** Encre libre (surlignage au doigt) : cache + version pour rafraîchir le lecteur. */
    private val inkCache = mutableMapOf<String, List<InkStroke>>()
    private val _inkVersion = MutableStateFlow(0)
    val inkVersion: StateFlow<Int> = _inkVersion.asStateFlow()

    suspend fun getInkStrokes(courseId: String): List<InkStroke> = withContext(Dispatchers.IO) {
        inkCache[courseId] ?: courseContentStorage.getInkStrokes(courseId).also { inkCache[courseId] = it }
    }

    fun saveInkStroke(courseId: String, stroke: InkStroke) {
        if (stroke.points.size < 4) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updated = getInkStrokes(courseId) + stroke
                courseContentStorage.saveInkStrokes(courseId, updated)
                inkCache[courseId] = updated
                _inkVersion.value++
            } catch (e: Exception) {
                android.util.Log.w("LearnSyncAI", "Sauvegarde d'encre impossible : ${e.message}")
            }
        }
    }

    fun clearInkPage(courseId: String, page: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updated = getInkStrokes(courseId).filter { it.page != page }
                courseContentStorage.saveInkStrokes(courseId, updated)
                inkCache[courseId] = updated
                _inkVersion.value++
            } catch (e: Exception) {
                android.util.Log.w("LearnSyncAI", "Effacement d'encre impossible : ${e.message}")
            }
        }
    }

    /** Recherche plein-texte dans le PDF local (style Ctrl+F). */
    suspend fun searchPdfText(courseId: String, query: String): List<PdfSearchHit> = withContext(Dispatchers.IO) {
        val file = courseContentStorage.getOriginalFile(courseId)
        if (file != null && file.exists() && file.extension.lowercase() == "pdf") {
            try {
                documentParser.searchPdfText(file, query)
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    /** Sommaire (outline) du PDF, extrait au moment de l'import et stocké localement. */
    fun getOutlineForCourse(courseId: String): Flow<List<OutlineEntry>> =
        flow {
            emit(courseContentStorage.getOutlineForCourse(courseId))
        }

    /** Concepts [[liés]] présents dans au moins 2 cours (graphe de connaissances). */
    fun getSharedConcepts(): Flow<List<SharedConcept>> = flow {
        val allCourses = try {
            courseRepo.getAllCourses().firstOrNull()
        } catch (_: Exception) {
            null
        }.orEmpty()
        val perCourse = allCourses.associate { course ->
            val note = try {
                noteRepo.getNoteForCourse(course.id).firstOrNull()?.content
            } catch (_: Exception) {
                null
            }.orEmpty()
            val cards = try {
                flashcardRepo.getFlashcardsForCourse(course.id).firstOrNull()
            } catch (_: Exception) {
                null
            }.orEmpty()
            val corpus = note + "\n" + cards.joinToString("\n") { it.question + "\n" + it.answer }
            course.id to (com.learnsyncai.domain.usecase.Concepts.extract(corpus) to cards)
        }
        val courseIdsByName = mutableMapOf<String, MutableSet<String>>()
        val displayName = mutableMapOf<String, String>()
        perCourse.forEach { (courseId, pair) ->
            pair.first.forEach { name ->
                courseIdsByName.getOrPut(name.lowercase()) { mutableSetOf() }.add(courseId)
                displayName.putIfAbsent(name.lowercase(), name)
            }
        }
        val shared = courseIdsByName.filter { it.value.size >= 2 }.map { (key, ids) ->
            val label = displayName[key] ?: key
            val count = ids.sumOf { cid ->
                perCourse[cid]?.second?.count { com.learnsyncai.domain.usecase.Concepts.cardMentions(it, label) } ?: 0
            }
            SharedConcept(label, ids.sorted(), count)
        }.sortedByDescending { it.cardCount }
        emit(shared)
    }.flowOn(Dispatchers.IO)

    fun addAnnotation(courseId: String, page: Int, text: String, kind: String) {
        viewModelScope.launch {
            val t = text.trim()
            if (t.length < 3) {
                _uiState.value = UiState.Error("Annotation trop courte.")
                return@launch
            }
            annotationRepo.addAnnotation(
                PdfAnnotation(
                    id = UUID.randomUUID().toString(),
                    courseId = courseId,
                    page = page,
                    text = t,
                    kind = kind,
                    createdAt = System.currentTimeMillis()
                )
            )
            _uiState.value = UiState.Success("Annotation enregistrée (p. ${page + 1}).")
        }
    }

    fun deleteAnnotation(id: String) {
        viewModelScope.launch {
            annotationRepo.deleteAnnotation(id)
            _uiState.value = UiState.Success("Annotation supprimée.")
        }
    }

    /** Cartes IA depuis une annotation (source = page du PDF). */
    fun cardsFromAnnotation(course: Course, annotation: PdfAnnotation) {
        generateFlashcardsFromExcerpt(course, annotation.text, sourcePage = annotation.page)
    }

    // --- YouTube / audio : transcription -> pipeline existant ---

    /** Import YouTube : URL + transcription collée (pas d'API Google requise). */
    fun importFromTranscript(title: String, url: String, transcript: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading("Import de la transcription...")
            try {
                val text = transcript.trim()
                if (title.isBlank() || text.length < 30) {
                    _uiState.value = UiState.Error("Titre et transcription (30 caractères min) requis.")
                    return@launch
                }
                val courseId = UUID.randomUUID().toString()
                courseContentStorage.saveExtractedText(courseId, text)
                courseRepo.insertCourse(
                    Course(
                        id = courseId,
                        title = title.trim().take(120),
                        description = "Importé depuis $url",
                        sourceFileName = title.trim().take(120),
                        sourceFileUri = url.trim(),
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        progress = 0f,
                        color = "#3B82F6",
                        generationStatus = "NONE"
                    )
                )
                _uiState.value = UiState.Success("Transcription importée : lancez la génération.")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Erreur d'import : ${e.localizedMessage}")
            }
        }
    }

    /** Import Anki (.apkg) : paquet → cours + flashcards, anti-doublons. */
    fun importApkg(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading("Import du paquet Anki...")
            try {
                val (deckName, imported) = withContext(Dispatchers.IO) {
                    AnkiImporter.importApkg(getApplication(), uri)
                }
                if (imported.isEmpty()) {
                    _uiState.value = UiState.Error("Aucune carte lisible dans ce paquet Anki.")
                    return@launch
                }
                val courseId = UUID.randomUUID().toString()
                val title = deckName.ifBlank { fileName.substringBeforeLast('.') }
                    .trim().take(120).ifBlank { "Paquet Anki" }
                courseContentStorage.saveExtractedText(
                    courseId,
                    imported.joinToString("\n\n") { "Q : ${it.question}\nR : ${it.answer}" }.take(200000)
                )
                courseRepo.insertCourse(
                    Course(
                        id = courseId,
                        title = title,
                        description = "Importé depuis Anki ($fileName, ${imported.size} cartes)",
                        sourceFileName = fileName,
                        sourceFileUri = uri.toString(),
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        progress = 0f,
                        color = "#3B82F6",
                        generationStatus = "NONE"
                    )
                )
                val existingKeys = flashcardRepo.getFlashcardsForCourse(courseId)
                    .firstOrNull()?.map { it.question.trim().lowercase() }
                    ?.toMutableSet() ?: mutableSetOf()
                val fresh = imported.filter { existingKeys.add(it.question.trim().lowercase()) }
                flashcardRepo.insertFlashcards(
                    fresh.map { newFlashcard(courseId, it.question, it.answer, "", sourceExcerpt = "Anki") }
                )
                _uiState.value = UiState.Success("Paquet Anki importé : ${fresh.size} carte(s) !")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Import Anki impossible : ${e.localizedMessage}")
            }
        }
    }

    /** Deck de démonstration : boucle complète (import → cartes → révision) sans clé IA. */
    fun seedDemoCourse() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading("Préparation du deck de démonstration...")
            try {
                val courseId = UUID.randomUUID().toString()
                courseContentStorage.saveExtractedText(
                    courseId,
                    "Deck de démonstration LearnSync : révises ces cartes puis explore l'application."
                )
                courseRepo.insertCourse(
                    Course(
                        id = courseId,
                        title = "Découverte : la mémoire",
                        description = "Deck de démonstration (8 cartes, sans IA)",
                        sourceFileName = "Démo",
                        sourceFileUri = "demo://deck",
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        progress = 0f,
                        color = "#3B82F6",
                        generationStatus = "NONE"
                    )
                )
                val demo = listOf(
                    "Quelle est la capitale de la France ?" to "Paris",
                    "La {{photosynthèse}} produit du {{glucose}}." to "La photosynthèse produit du glucose.",
                    "Combien font 2 + 2 × 3 ?" to "8 (la multiplication d'abord)",
                    "Qui a écrit {{Les Misérables}} ?" to "Victor Hugo",
                    "Quel est le plus grand océan ?" to "L'océan Pacifique",
                    "H2O, c'est…" to "La molécule d'eau",
                    "{{Paris}} est la capitale de la {{France}}." to "Paris est la capitale de la France.",
                    "Combien de jours dans une année bissextile ?" to "366 jours"
                )
                flashcardRepo.insertFlashcards(
                    demo.map { (q, a) -> newFlashcard(courseId, q, a, "", sourceExcerpt = "Démo") }
                )
                _uiState.value = UiState.Success("Deck de démonstration prêt : à toi de réviser !")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Démo impossible : ${e.localizedMessage}")
            }
        }
    }

    // --- Médias audio : enregistrement + transcription + cartes IA ---

    fun getMediaForCourse(courseId: String): Flow<List<CourseMedia>> =
        mediaRepo.getMediaForCourse(courseId)

    fun addAudioMedia(courseId: String, path: String) {
        viewModelScope.launch {
            mediaRepo.addMedia(
                CourseMedia(
                    id = UUID.randomUUID().toString(),
                    courseId = courseId,
                    kind = "audio",
                    path = path,
                    transcript = "",
                    createdAt = System.currentTimeMillis()
                )
            )
            _uiState.value = UiState.Success("Enregistrement ajouté.")
        }
    }

    fun updateMediaTranscript(media: CourseMedia, transcript: String) {
        viewModelScope.launch {
            mediaRepo.updateMedia(media.copy(transcript = transcript.trim()))
            _uiState.value = UiState.Success("Transcription enregistrée.")
        }
    }

    fun deleteMedia(media: CourseMedia) {
        viewModelScope.launch {
            try {
                if (media.path.isNotBlank()) java.io.File(media.path).delete()
            } catch (_: Exception) { }
            mediaRepo.deleteMedia(media.id)
            _uiState.value = UiState.Success("Enregistrement supprimé.")
        }
    }

    /** Cartes IA depuis la transcription d'un enregistrement. */
    fun cardsFromTranscript(course: Course, transcript: String) {
        generateFlashcardsFromExcerpt(course, transcript)
    }

    /** Crée une carte image (occlusion optionnelle) depuis la galerie. */
    fun createImageCard(
        courseId: String,
        srcUri: android.net.Uri,
        answer: String,
        maskX: Float = 0f,
        maskY: Float = 0f,
        maskW: Float = 0f,
        maskH: Float = 0f
    ) {
        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                val dir = java.io.File(app.filesDir, "images/$courseId").apply { mkdirs() }
                val target = java.io.File(dir, "${UUID.randomUUID()}.jpg")
                app.contentResolver.openInputStream(srcUri)?.use { src ->
                    target.outputStream().use { dst -> src.copyTo(dst) }
                } ?: return@launch.also {
                    _uiState.value = UiState.Error("Lecture de l'image impossible.")
                }
                val hasMask = maskW > 0.01f && maskH > 0.01f
                flashcardRepo.insertFlashcard(
                    newFlashcard(
                        courseId,
                        if (hasMask) "Que cache le rectangle ?" else "Que montre cette image ?",
                        answer.trim(),
                        ""
                    ).copy(
                        imagePath = target.absolutePath,
                        maskX = maskX, maskY = maskY, maskW = maskW, maskH = maskH
                    )
                )
                _uiState.value = UiState.Success("Carte image créée !")
                addXp(5)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Carte image impossible : ${e.localizedMessage}")
            }
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
