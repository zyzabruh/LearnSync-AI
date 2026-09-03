package com.learnsyncai.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.learnsyncai.data.sync.AuthManager
import com.learnsyncai.data.sync.FirebaseHelper
import com.learnsyncai.domain.model.AiProfile
import com.learnsyncai.domain.model.UserPreferences
import com.learnsyncai.ui.components.LearnSyncSecondaryButton
import com.learnsyncai.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    preferences: UserPreferences,
    aiProfiles: List<AiProfile> = emptyList(),
    activeAiProfile: AiProfile? = null,
    onUpdatePreferences: (UserPreferences) -> Unit,
    onAddAiProfile: (name: String, provider: String, baseUrl: String, apiKey: String, modelName: String) -> Unit = { _, _, _, _, _ -> },
    onUpdateAiProfile: (AiProfile) -> Unit = {},
    onDeleteAiProfile: (profileId: String) -> Unit = {},
    onSetActiveAiProfile: (profileId: String) -> Unit = {},
    onSyncCloud: () -> Unit,
    onSyncCalendar: () -> Unit,
    onNavigateToCalendar: () -> Unit = {},
    onTestAiConnection: suspend (baseUrl: String, apiKey: String, modelName: String) -> Result<String> = { _, _, _ -> Result.success("OK") },
    onImportLocalModel: suspend (Uri) -> Result<String> = { Result.failure(IllegalStateException("Import de modèle indisponible.")) },
    onDownloadGemmaModel: (url: String, hfToken: String, onResult: (Result<String>) -> Unit) -> Unit = { _, _, _ -> },
    modelDownloadProgress: Float? = null,
    localModels: List<com.learnsyncai.ui.viewmodels.LocalModelInfo> = emptyList(),
    onRefreshLocalModels: () -> Unit = {},
    onDeleteLocalModel: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authManager = remember { AuthManager() }
    val auth = FirebaseHelper.getAuth()
    var currentUser by remember { mutableStateOf(auth?.currentUser) }
    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth?.addAuthStateListener(listener)
        onDispose { auth?.removeAuthStateListener(listener) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Profil & Préférences",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = LearnSyncSpacing.large),
            verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.large),
            contentPadding = PaddingValues(bottom = LearnSyncSpacing.xxl)
        ) {
            // SECTION 1: Compte & Authentification
            item {
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
                        ProfileSectionTitle(icon = Icons.Default.AccountCircle, title = "Compte")

                        val user = currentUser
                        if (user != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldSoftBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = EmeraldDark
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.displayName ?: "Utilisateur",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = user.email ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        authManager.signOut()
                                        currentUser = null
                                    }
                                ) {
                                    Text("Se déconnecter", color = RoseError)
                                }
                            }
                        } else {
                            Text(
                                text = "Connectez votre compte Google pour sauvegarder vos cours et synchroniser vos progrès sur le cloud Firestore.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (authError != null) {
                                Text(
                                    text = authError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RoseError
                                )
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        isSigningIn = true
                                        authError = null
                                        val result = authManager.signInWithGoogle(context)
                                        if (result.isFailure) {
                                            authError = result.exceptionOrNull()?.message ?: "Erreur de connexion"
                                        } else {
                                            currentUser = result.getOrNull()
                                        }
                                        isSigningIn = false
                                    }
                                },
                                enabled = !isSigningIn,
                                modifier = Modifier.fillMaxWidth().testTag("google_sign_in_button"),
                                shape = LearnSyncShapes.medium
                            ) {
                                if (isSigningIn) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("Se connecter avec Google")
                            }
                        }
                    }
                }
            }

            // SECTION 2: Fournisseurs & Profils IA (Multi-API)
            item {
                AiProfilesSection(
                    aiProfiles = aiProfiles,
                    activeAiProfile = activeAiProfile,
                    onAddAiProfile = onAddAiProfile,
                    onUpdateAiProfile = onUpdateAiProfile,
                    onDeleteAiProfile = onDeleteAiProfile,
                    onSetActiveAiProfile = onSetActiveAiProfile,
                    onTestAiConnection = onTestAiConnection,
                    onImportLocalModel = onImportLocalModel,
                    onDownloadGemmaModel = onDownloadGemmaModel,
                    modelDownloadProgress = modelDownloadProgress
                )
            }

            // SECTION: Modèles locaux (Gemma) téléchargés / importés
            item {
                LocalModelsSection(
                    localModels = localModels,
                    onRefreshLocalModels = onRefreshLocalModels,
                    onDeleteLocalModel = onDeleteLocalModel
                )
            }

            // SECTION 3: Notifications & Rappels
            item {
                NotificationsSection(
                    preferences = preferences,
                    onUpdatePreferences = onUpdatePreferences
                )
            }

            // SECTION 3: Paramètres de Révision
            item {
                ReviewSettingsSection(
                    preferences = preferences,
                    onUpdatePreferences = onUpdatePreferences
                )
            }

            // SECTION: Paramètres de génération IA (Quantités)
            item {
                CalendarSettingsSection(
                    preferences = preferences,
                    onUpdatePreferences = onUpdatePreferences
                )
            }

            item {
                GenerationQuantitiesSection(
                    preferences = preferences,
                    onUpdatePreferences = onUpdatePreferences
                )
            }

            // SECTION 4: Calendrier & Synchronisation Cloud
            item {
                SyncSection(
                    onSyncCloud = onSyncCloud,
                    onNavigateToCalendar = onNavigateToCalendar
                )
            }

            // SECTION 5: À propos
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = LearnSyncShapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(LearnSyncSpacing.large),
                        verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
                    ) {
                        ProfileSectionTitle(icon = Icons.Default.Info, title = "À propos")
                        Text(
                            text = "LearnSync AI v1.0.0",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Application de révision intelligente propulsée par le modèle d'espacement FSRS v4 et l'analyse de documents par IA.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProfileSectionTitle(
    icon: ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
