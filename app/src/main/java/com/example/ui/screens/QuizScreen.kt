package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.QuizQuestion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    courseTitle: String,
    quizQuestions: List<QuizQuestion>,
    onFinishQuiz: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    var selectedOptionIndex by remember { mutableStateOf<Int?>(null) }
    var isSubmitted by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(0) }
    var isQuizCompleted by remember { mutableStateOf(false) }

    val currentQuestion = quizQuestions.getOrNull(currentIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QCM : $courseTitle", fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onFinishQuiz) {
                        Icon(Icons.Default.Close, contentDescription = "Quitter")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { innerPadding ->
        if (quizQuestions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(Icons.Default.Quiz, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Aucun QCM disponible", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Générez le matériel du cours avec l'IA pour créer des QCMs interactifs.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onFinishQuiz) {
                        Text("Retour")
                    }
                }
            }
        } else if (isQuizCompleted || currentQuestion == null) {
            // Quiz results screen
            val percentage = ((score.toFloat() / quizQuestions.size.toFloat()) * 100).toInt()
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            if (percentage >= 70) Icons.Default.EmojiEvents else Icons.Default.ThumbUp,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = if (percentage >= 70) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                        Text("Quiz terminé !", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Votre score : $score / ${quizQuestions.size} ($percentage%)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        
                        Text(
                            text = when {
                                percentage == 100 -> "Parfait ! Vous maîtrisez parfaitement ce cours."
                                percentage >= 75 -> "Très bon score ! Quelques notions à consolider."
                                percentage >= 50 -> "Bon début, n'hésitez pas à relire les flashcards."
                                else -> "Prenez le temps de revoir le cours et réessayez !"
                            },
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    currentIndex = 0
                                    selectedOptionIndex = null
                                    isSubmitted = false
                                    score = 0
                                    isQuizCompleted = false
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Recommencer")
                            }
                            Button(
                                onClick = onFinishQuiz,
                                modifier = Modifier.weight(1f).testTag("finish_quiz_button")
                            ) {
                                Text("Terminer")
                            }
                        }
                    }
                }
            }
        } else {
            // Question answering flow
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Question ${currentIndex + 1} / ${quizQuestions.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Score : $score",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    LinearProgressIndicator(
                        progress = { ((currentIndex + 1).toFloat() / quizQuestions.size.toFloat()) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = currentQuestion.question,
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(currentQuestion.options) { index, option ->
                            val isSelected = selectedOptionIndex == index
                            val isCorrect = option.trim() == currentQuestion.correctAnswer.trim()
                            
                            val backgroundColor = when {
                                !isSubmitted && isSelected -> MaterialTheme.colorScheme.primaryContainer
                                isSubmitted && isCorrect -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                isSubmitted && isSelected && !isCorrect -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surface
                            }

                            val borderColor = when {
                                !isSubmitted && isSelected -> MaterialTheme.colorScheme.primary
                                isSubmitted && isCorrect -> MaterialTheme.colorScheme.primary
                                isSubmitted && isSelected && !isCorrect -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
                                    .clickable(enabled = !isSubmitted) {
                                        selectedOptionIndex = index
                                    },
                                color = backgroundColor
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        when {
                                            isSubmitted && isCorrect -> Icons.Default.CheckCircle
                                            isSubmitted && isSelected && !isCorrect -> Icons.Default.Cancel
                                            isSelected -> Icons.Default.RadioButtonChecked
                                            else -> Icons.Default.RadioButtonUnchecked
                                        },
                                        contentDescription = null,
                                        tint = when {
                                            isSubmitted && isCorrect -> MaterialTheme.colorScheme.primary
                                            isSubmitted && isSelected && !isCorrect -> MaterialTheme.colorScheme.error
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected || (isSubmitted && isCorrect)) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = isSubmitted && currentQuestion.explanation.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text("Explication :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text(currentQuestion.explanation, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                // Action buttons
                if (!isSubmitted) {
                    Button(
                        onClick = {
                            if (selectedOptionIndex != null) {
                                isSubmitted = true
                                val chosenOption = currentQuestion.options[selectedOptionIndex!!]
                                if (chosenOption.trim() == currentQuestion.correctAnswer.trim()) {
                                    score++
                                }
                            }
                        },
                        enabled = selectedOptionIndex != null,
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("submit_answer_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Valider la réponse", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            if (currentIndex + 1 < quizQuestions.size) {
                                currentIndex++
                                selectedOptionIndex = null
                                isSubmitted = false
                            } else {
                                isQuizCompleted = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("next_question_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (currentIndex + 1 < quizQuestions.size) "Question suivante" else "Voir mes résultats",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
