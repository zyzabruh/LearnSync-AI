package com.learnsyncai.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.learnsyncai.data.parser.OutlineEntry
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
    initialPage: Int = 0,
    onLoadPageText: (suspend (Int) -> String)? = null,
    outline: List<OutlineEntry> = emptyList(),
    annotations: List<PdfAnnotation>,
    onAddAnnotation: (page: Int, text: String, kind: String) -> Unit,
    onQuickAddCard: (page: Int, question: String, answer: String) -> Unit = { _, _, _ -> },
    onLoadHighlightRects: (suspend (page: Int, text: String) -> List<android.graphics.RectF>)? = null,
    onDeleteAnnotation: (String) -> Unit,
    onCardsFromAnnotation: (PdfAnnotation) -> Unit,
    onBackClick: () -> Unit
) {
    var pageIndex by remember(initialPage) { mutableIntStateOf(initialPage.coerceAtLeast(0)) }
    var noteText by remember { mutableStateOf("") }
    var noteKind by remember { mutableStateOf(PdfAnnotation.KIND_NOTE) }
    var textMode by remember { mutableStateOf(false) }

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
    val textIndex = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    val bitmap = remember(rendererState, safeIndex) {
        try {
            val renderer = rendererState?.second ?: return@remember null
            renderer.openPage(safeIndex).use { page ->
                // Échelle 2.5x : net sur écran haute densité sans bitmap démesuré.
                val scale = 2.5f
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !textMode,
                        onClick = { textMode = false },
                        label = { Text("Page") }
                    )
                    FilterChip(
                        selected = textMode,
                        onClick = { textMode = true },
                        label = { Text("Texte") }
                    )
                }
            }

            if (outline.isNotEmpty()) {
                item {
                    Text(
                        "Sommaire (${outline.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                fun LazyListScope.outlineItems(entries: List<OutlineEntry>, depth: Int = 0) {
                    entries.forEach { entry ->
                        item(key = "outline_${entry.title}_${entry.pageIndex}_$depth".hashCode()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = (depth * 16).dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "p. ${entry.pageIndex + 1}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                IconButton(onClick = { pageIndex = entry.pageIndex }, enabled = entry.pageIndex >= 0) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = "Aller à la page", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        if (entry.children.isNotEmpty()) {
                            outlineItems(entry.children, depth + 1)
                        }
                    }
                }
                outlineItems(outline)
            }

            item {
                if (textMode) {
                    var loadedText by remember(pdfFile, textIndex) { mutableStateOf<String?>(null) }
                    LaunchedEffect(textMode, textIndex) {
                        loadedText = null
                        loadedText = try { onLoadPageText?.invoke(textIndex) } catch (_: Exception) { null } ?: ""
                    }
                    val pageText = loadedText
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
                            Text(
                                "Texte de la page ${textIndex + 1} — sélectionnez un passage",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (pageText == null) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                }
                            } else if (pageText.isBlank()) {
                                Text(
                                    "Aucun texte sélectionnable ici (PDF scanné sans couche texte, ou copie locale indisponible).",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                var fieldValue by remember(textIndex, pageText) {
                                    mutableStateOf(TextFieldValue(pageText))
                                }
                                BasicTextField(
                                    value = fieldValue,
                                    onValueChange = { fieldValue = it },
                                    readOnly = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 200.dp, max = 420.dp)
                                        .verticalScroll(rememberScrollState())
                                )
                                val selected = fieldValue.selection.let { sel ->
                                    if (sel.collapsed) "" else runCatching {
                                        pageText.substring(
                                            sel.start.coerceIn(0, pageText.length),
                                            sel.end.coerceIn(0, pageText.length)
                                        )
                                    }.getOrDefault("")
                                }
                                var showQuickCard by remember { mutableStateOf(false) }
                                var quickQuestion by remember { mutableStateOf("") }
                                var quickAnswer by remember { mutableStateOf("") }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    LearnSyncButton(
                                        text = "Noter",
                                        icon = Icons.Default.Add,
                                        enabled = selected.isNotBlank(),
                                        onClick = {
                                            onAddAnnotation(textIndex, selected, noteKind)
                                            fieldValue = fieldValue.copy(selection = TextRange.Zero)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    LearnSyncButton(
                                        text = "Carte express",
                                        icon = Icons.Default.Bolt,
                                        enabled = selected.isNotBlank(),
                                        onClick = {
                                            quickQuestion = selected
                                            quickAnswer = ""
                                            showQuickCard = true
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (showQuickCard) {
                                    AlertDialog(
                                        onDismissRequest = { showQuickCard = false },
                                        title = { Text("Carte express (sans IA)") },
                                        text = {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedTextField(
                                                    value = quickQuestion,
                                                    onValueChange = { quickQuestion = it },
                                                    label = { Text("Question ({{ }} = trou)") },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                OutlinedTextField(
                                                    value = quickAnswer,
                                                    onValueChange = { quickAnswer = it },
                                                    label = { Text("Réponse") },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(
                                                onClick = {
                                                    onQuickAddCard(textIndex, quickQuestion, quickAnswer)
                                                    showQuickCard = false
                                                },
                                                enabled = quickQuestion.trim().length >= 3
                                            ) {
                                                Text("Créer")
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showQuickCard = false }) {
                                                Text("Annuler")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = LearnSyncShapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        if (bitmap != null) {
                            var highlightRects by remember(pdfFile, safeIndex) {
                                mutableStateOf<List<android.graphics.RectF>>(emptyList())
                            }
                            LaunchedEffect(pdfFile, safeIndex, annotations) {
                                val keys = annotations.filter {
                                    it.page == safeIndex && it.kind == PdfAnnotation.KIND_KEY && it.text.trim().length >= 4
                                }.take(3)
                                highlightRects = keys.flatMap { ann ->
                                    try {
                                        onLoadHighlightRects?.invoke(safeIndex, ann.text)
                                    } catch (_: Exception) {
                                        null
                                    } ?: emptyList()
                                }.take(24)
                            }
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Page ${safeIndex + 1}",
                                    // FillBounds dans une boîte au ratio exact : pas de
                                    // distorsion, et correspondance exacte pour l'overlay.
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (highlightRects.isNotEmpty()) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        for (r in highlightRects) {
                                            drawRect(
                                                color = Color.Yellow.copy(alpha = 0.35f),
                                                topLeft = Offset(r.left * size.width, r.top * size.height),
                                                size = Size(r.width() * size.width, r.height() * size.height)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                "Page illisible.",
                                modifier = Modifier.padding(LearnSyncSpacing.large),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
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
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                PdfAnnotation.KIND_NOTE to "Note",
                                PdfAnnotation.KIND_KEY to "Important",
                                "difficult" to "Difficile",
                                "understood" to "Compris",
                                "definition" to "Définition"
                            ).forEach { (value, label) ->
                                FilterChip(
                                    selected = noteKind == value,
                                    onClick = { noteKind = value },
                                    label = { Text(label) }
                                )
                            }
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
                                if (annotation.kind != PdfAnnotation.KIND_NOTE) {
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
