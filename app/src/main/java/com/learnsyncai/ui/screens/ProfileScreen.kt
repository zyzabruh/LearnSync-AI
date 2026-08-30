package com.learnsyncai.ui.screens

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.learnsyncai.data.sync.AuthManager
import com.learnsyncai.data.sync.FirebaseHelper
import com.learnsyncai.domain.model.AiProfile
import com.learnsyncai.domain.model.UserPreferences
import com.learnsyncai.ui.components.*
import com.learnsyncai.ui.theme.*
import kotlinx.coroutines.launch

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

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
    CUSTOM(
        id = "CUSTOM",
        displayName = "Personnalisé",
        defaultBaseUrl = "",
        defaultModel = ""
    )
}

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
    onTestAiConnection: suspend (baseUrl: String, apiKey: String, modelName: String) -> Result<String> = { _, _, _ -> Result.success("OK") }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authManager = remember { AuthManager() }
    val auth = FirebaseHelper.getAuth()
    var currentUser by remember { mutableStateOf(auth?.currentUser) }
    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var showNotificationRationaleDialog by remember { mutableStateOf(false) }
    var showNotificationSettingsDialog by remember { mutableStateOf(false) }
    var notificationDeniedCount by remember { mutableStateOf(0) }

    // Multi-API profile dialogs
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var profileToEdit by remember { mutableStateOf<AiProfile?>(null) }
    var testingProfileId by remember { mutableStateOf<String?>(null) }
    var profileTestResults by remember { mutableStateOf<Map<String, Result<String>>>(emptyMap()) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onUpdatePreferences(preferences.copy(notificationsEnabled = true))
        } else {
            onUpdatePreferences(preferences.copy(notificationsEnabled = false))
            notificationDeniedCount++
        }
    }

    if (showNotificationRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationRationaleDialog = false },
            icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Activer les rappels quotidiens") },
            text = {
                Text("LearnSync AI utilise les notifications pour vous rappeler chaque matin les flashcards dont l'intervalle de répétition espacée (FSRS) arrive à échéance.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotificationRationaleDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onUpdatePreferences(preferences.copy(notificationsEnabled = true))
                        }
                    }
                ) {
                    Text("Autoriser")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationRationaleDialog = false }) {
                    Text("Plus tard")
                }
            }
        )
    }

    if (showNotificationSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationSettingsDialog = false },
            icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Permission requise") },
            text = {
                Text("L'accès aux notifications a été refusé de façon permanente. Veuillez l'autoriser dans les paramètres de l'application.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotificationSettingsDialog = false
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("Ouvrir les paramètres")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationSettingsDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

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
            }


            // SECTION 3: Notifications & Rappels
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
                        ProfileSectionTitle(icon = Icons.Default.Notifications, title = "Notifications")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Rappels quotidiens",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Notification pour les cartes dues",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = preferences.notificationsEnabled,
                                onCheckedChange = { isEnabled ->
                                    if (isEnabled) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            val hasPermission = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) == PackageManager.PERMISSION_GRANTED

                                            if (hasPermission) {
                                                onUpdatePreferences(preferences.copy(notificationsEnabled = true))
                                            } else {
                                                val activity = context.findActivity()
                                                val showRationale = activity?.let { 
                                                    ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS) 
                                                } ?: false

                                                if (notificationDeniedCount >= 1 && !showRationale) {
                                                    showNotificationSettingsDialog = true
                                                } else {
                                                    showNotificationRationaleDialog = true
                                                }
                                            }
                                        } else {
                                            onUpdatePreferences(preferences.copy(notificationsEnabled = true))
                                        }
                                    } else {
                                        onUpdatePreferences(preferences.copy(notificationsEnabled = false))
                                    }
                                }
                            )
                        }

                        if (preferences.notificationsEnabled) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Heure de rappel",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = preferences.reminderTime,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                LearnSyncSecondaryButton(
                                    text = "Modifier",
                                    icon = Icons.Default.AccessTime,
                                    onClick = {
                                        val parts = preferences.reminderTime.split(":")
                                        val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
                                        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                        TimePickerDialog(
                                            context,
                                            { _, hourOfDay, minute ->
                                                val timeStr = "%02d:%02d".format(hourOfDay, minute)
                                                onUpdatePreferences(preferences.copy(reminderTime = timeStr))
                                            },
                                            h,
                                            m,
                                            true
                                        ).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 3: Paramètres de Révision
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
                        ProfileSectionTitle(icon = Icons.Default.Tune, title = "Objectifs de révision")

                        Text(
                            text = "Objectif quotidien : ${preferences.dailyGoal} cartes / jour",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Slider(
                            value = preferences.dailyGoal.toFloat(),
                            onValueChange = { newValue ->
                                onUpdatePreferences(preferences.copy(dailyGoal = newValue.toInt()))
                            },
                            valueRange = 5f..100f,
                            steps = 18
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("5", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("50", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("100", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // SECTION: Paramètres de génération IA (Quantités)
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
                        ProfileSectionTitle(icon = Icons.Default.Tune, title = "Quantités de génération IA")

                        // 1. Points clés (Info only)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Points clés / Notions",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Mode automatique uniquement (couverture complète sans limite fixe, idéal pour ne rien omettre).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 2. Flashcards
                        Column(verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Flashcards",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilterChip(
                                        selected = preferences.flashcardsMode == "auto",
                                        onClick = { onUpdatePreferences(preferences.copy(flashcardsMode = "auto")) },
                                        label = { Text("Auto") }
                                    )
                                    FilterChip(
                                        selected = preferences.flashcardsMode == "custom",
                                        onClick = { onUpdatePreferences(preferences.copy(flashcardsMode = "custom")) },
                                        label = { Text("Personnalisé") }
                                    )
                                }
                            }
                            if (preferences.flashcardsMode == "custom") {
                                val quickOptions = listOf(5, 10, 15, 20, 30, 50)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    quickOptions.forEach { count ->
                                        FilterChip(
                                            selected = preferences.flashcardsCustomCount == count,
                                            onClick = { onUpdatePreferences(preferences.copy(flashcardsCustomCount = count)) },
                                            label = { Text("$count") }
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = preferences.flashcardsCustomCount.toString(),
                                    onValueChange = { s ->
                                        val c = s.toIntOrNull() ?: 10
                                        onUpdatePreferences(preferences.copy(flashcardsCustomCount = c.coerceIn(1, 200)))
                                    },
                                    label = { Text("Nombre exact de flashcards") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 3. QCM
                        Column(verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Questions QCM",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilterChip(
                                        selected = preferences.quizMode == "auto",
                                        onClick = { onUpdatePreferences(preferences.copy(quizMode = "auto")) },
                                        label = { Text("Auto") }
                                    )
                                    FilterChip(
                                        selected = preferences.quizMode == "custom",
                                        onClick = { onUpdatePreferences(preferences.copy(quizMode = "custom")) },
                                        label = { Text("Personnalisé") }
                                    )
                                }
                            }
                            if (preferences.quizMode == "custom") {
                                val quickOptions = listOf(5, 10, 15, 20, 30, 50)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    quickOptions.forEach { count ->
                                        FilterChip(
                                            selected = preferences.quizCustomCount == count,
                                            onClick = { onUpdatePreferences(preferences.copy(quizCustomCount = count)) },
                                            label = { Text("$count") }
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = preferences.quizCustomCount.toString(),
                                    onValueChange = { s ->
                                        val c = s.toIntOrNull() ?: 5
                                        onUpdatePreferences(preferences.copy(quizCustomCount = c.coerceIn(1, 100)))
                                    },
                                    label = { Text("Nombre exact de QCM") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 4. Mnemonic Tips
                        Column(verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Astuces mnémotechniques",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilterChip(
                                        selected = preferences.mnemonicTipsMode == "auto",
                                        onClick = { onUpdatePreferences(preferences.copy(mnemonicTipsMode = "auto")) },
                                        label = { Text("Auto") }
                                    )
                                    FilterChip(
                                        selected = preferences.mnemonicTipsMode == "custom",
                                        onClick = { onUpdatePreferences(preferences.copy(mnemonicTipsMode = "custom")) },
                                        label = { Text("Personnalisé") }
                                    )
                                }
                            }
                            if (preferences.mnemonicTipsMode == "custom") {
                                val quickOptions = listOf(3, 5, 10)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    quickOptions.forEach { count ->
                                        FilterChip(
                                            selected = preferences.mnemonicTipsCustomCount == count,
                                            onClick = { onUpdatePreferences(preferences.copy(mnemonicTipsCustomCount = count)) },
                                            label = { Text("$count") }
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = preferences.mnemonicTipsCustomCount.toString(),
                                    onValueChange = { s ->
                                        val c = s.toIntOrNull() ?: 3
                                        onUpdatePreferences(preferences.copy(mnemonicTipsCustomCount = c.coerceIn(1, 30)))
                                    },
                                    label = { Text("Nombre exact d'astuces") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 4: Calendrier & Synchronisation Cloud
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
                        ProfileSectionTitle(icon = Icons.Default.CloudSync, title = "Synchronisation")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Google Agenda",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Planifier les sessions de révision dans le calendrier",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(onClick = onNavigateToCalendar) {
                                Text("Voir")
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cloud Firestore",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Sauvegarder et synchroniser les cours",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(onClick = onSyncCloud) {
                                Text("Sync")
                            }
                        }
                    }
                }
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

    // Dialog: Ajouter un profil IA
    if (showAddProfileDialog) {
        AiProfileEditDialog(
            initialProfile = null,
            onDismiss = { showAddProfileDialog = false },
            onConfirm = { name, provider, baseUrl, apiKey, modelName ->
                onAddAiProfile(name, provider, baseUrl, apiKey, modelName)
                showAddProfileDialog = false
            },
            onTestConnection = onTestAiConnection
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
            onTestConnection = onTestAiConnection
        )
    }
}

@Composable
private fun AiProfileEditDialog(
    initialProfile: AiProfile?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, provider: String, baseUrl: String, apiKey: String, modelName: String) -> Unit,
    onTestConnection: suspend (baseUrl: String, apiKey: String, modelName: String) -> Result<String>
) {
    val scope = rememberCoroutineScope()
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
                modifier = Modifier.fillMaxWidth(),
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
                if (selectedPreset == AiProviderPreset.OPENCODE_ZEN && note != null) {
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

@Composable
private fun ProfileSectionTitle(
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

