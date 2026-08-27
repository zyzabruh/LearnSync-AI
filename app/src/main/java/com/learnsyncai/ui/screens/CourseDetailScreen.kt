package com.learnsyncai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsyncai.domain.model.Course
import com.learnsyncai.domain.model.Flashcard
import com.learnsyncai.domain.model.QuizQuestion
import com.learnsyncai.domain.model.StudyMaterial
import com.learnsyncai.ui.components.*
import com.learnsyncai.ui.theme.*

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
    onRegenerate: () -> Unit,
    onDeleteCourse: () -> Unit = {}
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val tabs = listOf("Résumé", "Notions clés", "Flashcards (${flashcards.size})", "QCM (${quizQuestions.size})")

    val dueFlashcardsCount = remember(flashcards) {
        val now = System.currentTimeMillis()
        flashcards.count { it.dueDate <= now }
    }

    val masteryPercent = remember(flashcards) {
        if (flashcards.isEmpty()) 0
        else {
            val mastered = flashcards.count { it.repetitions >= 2 && it.difficulty <= 6.0f }
            ((mastered.toFloat() / flashcards.size) * 100).toInt()
        }
    }

    val latestMaterial = materials.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = course.title,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Régénérer le contenu") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onRegenerate()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Supprimer le cours", color = RoseError) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = RoseError) },
                            onClick = {
                                showMenu = false
                                showDeleteConfirmDialog = true
                            }
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
            // Course Header Metric Card
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(IndigoSoftBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = null,
                                        tint = IndigoPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(LearnSyncSpacing.medium))
                                Column {
                                    Text(
                                        text = "${flashcards.size} flashcards · ${quizQuestions.size} QCM",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (dueFlashcardsCount > 0) "$dueFlashcardsCount cartes à réviser" else "À jour pour le moment",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (dueFlashcardsCount > 0) AmberFlame else EmeraldSuccess
                                    )
                                }
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
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { (masteryPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = EmeraldSuccess,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        // Action Buttons: Réviser, Quiz, Régénérer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
                        ) {
                            LearnSyncButton(
                                text = "Réviser ($dueFlashcardsCount)",
                                icon = Icons.Default.PlayArrow,
                                enabled = dueFlashcardsCount > 0,
                                onClick = onStartReview,
                                modifier = Modifier.weight(1.2f).testTag("course_review_button")
                            )

                            LearnSyncSecondaryButton(
                                text = "Quiz",
                                icon = Icons.Default.Quiz,
                                enabled = quizQuestions.isNotEmpty(),
                                onClick = onStartQuiz,
                                modifier = Modifier.weight(1f).testTag("course_quiz_button")
                            )
                        }
                    }
                }
            }

            // Tabs Selector
            item {
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.background,
                    edgePadding = 0.dp,
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }

            // Tab Content
            when (selectedTabIndex) {
                0 -> { // Résumé
                    if (latestMaterial != null && latestMaterial.summary.isNotBlank()) {
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
                                        Icon(
                                            imageVector = Icons.Default.Article,
                                            contentDescription = null,
                                            tint = IndigoPrimary
                                        )
                                        Text(
                                            text = "Synthèse du cours",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = latestMaterial.summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 22.sp
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            EmptyState(
                                title = "Aucun résumé",
                                description = "Cliquez sur Régénérer pour produire la synthèse pédagogique de ce cours.",
                                icon = Icons.Default.Article,
                                actionLabel = "Générer la synthèse",
                                onActionClick = onRegenerate
                            )
                        }
                    }
                }

                1 -> { // Notions clés
                    val keyPoints = latestMaterial?.keyPoints ?: emptyList()
                    if (keyPoints.isNotEmpty()) {
                        items(keyPoints) { point ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = LearnSyncShapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(LearnSyncSpacing.large),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(IndigoPrimary)
                                            .padding(top = 6.dp)
                                    )
                                    Text(
                                        text = point,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            EmptyState(
                                title = "Aucune notion clé",
                                description = "Les points d'ancrage essentiels seront extraits automatiquement lors de la prochaine génération.",
                                icon = Icons.Default.Lightbulb,
                                actionLabel = "Générer les notions",
                                onActionClick = onRegenerate
                            )
                        }
                    }
                }

                2 -> { // Flashcards List Preview
                    if (flashcards.isNotEmpty()) {
                        items(flashcards) { card ->
                            var expanded by remember { mutableStateOf(false) }
                            val isDue = card.dueDate <= System.currentTimeMillis()

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expanded = !expanded },
                                shape = LearnSyncShapes.medium,
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
                                        Text(
                                            text = "Q: ${card.question}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isDue) {
                                            Surface(
                                                shape = LearnSyncShapes.pill,
                                                color = AmberSoftBg
                                            ) {
                                                Text(
                                                    text = "Due",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AmberDark,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (expanded) {
                                        Divider(
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                        Text(
                                            text = "R: ${card.answer}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = EmeraldDark
                                        )
                                        if (card.explanation.isNotBlank()) {
                                            Text(
                                                text = "Note: ${card.explanation}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            EmptyState(
                                title = "Aucune flashcard",
                                description = "Générez des fiches de révision basées sur le document importé.",
                                icon = Icons.Default.School,
                                actionLabel = "Générer les flashcards",
                                onActionClick = onRegenerate
                            )
                        }
                    }
                }

                3 -> { // QCM List Preview
                    if (quizQuestions.isNotEmpty()) {
                        items(quizQuestions) { qcm ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = LearnSyncShapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(LearnSyncSpacing.large),
                                    verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
                                ) {
                                    Text(
                                        text = qcm.question,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = EmeraldSuccess,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Réponse : ${qcm.correctAnswer}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = EmeraldDark
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            EmptyState(
                                title = "Aucun QCM",
                                description = "Créez des tests d'auto-évaluation automatiques.",
                                icon = Icons.Default.Quiz,
                                actionLabel = "Générer les QCMs",
                                onActionClick = onRegenerate
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Supprimer ce cours ?", fontWeight = FontWeight.Bold) },
            text = { Text("Cette action supprimera définitivement le cours « ${course.title} » ainsi que toutes ses flashcards et QCMs.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteCourse()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoseError,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Annuler")
                }
            },
            shape = LearnSyncShapes.large
        )
    }
}
