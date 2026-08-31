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
import androidx.compose.foundation.clickable
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

/** Début de journée (00:00) correspondant à un timestamp, dans le fuseau local. */
private fun dayStartOf(millis: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** Résumé de révision d'un cours pour le calendrier. */
private data class CourseReviewSummary(
    val course: Course,
    val dueCount: Int,
    val nextDue: Long?
)

/** Grille mensuelle avec pastilles sur les jours ayant des révisions. */
@Composable
private fun ReviewMonthGrid(
    year: Int,
    month: Int,
    markerDays: Map<Long, Int>,
    selectedDay: Long?,
    onDayClick: (Long) -> Unit,
    onMonthChange: (year: Int, month: Int) -> Unit
) {
    val monthCal = remember(year, month) {
        Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    // Semaine commençant le lundi (convention française)
    val firstDayOffset = (monthCal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    val todayStart = remember { dayStartOf(System.currentTimeMillis()) }
    val monthLabel = remember(year, month) {
        SimpleDateFormat("MMMM yyyy", Locale.FRANCE).format(monthCal.time).replaceFirstChar { it.uppercase() }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = LearnSyncShapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(LearnSyncSpacing.large)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    if (month == 0) onMonthChange(year - 1, 11) else onMonthChange(year, month - 1)
                }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Mois précédent")
                }
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = {
                    if (month == 11) onMonthChange(year + 1, 0) else onMonthChange(year, month + 1)
                }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Mois suivant")
                }
            }

            Spacer(modifier = Modifier.height(LearnSyncSpacing.small))

            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("L", "M", "M", "J", "V", "S", "D").forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(LearnSyncSpacing.extraSmall))

            val cells: List<Long?> = buildList {
                repeat(firstDayOffset) { add(null) }
                for (d in 1..daysInMonth) {
                    val dayCal = Calendar.getInstance().apply {
                        set(year, month, d, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    add(dayStartOf(dayCal.timeInMillis))
                }
                val remainder = size % 7
                if (remainder != 0) repeat(7 - remainder) { add(null) }
            }

            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        day != null && day == selectedDay -> IndigoPrimary
                                        day != null && day == todayStart -> IndigoSoftBg
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable(enabled = day != null) { day?.let(onDayClick) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (day != null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "${Calendar.getInstance().apply { timeInMillis = day }.get(Calendar.DAY_OF_MONTH)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (day == selectedDay || day == todayStart) FontWeight.Bold else FontWeight.Normal,
                                        color = if (day == selectedDay) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                    val count = markerDays[day] ?: 0
                                    if (count > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(if (day == selectedDay) Color.White else AmberFlame)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(5.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(LearnSyncSpacing.small))

            Text(
                text = "Touche un jour pour voir ses révisions. Les jours avec une pastille orange ont des cartes planifiées.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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

    // ---- Données du calendrier ----

    val dayFormat = remember { SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE) }
    val shortFormat = remember { SimpleDateFormat("EEE d MMM", Locale.FRANCE) }

    // Jour (début de journée) -> cartes de ce jour. Les cartes en retard
    // comptent comme "aujourd'hui", comme sur l'écran d'accueil.
    val dayBuckets = remember(allFlashcards) {
        val current = System.currentTimeMillis()
        val buckets = mutableMapOf<Long, MutableList<Flashcard>>()
        allFlashcards.forEach { card ->
            val day = dayStartOf(card.dueDate.coerceAtLeast(current))
            buckets.getOrPut(day) { mutableListOf() }.add(card)
        }
        buckets.mapValues { it.value.sortedBy { c -> c.dueDate } }
    }

    // Résumé par cours : cartes dues maintenant + prochaine échéance
    val courseSummaries = remember(courses, allFlashcards) {
        val current = System.currentTimeMillis()
        courses.map { course ->
            val cards = allFlashcards.filter { it.courseId == course.id }
            CourseReviewSummary(
                course = course,
                dueCount = cards.count { it.dueDate <= current },
                nextDue = cards.map { it.dueDate.coerceAtLeast(current) }.minOrNull()
            )
        }.sortedWith(compareBy({ it.nextDue == null }, { it.nextDue }))
    }

    // Mois affiché dans la grille + jour sélectionné (début de journée, null = tout)
    var displayedYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var displayedMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    var selectedDay by remember { mutableStateOf<Long?>(null) }

    val upcomingReviews = remember(dayBuckets, selectedDay) {
        val current = System.currentTimeMillis()
        val cards = selectedDay?.let { day -> dayBuckets[day] ?: emptyList() } ?: dayBuckets.values.flatten()
        cards.groupBy { card ->
            dayFormat.format(Date(card.dueDate.coerceAtLeast(current))).replaceFirstChar { c -> c.uppercase() }
        }
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

            // Section : vue par cours
            if (courseSummaries.isNotEmpty()) {
                item { SectionHeader(title = "Mes cours à réviser") }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = LearnSyncShapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(vertical = LearnSyncSpacing.small)) {
                            courseSummaries.forEach { summary ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = LearnSyncSpacing.large, vertical = LearnSyncSpacing.small),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = summary.course.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = when {
                                                summary.nextDue == null -> "Aucune flashcard"
                                                else -> {
                                                    val label = shortFormat.format(Date(summary.nextDue))
                                                    if (summary.dueCount > 0) "$label · ${summary.dueCount} à réviser" else label
                                                }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (summary.dueCount > 0) AmberDark else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (summary.dueCount > 0) {
                                        Surface(
                                            shape = LearnSyncShapes.pill,
                                            color = AmberSoftBg
                                        ) {
                                            Text(
                                                text = "${summary.dueCount}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = AmberDark,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section : grille du mois
            item {
                SectionHeader(title = "Calendrier")
                ReviewMonthGrid(
                    year = displayedYear,
                    month = displayedMonth,
                    markerDays = dayBuckets.mapValues { it.value.size },
                    selectedDay = selectedDay,
                    onDayClick = { day ->
                        selectedDay = if (selectedDay == day) null else day
                    },
                    onMonthChange = { y, m ->
                        displayedYear = y
                        displayedMonth = m
                    }
                )
            }

            // Section Header
            item {
                if (selectedDay != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SectionHeader(title = "Cartes du jour sélectionné")
                        TextButton(onClick = { selectedDay = null }) {
                            Text("Tout afficher")
                        }
                    }
                } else {
                    SectionHeader(title = "Prochaines échéances")
                }
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
