package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Course

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(
    courses: List<Course>,
    generationProgress: String,
    onImportCourse: (Uri, String) -> Unit,
    onGenerateMaterial: (Course) -> Unit,
    onSelectCourse: (Course) -> Unit,
    onDeleteCourse: (String) -> Unit
) {
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
            onImportCourse(uri, fileName)
        }
    }

    var courseToDelete by remember { mutableStateOf<Course?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mes Cours", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { filePickerLauncher.launch(arrayOf("application/pdf", "text/plain", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) },
                modifier = Modifier.testTag("fab_import_course"),
                icon = { Icon(Icons.Default.Add, contentDescription = "Importer") },
                text = { Text("Importer un cours") }
            )
        }
    ) { innerPadding ->
        if (courses.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Aucun cours pour le moment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Importez vos cours en PDF, DOCX ou TXT pour que l'IA génère automatiquement vos résumés, flashcards et QCMs.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { filePickerLauncher.launch(arrayOf("application/pdf", "text/plain", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) }) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choisir un document")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(courses) { course ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        onClick = { onSelectCourse(course) }
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(course.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = { courseToDelete = course }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            
                            Text(course.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            
                            if (course.generationStatus == "GENERATING" && generationProgress.isNotBlank()) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(generationProgress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BadgeContainer(
                                    icon = when(course.generationStatus) {
                                        "COMPLETED" -> Icons.Default.CheckCircle
                                        "GENERATING" -> Icons.Default.HourglassEmpty
                                        "ERROR" -> Icons.Default.Error
                                        else -> Icons.Default.Pending
                                    },
                                    label = when(course.generationStatus) {
                                        "COMPLETED" -> "Prêt pour révision"
                                        "GENERATING" -> "Génération IA..."
                                        "ERROR" -> "Erreur de génération"
                                        else -> "Non analysé"
                                    }
                                )
                                if (course.generationStatus != "GENERATING") {
                                    Button(
                                        onClick = {
                                            if (course.generationStatus == "COMPLETED") {
                                                onSelectCourse(course)
                                            } else {
                                                onGenerateMaterial(course)
                                            }
                                        },
                                        modifier = Modifier.testTag("generate_material_button")
                                    ) {
                                        Icon(
                                            if (course.generationStatus == "COMPLETED") Icons.Default.Visibility else Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (course.generationStatus == "COMPLETED") "Ouvrir" else "Générer l'IA")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Delete confirmation dialog
        courseToDelete?.let { course ->
            AlertDialog(
                onDismissRequest = { courseToDelete = null },
                icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Supprimer ce cours ?") },
                text = { Text("La suppression de « ${course.title} » effacera définitivement tout le matériel associé (résumé, flashcards, QCMs et historique de révision).") },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteCourse(course.id)
                            courseToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Supprimer")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { courseToDelete = null }) {
                        Text("Annuler")
                    }
                }
            )
        }
    }
}
