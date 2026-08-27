package com.learnsyncai.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.learnsyncai.ui.components.*
import com.learnsyncai.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(
    courses: List<Course>,
    allFlashcards: List<Flashcard> = emptyList(),
    dueCards: List<Flashcard> = emptyList(),
    generationProgress: String,
    onImportCourse: (Uri, String) -> Unit,
    onGenerateMaterial: (Course) -> Unit,
    onSelectCourse: (Course) -> Unit,
    onDeleteCourse: (String) -> Unit,
    onNavigateToCalendar: () -> Unit = {},
    onReviewCourse: (String) -> Unit = {}
) {
    var pendingImportFileName by remember { mutableStateOf<String?>(null) }
    var courseToDelete by remember { mutableStateOf<Course?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
            pendingImportFileName = fileName
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
                onClick = {
                    filePickerLauncher.launch(
                        arrayOf(
                            "application/pdf",
                            "text/plain",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        )
                    )
                },
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
                    description = "Importe tes cours au format PDF, DOCX ou TXT. LearnSync créera automatiquement tes fiches, synthèses, flashcards et QCMs d'entraînement.",
                    icon = Icons.Default.UploadFile,
                    actionLabel = "Choisir un document",
                    onActionClick = {
                        filePickerLauncher.launch(
                            arrayOf(
                                "application/pdf",
                                "text/plain",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                            )
                        )
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = LearnSyncSpacing.large),
                verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium),
                contentPadding = PaddingValues(top = LearnSyncSpacing.small, bottom = 88.dp)
            ) {
                items(courses, key = { it.id }) { course ->
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

    // AI Generation Modal
    if (generationProgress.isNotBlank()) {
        AiGeneratingProgressModal(
            progressStageText = generationProgress,
            courseTitle = pendingImportFileName ?: "Nouveau cours",
            onDismiss = {}
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
}
