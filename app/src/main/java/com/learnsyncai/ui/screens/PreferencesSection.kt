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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.learnsyncai.domain.model.UserPreferences
import com.learnsyncai.ui.components.LearnSyncSecondaryButton
import com.learnsyncai.ui.theme.*

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Carte « Notifications » : rappels quotidiens et heure de rappel. */
@Composable
internal fun NotificationsSection(
    preferences: UserPreferences,
    onUpdatePreferences: (UserPreferences) -> Unit
) {
    val context = LocalContext.current
    var showNotificationRationaleDialog by remember { mutableStateOf(false) }
    var showNotificationSettingsDialog by remember { mutableStateOf(false) }
    var notificationDeniedCount by remember { mutableStateOf(0) }

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

/** Carte « Paramètres de révision » : lecture vocale automatique. */
@Composable
internal fun ReviewSettingsSection(
    preferences: UserPreferences,
    onUpdatePreferences: (UserPreferences) -> Unit
) {
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
            ProfileSectionTitle(icon = Icons.Default.Tune, title = "Paramètres de révision")

            Text(
                text = "La session de révision couvre toujours toutes les cartes dues de tous tes cours (plus d'objectif quotidien).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lecture vocale automatique",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Lire la question à voix haute à l'affichage de chaque carte",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = preferences.autoTtsEnabled,
                    onCheckedChange = { enabled ->
                        onUpdatePreferences(preferences.copy(autoTtsEnabled = enabled))
                    }
                )
            }
        }
    }
}

/** Carte « Quantités de génération IA » : modes auto/personnalisé par type de contenu. */
@Composable
internal fun GenerationQuantitiesSection(
    preferences: UserPreferences,
    onUpdatePreferences: (UserPreferences) -> Unit
) {
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
