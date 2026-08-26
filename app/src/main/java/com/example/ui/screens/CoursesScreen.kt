package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    onImportCourse: (Uri, String) -> Unit,
    onGenerateMaterial: (Course) -> Unit,
    onDeleteCourse: (String) -> Unit
) {
    var selectedCourseForDetail by remember { mutableStateOf<Course?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment ?: "document.txt"
            onImportCourse(uri, fileName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mes Cours", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { filePickerLauncher.launch(arrayOf("application/pdf", "text/plain", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) },
                modifier = Modifier.testTag("fab_import_course")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Importer un cours")
            }
        }
    ) { innerPadding ->
        if (courses.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Aucun cours importé", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Appuyez sur + pour importer un PDF ou TXT", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        onClick = { selectedCourseForDetail = course }
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(course.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = { onDeleteCourse(course.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            Text(course.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BadgeContainer(
                                    icon = if (course.generationStatus == "COMPLETED") Icons.Default.CheckCircle else Icons.Default.HourglassEmpty,
                                    label = when(course.generationStatus) {
                                        "COMPLETED" -> "Matériel généré"
                                        "GENERATING" -> "Analyse en cours..."
                                        "ERROR" -> "Erreur de génération"
                                        else -> "Non généré"
                                    }
                                )
                                if (course.generationStatus != "COMPLETED" && course.generationStatus != "GENERATING") {
                                    Button(
                                        onClick = { onGenerateMaterial(course) },
                                        modifier = Modifier.testTag("generate_material_button")
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Générer l'IA")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        selectedCourseForDetail?.let { course ->
            AlertDialog(
                onDismissRequest = { selectedCourseForDetail = null },
                title = { Text(course.title) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        Text("Fichier source : ${course.sourceFileName}", fontWeight = FontWeight.Bold)
                        Text("Description : ${course.description}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Extrait du texte :", fontWeight = FontWeight.Bold)
                        Text(course.extractedText.take(500) + if (course.extractedText.length > 500) "..." else "", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedCourseForDetail = null }) {
                        Text("Fermer")
                    }
                },
                dismissButton = {
                    if (course.generationStatus != "COMPLETED") {
                        Button(onClick = {
                            onGenerateMaterial(course)
                            selectedCourseForDetail = null
                        }) {
                            Text("Générer avec l'IA")
                        }
                    }
                }
            )
        }
    }
}
