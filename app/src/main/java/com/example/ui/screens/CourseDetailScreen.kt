package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Course
import com.example.domain.model.Flashcard
import com.example.domain.model.QuizQuestion
import com.example.domain.model.StudyMaterial

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    course: Course,
    materials: List<StudyMaterial>,
    flashcards: List<Flashcard>,
    quizQuestions: List<QuizQuestion>,
    onBackClick: () -> Unit,
    onStartReview: () -> Unit,
    onStartQuiz: () -> Unit,
    onRegenerate: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Résumé", "Points clés", "Flashcards (${flashcards.size})", "QCM (${quizQuestions.size})")

    val latestMaterial = materials.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(course.title, fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = onRegenerate) {
                        Icon(Icons.Default.Refresh, contentDescription = "Régénérer")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (flashcards.isNotEmpty()) {
                        Button(
                            onClick = onStartReview,
                            modifier = Modifier.weight(1f).testTag("course_review_button")
                        ) {
                            Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Réviser")
                        }
                    }
                    if (quizQuestions.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onStartQuiz,
                            modifier = Modifier.weight(1f).testTag("course_quiz_button")
                        ) {
                            Icon(Icons.Default.Quiz, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tester en QCM")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 13.sp, maxLines = 1) }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    // Résumé
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Text("Fiche de synthèse IA", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    }
                                    Text(
                                        text = latestMaterial?.summary ?: "Aucun résumé disponible. Cliquez sur Régénérer pour analyser ce cours.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        lineHeight = 24.sp
                                    )
                                }
                            }
                        }

                        if (latestMaterial != null && latestMaterial.mnemonicTips.isNotEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                            Text("Astuces mnémotechniques", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                        }
                                        latestMaterial.mnemonicTips.forEach { tip ->
                                            Text("• $tip", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // Points clés
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (latestMaterial == null || latestMaterial.keyPoints.isEmpty()) {
                            item {
                                Text("Aucun point clé généré.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            items(latestMaterial.keyPoints) { point ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Text(point, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // Flashcards
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (flashcards.isEmpty()) {
                            item {
                                Text("Aucune flashcard pour ce cours.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            items(flashcards) { card ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("Q : ${card.question}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                        Text("R : ${card.answer}", style = MaterialTheme.typography.bodyMedium)
                                        if (card.explanation.isNotBlank()) {
                                            Text("💡 ${card.explanation}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // QCM
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (quizQuestions.isEmpty()) {
                            item {
                                Text("Aucun QCM pour ce cours.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            items(quizQuestions) { question ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(question.question, fontWeight = FontWeight.Bold)
                                        question.options.forEach { opt ->
                                            val isCorrect = opt.trim() == question.correctAnswer.trim()
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(
                                                    if (isCorrect) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(opt, style = MaterialTheme.typography.bodySmall, fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal)
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
    }
}
