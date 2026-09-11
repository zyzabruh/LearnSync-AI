package com.learnsyncai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.learnsyncai.domain.model.QuizQuestion
import com.learnsyncai.ui.components.*
import com.learnsyncai.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Examen blanc : QCM mélangés en temps limité, sans feedback immédiat,
 * note sur 20 + analyse des erreurs à la fin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseExamScreen(
    courseTitle: String,
    quizQuestions: List<QuizQuestion>,
    onFinishExam: (score20: Int, total: Int) -> Unit,
    onQuit: () -> Unit
) {
    val questions = remember(quizQuestions) { quizQuestions.shuffled().take(20) }
    val totalSeconds = remember(questions) { (questions.size * 60).coerceIn(60, 45 * 60) }
    var remaining by remember(questions) { mutableIntStateOf(totalSeconds) }
    var answers by remember { mutableStateOf(mapOf<String, String>()) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(questions) {
        while (remaining > 0 && !finished) {
            delay(1000)
            remaining--
        }
        if (remaining <= 0) finished = true
    }

    val correct = remember(answers, finished) {
        if (!finished) 0 else questions.count { answers[it.id] == it.correctAnswer }
    }
    val score20 = remember(correct, questions) {
        if (questions.isEmpty()) 0 else ((correct.toFloat() / questions.size) * 20).toInt()
    }
    LaunchedEffect(finished) {
        if (finished) onFinishExam(score20, questions.size)
    }

    fun mmss(s: Int): String = "%02d:%02d".format(s / 60, s % 60)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Examen blanc · $courseTitle", maxLines = 1, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onQuit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quitter")
                    }
                },
                actions = {
                    Surface(
                        shape = LearnSyncShapes.pill,
                        color = if (remaining < 60) MaterialTheme.colorScheme.errorContainer else IndigoSoftBg
                    ) {
                        Text(
                            text = "⏱ ${mmss(remaining)}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (remaining < 60) MaterialTheme.colorScheme.onErrorContainer else IndigoPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (questions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Pas de QCM pour simuler un examen : génère du contenu d'abord.")
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = LearnSyncSpacing.large),
            verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium),
            contentPadding = PaddingValues(bottom = LearnSyncSpacing.xxl)
        ) {
            if (!finished) {
                questions.forEachIndexed { idx, q ->
                    item(key = "q_${q.id}") {
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
                                Text("Question ${idx + 1}/${questions.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(q.question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                q.options.forEach { option ->
                                    val selected = answers[q.id] == option
                                    FilterChip(
                                        selected = selected,
                                        onClick = { answers = answers + (q.id to option) },
                                        label = { Text(option) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    LearnSyncButton(
                        text = "Rendre ma copie (${answers.size}/${questions.size})",
                        icon = Icons.Default.Check,
                        onClick = { finished = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = LearnSyncShapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(LearnSyncSpacing.xxl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
                        ) {
                            Text("$score20 / 20", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = IndigoPrimary)
                            Text(
                                text = when {
                                    score20 >= 16 -> "Excellent, niveau examen !"
                                    score20 >= 12 -> "Bien, encore quelques révisions."
                                    score20 >= 10 -> "Passable : cible tes erreurs ci-dessous."
                                    else -> "À retravailler : revois le cours puis retente."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("$correct/${questions.size} bonnes réponses", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                val errors = questions.filter { answers[it.id] != it.correctAnswer }
                if (errors.isNotEmpty()) {
                    item {
                        Text("À corriger (${errors.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    errors.forEach { q ->
                        item(key = "err_${q.id}") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = LearnSyncShapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(LearnSyncSpacing.medium)) {
                                    Text(q.question, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text("Ta réponse : ${answers[q.id] ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    Text("Bonne réponse : ${q.correctAnswer}", style = MaterialTheme.typography.bodySmall, color = EmeraldDark)
                                    if (q.explanation.isNotBlank()) {
                                        Text(q.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    LearnSyncButton(text = "Terminer", onClick = onQuit, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
