package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Flashcard
import com.example.domain.model.ReviewLog
import com.example.domain.usecase.SpacedRepetition
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    reviewLogs: List<ReviewLog>,
    allFlashcards: List<Flashcard>
) {
    val streak = SpacedRepetition.calculateStreak(reviewLogs)
    val totalReviews = reviewLogs.size
    val totalCards = allFlashcards.size

    val learningCards = allFlashcards.count { it.box in 1..2 }
    val consolidatingCards = allFlashcards.count { it.box == 3 }
    val masteredCards = allFlashcards.count { it.box >= 4 }

    val goodReviews = reviewLogs.count { it.rating >= 3 }
    val accuracyPercentage = if (totalReviews > 0) ((goodReviews.toFloat() / totalReviews.toFloat()) * 100).toInt() else 0

    // Compute last 7 days activity
    val today = LocalDate.now()
    val last7Days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val reviewsPerDay = last7Days.map { date ->
        val count = reviewLogs.count {
            Instant.ofEpochMilli(it.reviewedAt).atZone(ZoneId.systemDefault()).toLocalDate() == date
        }
        Pair(date, count)
    }
    val maxDayCount = reviewsPerDay.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistiques & Progrès", fontWeight = FontWeight.Bold) },
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
            // Key Indicators
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(modifier = Modifier.weight(1f), title = "Streak actuel", value = "$streak j", icon = Icons.Default.LocalFireDepartment)
                    StatCard(modifier = Modifier.weight(1f), title = "Taux de rétention", value = "$accuracyPercentage%", icon = Icons.AutoMirrored.Filled.TrendingUp)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(modifier = Modifier.weight(1f), title = "Cartes totales", value = "$totalCards", icon = Icons.Default.Style)
                    StatCard(modifier = Modifier.weight(1f), title = "Total révisions", value = "$totalReviews", icon = Icons.Default.CheckCircle)
                }
            }

            // 7-day Activity Chart
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Activité des 7 derniers jours", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                        Row(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            val dayFormatter = DateTimeFormatter.ofPattern("E", Locale.FRENCH)
                            reviewsPerDay.forEach { (date, count) ->
                                val barHeightFactor = (count.toFloat() / maxDayCount.toFloat()).coerceIn(0.08f, 1f)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "$count",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(20.dp)
                                            .fillMaxHeight(barHeightFactor)
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                            .background(if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = date.format(dayFormatter).take(2).uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                                        color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Mastery Distribution
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("Niveau de maîtrise des cartes", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                        MasteryBar(
                            title = "Cartes maîtrisées (Boîte 4-5)",
                            count = masteredCards,
                            total = totalCards,
                            color = MaterialTheme.colorScheme.primary
                        )
                        MasteryBar(
                            title = "En consolidation (Boîte 3)",
                            count = consolidatingCards,
                            total = totalCards,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        MasteryBar(
                            title = "En apprentissage (Boîte 1-2)",
                            count = learningCards,
                            total = totalCards,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MasteryBar(title: String, count: Int, total: Int, color: Color) {
    val fraction = if (total > 0) count.toFloat() / total.toFloat() else 0f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val percentage = (fraction * 100).toInt()
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text("$count ($percentage%)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.15f)
        )
    }
}
