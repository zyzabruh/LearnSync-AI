package com.learnsyncai.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.learnsyncai.domain.model.Course
import com.learnsyncai.domain.model.Flashcard
import com.learnsyncai.ui.components.*
import com.learnsyncai.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    allFlashcards: List<Flashcard>,
    courses: List<Course> = emptyList(),
    onSyncCalendar: () -> Unit,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var hasCalendarPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showCalendarRationaleDialog by remember { mutableStateOf(false) }
    var showCalendarSettingsDialog by remember { mutableStateOf(false) }
    var calendarDeniedCount by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCalendarPermission = permissions[Manifest.permission.WRITE_CALENDAR] == true
        if (hasCalendarPermission) {
            onSyncCalendar()
        } else {
            calendarDeniedCount++
        }
    }

    if (showCalendarRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showCalendarRationaleDialog = false },
            icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Accès au Calendrier") },
            text = {
                Text("LearnSync AI a besoin de l'accès à votre agenda pour y inscrire automatiquement vos séances de révision FSRS et optimiser votre mémorisation.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCalendarRationaleDialog = false
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CALENDAR,
                                Manifest.permission.WRITE_CALENDAR
                            )
                        )
                    }
                ) {
                    Text("Continuer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCalendarRationaleDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    if (showCalendarSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showCalendarSettingsDialog = false },
            icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Permission requise") },
            text = {
                Text("L'accès au calendrier a été refusé de façon permanente. Veuillez l'autoriser dans les paramètres de l'application.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCalendarSettingsDialog = false
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
                TextButton(onClick = { showCalendarSettingsDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Group flashcards due dates
    val upcomingReviews = remember(allFlashcards) {
        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE)
        allFlashcards
            .sortedBy { it.dueDate }
            .groupBy {
                val date = Date(it.dueDate.coerceAtLeast(now))
                sdf.format(date).replaceFirstChar { char -> char.uppercase() }
            }
    }

    val dueTodayCount = remember(allFlashcards) {
        val now = System.currentTimeMillis()
        allFlashcards.count { it.dueDate <= now }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Planning & Calendrier",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
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
            // Hero Calendar Card
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(IndigoSoftBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = IndigoPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "Synchronisation Calendrier",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (hasCalendarPermission) "✓ Agenda synchronisé" else "Non synchronisé",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (hasCalendarPermission) EmeraldSuccess else AmberFlame,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Ajoute automatiquement tes sessions de révision FSRS directement dans ton calendrier Android pour ne manquer aucune date d'oubli critique.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        LearnSyncButton(
                            text = "Synchroniser mes révisions",
                            icon = Icons.Default.Sync,
                            onClick = {
                                if (hasCalendarPermission) {
                                    onSyncCalendar()
                                } else {
                                    val activity = context.findActivity()
                                    val showRationale = activity?.let { 
                                        ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.WRITE_CALENDAR) 
                                    } ?: false

                                    if (calendarDeniedCount >= 1 && !showRationale) {
                                        showCalendarSettingsDialog = true
                                    } else {
                                        showCalendarRationaleDialog = true
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Section Header
            item {
                SectionHeader(title = "Prochaines échéances")
            }

            if (upcomingReviews.isEmpty()) {
                item {
                    EmptyState(
                        title = "Aucune session planifiée",
                        description = "Importe des cours pour générer des flashcards et planifier tes révisions.",
                        icon = Icons.AutoMirrored.Filled.EventNote
                    )
                }
            } else {
                upcomingReviews.forEach { (dateGroup, cards) ->
                    item {
                        Surface(
                            shape = LearnSyncShapes.pill,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = IndigoPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "$dateGroup · ${cards.size} cartes",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    items(cards) { card ->
                        val course = courses.find { it.id == card.courseId }
                        val isDueNow = card.dueDate <= System.currentTimeMillis()

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = LearnSyncShapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(LearnSyncSpacing.large),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (course != null) {
                                        Text(
                                            text = course.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = IndigoPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = card.question,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                if (isDueNow) {
                                    Surface(
                                        shape = LearnSyncShapes.pill,
                                        color = AmberSoftBg
                                    ) {
                                        Text(
                                            text = "À réviser",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = AmberDark,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
}
