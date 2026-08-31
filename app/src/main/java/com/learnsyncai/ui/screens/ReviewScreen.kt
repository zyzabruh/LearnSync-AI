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
import androidx.compose.material.icons.automirrored.filled.*
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
    aheadCount: Int,
    reviewQueue: List<Flashcard>?,
    autoTtsEnabled: Boolean,
    onReviewCard: (Flashcard, Int, Long) -> Unit,
    onStartSession: (Int?) -> Unit,
    onStartAheadSession: () -> Unit,
    onEndSession: () -> Unit,
    onFinishReview: () -> Unit
) {
    var sessionTotal by remember { mutableIntStateOf(0) }
    var totalReviewedCount by remember { mutableIntStateOf(0) }
    var againCount by remember { mutableIntStateOf(0) }
    var hardCount by remember { mutableIntStateOf(0) }
    var goodCount by remember { mutableIntStateOf(0) }
    var easyCount by remember { mutableIntStateOf(0) }
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val resetStats = {
        sessionTotal = 0
        totalReviewedCount = 0
        againCount = 0
        hardCount = 0
        goodCount = 0
        easyCount = 0
        sessionStartTime = System.currentTimeMillis()
    }

    when {
        // Aucune session active : écran de choix (tout réviser / cible / à l'avance)
        reviewQueue == null -> {
            ReviewSessionStartScreen(
                dueCount = dueCards.size,
                aheadCount = aheadCount,
                onStart = { limit ->
                    resetStats()
                    onStartSession(limit)
                },
                onStartAhead = {
                    resetStats()
                    onStartAheadSession()
                },
                onFinishReview = onFinishReview
            )
        }

        // Session terminée : bilan avec répartition des notes
        reviewQueue.isEmpty() -> {
            ReviewSessionCompleteScreen(
                totalReviewed = totalReviewedCount,
                againCount = againCount,
                hardCount = hardCount,
                goodCount = goodCount,
                easyCount = easyCount,
                sessionStartTime = sessionStartTime,
                onFinish = {
                    onEndSession()
                    onFinishReview()
                }
            )
        }

        // Session en cours (ou en pause, reprise telle quelle)
        else -> {
            ReviewSessionScreen(
                queue = reviewQueue,
                sessionTotal = sessionTotal,
                autoTtsEnabled = autoTtsEnabled,
                onRate = { card, rating, reviewTime ->
                    totalReviewedCount++
                    when (rating) {
                        SpacedRepetition.RATING_AGAIN -> againCount++
                        SpacedRepetition.RATING_HARD -> hardCount++
                        SpacedRepetition.RATING_GOOD -> goodCount++
                        else -> easyCount++
                    }
                    onReviewCard(card, rating, reviewTime)
                },
                onNewSession = onEndSession,
                onFinishReview = onFinishReview,
                onSessionSizeInitialized = { size -> if (sessionTotal == 0) sessionTotal = size }
            )
        }
    }
}

/** Écran de départ : session complète, cible de 20 cartes, ou révision à l'avance. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewSessionStartScreen(
    dueCount: Int,
    aheadCount: Int,
    onStart: (Int?) -> Unit,
    onStartAhead: () -> Unit,
    onFinishReview: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réviser", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onFinishReview) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(LearnSyncSpacing.xxl),
            contentAlignment = Alignment.Center
        ) {
            if (dueCount == 0 && aheadCount == 0) {
                EmptyState(
                    title = "Aucune carte à réviser",
                    description = "Génère du contenu pour tes cours pour créer des flashcards, puis reviens ici.",
                    icon = Icons.Default.CheckCircle,
                    actionLabel = "Retour à l'accueil",
                    onActionClick = onFinishReview
                )
            } else {
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
                                .background(IndigoSoftBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (dueCount == 0) "Rien à réviser aujourd'hui" else "$dueCount carte${if (dueCount > 1) "s" else ""} à réviser",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (dueCount == 0)
                                    "Aucune carte n'est due pour le moment. Tu peux quand même réviser à l'avance."
                                else
                                    "Révision complète de tous tes cours, dans un ordre aléatoire.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }

                        if (dueCount > 0) {
                            LearnSyncButton(
                                text = "Tout réviser ($dueCount)",
                                icon = Icons.Default.PlayArrow,
                                onClick = { onStart(null) },
                                modifier = Modifier.fillMaxWidth().testTag("start_session_button")
                            )

                            if (dueCount > SESSION_TARGET_SIZE) {
                                LearnSyncSecondaryButton(
                                    text = "Réviser $SESSION_TARGET_SIZE cartes",
                                    onClick = { onStart(SESSION_TARGET_SIZE) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Révision anticipée : possible même quand rien n'est dû
                        if (aheadCount > 0) {
                            if (dueCount == 0) {
                                LearnSyncButton(
                                    text = "Réviser à l'avance ($aheadCount carte${if (aheadCount > 1) "s" else ""})",
                                    icon = Icons.Default.FastForward,
                                    onClick = onStartAhead,
                                    modifier = Modifier.fillMaxWidth().testTag("start_ahead_button")
                                )
                            } else {
                                LearnSyncSecondaryButton(
                                    text = "Réviser à l'avance (+$aheadCount à venir)",
                                    onClick = onStartAhead,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Cible par défaut d'une session partielle. */
const val SESSION_TARGET_SIZE = 20

/** Session en cours : file fournie par le ViewModel (mélangée, requeue "Again" en fin). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewSessionScreen(
    queue: List<Flashcard>,
    sessionTotal: Int,
    autoTtsEnabled: Boolean,
    onRate: (Flashcard, Int, Long) -> Unit,
    onNewSession: () -> Unit,
    onFinishReview: () -> Unit,
    onSessionSizeInitialized: (Int) -> Unit
) {
    // Taille totale de la session mémorisée une seule fois (résiste au requeue des "Again")
    if (queue.isNotEmpty()) {
        LaunchedEffect(Unit) { onSessionSizeInitialized(queue.size) }
    }

    val currentCard = queue.firstOrNull() ?: return
    var isAnswerRevealed by remember { mutableStateOf(false) }
    var cardStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val haptic = LocalHapticFeedback.current

    // Anti double-tap : ignore une seconde note sur la même carte
    var lastRatedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentCard?.id) { lastRatedId = null }

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

    fun rate(rating: Int) {
        val card = currentCard
        if (card.id == lastRatedId) return
        lastRatedId = card.id
        val reviewTime = System.currentTimeMillis() - cardStartTime
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        isAnswerRevealed = false
        cardStartTime = System.currentTimeMillis()
        onRate(card, rating, reviewTime)
    }

    // Lecture vocale automatique de la question (option activée)
    LaunchedEffect(currentCard.id) {
        if (autoTtsEnabled) {
            tts.value?.speak(currentCard.question, TextToSpeech.QUEUE_FLUSH, null, "auto_question")
        }
    }

    // Intervalle "Again" : étape d'apprentissage de 10 minutes
    val intervalAgain = "10 min"
    val intervalHard = remember(currentCard.id) {
        val result = SpacedRepetition.calculateReview(currentCard, SpacedRepetition.RATING_HARD, System.currentTimeMillis())
        formatIntervalDays(result.newInterval)
    }
    val intervalGood = remember(currentCard.id) {
        val result = SpacedRepetition.calculateReview(currentCard, SpacedRepetition.RATING_GOOD, System.currentTimeMillis())
        formatIntervalDays(result.newInterval)
    }
    val intervalEasy = remember(currentCard.id) {
        val result = SpacedRepetition.calculateReview(currentCard, SpacedRepetition.RATING_EASY, System.currentTimeMillis())
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
                            text = "${queue.size} restante${if (queue.size > 1) "s" else ""}",
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
                            contentDescription = "Mettre en pause"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNewSession) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Nouvelle session"
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
                progress = { ((sessionTotal - queue.size).toFloat() / sessionTotal.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
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
                                    if (!isAnswerRevealed) {
                                        isAnswerRevealed = true
                                    } else {
                                        // Swipe right -> Good rating
                                        rate(SpacedRepetition.RATING_GOOD)
                                    }
                                } else if (totalDrag < -80f) {
                                    if (!isAnswerRevealed) {
                                        isAnswerRevealed = true
                                    } else {
                                        // Swipe left -> Again rating (requeue en fin de session)
                                        rate(SpacedRepetition.RATING_AGAIN)
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

                    // Carte difficile (leech) : souvent oubliée
                    if (currentCard.lapses >= 5) {
                        Spacer(modifier = Modifier.height(LearnSyncSpacing.small))
                        Surface(
                            shape = LearnSyncShapes.pill,
                            color = AmberSoftBg
                        ) {
                            Text(
                                text = "Carte difficile (${currentCard.lapses} oublis)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AmberDark,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
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
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
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
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
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
                        onClick = { rate(SpacedRepetition.RATING_AGAIN) }
                    )

                    ReviewRatingButton(
                        title = "Hard",
                        intervalText = intervalHard,
                        color = RatingHardColor,
                        modifier = Modifier.weight(1f),
                        onClick = { rate(SpacedRepetition.RATING_HARD) }
                    )

                    ReviewRatingButton(
                        title = "Good",
                        intervalText = intervalGood,
                        color = RatingGoodColor,
                        modifier = Modifier.weight(1f),
                        onClick = { rate(SpacedRepetition.RATING_GOOD) }
                    )

                    ReviewRatingButton(
                        title = "Easy",
                        intervalText = intervalEasy,
                        color = RatingEasyColor,
                        modifier = Modifier.weight(1f),
                        onClick = { rate(SpacedRepetition.RATING_EASY) }
                    )
                }
            }
        }
    }
}

/** Bilan de fin de session avec la répartition des notes. */
@Composable
private fun ReviewSessionCompleteScreen(
    totalReviewed: Int,
    againCount: Int,
    hardCount: Int,
    goodCount: Int,
    easyCount: Int,
    sessionStartTime: Long,
    onFinish: () -> Unit
) {
    val sessionDurationSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).coerceAtLeast(1)
    val minutes = sessionDurationSeconds / 60
    val seconds = sessionDurationSeconds % 60
    val durationText = if (minutes > 0) "$minutes min $seconds s" else "$seconds s"
    val successRate = if (totalReviewed > 0) {
        (((goodCount + easyCount).toFloat() / totalReviewed) * 100).toInt()
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
                                text = "$totalReviewed",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimary
                            )
                            Text(
                                text = "Réponses",
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

                    // Répartition des notes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(
                            "Again" to againCount to RatingAgainColor,
                            "Hard" to hardCount to RatingHardColor,
                            "Good" to goodCount to RatingGoodColor,
                            "Easy" to easyCount to RatingEasyColor
                        ).forEach { (pair, color) ->
                            val (label, count) = pair
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$count",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = color
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                            text = if (againCount > 0)
                                "Les cartes « Again » reviennent dans 10 minutes"
                            else
                                "Prochaine session quand de nouvelles cartes seront dues",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(LearnSyncSpacing.small))

                    LearnSyncButton(
                        text = "Retour à l'accueil",
                        onClick = onFinish,
                        modifier = Modifier.fillMaxWidth()
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
