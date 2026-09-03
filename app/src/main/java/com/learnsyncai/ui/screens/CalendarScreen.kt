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
import androidx.compose.foundation.border
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
import com.learnsyncai.domain.model.ReviewLog
import com.learnsyncai.domain.model.ReviewSession
import com.learnsyncai.domain.usecase.SpacedRepetition
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

private data class ScheduledReview(
    val card: Flashcard,
    val scheduledAt: Long,
    val isForecast: Boolean
)

private data class CalendarDayMarkers(
    val actualCount: Int = 0,
    val forecastCount: Int = 0,
    val overdueCount: Int = 0,
    val reviewedCount: Int = 0
)

private const val FORECAST_HORIZON_DAYS = 30

/** Grille mensuelle avec pastilles sur les jours ayant des révisions. */
@Composable
private fun ReviewMonthGrid(
    year: Int,
    month: Int,
    markerDays: Map<Long, CalendarDayMarkers>,
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
                                    val markers = markerDays[day]
                                    if (markers != null && (markers.actualCount > 0 || markers.forecastCount > 0 || markers.reviewedCount > 0)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            if (markers.reviewedCount > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(if (day == selectedDay) Color.White else EmeraldSuccess)
                                                )
                                            }
                                            if (markers.actualCount > 0) {
                                                val actualColor = when {
                                                    day == selectedDay -> Color.White
                                                    markers.overdueCount > 0 -> RoseError
                                                    day == todayStart -> AmberFlame
                                                    else -> IndigoPrimary
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(actualColor)
                                                )
                                            }
                                            if (markers.forecastCount > 0) {
                                                val forecastColor = if (day == selectedDay) Color.White else BlueInfo
                                                Box(
                                                    modifier = Modifier
                                                        .size(7.dp)
                                                        .border(1.dp, forecastColor, CircleShape)
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.height(7.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(LearnSyncSpacing.small))

            Text(
                text = "Touche un jour pour voir ses révisions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.extraSmall)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CalendarLegendItem(color = EmeraldSuccess, label = "Révisée")
                    CalendarLegendItem(color = RoseError, label = "En retard")
                    CalendarLegendItem(color = AmberFlame, label = "Aujourd'hui")
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CalendarLegendItem(color = IndigoPrimary, label = "Planifiée")
                    CalendarLegendItem(color = BlueInfo, label = "Prévision", outlined = true)
                }
            }
        }
    }
}

@Composable
private fun CalendarLegendItem(
    color: Color,
    label: String,
    outlined: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = if (outlined) {
                Modifier.size(8.dp).border(1.dp, color, CircleShape)
            } else {
                Modifier.size(6.dp).clip(CircleShape).background(color)
            }
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    allFlashcards: List<Flashcard>,
    reviewLogs: List<ReviewLog> = emptyList(),
    reviewSessions: List<ReviewSession> = emptyList(),
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

    // Les dates stockées restent les échéances réelles, y compris les retards.
    // Les dates de prévision sont ajoutées séparément pour ne pas masquer l'état réel.
    val scheduledReviews = remember(allFlashcards) {
        val current = System.currentTimeMillis()
        buildList {
            allFlashcards.forEach { card ->
                add(ScheduledReview(card, card.dueDate, isForecast = false))
                SpacedRepetition.forecastSchedule(
                    card = card,
                    horizonDays = FORECAST_HORIZON_DAYS,
                    currentTime = current
                ).forEach { forecastDate ->
                    add(ScheduledReview(card, forecastDate, isForecast = true))
                }
            }
        }
    }

    val dayBuckets = remember(scheduledReviews) {
        scheduledReviews.groupBy { review -> dayStartOf(review.scheduledAt) }
            .mapValues { (_, reviews) -> reviews.sortedBy { it.scheduledAt } }
    }

    val markerDays = remember(scheduledReviews, reviewLogs, reviewSessions) {
        val todayStart = dayStartOf(System.currentTimeMillis())
        val plannedMarkers = scheduledReviews.groupBy { review -> dayStartOf(review.scheduledAt) }
            .mapValues { (_, reviews) ->
                CalendarDayMarkers(
                    actualCount = reviews.count { !it.isForecast },
                    forecastCount = reviews.count { it.isForecast },
                    overdueCount = reviews.count { !it.isForecast && it.scheduledAt < todayStart }
                )
            }
        val reviewedMarkers = (reviewLogs.map { it.reviewedAt } + reviewSessions
            .filter { it.cardsReviewed > 0 }
            .map { it.startedAt })
            .groupingBy { dayStartOf(it) }
            .eachCount()

        (plannedMarkers.keys + reviewedMarkers.keys).associateWith { day ->
            (plannedMarkers[day] ?: CalendarDayMarkers()).copy(reviewedCount = reviewedMarkers[day] ?: 0)
        }
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
        val reviews = selectedDay?.let { day -> dayBuckets[day] ?: emptyList() } ?: dayBuckets.values.flatten()
        reviews.groupBy { review ->
            dayFormat.format(Date(review.scheduledAt)).replaceFirstChar { c -> c.uppercase() }
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
                    markerDays = markerDays,
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

                    items(cards) { review ->
                        val card = review.card
                        val course = courses.find { it.id == card.courseId }
                        val current = System.currentTimeMillis()
                        val isOverdue = !review.isForecast && review.scheduledAt < dayStartOf(current)
                        val isDueNow = !review.isForecast && review.scheduledAt <= current

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

                                Surface(
                                    shape = LearnSyncShapes.pill,
                                    color = when {
                                        review.isForecast -> BlueSoftBg
                                        isOverdue -> RoseSoftBg
                                        isDueNow -> AmberSoftBg
                                        else -> IndigoSoftBg
                                    }
                                ) {
                                    Text(
                                        text = when {
                                            review.isForecast -> "Prévue"
                                            isOverdue -> "En retard"
                                            isDueNow -> "À réviser"
                                            else -> "Planifiée"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            review.isForecast -> BlueInfo
                                            isOverdue -> RoseDark
                                            isDueNow -> AmberDark
                                            else -> IndigoPrimary
                                        },
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
