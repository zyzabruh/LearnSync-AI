package com.learnsyncai.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.learnsyncai.domain.model.AiProfile
import com.learnsyncai.ui.theme.*
import kotlinx.coroutines.launch

private fun formatModelSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) {
        String.format(java.util.Locale.FRANCE, "%.2f Go", mb / 1024.0)
    } else {
        String.format(java.util.Locale.FRANCE, "%.0f Mo", mb)
    }
}

/**
 * Section « Fournisseurs IA » : liste des profils (sélection, test de
 * connexion, édition, suppression) et ouverture des dialogs d'ajout/édition.
 */
@Composable
internal fun AiProfilesSection(
    aiProfiles: List<AiProfile>,
    activeAiProfile: AiProfile?,
    onAddAiProfile: (name: String, provider: String, baseUrl: String, apiKey: String, modelName: String) -> Unit,
    onUpdateAiProfile: (AiProfile) -> Unit,
    onDeleteAiProfile: (profileId: String) -> Unit,
    onSetActiveAiProfile: (profileId: String) -> Unit,
    onTestAiConnection: suspend (baseUrl: String, apiKey: String, modelName: String) -> Result<String>,
    onImportLocalModel: suspend (Uri) -> Result<String>,
    onDownloadGemmaModel: (url: String, hfToken: String, onResult: (Result<String>) -> Unit) -> Unit,
    modelDownloadProgress: Float?
) {
    val scope = rememberCoroutineScope()
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var profileToEdit by remember { mutableStateOf<AiProfile?>(null) }
    var testingProfileId by remember { mutableStateOf<String?>(null) }
    var profileTestResults by remember { mutableStateOf<Map<String, Result<String>>>(emptyMap()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = LearnSyncShapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(LearnSyncSpacing.large),
            verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProfileSectionTitle(icon = Icons.Default.SmartToy, title = "Fournisseurs IA (${aiProfiles.size})")
                FilledTonalButton(
                    onClick = { showAddProfileDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ajouter")
                }
            }

            Text(
                text = "Configurez plusieurs clés et modèles en parallèle (Gemini, OpenRouter, NVIDIA, OpenCode Zen, Ollama/Local) et sélectionnez celui utilisé pour vos cours.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (aiProfiles.isEmpty()) {
                Surface(
                    color = AmberFlame.copy(alpha = 0.08f),
                    shape = LearnSyncShapes.medium,
                    border = BorderStroke(1.dp, AmberFlame.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(LearnSyncSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = AmberFlame)
                        Text(
                            text = "Aucun profil IA configuré. Cliquez sur « Ajouter » pour configurer votre premier modèle.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)) {
                    aiProfiles.forEach { profile ->
                        val isActive = profile.isActive || (activeAiProfile?.id == profile.id)
                        val isTesting = testingProfileId == profile.id
                        val testResult = profileTestResults[profile.id]

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = LearnSyncShapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) IndigoSoftBg.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = BorderStroke(
                                width = if (isActive) 1.5.dp else 1.dp,
                                color = if (isActive) IndigoPrimary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(LearnSyncSpacing.medium),
                                verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        RadioButton(
                                            selected = isActive,
                                            onClick = { onSetActiveAiProfile(profile.id) }
                                        )
                                        Column {
                                            Text(
                                                text = profile.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isActive) IndigoPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Modèle : ${profile.modelName.ifBlank { "Non spécifié" }}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (isActive) {
                                        Surface(
                                            shape = LearnSyncShapes.pill,
                                            color = EmeraldSoftBg
                                        ) {
                                            Text(
                                                text = "Actif",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = EmeraldDark,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "URL : ${profile.baseUrl.ifBlank { "https://..." }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )

                                // Action buttons row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                testingProfileId = profile.id
                                                val res = onTestAiConnection(
                                                    profile.baseUrl,
                                                    profile.apiKey,
                                                    profile.modelName
                                                )
                                                profileTestResults = profileTestResults + (profile.id to res)
                                                testingProfileId = null
                                            }
                                        },
                                        enabled = !isTesting && profile.baseUrl.isNotBlank() && profile.modelName.isNotBlank()
                                    ) {
                                        if (isTesting) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text("Tester", style = MaterialTheme.typography.labelMedium)
                                    }

                                    TextButton(onClick = { profileToEdit = profile }) {
                                        Text("Modifier", style = MaterialTheme.typography.labelMedium)
                                    }

                                    if (aiProfiles.size > 1) {
                                        IconButton(
                                            onClick = { onDeleteAiProfile(profile.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Supprimer",
                                                tint = RoseError.copy(alpha = 0.8f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                // Result banner if tested
                                testResult?.let { res ->
                                    res.fold(
                                        onSuccess = { msg ->
                                            Surface(
                                                color = EmeraldDark.copy(alpha = 0.1f),
                                                shape = LearnSyncShapes.small
                                            ) {
                                                Text(
                                                    text = "✓ $msg",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = EmeraldDark,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        },
                                        onFailure = { err ->
                                            Surface(
                                                color = RoseError.copy(alpha = 0.1f),
                                                shape = LearnSyncShapes.small
                                            ) {
                                                Text(
                                                    text = "✗ ${err.localizedMessage ?: "Erreur de connexion"}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = RoseError,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Ajouter un profil IA
    if (showAddProfileDialog) {
        AiProfileEditDialog(
            initialProfile = null,
            onDismiss = { showAddProfileDialog = false },
            onConfirm = { name, provider, baseUrl, apiKey, modelName ->
                onAddAiProfile(name, provider, baseUrl, apiKey, modelName)
                showAddProfileDialog = false
            },
            onTestConnection = onTestAiConnection,
            onImportLocalModel = onImportLocalModel,
            onDownloadGemmaModel = onDownloadGemmaModel,
            modelDownloadProgress = modelDownloadProgress
        )
    }

    // Dialog: Modifier un profil IA existant
    profileToEdit?.let { profile ->
        AiProfileEditDialog(
            initialProfile = profile,
            onDismiss = { profileToEdit = null },
            onConfirm = { name, provider, baseUrl, apiKey, modelName ->
                onUpdateAiProfile(
                    profile.copy(
                        name = name,
                        provider = provider,
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        modelName = modelName
                    )
                )
                profileToEdit = null
            },
            onTestConnection = onTestAiConnection,
            onImportLocalModel = onImportLocalModel,
            onDownloadGemmaModel = onDownloadGemmaModel,
            modelDownloadProgress = modelDownloadProgress
        )
    }
}

/** Section « Modèles IA locaux » : fichiers Gemma stockés sur l'appareil. */
@Composable
internal fun LocalModelsSection(
    localModels: List<com.learnsyncai.ui.viewmodels.LocalModelInfo>,
    onRefreshLocalModels: () -> Unit,
    onDeleteLocalModel: (String) -> Unit
) {
    LaunchedEffect(Unit) { onRefreshLocalModels() }
    var localModelToDelete by remember { mutableStateOf<com.learnsyncai.ui.viewmodels.LocalModelInfo?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = LearnSyncShapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(LearnSyncSpacing.large),
            verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium)
        ) {
            ProfileSectionTitle(icon = Icons.Default.SmartToy, title = "Modèles IA locaux (${localModels.size})")

            if (localModels.isEmpty()) {
                Text(
                    text = "Aucun modèle stocké sur l'appareil. Les modèles Gemma téléchargés ou importés (2 à 4 Go chacun) apparaîtront ici et pourront être supprimés pour libérer de l'espace.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val totalSize = localModels.sumOf { it.sizeBytes }
                Text(
                    text = "Stockage total utilisé : ${formatModelSize(totalSize)}. Supprimez les modèles que vous n'utilisez plus pour libérer de l'espace.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                localModels.forEach { model ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = LearnSyncShapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatModelSize(model.sizeBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (model.usedByActiveProfile) {
                                Surface(shape = LearnSyncShapes.pill, color = EmeraldSoftBg) {
                                    Text(
                                        text = "Utilisé",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldDark,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            IconButton(
                                onClick = { localModelToDelete = model },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Supprimer le modèle",
                                    tint = RoseError.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation de suppression
    localModelToDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { localModelToDelete = null },
            title = { Text("Supprimer ce modèle ?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "« ${model.name} » (${formatModelSize(model.sizeBytes)}) sera définitivement supprimé du stockage de l'app." +
                        if (model.usedByActiveProfile) "\n\nAttention : ce modèle est actuellement utilisé par votre profil IA actif — vous devrez le retélécharger pour générer hors-ligne." else "",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteLocalModel(model.path)
                        localModelToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoseError,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { localModelToDelete = null }) {
                    Text("Annuler")
                }
            },
            shape = LearnSyncShapes.large
        )
    }
}
