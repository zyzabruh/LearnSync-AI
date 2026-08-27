package com.learnsyncai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsyncai.domain.model.Course
import com.learnsyncai.domain.model.Flashcard
import com.learnsyncai.domain.model.ReviewLog
import com.learnsyncai.domain.usecase.SpacedRepetition
import com.learnsyncai.ui.components.*
import com.learnsyncai.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    reviewLogs: List<ReviewLog>,
    allFlashcards: List<Flashcard>,
    courses: List<Course> = emptyList()
) {
    val totalReviews = reviewLogs.size
    val streak = remember(reviewLogs) { SpacedRepetition.calculateStreak(reviewLogs) }

    val successRate = remember(reviewLogs) {
        if (reviewLogs.isEmpty()) 0
        else {
            val goodOrEasy = reviewLogs.count { it.rating >= SpacedRepetition.RATING_GOOD }
            ((goodOrEasy.toFloat() / reviewLogs.size) * 100).toInt()
        }
    }

    val totalStudyMinutes = remember(reviewLogs) {
        val totalMs = reviewLogs.sumOf { it.responseTime }
        (totalMs / 60000).toInt()
    }

    val averageRetention = remember(allFlashcards) {
        if (allFlashcards.isEmpty()) 0
        else {
            val totalR = allFlashcards.sumOf { card ->
                val elapsedDays = card.lastReviewedAt?.let {
                    ((System.currentTimeMillis() - it) / (1000f * 3600 * 24)).coerceAtLeast(0f)
                } ?: 0f
                SpacedRepetition.calculateRetrievability(elapsedDays, card.easeFactor).toDouble()
            }
            ((totalR / allFlashcards.size) * 100).toInt()
        }
    }

    // 7-day Activity aggregation
    val last7DaysData = remember(reviewLogs) {
        val today = LocalDate.now()
        (6 downTo 0).map { daysAgo ->
            val targetDate = today.minusDays(daysAgo.toLong())
            val count = reviewLogs.count { log ->
                Instant.ofEpochMilli(log.reviewedAt).atZone(ZoneId.systemDefault()).toLocalDate() == targetDate
            }
            val dayLabel = targetDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRANCE).take(3).uppercase()
            dayLabel to count
        }
    }

    val maxDayCount = remember(last7DaysData) {
        (last7DaysData.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Statistiques & Progrès",
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
            // Top Grid of Key Metrics
            item {
                Column(verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium)
                    ) {
                        StatCard(
                            title = "Cartes révisées",
                            value = "$totalReviews",
                            icon = Icons.Default.CheckCircle,
                            iconColor = IndigoPrimary,
                            iconBgColor = IndigoSoftBg,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "Précision",
                            value = if (totalReviews > 0) "$successRate%" else "—",
                            icon = Icons.Default.TrendingUp,
                            iconColor = EmeraldDark,
                            iconBgColor = EmeraldSoftBg,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium)
                    ) {
                        StatCard(
                            title = "Série actuelle",
                            value = "$streak j",
                            subtitle = "Jours consécutifs",
                            icon = Icons.Default.LocalFireDepartment,
                            iconColor = AmberDark,
                            iconBgColor = AmberSoftBg,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "Temps d'étude",
                            value = "${totalStudyMinutes}m",
                            subtitle = "Total accumulé",
                            icon = Icons.Default.Timer,
                            iconColor = IndigoLight,
                            iconBgColor = IndigoSoftBg,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 7-day Activity Chart Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = LearnSyncShapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(LearnSyncSpacing.large),
                        verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.large)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Activité des 7 derniers jours",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Nombre de révisions quotidiennes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = LearnSyncShapes.pill,
                                color = IndigoSoftBg
                            ) {
                                Text(
                                    text = "${last7DaysData.sumOf { it.second }} cette semaine",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = IndigoPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Bar Chart Visualization
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            last7DaysData.forEach { (day, count) ->
                                val barHeightRatio = (count.toFloat() / maxDayCount.toFloat()).coerceIn(0.08f, 1f)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                ) {
                                    if (count > 0) {
                                        Text(
                                            text = "$count",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = IndigoPrimary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(28.dp)
                                            .fillMaxHeight(barHeightRatio)
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                            .background(
                                                if (count > 0) IndigoPrimary else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = day,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Retention & FSRS Algorithm Overview
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldSoftBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = EmeraldDark,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Rétention globale estimée",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Modèle FSRS v4 (Free Spaced Repetition Scheduler)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { (averageRetention / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = EmeraldSuccess,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Text(
                            text = "Score moyen : $averageRetention% de probabilité de rappel sur l'ensemble de votre deck.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Per Course Statistics
            if (courses.isNotEmpty()) {
                item {
                    SectionHeader(title = "Statistiques par cours")
                }

                items(courses) { course ->
                    val courseCards = allFlashcards.filter { it.courseId == course.id }
                    val courseLogs = reviewLogs.filter { log -> courseCards.any { it.id == log.flashcardId } }
                    val masteryPercent = if (courseCards.isEmpty()) 0 else {
                        val mastered = courseCards.count { it.repetitions >= 2 && it.difficulty <= 6.0f }
                        ((mastered.toFloat() / courseCards.size) * 100).toInt()
                    }

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
                                Text(
                                    text = course.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${courseCards.size} cartes · ${courseLogs.size} révisions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                shape = LearnSyncShapes.pill,
                                color = IndigoSoftBg
                            ) {
                                Text(
                                    text = "$masteryPercent% maîtrise",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = IndigoPrimary,
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
