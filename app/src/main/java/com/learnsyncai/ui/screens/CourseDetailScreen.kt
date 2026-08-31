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
import androidx.compose.material.icons.automirrored.filled.*
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
                            leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
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
                0 -> { // Résumé
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Synthèse du cours",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            FilledTonalButton(
                                onClick = { showEditSummaryDialog = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (latestMaterial?.summary?.isNotBlank() == true) "Modifier" else "Rédiger")
                            }
                        }
                    }

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
                                            imageVector = Icons.AutoMirrored.Filled.Article,
                                            contentDescription = null,
                                            tint = IndigoPrimary
                                        )
                                        Text(
                                            text = "Contenu de la synthèse",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
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
                                description = "Rédigez manuellement votre synthèse ou lancez la génération IA.",
                                icon = Icons.AutoMirrored.Filled.Article,
                                actionLabel = "Générer avec l'IA",
                                onActionClick = onRegenerate
                            )
                        }
                    }
                }

                1 -> { // Notions clés
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Points d'ancrage essentiels",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            FilledTonalButton(
                                onClick = { showAddKeyPointDialog = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ajouter")
                            }
                        }
                    }

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
                                    modifier = Modifier.padding(LearnSyncSpacing.medium),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(IndigoPrimary)
                                    )
                                    Text(
                                        text = point,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { onRemoveKeyPoint(point) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Supprimer la notion",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            EmptyState(
                                title = "Aucune notion clé",
                                description = "Ajoutez manuellement vos points clés ou laissez l'IA les extraire.",
                                icon = Icons.Default.Lightbulb,
                                actionLabel = "Générer avec l'IA",
                                onActionClick = onRegenerate
                            )
                        }
                    }
                }

                2 -> { // Flashcards List Preview
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cartes de révision (${flashcards.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            FilledTonalButton(
                                onClick = { showAddFlashcardDialog = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ajouter")
                            }
                        }
                    }

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
                                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            IconButton(
                                                onClick = { onDeleteFlashcard(card.id) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Supprimer",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (expanded) {
                                        HorizontalDivider(
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
                                description = "Créez vos propres flashcards ou générez-les avec l'IA.",
                                icon = Icons.Default.School,
                                actionLabel = "Générer avec l'IA",
                                onActionClick = onRegenerate
                            )
                        }
                    }
                }

                3 -> { // QCM List Preview
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Questions de QCM (${quizQuestions.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            FilledTonalButton(
                                onClick = { showAddQuizDialog = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ajouter")
                            }
                        }
                    }

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
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = qcm.question,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { onDeleteQuizQuestion(qcm.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Supprimer",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
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
                                            text = "Bonne réponse : ${qcm.correctAnswer}",
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
                                description = "Créez vos propres questions ou laissez l'IA générer l'évaluation.",
                                icon = Icons.Default.Quiz,
                                actionLabel = "Générer avec l'IA",
                                onActionClick = onRegenerate
                            )
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS FOR MANUAL CONTENT CREATION ---

    // 1. Add Flashcard Dialog
    if (showAddFlashcardDialog) {
        var cardQuestion by remember { mutableStateOf("") }
        var cardAnswer by remember { mutableStateOf("") }
        var cardExplanation by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddFlashcardDialog = false },
            title = { Text("Nouvelle Flashcard", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium)
                ) {
                    OutlinedTextField(
                        value = cardQuestion,
                        onValueChange = { cardQuestion = it },
                        label = { Text("Question *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cardAnswer,
                        onValueChange = { cardAnswer = it },
                        label = { Text("Réponse *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cardExplanation,
                        onValueChange = { cardExplanation = it },
                        label = { Text("Explication / Astuce (facultatif)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (cardQuestion.isNotBlank() && cardAnswer.isNotBlank()) {
                            onAddFlashcard(cardQuestion, cardAnswer, cardExplanation)
                            showAddFlashcardDialog = false
                        }
                    },
                    enabled = cardQuestion.isNotBlank() && cardAnswer.isNotBlank()
                ) {
                    Text("Ajouter")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFlashcardDialog = false }) {
                    Text("Annuler")
                }
            },
            shape = LearnSyncShapes.large
        )
    }

    // 2. Add Quiz Question Dialog
    if (showAddQuizDialog) {
        var quizQuestionText by remember { mutableStateOf("") }
        var optA by remember { mutableStateOf("") }
        var optB by remember { mutableStateOf("") }
        var optC by remember { mutableStateOf("") }
        var optD by remember { mutableStateOf("") }
        var correctOptionIndex by remember { mutableIntStateOf(0) }
        var quizExplanation by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddQuizDialog = false },
            title = { Text("Nouveau QCM", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
                ) {
                    OutlinedTextField(
                        value = quizQuestionText,
                        onValueChange = { quizQuestionText = it },
                        label = { Text("Question *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = optA,
                        onValueChange = { optA = it },
                        label = { Text("Option A *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = optB,
                        onValueChange = { optB = it },
                        label = { Text("Option B *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = optC,
                        onValueChange = { optC = it },
                        label = { Text("Option C *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = optD,
                        onValueChange = { optD = it },
                        label = { Text("Option D *") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Bonne réponse :",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        listOf("A", "B", "C", "D").forEachIndexed { index, label ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = correctOptionIndex == index,
                                    onClick = { correctOptionIndex = index }
                                )
                                Text(label)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = quizExplanation,
                        onValueChange = { quizExplanation = it },
                        label = { Text("Explication (facultatif)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                val opts = listOf(optA, optB, optC, optD)
                val isValid = quizQuestionText.isNotBlank() && opts.all { it.isNotBlank() } && opts.distinct().size == 4
                Button(
                    onClick = {
                        if (isValid) {
                            val correctAnswer = opts[correctOptionIndex]
                            onAddQuizQuestion(quizQuestionText, opts, correctAnswer, quizExplanation)
                            showAddQuizDialog = false
                        }
                    },
                    enabled = isValid
                ) {
                    Text("Ajouter")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddQuizDialog = false }) {
                    Text("Annuler")
                }
            },
            shape = LearnSyncShapes.large
        )
    }

    // 3. Edit Summary Dialog
    if (showEditSummaryDialog) {
        var summaryText by remember { mutableStateOf(latestMaterial?.summary ?: "") }

        AlertDialog(
            onDismissRequest = { showEditSummaryDialog = false },
            title = { Text("Synthèse du cours", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = summaryText,
                    onValueChange = { summaryText = it },
                    label = { Text("Texte du résumé") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    maxLines = 15
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveSummary(summaryText)
                        showEditSummaryDialog = false
                    }
                ) {
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditSummaryDialog = false }) {
                    Text("Annuler")
                }
            },
            shape = LearnSyncShapes.large
        )
    }

    // 4. Add Key Point Dialog
    if (showAddKeyPointDialog) {
        var keyPointText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddKeyPointDialog = false },
            title = { Text("Nouvelle notion clé", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = keyPointText,
                    onValueChange = { keyPointText = it },
                    label = { Text("Point d'ancrage / Notion essentielle *") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (keyPointText.isNotBlank()) {
                            onAddKeyPoint(keyPointText)
                            showAddKeyPointDialog = false
                        }
                    },
                    enabled = keyPointText.isNotBlank()
                ) {
                    Text("Ajouter")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddKeyPointDialog = false }) {
                    Text("Annuler")
                }
            },
            shape = LearnSyncShapes.large
        )
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

