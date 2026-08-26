package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Flashcard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    dueCards: List<Flashcard>,
    onReviewCard: (Flashcard, Int, Long) -> Unit,
    onFinishReview: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    var showAnswer by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf(System.currentTimeMillis()) }

    val currentCard = dueCards.getOrNull(currentIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session de Révision", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onFinishReview) {
                        Icon(Icons.Default.Close, contentDescription = "Quitter")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { innerPadding ->
        if (dueCards.isEmpty() || currentCard == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(Icons.Default.Celebration, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Toutes les cartes sont révisées !", fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("Revenez plus tard pour de nouvelles révisions planifiées.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onFinishReview, modifier = Modifier.testTag("finish_review_button")) {
                        Text("Retour à l'accueil")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = { (currentIndex.toFloat() / dueCards.size.toFloat()) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 24.dp)
                        .clickable { showAnswer = !showAnswer },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (!showAnswer) "QUESTION" else "RÉPONSE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (!showAnswer) currentCard.question else currentCard.answer,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold
                        )

                        AnimatedVisibility(visible = showAnswer && currentCard.explanation.isNotBlank()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "Explication : ${currentCard.explanation}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                if (!showAnswer) {
                    Button(
                        onClick = { showAnswer = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("show_answer_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Afficher la réponse", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Comment évaluez-vous votre mémorisation ?", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RatingButton(
                                text = "À revoir",
                                color = MaterialTheme.colorScheme.errorContainer,
                                textColor = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f).testTag("rating_again")
                            ) {
                                val responseTime = System.currentTimeMillis() - startTime
                                onReviewCard(currentCard, 1, responseTime)
                                showAnswer = false
                                currentIndex++
                                startTime = System.currentTimeMillis()
                            }
                            RatingButton(
                                text = "Difficile",
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f).testTag("rating_hard")
                            ) {
                                val responseTime = System.currentTimeMillis() - startTime
                                onReviewCard(currentCard, 2, responseTime)
                                showAnswer = false
                                currentIndex++
                                startTime = System.currentTimeMillis()
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RatingButton(
                                text = "Bien",
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f).testTag("rating_good")
                            ) {
                                val responseTime = System.currentTimeMillis() - startTime
                                onReviewCard(currentCard, 3, responseTime)
                                showAnswer = false
                                currentIndex++
                                startTime = System.currentTimeMillis()
                            }
                            RatingButton(
                                text = "Facile",
                                color = MaterialTheme.colorScheme.primaryContainer,
                                textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f).testTag("rating_easy")
                            ) {
                                val responseTime = System.currentTimeMillis() - startTime
                                onReviewCard(currentCard, 4, responseTime)
                                showAnswer = false
                                currentIndex++
                                startTime = System.currentTimeMillis()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RatingButton(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = textColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
