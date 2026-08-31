package com.learnsyncai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    courses: List<Course>,
    dueCards: List<Flashcard>,
    allFlashcards: List<Flashcard>,
    reviewLogs: List<ReviewLog>,
    onNavigateToReview: () -> Unit,
    onNavigateToCourses: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onSelectCourse: (Course) -> Unit,
    onSyncCalendar: () -> Unit
) {
    val streak = remember(reviewLogs) { SpacedRepetition.calculateStreak(reviewLogs) }
    val today = LocalDate.now()
    val todayReviewsCount = remember(reviewLogs) {
        reviewLogs.count {
            Instant.ofEpochMilli(it.reviewedAt).atZone(ZoneId.systemDefault()).toLocalDate() == today
        }
    }

    val greetingText = remember {
        val hour = LocalTime.now().hour
        when {
            hour in 5..11 -> "Bonjour 👋"
            hour in 12..17 -> "Bon après-midi ☀️"
            else -> "Bonsoir 🌙"
        }
    }

    val masteredCardsCount = remember(allFlashcards) {
        allFlashcards.count { it.repetitions >= 3 && it.difficulty <= 5.0f }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = greetingText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Prêt pour tes révisions intelligentes ?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToCalendar,
                        modifier = Modifier.testTag("home_calendar_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Calendrier",
                            tint = MaterialTheme.colorScheme.primary
                        )
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
            // HERO CARD: What should I do today?
            item {
                HeroProgressCard(
                    dueCardsCount = dueCards.size,
                    streak = streak,
                    todayReviewsCount = todayReviewsCount,
                    onStartSession = onNavigateToReview
                )
            }

            // SECTION 1: Ta progression
            item {
                Column(verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)) {
                    SectionHeader(
                        title = "Ta progression",
                        actionLabel = "Détails",
                        onActionClick = onNavigateToStats
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium)
                    ) {
                        StatCard(
                            title = "Révisées ajd",
                            value = "$todayReviewsCount",
                            icon = Icons.Default.DoneAll,
                            iconColor = EmeraldDark,
                            iconBgColor = EmeraldSoftBg,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "Rétention",
                            value = if (allFlashcards.isNotEmpty()) "$averageRetention%" else "—",
                            icon = Icons.Default.Psychology,
                            iconColor = IndigoPrimary,
                            iconBgColor = IndigoSoftBg,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "Maîtrisées",
                            value = "$masteredCardsCount",
                            icon = Icons.Default.Star,
                            iconColor = AmberDark,
                            iconBgColor = AmberSoftBg,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // SECTION 2: Cours récents
            item {
                SectionHeader(
                    title = "Tes cours",
                    actionLabel = if (courses.isNotEmpty()) "Voir tout" else null,
                    onActionClick = onNavigateToCourses
                )
            }

            if (courses.isEmpty()) {
                item {
                    EmptyState(
                        title = "Aucun cours pour le moment",
                        description = "Importe ton premier cours en PDF ou texte et LearnSync générera instantanément tes flashcards et QCMs.",
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        actionLabel = "Importer un cours",
                        onActionClick = onNavigateToCourses
                    )
                }
            } else {
                items(courses.take(4)) { course ->
                    val courseCards = allFlashcards.filter { it.courseId == course.id }
                    val courseDueCards = dueCards.filter { it.courseId == course.id }
                    val masteryPercent = if (courseCards.isEmpty()) 0 else {
                        val mastered = courseCards.count { it.repetitions >= 2 && it.difficulty <= 6.0f }
                        ((mastered.toFloat() / courseCards.size) * 100).toInt()
                    }

                    CourseCard(
                        course = course,
                        totalCardsCount = courseCards.size,
                        dueCardsCount = courseDueCards.size,
                        progressPercentage = masteryPercent,
                        onSelectCourse = onSelectCourse,
                        onReviewClick = if (courseDueCards.isNotEmpty()) onNavigateToReview else null
                    )
                }
            }
        }
    }
}
