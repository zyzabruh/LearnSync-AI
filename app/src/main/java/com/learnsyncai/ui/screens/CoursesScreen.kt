package com.learnsyncai.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.learnsyncai.domain.model.Course
import com.learnsyncai.domain.model.Flashcard
import com.learnsyncai.data.parser.ScannedPdfException
import com.learnsyncai.ui.components.*
import com.learnsyncai.ui.theme.*

private val IMPORT_MIME_TYPES = arrayOf(
    "application/pdf",
    "text/plain",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(
    courses: List<Course>,
    allFlashcards: List<Flashcard> = emptyList(),
    dueCards: List<Flashcard> = emptyList(),
    hasValidAiConfig: Boolean = true,
    onImportCourse: (Uri, String) -> Unit,
    onImportFromUrl: (String) -> Unit = {},
    onGenerateMaterial: (Course) -> Unit,
    onSelectCourse: (Course) -> Unit,
    onDeleteCourse: (String) -> Unit,
    onUpdateCourseTag: (String, String) -> Unit = { _, _ -> },
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onReviewCourse: (String) -> Unit = {},
    ocrRequest: ScannedPdfException? = null,
    ocrProgress: Pair<Int, Int>? = null,
    onRunPdfOcr: (ScannedPdfException) -> Unit = {},
    onCancelPdfOcr: () -> Unit = {}
) {
    var courseToDelete by remember { mutableStateOf<Course?>(null) }
    var courseToTag by remember { mutableStateOf<Course?>(null) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showOnboardingDismissed by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
            onImportCourse(uri, fileName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Mes Cours",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${courses.size} ${if (courses.size > 1) "cours enregistrés" else "cours enregistré"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Rechercher",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showUrlDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Importer depuis une URL",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onNavigateToCalendar) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Calendrier",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { filePickerLauncher.launch(IMPORT_MIME_TYPES) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = LearnSyncShapes.pill,
                modifier = Modifier.testTag("fab_import_course")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(LearnSyncSpacing.small))
                Text(
                    text = "Importer un cours",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (courses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    title = "Aucun cours pour le moment",
                    description = "Importe tes cours au format PDF, DOCX, PPTX ou TXT (ou via une URL). LearnSync créera automatiquement tes fiches, synthèses, flashcards et QCMs d'entraînement.",
                    icon = Icons.Default.UploadFile,
                    actionLabel = "Choisir un document",
                    onActionClick = { filePickerLauncher.launch(IMPORT_MIME_TYPES) }
                )
            }
        } else {
            val availableTags = courses.map { it.tag }.filter { it.isNotBlank() }.distinct()
            var selectedTag by remember { mutableStateOf<String?>(null) }
            val filteredCourses = if (selectedTag == null) courses else courses.filter { it.tag == selectedTag }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = LearnSyncSpacing.large),
                verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium),
                contentPadding = PaddingValues(top = LearnSyncSpacing.small, bottom = 88.dp)
            ) {
                // Onboarding : config IA manquante
                if (!hasValidAiConfig && !showOnboardingDismissed) {
                    item {
                        OnboardingCard(
                            onConfigureAi = onNavigateToProfile,
                            onDismiss = { showOnboardingDismissed = true }
                        )
                    }
                }

                // Filtres par étiquette
                if (availableTags.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedTag == null,
                                onClick = { selectedTag = null },
                                label = { Text("Tous (${courses.size})") }
                            )
                            availableTags.forEach { tag ->
                                FilterChip(
                                    selected = selectedTag == tag,
                                    onClick = { selectedTag = if (selectedTag == tag) null else tag },
                                    label = { Text(tag) }
                                )
                            }
                        }
                    }
                }

                items(filteredCourses, key = { it.id }) { course ->
                    val courseCards = allFlashcards.filter { it.courseId == course.id }
                    val courseDueCards = dueCards.filter { it.courseId == course.id }
                    val masteryPercent = if (courseCards.isEmpty()) 0 else {
                        val mastered = courseCards.count { it.repetitions >= 2 && it.difficulty <= 6.0f }
                        ((mastered.toFloat() / courseCards.size) * 100).toInt()
                    }

                    CourseCard(
                        course = course,
                        totalCardsCount = courseCards.size,
                        dueCardsCount = courseDueCards.size,
                        progressPercentage = masteryPercent,
                        onSelectCourse = onSelectCourse,
                        onReviewClick = if (courseDueCards.isNotEmpty()) {
                            { onReviewCourse(course.id) }
                        } else null
                    )
                }
            }
        }
    }

    if (ocrRequest != null) {
        AlertDialog(
            onDismissRequest = { onCancelPdfOcr() },
            icon = { Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("PDF scanné détecté") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)) {
                    Text("Ce document ne contient pas de couche texte. Lancez l'OCR local pour récupérer le contenu avant la génération.")
                    if (ocrProgress != null) {
                        val (completed, total) = ocrProgress
                        LinearProgressIndicator(
                            progress = { if (total > 0) completed.toFloat() / total else 0f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Page $completed/$total", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                if (ocrProgress == null) {
                    Button(onClick = { onRunPdfOcr(ocrRequest) }) {
                        Text("Lancer l'OCR")
                    }
                } else {
                    TextButton(onClick = onCancelPdfOcr) { Text("Annuler") }
                }
            },
            dismissButton = {
                if (ocrProgress == null) {
                    TextButton(onClick = onCancelPdfOcr) { Text("Ignorer") }
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (courseToDelete != null) {
        val course = courseToDelete!!
        AlertDialog(
            onDismissRequest = { courseToDelete = null },
            title = {
                Text(
                    text = "Supprimer ce cours ?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Toutes les flashcards, résumés et QCM associés au cours « ${course.title} » seront définitivement supprimés.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCourse(course.id)
                        courseToDelete = null
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
                TextButton(onClick = { courseToDelete = null }) {
                    Text("Annuler")
                }
            },
            shape = LearnSyncShapes.large
        )
    }

    // URL Import Dialog
    if (showUrlDialog) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Importer depuis une URL", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Collez le lien d'une page web (article de cours, documentation...). Le texte sera extrait puis généré comme un document classique.")
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("https://...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onImportFromUrl(url)
                        showUrlDialog = false
                    },
                    enabled = url.startsWith("http://") || url.startsWith("https://")
                ) {
                    Text("Importer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Annuler")
                }
            },
            shape = LearnSyncShapes.large
        )
    }
}

@Composable
private fun OnboardingCard(
    onConfigureAi: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = LearnSyncShapes.large,
        colors = CardDefaults.cardColors(containerColor = AmberSoftBg),
        border = BorderStroke(1.dp, AmberFlame.copy(alpha = 0.4f))
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
                ) {
                    Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = AmberDark)
                    Text(
                        text = "Bienvenue ! Démarrez en 3 étapes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AmberDark
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer", modifier = Modifier.size(16.dp))
                }
            }

            OnboardingStep(number = "1", text = "Importez un cours (PDF, DOCX, PPTX, TXT ou URL) — bouton en bas à droite.")
            OnboardingStep(number = "2", text = "Configurez votre IA (Gemini, OpenRouter...) pour des contenus de qualité. Sans clé, LearnSync génère un contenu de secours hors-ligne.")
            OnboardingStep(number = "3", text = "Générez, puis révisez : les flashcards suivent l'algorithme FSRS automatiquement.")

            LearnSyncSecondaryButton(
                text = "Configurer mon IA",
                icon = Icons.Default.SmartToy,
                onClick = onConfigureAi
            )
        }
    }
}

@Composable
private fun OnboardingStep(number: String, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = AmberFlame.copy(alpha = 0.2f)
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = AmberDark,
                modifier = Modifier.padding(6.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
