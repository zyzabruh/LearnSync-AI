package com.learnsyncai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.learnsyncai.ui.components.*
import com.learnsyncai.ui.theme.*

/** Une étape du parcours guidé d'un cours. */
data class LearnStep(
    val index: Int,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val progress: Float,
    val actionLabel: String,
    val actionEnabled: Boolean,
    val onAction: () -> Unit
)

/**
 * Parcours guidé (Learn Mode) : comprendre → mémoriser → réviser →
 * tester → corriger, avec la prochaine action recommandée.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseLearnScreen(
    courseTitle: String,
    hasSummary: Boolean,
    flashcardsCount: Int,
    dueCount: Int,
    quizCount: Int,
    leechCount: Int,
    onBackClick: () -> Unit,
    onSeeSummary: () -> Unit,
    onGenerate: () -> Unit,
    onGoReview: () -> Unit,
    onGoQuiz: () -> Unit,
    onStartGap: () -> Unit,
    onGoTutor: () -> Unit
) {
    val steps = remember(hasSummary, flashcardsCount, dueCount, quizCount, leechCount) {
        listOf(
            LearnStep(
                index = 1,
                title = "Comprendre",
                description = if (hasSummary) "Résumé disponible." else "Pas encore de résumé.",
                icon = Icons.AutoMirrored.Filled.Article,
                progress = if (hasSummary) 1f else 0f,
                actionLabel = if (hasSummary) "Voir le résumé" else "Générer",
                actionEnabled = true,
                onAction = if (hasSummary) onSeeSummary else onGenerate
            ),
            LearnStep(
                index = 2,
                title = "Mémoriser",
                description = "$flashcardsCount flashcards créées.",
                icon = Icons.Default.CreditCard,
                progress = if (flashcardsCount > 0) 1f else 0f,
                actionLabel = "Générer du contenu",
                actionEnabled = true,
                onAction = onGenerate
            ),
            LearnStep(
                index = 3,
                title = "Réviser",
                description = if (dueCount == 0) "À jour !" else "$dueCount cartes dues.",
                icon = Icons.Default.School,
                progress = if (flashcardsCount == 0) 0f else ((flashcardsCount - dueCount).toFloat() / flashcardsCount).coerceIn(0f, 1f),
                actionLabel = "Réviser",
                actionEnabled = dueCount > 0,
                onAction = onGoReview
            ),
            LearnStep(
                index = 4,
                title = "Tester",
                description = "$quizCount QCM disponibles.",
                icon = Icons.Default.Quiz,
                progress = if (quizCount > 0) 1f else 0f,
                actionLabel = "Lancer le quiz",
                actionEnabled = quizCount > 0,
                onAction = onGoQuiz
            ),
            LearnStep(
                index = 5,
                title = "Corriger",
                description = if (leechCount == 0) "Aucune carte en difficulté." else "$leechCount cartes en difficulté.",
                icon = Icons.Default.Warning,
                progress = if (leechCount == 0) 1f else 0f,
                actionLabel = "Session Lacunes",
                actionEnabled = leechCount > 0,
                onAction = onStartGap
            )
        )
    }
    val overall = remember(steps) { steps.map { it.progress }.average().toFloat() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parcours · $courseTitle", maxLines = 1, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = LearnSyncSpacing.large),
            verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium),
            contentPadding = PaddingValues(bottom = LearnSyncSpacing.xxl)
        ) {
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Progression", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("${(overall * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = IndigoPrimary)
                        }
                        LinearProgressIndicator(
                            progress = { overall },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(androidx.compose.foundation.shape.CircleShape),
                            color = IndigoPrimary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        steps.firstOrNull { it.progress < 1f && it.actionEnabled }?.let { next ->
                            Text(
                                "Prochaine étape : ${next.title} — ${next.actionLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            steps.forEach { step ->
                item(key = "step_${step.index}") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = LearnSyncShapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(LearnSyncSpacing.large),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium)
                        ) {
                            Surface(
                                shape = LearnSyncShapes.medium,
                                color = if (step.progress >= 1f) EmeraldSoftBg else IndigoSoftBg
                            ) {
                                Icon(
                                    imageVector = if (step.progress >= 1f) Icons.Default.CheckCircle else step.icon,
                                    contentDescription = null,
                                    tint = if (step.progress >= 1f) EmeraldDark else IndigoPrimary,
                                    modifier = Modifier.padding(10.dp).size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Étape ${step.index} · ${step.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(step.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                FilledTonalButton(
                                    onClick = step.onAction,
                                    enabled = step.actionEnabled,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(step.actionLabel, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onGoTutor, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Bloqué ? Demander au tuteur IA")
                }
            }
        }
    }
}
