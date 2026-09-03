package com.learnsyncai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.learnsyncai.domain.model.AiProfile
import com.learnsyncai.domain.model.Course
import com.learnsyncai.domain.model.Flashcard
import com.learnsyncai.domain.model.QuizQuestion
import com.learnsyncai.domain.model.StudyMaterial
import com.learnsyncai.ui.components.*
import com.learnsyncai.ui.theme.*

/** Options de langue de réponse IA : "auto" suit la langue du document. */
private val courseLanguageOptions = listOf(
    "auto" to "Auto (langue du document)",
    "fr" to "Français",
    "en" to "English",
    "es" to "Español",
    "de" to "Deutsch",
    "it" to "Italiano",
    "pt" to "Português",
    "nl" to "Nederlands",
    "ar" to "العربية"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    course: Course,
    materials: List<StudyMaterial>,
    flashcards: List<Flashcard>,
    quizQuestions: List<QuizQuestion>,
    generationProgress: String = "",
    activeAiProfile: AiProfile? = null,
    coursePreview: String = "",
    onBackClick: () -> Unit,
    onStartReview: () -> Unit,
    onStartQuiz: () -> Unit,
    onRegenerate: () -> Unit,
    onGenerateMore: () -> Unit = {},
    onCourseLanguageChange: (String) -> Unit = {},
    onDeleteCourse: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onExportCsv: (android.net.Uri) -> Unit = {},
    onAddFlashcard: (question: String, answer: String, explanation: String) -> Unit = { _, _, _ -> },
    onDeleteFlashcard: (flashcardId: String) -> Unit = {},
    onAddQuizQuestion: (question: String, options: List<String>, correctAnswer: String, explanation: String) -> Unit = { _, _, _, _ -> },
    onDeleteQuizQuestion: (quizQuestionId: String) -> Unit = {},
    onSaveSummary: (summary: String) -> Unit = {},
    onAddKeyPoint: (point: String) -> Unit = {},
    onRemoveKeyPoint: (point: String) -> Unit = {}
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Dialog states for custom content creation
    var showAddFlashcardDialog by remember { mutableStateOf(false) }
    var showAddQuizDialog by remember { mutableStateOf(false) }
    var showEditSummaryDialog by remember { mutableStateOf(false) }
    var showAddKeyPointDialog by remember { mutableStateOf(false) }

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

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: android.net.Uri? ->
        if (uri != null) onExportCsv(uri)
    }

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
                    // Langue de réponse IA du cours ("auto" = langue du document)
                    Box {
                        IconButton(onClick = { showLanguageMenu = !showLanguageMenu }) {
                            Icon(Icons.Default.Translate, contentDescription = "Langue de réponse")
                        }
                        DropdownMenu(
                            expanded = showLanguageMenu,
                            onDismissRequest = { showLanguageMenu = false }
                        ) {
                            courseLanguageOptions.forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            fontWeight = if (course.language == code) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = {
                                        if (course.language == code) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        } else {
                                            Spacer(modifier = Modifier.size(24.dp))
                                        }
                                    },
                                    onClick = {
                                        showLanguageMenu = false
                                        if (course.language != code) onCourseLanguageChange(code)
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Générer plus de contenu") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onGenerateMore()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Régénérer le contenu") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onRegenerate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Exporter en CSV (Anki)") },
                            leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                val safeTitle = course.title.replace(Regex("[^a-zA-Z0-9 _-]"), "").trim().ifBlank { "cours" }
                                exportLauncher.launch("$safeTitle.csv")
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
            // Aperçu du document avant génération
            if (materials.isEmpty() && generationProgress.isEmpty() && coursePreview.isNotBlank()) {
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
                                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "Aperçu du document",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = coursePreview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 8,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            LearnSyncSecondaryButton(
                                text = "Générer les contenus",
                                icon = Icons.Default.AutoAwesome,
                                onClick = onRegenerate
                            )
                        }
                    }
                }
            }

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

            // AI Status & Diagnostics Banner
            val isGenerating = course.generationStatus == "GENERATING"
            val isError = course.generationStatus == "ERROR"

            if (isGenerating) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = LearnSyncShapes.large,
                        colors = CardDefaults.cardColors(containerColor = IndigoSoftBg),
                        border = BorderStroke(1.5.dp, IndigoPrimary)
                    ) {
                        Row(
                            modifier = Modifier.padding(LearnSyncSpacing.large),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp,
                                color = IndigoPrimary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Génération IA en cours...",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = IndigoPrimary
                                )
                                Text(
                                    text = if (generationProgress.isNotBlank()) generationProgress else "Analyse du document et structuration pédagogique...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            } else if (isError) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = LearnSyncShapes.large,
                        colors = CardDefaults.cardColors(containerColor = RoseError.copy(alpha = 0.08f)),
                        border = BorderStroke(1.dp, RoseError.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(LearnSyncSpacing.large),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = RoseError, modifier = Modifier.size(28.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Échec de la génération IA",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = RoseError
                                )
                                Text(
                                    text = "Une erreur est survenue lors de la communication avec l'IA. Vérifiez vos clés d'API et votre connexion.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            FilledTonalButton(
                                onClick = onRegenerate,
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = RoseError.copy(alpha = 0.15f), contentColor = RoseError)
                            ) {
                                Text("Réessayer")
                            }
                        }
                    }
                }
            } else if (activeAiProfile == null || (activeAiProfile.apiKey.isBlank() && !activeAiProfile.baseUrl.contains("localhost") && !activeAiProfile.baseUrl.contains("127.0.0.1"))) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = LearnSyncShapes.large,
                        colors = CardDefaults.cardColors(containerColor = AmberFlame.copy(alpha = 0.08f)),
                        border = BorderStroke(1.dp, AmberFlame.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(LearnSyncSpacing.large),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium)
                        ) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = AmberFlame, modifier = Modifier.size(24.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Aucune clé API configurée",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberFlame
                                )
                                Text(
                                    text = "Configurez votre fournisseur d'IA dans les paramètres de profil pour débloquer la génération automatique.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            TextButton(onClick = onNavigateToProfile) {
                                Text("Profil", color = AmberFlame, fontWeight = FontWeight.Bold)
                            }
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
                0 -> CourseSummaryTab(
                    latestMaterial = latestMaterial,
                    onEditSummary = { showEditSummaryDialog = true },
                    onRegenerate = onRegenerate
                )

                1 -> CourseKeyPointsTab(
                    keyPoints = latestMaterial?.keyPoints ?: emptyList(),
                    onAddKeyPoint = { showAddKeyPointDialog = true },
                    onRemoveKeyPoint = onRemoveKeyPoint,
                    onRegenerate = onRegenerate
                )

                2 -> CourseFlashcardsTab(
                    flashcards = flashcards,
                    onAddFlashcard = { showAddFlashcardDialog = true },
                    onDeleteFlashcard = onDeleteFlashcard,
                    onRegenerate = onRegenerate
                )

                3 -> CourseQuizTab(
                    quizQuestions = quizQuestions,
                    onAddQuizQuestion = { showAddQuizDialog = true },
                    onDeleteQuizQuestion = onDeleteQuizQuestion,
                    onRegenerate = onRegenerate
                )
            }
        }
    }

    // --- DIALOGS FOR MANUAL CONTENT CREATION ---

    if (showAddFlashcardDialog) {
        AddFlashcardDialog(
            onDismiss = { showAddFlashcardDialog = false },
            onConfirm = onAddFlashcard
        )
    }

    if (showAddQuizDialog) {
        AddQuizQuestionDialog(
            onDismiss = { showAddQuizDialog = false },
            onConfirm = onAddQuizQuestion
        )
    }

    if (showEditSummaryDialog) {
        EditSummaryDialog(
            initialSummary = latestMaterial?.summary ?: "",
            onDismiss = { showEditSummaryDialog = false },
            onConfirm = onSaveSummary
        )
    }

    if (showAddKeyPointDialog) {
        AddKeyPointDialog(
            onDismiss = { showAddKeyPointDialog = false },
            onConfirm = onAddKeyPoint
        )
    }

    if (showDeleteConfirmDialog) {
        DeleteCourseConfirmDialog(
            courseTitle = course.title,
            onDismiss = { showDeleteConfirmDialog = false },
            onConfirm = onDeleteCourse
        )
    }
}
