package com.learnsyncai.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.learnsyncai.data.sync.CloudSyncWorker
import com.learnsyncai.data.sync.ReviewNotificationWorker
import com.learnsyncai.domain.model.AiProfile
import com.learnsyncai.domain.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Levée quand le téléchargement d'un modèle échoue pour cause de licence
 * non acceptée (HTTP 401/403) : [pageUrl] pointe vers la page Hugging Face
 * du modèle où accepter la licence.
 */
class LicenseRequiredException(val pageUrl: String) : IllegalStateException(
    "Accès refusé avec ce token : acceptez la licence du modèle sur sa page Hugging Face (gratuit), puis réessayez."
)

/** Levée quand aucun token Hugging Face n'a été fourni : les modèles Google exigent une authentification. */
class HfAuthenticationRequiredException(val pageUrl: String) : IllegalStateException(
    "Authentification requise : collez un token Hugging Face dans le champ prévu (gratuit), puis réessayez."
)

/** Un fichier modèle local (Gemma) présent dans le stockage privé de l'app. */
data class LocalModelInfo(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val usedByActiveProfile: Boolean
)

/**
 * Profil utilisateur : préférences (dont rappel quotidien), profils IA
 * (cloud ou Gemma local), téléchargement/import de modèles locaux.
 */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    // Câblage délégué au conteneur d'injection de l'Application.
    private val container = (application as com.learnsyncai.LearnSyncApplication).container
    private val prefsRepo = container.preferencesRepository
    private val aiProfileRepo = container.aiProfileRepository
    private val openAiClient = container.openAiClient

    init {
        // Initialize daily background reminder if enabled
        viewModelScope.launch {
            try {
                val prefs = prefsRepo.getPreferences().firstOrNull()
                if (prefs != null && prefs.notificationsEnabled) {
                    ReviewNotificationWorker.scheduleDailyReminder(application, prefs.reminderTime)
                }
                if (prefs?.periodicSyncEnabled == true) {
                    CloudSyncWorker.schedulePeriodic(application)
                }
            } catch (e: Throwable) {
                android.util.Log.w("LearnSyncAI", "Planification du rappel quotidien impossible : ${e.message}")
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
            } catch (e: Throwable) {
                android.util.Log.w("LearnSyncAI", "Initialisation du profil IA par défaut impossible : ${e.message}")
            }
        }
    }

    val aiProfiles: StateFlow<List<AiProfile>> = aiProfileRepo.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeAiProfile: StateFlow<AiProfile?> = aiProfileRepo.getAllProfiles()
        .map { list -> list.find { it.isActive } ?: list.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val preferences: StateFlow<UserPreferences> = prefsRepo.getPreferences()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences.DEFAULT)

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun updatePreferences(prefs: UserPreferences) {
        viewModelScope.launch {
            prefsRepo.updatePreferences(prefs)
            if (prefs.periodicSyncEnabled) {
                CloudSyncWorker.schedulePeriodic(getApplication())
            } else {
                CloudSyncWorker.cancelPeriodic(getApplication())
            }
            if (prefs.notificationsEnabled) {
                ReviewNotificationWorker.scheduleDailyReminder(getApplication(), prefs.reminderTime)
            } else {
                ReviewNotificationWorker.cancelDailyReminder(getApplication())
            }
        }
    }

    fun updatePeriodicSync(enabled: Boolean) {
        updatePreferences(preferences.value.copy(periodicSyncEnabled = enabled))
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
    fun downloadGemmaModel(url: String, hfToken: String, onResult: (Result<String>) -> Unit) {
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
                    connection.setRequestProperty("User-Agent", "LearnSyncAI/1.0")
                    if (hfToken.isNotBlank()) {
                        connection.setRequestProperty("Authorization", "Bearer ${hfToken.trim()}")
                    }
                    try {
                        val code = connection.responseCode
                        if (code == 401 || code == 403) {
                            val pageUrl = url.substringBefore("/resolve/")
                            if (hfToken.isBlank()) {
                                throw HfAuthenticationRequiredException(pageUrl)
                            }
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

    // --- Gestion des fichiers modèles locaux (Gemma) ---

    private val _localModels = MutableStateFlow<List<LocalModelInfo>>(emptyList())
    val localModels: StateFlow<List<LocalModelInfo>> = _localModels.asStateFlow()

    fun refreshLocalModels() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val activePath = aiProfileRepo.getAllProfiles().firstOrNull()
                ?.find { it.isActive && it.provider == "LOCAL_GEMMA" }?.baseUrl
            val dir = java.io.File(getApplication<Application>().filesDir, "models")
            val list = dir.listFiles()
                ?.filter { it.isFile && it.length() > 0 && !it.name.endsWith(".part") }
                ?.map {
                    LocalModelInfo(
                        path = it.absolutePath,
                        name = it.name,
                        sizeBytes = it.length(),
                        usedByActiveProfile = it.absolutePath == activePath
                    )
                }
                ?.sortedByDescending { it.sizeBytes }
                ?: emptyList()
            _localModels.value = list
        }
    }

    /** Supprime un fichier modèle local (et son éventuel .part) ; les profils qui le référencent sont vidés. */
    fun deleteLocalModel(path: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val file = java.io.File(path)
                file.delete()
                java.io.File(file.parentFile, file.name + ".part").delete()
            }
            val profiles = aiProfileRepo.getAllProfiles().firstOrNull() ?: emptyList()
            profiles.filter { it.baseUrl == path }.forEach { profile ->
                aiProfileRepo.updateProfile(profile.copy(baseUrl = ""))
            }
            refreshLocalModels()
        }
    }

    fun clearState() {
        _uiState.value = UiState.Idle
    }
}
