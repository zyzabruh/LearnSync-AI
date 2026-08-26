package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.sync.AuthManager
import com.example.data.sync.FirebaseHelper
import com.example.domain.model.UserPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    preferences: UserPreferences,
    onUpdatePreferences: (UserPreferences) -> Unit,
    onSyncCloud: () -> Unit,
    onSyncCalendar: () -> Unit
) {
    val auth = remember { FirebaseHelper.getAuth() }
    val authManager = remember { AuthManager() }
    var currentUser by remember { mutableStateOf(authManager.getCurrentUser()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var showTimeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil & Paramètres", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Account Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentUser?.displayName ?: currentUser?.email ?: "Compte Local (Hors ligne)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = if (currentUser != null) (currentUser?.email ?: "Connecté avec Google") else "Données stockées localement sur l'appareil (Room)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (authError != null) {
                            Text(
                                text = authError ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (currentUser == null) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            isSigningIn = true
                                            authError = null
                                            val res = authManager.signInWithGoogle(context)
                                            isSigningIn = false
                                            if (res.isSuccess) {
                                                currentUser = res.getOrNull()
                                            } else {
                                                authError = "Erreur Google Sign-in: ${res.exceptionOrNull()?.localizedMessage}"
                                            }
                                        }
                                    },
                                    enabled = !isSigningIn,
                                    modifier = Modifier.weight(1f).testTag("google_sign_in_button")
                                ) {
                                    Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isSigningIn) "Connexion..." else "Google Sign-In")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        authManager.signOut()
                                        currentUser = null
                                    },
                                    modifier = Modifier.weight(1f).testTag("sign_out_button")
                                ) {
                                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Déconnexion")
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onSyncCloud,
                                modifier = Modifier.weight(1f).testTag("sync_cloud_button")
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync Cloud")
                            }
                            OutlinedButton(
                                onClick = onSyncCalendar,
                                modifier = Modifier.weight(1f).testTag("sync_calendar_button")
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync Calendrier")
                            }
                        }
                    }
                }
            }

            // Learning Preferences
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Préférences d'apprentissage", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Notifications, contentDescription = null)
                                Column {
                                    Text("Rappels quotidiens", fontWeight = FontWeight.SemiBold)
                                    Text("Notification quotidienne pour les cartes dues", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = preferences.notificationsEnabled,
                                onCheckedChange = { onUpdatePreferences(preferences.copy(notificationsEnabled = it)) }
                            )
                        }

                        if (preferences.notificationsEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccessTime, contentDescription = null)
                                    Column {
                                        Text("Heure du rappel", fontWeight = FontWeight.SemiBold)
                                        Text("Heure quotidienne du WorkManager", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                TextButton(onClick = { showTimeDialog = true }) {
                                    Text(preferences.reminderTime, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TrackChanges, contentDescription = null)
                                Column {
                                    Text("Objectif quotidien", fontWeight = FontWeight.SemiBold)
                                    Text("Nombre de cartes ciblées par jour", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(
                                text = "${preferences.dailyGoal} cartes",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // About Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("À propos de LearnSync AI", fontWeight = FontWeight.Bold)
                        Text(
                            "Version 1.0.0 — Moteur de révision intelligente propulsé par Gemini & algorithme de répétition espacée FSRS.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showTimeDialog) {
        AlertDialog(
            onDismissRequest = { showTimeDialog = false },
            title = { Text("Choisir l'heure de rappel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("07:00", "08:00", "09:00", "12:00", "18:00", "20:00").forEach { timeStr ->
                        TextButton(
                            onClick = {
                                onUpdatePreferences(preferences.copy(reminderTime = timeStr))
                                showTimeDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(timeStr, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimeDialog = false }) {
                    Text("Fermer")
                }
            }
        )
    }
}

