package com.learnsyncai.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsyncai.domain.model.QuizQuestion
import com.learnsyncai.ui.components.*
import com.learnsyncai.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    courseTitle: String,
    quizQuestions: List<QuizQuestion>,
    onFinishQuiz: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var selectedOptionIndex by remember { mutableStateOf<Int?>(null) }
    var hasAnswered by remember { mutableStateOf(false) }
    var correctCount by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current

    // Ordre aléatoire des questions, figé une seule fois par session
    val questions = remember(quizQuestions.isNotEmpty()) {
        if (quizQuestions.isNotEmpty()) quizQuestions.shuffled() else quizQuestions
    }

    if (quizQuestions.isEmpty()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(courseTitle) },
                    navigationIcon = {
                        IconButton(onClick = onFinishQuiz) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    title = "Aucun QCM disponible",
                    description = "Générez du matériel pédagogique pour créer automatiquement des QCM sur ce cours.",
                    icon = Icons.Default.Quiz,
                    actionLabel = "Retour",
                    onActionClick = onFinishQuiz
                )
            }
        }
        return
    }

    val isQuizFinished = currentIndex >= questions.size

    if (isQuizFinished) {
        val total = questions.size
        val scorePercent = ((correctCount.toFloat() / total) * 100).toInt()

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(LearnSyncSpacing.xxl),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = LearnSyncShapes.card,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(LearnSyncSpacing.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.large)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(
                                    if (scorePercent >= 70) EmeraldSoftBg else AmberSoftBg
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (scorePercent >= 70) "🏆" else "📚",
                                fontSize = 44.sp
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Quiz terminé !",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = courseTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$correctCount / $total",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Bonnes réponses",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$scorePercent%",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (scorePercent >= 70) EmeraldSuccess else AmberFlame
                                )
                                Text(
                                    text = "Score global",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        LearnSyncButton(
                            text = "Terminer",
                            onClick = onFinishQuiz,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        return
    }

    val currentQuestion = questions[currentIndex]
    // Ordre aléatoire des options (la correction compare le texte, pas l'index)
    val options = remember(currentQuestion.id) { currentQuestion.options.shuffled() }
    val optionLabels = listOf("A", "B", "C", "D")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Question ${currentIndex + 1} / ${questions.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = LearnSyncShapes.pill,
                            color = EmeraldSoftBg
                        ) {
                            Text(
                                text = "Score : $correctCount",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldDark,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onFinishQuiz) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quitter")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(LearnSyncSpacing.large)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.large)
        ) {
            // Progress Bar
            LinearProgressIndicator(
                progress = { ((currentIndex.toFloat()) / questions.size.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = IndigoPrimary,
                trackColor = Slate200
            )

            // Question Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = LearnSyncShapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(LearnSyncSpacing.large)) {
                    Text(
                        text = currentQuestion.question,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Options
            Column(verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium)) {
                options.forEachIndexed { index, optionText ->
                    val isSelected = selectedOptionIndex == index
                    val isCorrectOption = optionText.trim().equals(currentQuestion.correctAnswer.trim(), ignoreCase = true)

                    val cardBorderColor = when {
                        !hasAnswered && isSelected -> IndigoPrimary
                        hasAnswered && isCorrectOption -> EmeraldSuccess
                        hasAnswered && isSelected && !isCorrectOption -> RoseError
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }

                    val cardBgColor = when {
                        !hasAnswered && isSelected -> IndigoSoftBg
                        hasAnswered && isCorrectOption -> EmeraldSoftBg
                        hasAnswered && isSelected && !isCorrectOption -> RoseSoftBg
                        else -> MaterialTheme.colorScheme.surface
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                            .clickable(enabled = !hasAnswered) {
                                selectedOptionIndex = index
                            }
                            .testTag("quiz_option_$index"),
                        shape = LearnSyncShapes.medium,
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        border = BorderStroke(
                            width = if (isSelected || (hasAnswered && isCorrectOption)) 2.dp else 1.dp,
                            color = cardBorderColor
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            hasAnswered && isCorrectOption -> EmeraldSuccess
                                            hasAnswered && isSelected && !isCorrectOption -> RoseError
                                            isSelected -> IndigoPrimary
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = optionLabels.getOrElse(index) { "${index + 1}" },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected || (hasAnswered && isCorrectOption)) Color.White
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(LearnSyncSpacing.medium))

                            Text(
                                text = optionText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )

                            if (hasAnswered) {
                                if (isCorrectOption) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Correct",
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = "Incorrect",
                                        tint = RoseError,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Explanation & Next Button
            if (hasAnswered) {
                if (currentQuestion.explanation.isNotBlank()) {
                    Surface(
                        shape = LearnSyncShapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(LearnSyncSpacing.large)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.extraSmall)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = IndigoPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Explication",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = IndigoPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(LearnSyncSpacing.extraSmall))
                            Text(
                                text = currentQuestion.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                LearnSyncButton(
                    text = if (currentIndex + 1 < questions.size) "Question suivante" else "Voir les résultats",
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    onClick = {
                        currentIndex++
                        selectedOptionIndex = null
                        hasAnswered = false
                    },
                    modifier = Modifier.fillMaxWidth().testTag("next_question_button")
                )
            } else {
                LearnSyncButton(
                    text = "Valider la réponse",
                    enabled = selectedOptionIndex != null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        hasAnswered = true
                        val chosen = selectedOptionIndex?.let { options.getOrNull(it) }
                        if (chosen != null && chosen.trim().equals(currentQuestion.correctAnswer.trim(), ignoreCase = true)) {
                            correctCount++
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("validate_answer_button")
                )
            }
        }
    }
}
