package com.learnsyncai.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.learnsyncai.domain.model.PdfAnnotation
import com.learnsyncai.ui.components.*
import com.learnsyncai.ui.theme.*
import java.io.File

/**
 * Lecteur PDF intégré : pages rendues localement, note par page et
 * passages « à retenir » convertibles en cartes IA.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    courseTitle: String,
    pdfFile: File?,
    annotations: List<PdfAnnotation>,
    onAddAnnotation: (page: Int, text: String, kind: String) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onCardsFromAnnotation: (PdfAnnotation) -> Unit,
    onBackClick: () -> Unit
) {
    var pageIndex by remember { mutableIntStateOf(0) }
    var noteText by remember { mutableStateOf("") }
    var noteKind by remember { mutableStateOf(PdfAnnotation.KIND_NOTE) }

    if (pdfFile == null || !pdfFile.exists()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Lire le PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding).padding(LearnSyncSpacing.xxl), contentAlignment = Alignment.Center) {
                Text(
                    "Aucune copie locale du PDF (import web ou fichier déplacé). Ouvrez le document via « Ouvrir le document » ou réimportez-le.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val rendererState = remember(pdfFile) {
        try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            Triple(pfd, renderer, renderer.pageCount)
        } catch (_: Exception) {
            null
        }
    }
    DisposableEffect(pdfFile) {
        onDispose {
            try {
                rendererState?.second?.close()
                rendererState?.first?.close()
            } catch (_: Exception) { }
        }
    }

    val pageCount = rendererState?.third ?: 0
    val safeIndex = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    val bitmap = remember(rendererState, safeIndex) {
        try {
            val renderer = rendererState?.second ?: return@remember null
            renderer.openPage(safeIndex).use { page ->
                val scale = 2f
                val bmp = Bitmap.createBitmap(
                    (page.width * scale).toInt(),
                    (page.height * scale).toInt(),
                    Bitmap.Config.ARGB_8888
                )
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(courseTitle, maxLines = 1, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = LearnSyncSpacing.large),
            verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium),
            contentPadding = PaddingValues(bottom = LearnSyncSpacing.xxl)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { pageIndex = (safeIndex - 1).coerceAtLeast(0) }, enabled = safeIndex > 0) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Page précédente")
                    }
                    Text(
                        "Page ${safeIndex + 1} / $pageCount",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { pageIndex = (safeIndex + 1).coerceAtMost(pageCount - 1) }, enabled = safeIndex < pageCount - 1) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Page suivante")
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = LearnSyncShapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Page ${safeIndex + 1}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            "Page illisible.",
                            modifier = Modifier.padding(LearnSyncSpacing.large),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = LearnSyncShapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(LearnSyncSpacing.large),
                        verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
                    ) {
                        Text("Annoter la page ${safeIndex + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            label = { Text("Note ou passage à retenir…") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = noteKind == PdfAnnotation.KIND_NOTE,
                                onClick = { noteKind = PdfAnnotation.KIND_NOTE },
                                label = { Text("Note") }
                            )
                            FilterChip(
                                selected = noteKind == PdfAnnotation.KIND_KEY,
                                onClick = { noteKind = PdfAnnotation.KIND_KEY },
                                label = { Text("À retenir") }
                            )
                        }
                        LearnSyncButton(
                            text = "Enregistrer",
                            icon = Icons.Default.Add,
                            enabled = noteText.trim().length >= 3,
                            onClick = {
                                onAddAnnotation(safeIndex, noteText, noteKind)
                                noteText = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            if (annotations.isNotEmpty()) {
                item {
                    Text("Annotations (${annotations.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                annotations.forEach { annotation ->
                    item(key = "ann_${annotation.id}") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = LearnSyncShapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(LearnSyncSpacing.medium)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "p. ${annotation.page + 1} · ${if (annotation.kind == PdfAnnotation.KIND_KEY) "À retenir" else "Note"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(onClick = { onDeleteAnnotation(annotation.id) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer", modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text(annotation.text, style = MaterialTheme.typography.bodySmall)
                                if (annotation.kind == PdfAnnotation.KIND_KEY) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    TextButton(
                                        onClick = { onCardsFromAnnotation(annotation) },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Créer des cartes IA", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
