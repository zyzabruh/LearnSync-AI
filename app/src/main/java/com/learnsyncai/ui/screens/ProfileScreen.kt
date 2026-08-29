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
        defaultModel = "gemini-2.0-flash"
    ),
    OPENROUTER(
        id = "OPENROUTER",
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "google/gemini-2.0-flash-exp:free"
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
    onUpdatePreferences: (UserPreferences) -> Unit,
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
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var isTestingAi by remember { mutableStateOf(false) }
    var aiTestResult by remember { mutableStateOf<Result<String>?>(null) }

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

            // SECTION 2: Fournisseur IA (Génération)
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
                        ProfileSectionTitle(icon = Icons.Default.SmartToy, title = "Fournisseur IA")

                        Text(
                            text = "Choisissez et personnalisez votre service d'intelligence artificielle compatible OpenAI pour la génération de résumés, flashcards et QCM.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Sélecteur de préréglage
                        Text(
                            text = "Préréglage",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val currentPreset = AiProviderPreset.entries.find { it.id == preferences.aiProvider } ?: AiProviderPreset.GEMINI

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
                        ) {
                            AiProviderPreset.entries.forEach { preset ->
                                val isSelected = preset == currentPreset
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (!isSelected) {
                                            aiTestResult = null
                                            if (preset == AiProviderPreset.CUSTOM) {
                                                onUpdatePreferences(
                                                    preferences.copy(
                                                        aiProvider = preset.id
                                                    )
                                                )
                                            } else {
                                                onUpdatePreferences(
                                                    preferences.copy(
                                                        aiProvider = preset.id,
                                                        aiBaseUrl = preset.defaultBaseUrl,
                                                        aiModelName = preset.defaultModel
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    label = { Text(preset.displayName) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }

                        // Note informative pour OpenCode Zen
                        if (currentPreset == AiProviderPreset.OPENCODE_ZEN && currentPreset.note != null) {
                            Surface(
                                color = AmberFlame.copy(alpha = 0.12f),
                                shape = LearnSyncShapes.small,
                                border = BorderStroke(1.dp, AmberFlame.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(LearnSyncSpacing.medium),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = AmberFlame,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = currentPreset.note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Champ Base URL
                        OutlinedTextField(
                            value = preferences.aiBaseUrl,
                            onValueChange = { newUrl ->
                                onUpdatePreferences(preferences.copy(aiBaseUrl = newUrl))
                            },
                            label = { Text("URL de base (Base URL)") },
                            placeholder = { Text("https://...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = LearnSyncShapes.medium
                        )

                        // Champ Nom du modèle
                        OutlinedTextField(
                            value = preferences.aiModelName,
                            onValueChange = { newModel ->
                                onUpdatePreferences(preferences.copy(aiModelName = newModel))
                            },
                            label = { Text("Nom du modèle") },
                            placeholder = { Text("ex: gemini-2.0-flash, gpt-4o-mini...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = LearnSyncShapes.medium
                        )

                        // Champ Clé API (masqué avec toggle)
                        OutlinedTextField(
                            value = preferences.aiApiKey,
                            onValueChange = { newKey ->
                                onUpdatePreferences(preferences.copy(aiApiKey = newKey))
                            },
                            label = { Text("Clé API") },
                            placeholder = { Text("Clé API secrète (stockée en local uniquement)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = LearnSyncShapes.medium,
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

                        // Bouton de test de connexion
                        Button(
                            onClick = {
                                scope.launch {
                                    isTestingAi = true
                                    aiTestResult = null
                                    val res = onTestAiConnection(
                                        preferences.aiBaseUrl,
                                        preferences.aiApiKey,
                                        preferences.aiModelName
                                    )
                                    aiTestResult = res
                                    isTestingAi = false
                                }
                            },
                            enabled = !isTestingAi && preferences.aiBaseUrl.isNotBlank() && preferences.aiModelName.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = LearnSyncShapes.medium
                        ) {
                            if (isTestingAi) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Test en cours...")
                            } else {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Tester la connexion")
                            }
                        }

                        // Résultat du test de connexion
                        aiTestResult?.let { result ->
                            result.fold(
                                onSuccess = { successMessage ->
                                    Surface(
                                        color = EmeraldDark.copy(alpha = 0.12f),
                                        shape = LearnSyncShapes.small,
                                        border = BorderStroke(1.dp, EmeraldDark.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(LearnSyncSpacing.medium),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = EmeraldDark,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = successMessage,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = EmeraldDark,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                },
                                onFailure = { error ->
                                    Surface(
                                        color = RoseError.copy(alpha = 0.12f),
                                        shape = LearnSyncShapes.small,
                                        border = BorderStroke(1.dp, RoseError.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(LearnSyncSpacing.medium),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ErrorOutline,
                                                contentDescription = null,
                                                tint = RoseError,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = error.localizedMessage ?: "Échec du test de connexion",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = RoseError
                                            )
                                        }
                                    }
                                }
                            )
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
