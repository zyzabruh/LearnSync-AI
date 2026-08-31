package com.learnsyncai.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsyncai.domain.model.Flashcard
import com.learnsyncai.domain.usecase.SpacedRepetition
import com.learnsyncai.ui.components.*
import com.learnsyncai.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    dueCards: List<Flashcard>,
    onReviewCard: (Flashcard, Int, Long) -> Unit,
    onFinishReview: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var isAnswerRevealed by remember { mutableStateOf(false) }
    var cardStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var totalGoodOrEasyCount by remember { mutableIntStateOf(0) }
    var totalReviewedCount by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current

    // Synthèse vocale locale (lecture des questions / réponses)
    val ttsContext = LocalContext.current
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(ttsContext) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(ttsContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.FRANCE
            }
        }
        tts.value = engine
        onDispose {
            engine?.stop()
            engine?.shutdown()
        }
    }

    val isSessionComplete = dueCards.isEmpty() || currentIndex >= dueCards.size

    if (isSessionComplete) {
        // Session Completed Celebration Screen
        val sessionDurationSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).coerceAtLeast(1)
        val minutes = sessionDurationSeconds / 60
        val seconds = sessionDurationSeconds % 60
        val durationText = if (minutes > 0) "$minutes min $seconds s" else "$seconds s"
        val successRate = if (totalReviewedCount > 0) {
            ((totalGoodOrEasyCount.toFloat() / totalReviewedCount) * 100).toInt()
        } else 100

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
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(EmeraldSoftBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🎉",
                                fontSize = 40.sp
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.extraSmall)
                        ) {
                            Text(
                                text = "Session terminée !",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Bravo pour ta régularité !",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Stats Summary Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$totalReviewedCount",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = IndigoPrimary
                                )
                                Text(
                                    text = "Cartes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$successRate%",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldSuccess
                                )
                                Text(
                                    text = "Réussite",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = durationText,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberFlame
                                )
                                Text(
                                    text = "Temps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventRepeat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Prochaine session recommandée demain",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(LearnSyncSpacing.small))

                        LearnSyncButton(
                            text = "Retour à l'accueil",
                            onClick = onFinishReview,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        return
    }

    val currentCard = dueCards[currentIndex]

    // Precalculate next intervals for 4 rating options using FSRS algorithm
    val currentTime = System.currentTimeMillis()
    val intervalAgain = remember(currentCard.id) {
        val result = SpacedRepetition.calculateReview(currentCard, SpacedRepetition.RATING_AGAIN, currentTime)
        formatIntervalDays(result.newInterval)
    }
    val intervalHard = remember(currentCard.id) {
        val result = SpacedRepetition.calculateReview(currentCard, SpacedRepetition.RATING_HARD, currentTime)
        formatIntervalDays(result.newInterval)
    }
    val intervalGood = remember(currentCard.id) {
        val result = SpacedRepetition.calculateReview(currentCard, SpacedRepetition.RATING_GOOD, currentTime)
        formatIntervalDays(result.newInterval)
    }
    val intervalEasy = remember(currentCard.id) {
        val result = SpacedRepetition.calculateReview(currentCard, SpacedRepetition.RATING_EASY, currentTime)
        formatIntervalDays(result.newInterval)
    }

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
                            text = "${currentIndex + 1} / ${dueCards.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Surface(
                            shape = LearnSyncShapes.pill,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "FSRS v4",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onFinishReview) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quitter"
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(LearnSyncSpacing.large),
            verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.large)
        ) {
            // Linear Progress Bar
            LinearProgressIndicator(
                progress = { ((currentIndex.toFloat()) / dueCards.size.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = IndigoPrimary,
                trackColor = Slate200
            )

            val rotation by animateFloatAsState(
                targetValue = if (isAnswerRevealed) 180f else 0f,
                animationSpec = tween(400, easing = FastOutSlowInEasing),
                label = "cardFlipRotation"
            )

            // Flashcard container with scrollable content & 3D Flip + Swipe
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 12f * density
                    }
                    .pointerInput(isAnswerRevealed) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount ->
                                totalDrag += dragAmount
                            },
                            onDragEnd = {
                                if (totalDrag > 80f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (!isAnswerRevealed) {
                                        isAnswerRevealed = true
                                    } else {
                                        // Swipe right -> Good rating
                                        val reviewTime = System.currentTimeMillis() - cardStartTime
                                        totalReviewedCount++
                                        totalGoodOrEasyCount++
                                        onReviewCard(currentCard, SpacedRepetition.RATING_GOOD, reviewTime)
                                        isAnswerRevealed = false
                                        cardStartTime = System.currentTimeMillis()
                                        currentIndex++
                                    }
                                } else if (totalDrag < -80f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (!isAnswerRevealed) {
                                        isAnswerRevealed = true
                                    } else {
                                        // Swipe left -> Again rating
                                        val reviewTime = System.currentTimeMillis() - cardStartTime
                                        totalReviewedCount++
                                        onReviewCard(currentCard, SpacedRepetition.RATING_AGAIN, reviewTime)
                                        isAnswerRevealed = false
                                        cardStartTime = System.currentTimeMillis()
                                        currentIndex++
                                    }
                                }
                                totalDrag = 0f
                            }
                        )
                    }
                    .clickable {
                        if (!isAnswerRevealed) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isAnswerRevealed = true
                        }
                    },
                shape = LearnSyncShapes.card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Prevent text mirroring past 90 degrees
                            if (rotation > 90f) {
                                rotationY = 180f
                            }
                        }
                        .padding(LearnSyncSpacing.xxl)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = LearnSyncShapes.pill,
                        color = IndigoSoftBg
                    ) {
                        Text(
                            text = "QUESTION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = IndigoDark,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(LearnSyncSpacing.large))

                    Text(
                        text = currentCard.question,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(
                        onClick = {
                            tts.value?.speak(currentCard.question, TextToSpeech.QUEUE_FLUSH, null, "question")
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Lire la question à voix haute",
                            tint = IndigoPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = isAnswerRevealed,
                        enter = fadeIn(animationSpec = tween(300)) + expandVertically()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = LearnSyncSpacing.xxl)
                        ) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(vertical = LearnSyncSpacing.large)
                            )

                            Surface(
                                shape = LearnSyncShapes.pill,
                                color = EmeraldSoftBg
                            ) {
                                Text(
                                    text = "RÉPONSE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldDark,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(LearnSyncSpacing.medium))

                            Text(
                                text = currentCard.answer,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            IconButton(
                                onClick = {
                                    tts.value?.speak(currentCard.answer, TextToSpeech.QUEUE_FLUSH, null, "answer")
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Lire la réponse à voix haute",
                                    tint = EmeraldDark,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (currentCard.explanation.isNotBlank()) {
                                Spacer(modifier = Modifier.height(LearnSyncSpacing.large))
                                Surface(
                                    shape = LearnSyncShapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(LearnSyncSpacing.medium)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.extraSmall)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = IndigoPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Explication",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = IndigoPrimary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = currentCard.explanation,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Actions: "Afficher la réponse" vs 4 Rating Buttons
            if (!isAnswerRevealed) {
                LearnSyncButton(
                    text = "Afficher la réponse",
                    icon = Icons.Default.Visibility,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isAnswerRevealed = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("show_answer_button")
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
                ) {
                    ReviewRatingButton(
                        title = "Again",
                        intervalText = intervalAgain,
                        color = RatingAgainColor,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val reviewTime = System.currentTimeMillis() - cardStartTime
                            totalReviewedCount++
                            onReviewCard(currentCard, SpacedRepetition.RATING_AGAIN, reviewTime)
                            isAnswerRevealed = false
                            cardStartTime = System.currentTimeMillis()
                            currentIndex++
                        }
                    )

                    ReviewRatingButton(
                        title = "Hard",
                        intervalText = intervalHard,
                        color = RatingHardColor,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val reviewTime = System.currentTimeMillis() - cardStartTime
                            totalReviewedCount++
                            onReviewCard(currentCard, SpacedRepetition.RATING_HARD, reviewTime)
                            isAnswerRevealed = false
                            cardStartTime = System.currentTimeMillis()
                            currentIndex++
                        }
                    )

                    ReviewRatingButton(
                        title = "Good",
                        intervalText = intervalGood,
                        color = RatingGoodColor,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val reviewTime = System.currentTimeMillis() - cardStartTime
                            totalReviewedCount++
                            totalGoodOrEasyCount++
                            onReviewCard(currentCard, SpacedRepetition.RATING_GOOD, reviewTime)
                            isAnswerRevealed = false
                            cardStartTime = System.currentTimeMillis()
                            currentIndex++
                        }
                    )

                    ReviewRatingButton(
                        title = "Easy",
                        intervalText = intervalEasy,
                        color = RatingEasyColor,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val reviewTime = System.currentTimeMillis() - cardStartTime
                            totalReviewedCount++
                            totalGoodOrEasyCount++
                            onReviewCard(currentCard, SpacedRepetition.RATING_EASY, reviewTime)
                            isAnswerRevealed = false
                            cardStartTime = System.currentTimeMillis()
                            currentIndex++
                        }
                    )
                }
            }
        }
    }
}

private fun formatIntervalDays(days: Int): String {
    return when {
        days <= 0 -> "< 1 j"
        days == 1 -> "1 j"
        days < 30 -> "$days j"
        days < 365 -> "${days / 30} m"
        else -> "${days / 365} a"
    }
}
