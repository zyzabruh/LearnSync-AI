package com.learnsyncai.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.learnsyncai.domain.model.AiProfile
import com.learnsyncai.ui.theme.*
import kotlinx.coroutines.launch

enum class AiProviderPreset(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val note: String? = null
) {
    GEMINI(
        id = "GEMINI",
        displayName = "Gemini (direct)",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        defaultModel = "gemini-2.5-flash"
    ),
    OPENROUTER(
        id = "OPENROUTER",
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "google/gemini-2.5-flash"
    ),
    NVIDIA_NIM(
        id = "NVIDIA_NIM",
        displayName = "NVIDIA NIM",
        defaultBaseUrl = "https://integrate.api.nvidia.com/v1",
        defaultModel = "meta/llama-3.3-70b-instruct"
    ),
    OPENCODE_ZEN(
        id = "OPENCODE_ZEN",
        displayName = "OpenCode Zen",
        defaultBaseUrl = "https://opencode.ai/zen/v1",
        defaultModel = "nemotron-3.5-lightning-free",
        note = "Seuls les modèles listés comme gratuits (ex. big-pickle, nemotron-3-ultra-free, nemotron-3.5-lightning-free, hy3-free, mimo-v2.5-free, ling-3.0-flash-fin-free) sont utilisables sans facturation."
    ),
    LOCAL_GEMMA(
        id = "LOCAL_GEMMA",
        displayName = "Local (Gemma)",
        defaultBaseUrl = "",
        defaultModel = "gemma-local",
        note = "IA 100% hors-ligne (aucune clé API) : importez un fichier modèle Gemma (.task / .litertlm), par ex. google/gemma-3n-E2B-it-litert-preview sur Hugging Face, ou un modèle déjà téléchargé par Google AI Edge Gallery (Android/media/com.google.ai.edge.gallery/...). Nécessite ~2 à 4 Go et assez de RAM ; la 1re génération charge le modèle (comptez quelques secondes)."
    ),
    CUSTOM(
        id = "CUSTOM",
        displayName = "Personnalisé",
        defaultBaseUrl = "",
        defaultModel = ""
    )
}

@Composable
internal fun AiProfileEditDialog(
    initialProfile: AiProfile?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, provider: String, baseUrl: String, apiKey: String, modelName: String) -> Unit,
    onTestConnection: suspend (baseUrl: String, apiKey: String, modelName: String) -> Result<String>,
    onImportLocalModel: suspend (Uri) -> Result<String> = { Result.failure(IllegalStateException("Import de modèle indisponible.")) },
    onDownloadGemmaModel: (url: String, hfToken: String, onResult: (Result<String>) -> Unit) -> Unit = { _, _, _ -> },
    modelDownloadProgress: Float? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialProfile?.name ?: "") }
    var selectedPreset by remember {
        mutableStateOf(
            AiProviderPreset.entries.find { it.id == initialProfile?.provider } ?: AiProviderPreset.GEMINI
        )
    }
    var baseUrl by remember { mutableStateOf(initialProfile?.baseUrl ?: selectedPreset.defaultBaseUrl) }
    var modelName by remember { mutableStateOf(initialProfile?.modelName ?: selectedPreset.defaultModel) }
    var apiKey by remember { mutableStateOf(initialProfile?.apiKey ?: "") }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Result<String>?>(null) }
    var isImportingModel by remember { mutableStateOf(false) }
    var licensePageUrl by remember { mutableStateOf<String?>(null) }
    var hfToken by remember { mutableStateOf("") }

    val modelFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isImportingModel = true
                testResult = null
                onImportLocalModel(uri).fold(
                    onSuccess = { path ->
                        baseUrl = path
                        if (name.isBlank() || AiProviderPreset.entries.any { it.displayName == name }) {
                            name = AiProviderPreset.LOCAL_GEMMA.displayName
                        }
                    },
                    onFailure = { err -> testResult = Result.failure(err) }
                )
                isImportingModel = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialProfile == null) "Ajouter un Fournisseur IA" else "Modifier le Fournisseur IA",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
            ) {
                // Preset chips
                Text(
                    text = "Préréglage",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
                ) {
                    AiProviderPreset.entries.forEach { preset ->
                        val isSelected = preset == selectedPreset
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedPreset = preset
                                testResult = null
                                if (preset != AiProviderPreset.CUSTOM) {
                                    baseUrl = preset.defaultBaseUrl
                                    modelName = preset.defaultModel
                                    if (name.isBlank() || AiProviderPreset.entries.any { it.displayName == name }) {
                                        name = preset.displayName
                                    }
                                }
                            },
                            label = { Text(preset.displayName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                val note = selectedPreset.note
                if (selectedPreset in listOf(AiProviderPreset.OPENCODE_ZEN, AiProviderPreset.LOCAL_GEMMA) && note != null) {
                    Surface(
                        color = AmberFlame.copy(alpha = 0.1f),
                        shape = LearnSyncShapes.small
                    ) {
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom du profil *") },
                    placeholder = { Text("ex: Mon Gemini Pro, OpenRouter...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (selectedPreset == AiProviderPreset.LOCAL_GEMMA) {
                    // Modèle local : import de fichier, pas d'URL ni de clé
                    if (isImportingModel) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Copie du modèle en cours (peut prendre une minute)...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedTextField(
                        value = baseUrl.substringAfterLast('/'),
                        onValueChange = {},
                        label = { Text("Fichier modèle importé") },
                        placeholder = { Text("Aucun fichier importé") },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = { modelFilePicker.launch("*/*") },
                        enabled = !isImportingModel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (baseUrl.isBlank()) "Importer un fichier modèle (.task / .litertlm)" else "Choisir un autre modèle")
                    }

                    Text(
                        text = "ou télécharger directement :",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Token Hugging Face : requis pour télécharger les modèles Google (gated)
                    OutlinedTextField(
                        value = hfToken,
                        onValueChange = { hfToken = it },
                        label = { Text("Token Hugging Face (requis pour les modèles Google)") },
                        placeholder = { Text("hf_...") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Créez-le gratuitement (permission « Read ») : Settings → Access Tokens.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/settings/tokens")))
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Ouvrir la page des tokens Hugging Face",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Modèles recommandés : téléchargement en un clic
                    val recommendedModels = listOf(
                        "Gemma 3 1B Instruct (~523 Mo, rapide)" to
                            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
                        "Gemma 3n E2B (~3,1 Go, plus performant)" to
                            "https://huggingface.co/google/gemma-3n-E2B-it-litert-preview/resolve/main/gemma-3n-E2B-it-int4.task"
                    )
                    recommendedModels.forEach { (label, url) ->
                        val pageUrl = url.substringBefore("/resolve/")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    testResult = null
                                    licensePageUrl = null
                                    onDownloadGemmaModel(url, hfToken) { result ->
                                        result.fold(
                                            onSuccess = { path ->
                                                baseUrl = path
                                                if (name.isBlank() || AiProviderPreset.entries.any { it.displayName == name }) {
                                                    name = AiProviderPreset.LOCAL_GEMMA.displayName
                                                }
                                            },
                                            onFailure = { err ->
                                                testResult = Result.failure(err)
                                                val troubleUrl = when (err) {
                                                    is com.learnsyncai.ui.viewmodels.LicenseRequiredException -> err.pageUrl
                                                    is com.learnsyncai.ui.viewmodels.HfAuthenticationRequiredException -> err.pageUrl
                                                    else -> null
                                                }
                                                if (troubleUrl != null) {
                                                    licensePageUrl = troubleUrl
                                                }
                                            }
                                        )
                                    }
                                },
                                enabled = modelDownloadProgress == null && !isImportingModel,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(label)
                            }
                            IconButton(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl)))
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Ouvrir la page Hugging Face du modèle",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Accès refusé : redirections licence + token
                    licensePageUrl?.let { pageUrl ->
                        Surface(
                            color = AmberFlame.copy(alpha = 0.12f),
                            shape = LearnSyncShapes.small,
                            border = BorderStroke(1.dp, AmberFlame.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Accès refusé : les modèles Google exigent un token Hugging Face ET la licence acceptée (gratuit, une seule fois).",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Button(
                                    onClick = {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/settings/tokens")))
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("1. Créer un token (Settings → Access Tokens)")
                                }
                                Button(
                                    onClick = {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl)))
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("2. Accepter la licence sur la page du modèle")
                                }
                                Text(
                                    text = "Collez ensuite le token dans le champ ci-dessus et relancez le téléchargement.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Barre de progression du téléchargement
                    if (modelDownloadProgress != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (modelDownloadProgress >= 0f) {
                                LinearProgressIndicator(
                                    progress = { modelDownloadProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "Téléchargement : ${(modelDownloadProgress * 100).toInt()} %",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    text = "Téléchargement en cours...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "Les modèles Google sont protégés par une licence : si le téléchargement échoue, acceptez la licence sur la page Hugging Face du modèle (gratuit, avec un compte) puis réessayez.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("URL de base *") },
                        placeholder = { Text("https://...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        label = { Text("Nom du modèle *") },
                        placeholder = { Text("ex: gemini-2.0-flash, gpt-4o-mini...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("Clé API") },
                        placeholder = { Text("Clé API (laisser vide si Ollama/Local)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(
                                    imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isApiKeyVisible) "Masquer la clé" else "Afficher la clé"
                                )
                            }
                        }
                    )
                }

                // Test connection inside dialog
                Button(
                    onClick = {
                        scope.launch {
                            isTesting = true
                            testResult = null
                            val res = onTestConnection(baseUrl, apiKey, modelName)
                            testResult = res
                            isTesting = false
                        }
                    },
                    enabled = !isTesting && baseUrl.isNotBlank() && modelName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test en cours...")
                    } else {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tester la connexion")
                    }
                }

                testResult?.let { res ->
                    res.fold(
                        onSuccess = { msg ->
                            Surface(color = EmeraldDark.copy(alpha = 0.12f), shape = LearnSyncShapes.small) {
                                Text(
                                    text = "✓ $msg",
                                    color = EmeraldDark,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                        },
                        onFailure = { err ->
                            Surface(color = RoseError.copy(alpha = 0.12f), shape = LearnSyncShapes.small) {
                                Text(
                                    text = "✗ ${err.localizedMessage ?: "Échec de connexion"}",
                                    color = RoseError,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (baseUrl.isNotBlank() && modelName.isNotBlank()) {
                        val finalName = name.ifBlank { selectedPreset.displayName }
                        onConfirm(finalName, selectedPreset.id, baseUrl, apiKey, modelName)
                    }
                },
                enabled = baseUrl.isNotBlank() && modelName.isNotBlank()
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        },
        shape = LearnSyncShapes.large
    )
}
