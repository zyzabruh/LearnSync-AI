package com.learnsyncai.di

import android.app.Application
import com.learnsyncai.data.ai.AiRepositoryImpl
import com.learnsyncai.data.ai.AiConfig
import com.learnsyncai.data.ai.LocalLlmClient
import com.learnsyncai.data.ai.OfflineMaterialGenerator
import com.learnsyncai.data.ai.OpenAiCompatibleClient
import com.learnsyncai.data.database.LearnSyncDatabase
import com.learnsyncai.data.parser.DocumentParser
import com.learnsyncai.data.repository.*
import com.learnsyncai.data.storage.CourseContentStorage
import com.learnsyncai.data.sync.CloudSyncCoordinator
import com.learnsyncai.data.sync.FirestoreSyncManager
import com.learnsyncai.domain.repository.*

/**
 * Conteneur d'injection manuel : unique point de câblage des dépendances
 * (base Room, repositories, clients IA, sync). Les ViewModels y puisent
 * leurs dépendances au lieu de les instancier eux-mêmes.
 */
class AppContainer(application: Application) {
    private val appContext = application.applicationContext

    val database: LearnSyncDatabase = LearnSyncDatabase.getDatabase(appContext)

    val courseRepository: CourseRepository =
        CourseRepositoryImpl(database.courseDao(), database)
    val studyMaterialRepository: StudyMaterialRepository =
        StudyMaterialRepositoryImpl(database.studyMaterialDao(), database.tombstoneDao())
    val flashcardRepository: FlashcardRepository =
        FlashcardRepositoryImpl(database.flashcardDao(), database.tombstoneDao())
    val quizRepository: QuizRepository =
        QuizRepositoryImpl(database.quizQuestionDao(), database.tombstoneDao())
    val reviewRepository: ReviewRepository =
        ReviewRepositoryImpl(database.reviewLogDao(), database.reviewSessionDao(), database.flashcardDao(), database)
    val preferencesRepository: PreferencesRepository =
        PreferencesRepositoryImpl(database.userPreferencesDao())
    val calendarEventRepository: CalendarEventRepository =
        CalendarEventRepositoryImpl(database.calendarEventDao())
    val aiProfileRepository: AiProfileRepository =
        AiProfileRepositoryImpl(database.aiProfileDao())
    val tombstoneRepository: TombstoneRepository =
        TombstoneRepositoryImpl(database.tombstoneDao())
    val syncStatusRepository: SyncStatusRepository =
        SyncStatusRepositoryImpl(database.syncStatusDao())

    val openAiClient: OpenAiCompatibleClient = OpenAiCompatibleClient()
    val localLlmClient: LocalLlmClient = LocalLlmClient(appContext)

    val aiRepository: AiRepository = AiRepositoryImpl(
        openAiClient = openAiClient,
        localLlmClient = localLlmClient,
        configProvider = {
            val active = aiProfileRepository.getActiveProfile()
            if (active != null) {
                AiConfig(
                    baseUrl = active.baseUrl,
                    apiKey = active.apiKey,
                    modelName = active.modelName,
                    isLocal = active.provider == "LOCAL_GEMMA"
                )
            } else {
                val currentPrefs = preferencesRepository.getPreferencesSync()
                AiConfig(
                    baseUrl = currentPrefs.aiBaseUrl,
                    apiKey = currentPrefs.aiApiKey,
                    modelName = currentPrefs.aiModelName
                )
            }
        },
        preferencesProvider = { preferencesRepository.getPreferencesSync() }
    )

    val documentParser: DocumentParser = DocumentParser(appContext)
    val firestoreSyncManager: FirestoreSyncManager = FirestoreSyncManager()
    val cloudSyncCoordinator: CloudSyncCoordinator = CloudSyncCoordinator(
        courseRepo = courseRepository,
        studyMaterialRepo = studyMaterialRepository,
        flashcardRepo = flashcardRepository,
        quizRepo = quizRepository,
        reviewRepo = reviewRepository,
        prefsRepo = preferencesRepository,
        tombstoneRepo = tombstoneRepository,
        syncStatusRepo = syncStatusRepository,
        firestoreSyncManager = firestoreSyncManager
    )
    val courseContentStorage: CourseContentStorage = CourseContentStorage(appContext)
    val offlineMaterialGenerator: OfflineMaterialGenerator = OfflineMaterialGenerator()
}
