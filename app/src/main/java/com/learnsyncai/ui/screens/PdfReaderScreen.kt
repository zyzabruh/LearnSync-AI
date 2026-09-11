package com.learnsyncai.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.learnsyncai.data.parser.OutlineEntry
import com.learnsyncai.data.parser.PageLink
import com.learnsyncai.data.parser.PageWord
import com.learnsyncai.data.parser.PdfSearchHit
import com.learnsyncai.domain.model.InkStroke
import com.learnsyncai.domain.model.PdfAnnotation
import com.learnsyncai.ui.components.*
import com.learnsyncai.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

/**
 * Lecteur PDF intégré : pages rendues localement, note par page et
 * passages « à retenir » convertibles en cartes IA.
 */
/** Bitmap d'une page, mis en cache par page (pager à défilement). */
@Composable
private fun rememberPageBitmap(
    rendererState: Triple<ParcelFileDescriptor, PdfRenderer, Int>?,
    page: Int
): Bitmap? {
    return remember(rendererState, page) {
        try {
            val renderer = rendererState?.second ?: return@remember null
            if (page < 0 || page >= renderer.pageCount) return@remember null
            renderer.openPage(page).use { pg ->
                val longestSide = maxOf(pg.width, pg.height).coerceAtLeast(1)
                val scale = minOf(2.5f, 2048f / longestSide.toFloat())
                val bmp = Bitmap.createBitmap(
                    (pg.width * scale).toInt(),
                    (pg.height * scale).toInt(),
                    Bitmap.Config.ARGB_8888
                )
                pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        } catch (_: Exception) {
            null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    courseTitle: String,
    pdfFile: File?,
    initialPage: Int = 0,
    onLoadPageText: (suspend (Int) -> String)? = null,
    onLoadPageWords: (suspend (page: Int) -> List<PageWord>)? = null,
    onLoadPageLinks: (suspend (page: Int) -> List<PageLink>)? = null,
    onSearchInPdf: (suspend (String) -> List<PdfSearchHit>)? = null,
    outline: List<OutlineEntry> = emptyList(),
    annotations: List<PdfAnnotation>,
    onAddAnnotation: (page: Int, text: String, kind: String) -> Unit,
    onQuickAddCard: (page: Int, question: String, answer: String) -> Unit = { _, _, _ -> },
    onLoadHighlightRects: (suspend (page: Int, texts: List<String>) -> List<android.graphics.RectF>)? = null,
    inkVersion: Int = 0,
    onLoadInkStrokes: (suspend () -> List<InkStroke>)? = null,
    onSaveInkStroke: (page: Int, stroke: InkStroke) -> Unit = { _, _ -> },
    onClearInkPage: (page: Int) -> Unit = {},
    onAskTutor: (String) -> Unit = {},
    onDeleteAnnotation: (String) -> Unit,
    onCardsFromAnnotation: (PdfAnnotation) -> Unit,
    onBackClick: () -> Unit
) {
    var noteText by remember { mutableStateOf("") }
    var noteKind by remember { mutableStateOf(PdfAnnotation.KIND_NOTE) }
    var textMode by remember { mutableStateOf(false) }
    var nightMode by remember { mutableStateOf(false) }
    var drawMode by remember { mutableStateOf(false) }
    var inkColor by remember { mutableLongStateOf(0xFFFFFF00L) }
    var tempInk by remember { mutableStateOf(emptyList<Offset>()) }
    var inkStrokes by remember { mutableStateOf(emptyList<InkStroke>()) }
    LaunchedEffect(pdfFile, inkVersion) {
        inkStrokes = try { onLoadInkStrokes?.invoke() } catch (_: Exception) { null } ?: emptyList()
    }
    // Zoom (pincement quand zoomé, double-tap 1x/2.5x) + sélection d'un mot au doigt.
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomPan by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoomScale = (zoomScale * zoomChange).coerceIn(1f, 5f)
        zoomPan = if (zoomScale <= 1f) Offset.Zero else zoomPan + panChange
    }
    var boxPx by remember { mutableStateOf(IntSize.Zero) }
    var pageWords by remember { mutableStateOf(emptyList<PageWord>()) }
    var pageLinks by remember { mutableStateOf(emptyList<PageLink>()) }
    var showQuickCard by remember { mutableStateOf(false) }
    var quickQuestion by remember { mutableStateOf("") }
    var quickAnswer by remember { mutableStateOf("") }
    var wordSel by remember { mutableStateOf<IntRange?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchDone by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf(emptyList<PdfSearchHit>()) }
    var searchJump by remember { mutableStateOf<Pair<Int, String>?>(null) }

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
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))) { pageCount }
    val scope = rememberCoroutineScope()
    val safeIndex = if (pageCount > 0) pagerState.currentPage.coerceIn(0, pageCount - 1) else 0
    val textIndex = safeIndex
    LaunchedEffect(pdfFile, safeIndex) {
        zoomScale = 1f
        zoomPan = Offset.Zero
        wordSel = null
        pageWords = try { onLoadPageWords?.invoke(safeIndex) } catch (_: Exception) { null } ?: emptyList()
        pageLinks = try { onLoadPageLinks?.invoke(safeIndex) } catch (_: Exception) { null } ?: emptyList()
    }
    /** Tracé d'encre (points normalisés 0..1) mis à l'échelle du Canvas. */
    fun DrawScope.drawInkStroke(points: List<Float>, color: Color, canvasSize: Size) {
        if (points.size < 4) return
        val path = Path()
        path.moveTo(points[0] * canvasSize.width, points[1] * canvasSize.height)
        var i = 2
        while (i + 1 < points.size) {
            path.lineTo(points[i] * canvasSize.width, points[i + 1] * canvasSize.height)
            i += 2
        }
        drawPath(
            path,
            color.copy(alpha = 0.55f),
            style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    /** Lance la recherche plein-texte (barre de recherche du lecteur). */
    fun doSearch() {
        val q = searchQuery.trim()
        if (q.length < 2 || searching) return
        scope.launch {
            searching = true
            searchDone = false
            searchResults = try { onSearchInPdf?.invoke(q) } catch (_: Exception) { null } ?: emptyList()
            searchDone = true
            searching = false
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
                    IconButton(onClick = { scope.launch { pagerState.animateScrollToPage((safeIndex - 1).coerceAtLeast(0)) } }, enabled = safeIndex > 0) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Page précédente")
                    }
                    Text(
                        "Page ${safeIndex + 1} / $pageCount",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { scope.launch { pagerState.animateScrollToPage((safeIndex + 1).coerceAtMost(pageCount - 1)) } }, enabled = safeIndex < pageCount - 1) {
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
                        selected = !textMode && !drawMode,
                        onClick = { textMode = false; drawMode = false },
                        label = { Text("Page") }
                    )
                    FilterChip(
                        selected = textMode,
                        onClick = { textMode = true; drawMode = false },
                        label = { Text("Texte") }
                    )
                    FilterChip(
                        selected = drawMode,
                        onClick = {
                            drawMode = !drawMode
                            if (drawMode) {
                                textMode = false
                                zoomScale = 1f
                                zoomPan = Offset.Zero
                                wordSel = null
                            }
                        },
                        label = { Text("Dessiner") }
                    )
                    FilterChip(
                        selected = showSearch,
                        onClick = {
                            showSearch = !showSearch
                            if (showSearch) {
                                textMode = false
                                drawMode = false
                            }
                        },
                        label = { Text("Recherche") }
                    )
                    FilterChip(
                        selected = nightMode,
                        onClick = { nightMode = !nightMode },
                        label = { Text("Nuit") }
                    )
                }
            }
            if (drawMode) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            0xFFFFFF00L to "Jaune",
                            0xFF4CAF50L to "Vert",
                            0xFF2196F3L to "Bleu",
                            0xFFF44336L to "Rouge",
                            0xFF9C27AFL to "Violet"
                        ).forEach { (c, label) ->
                            FilterChip(
                                selected = inkColor == c,
                                onClick = { inkColor = c },
                                label = {
                                    Box(
                                        modifier = Modifier.size(16.dp).clip(CircleShape)
                                            .background(Color(c.toULong()))
                                    )
                                }
                            )
                        }
                        IconButton(onClick = { tempInk = emptyList() }) {
                            Icon(Icons.Default.Undo, contentDescription = "Annuler le trait")
                        }
                        IconButton(onClick = { onClearInkPage(safeIndex) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Effacer la page")
                        }
                    }
                }
            }

            if (showSearch) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            searchDone = false
                        },
                        label = { Text("Rechercher dans le PDF") },
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    searchResults = emptyList()
                                    searchDone = false
                                    searchJump = null
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Effacer")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searching) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Recherche…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (searchDone && searchQuery.trim().length >= 2) {
                    if (searchResults.isEmpty()) {
                        item {
                            Text(
                                "Aucun résultat.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    searchResults.take(30).forEach { hit ->
                        item(key = "search_${hit.page}_${hit.snippet.hashCode()}") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = LearnSyncShapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                onClick = {
                                    searchJump = hit.page to searchQuery.trim()
                                    scope.launch {
                                        pagerState.animateScrollToPage(hit.page.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
                                    }
                                }
                            ) {
                                Column(modifier = Modifier.padding(LearnSyncSpacing.medium)) {
                                    Text(
                                        text = "p. ${hit.page + 1}" + if (hit.count > 1) " · ${hit.count} occurrences" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(hit.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                                }
                            }
                        }
                    }
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
                                    fontWeight = if (entry.pageIndex == safeIndex) FontWeight.Bold else FontWeight.Normal,
                                    color = if (entry.pageIndex == safeIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "p. ${entry.pageIndex + 1}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(entry.pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))) } }, enabled = entry.pageIndex >= 0) {
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
                } else if (pageCount > 0) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth(),
                        userScrollEnabled = zoomScale <= 1f && !drawMode,
                        beyondViewportPageCount = 1
                    ) { page ->
                        val pageBitmap = rememberPageBitmap(rendererState, page)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = LearnSyncShapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            if (pageBitmap != null) {
                            var highlightRects by remember(pdfFile, safeIndex) {
                                mutableStateOf<List<android.graphics.RectF>>(emptyList())
                            }
                            LaunchedEffect(pdfFile, safeIndex, annotations, searchJump) {
                                val keys = annotations.filter {
                                    it.page == safeIndex && it.kind == PdfAnnotation.KIND_KEY && it.text.trim().length >= 4
                                }.take(3).map { it.text }.toMutableList()
                                searchJump?.let { (pg, q) ->
                                    if (pg == safeIndex && q.isNotBlank()) keys.add(q)
                                }
                                highlightRects = try {
                                    onLoadHighlightRects?.invoke(safeIndex, keys)
                                } catch (_: Exception) {
                                    null
                                } ?: emptyList()
                            }
                            val aspect = pageBitmap.width.toFloat() / pageBitmap.height.toFloat()
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .aspectRatio(if (aspect.isFinite() && aspect > 0f) aspect else 1f)
                                    .onSizeChanged { boxPx = it }
                                    .graphicsLayer {
                                        scaleX = zoomScale
                                        scaleY = zoomScale
                                        translationX = zoomPan.x
                                        translationY = zoomPan.y
                                    }
                                    // Pincement actif seulement quand zoomé (sinon le
                                    // scroll vertical de la liste reste prioritaire).
                                    .transformable(transformState, enabled = zoomScale > 1f && !drawMode)
                                    .pointerInput(drawMode, safeIndex) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                zoomScale = if (zoomScale > 1.5f) 1f else 2.5f
                                                zoomPan = Offset.Zero
                                            },
                                            onTap = { offset ->
                                                if (drawMode || boxPx.width <= 0 || boxPx.height <= 0) return@detectTapGestures
                                                val fx = 0.5f + ((offset.x - boxPx.width / 2f - zoomPan.x) / zoomScale) / boxPx.width
                                                val fy = 0.5f + ((offset.y - boxPx.height / 2f - zoomPan.y) / zoomScale) / boxPx.height
                                                val idx = pageWords.indexOfFirst { w ->
                                                    fx in w.left..w.right && fy in w.top..w.bottom
                                                }
                                                wordSel = if (idx >= 0) idx..idx else null
                                            }
                                        )
                                    }
                            ) {
                                Image(
                                    bitmap = pageBitmap.asImageBitmap(),
                                    contentDescription = "Page ${safeIndex + 1}",
                                    // FillBounds dans une boîte au ratio exact : pas de
                                    // distorsion, et correspondance exacte pour l'overlay.
                                    contentScale = ContentScale.FillBounds,
                                    // Mode nuit : inversion des couleurs (lecture du soir).
                                    colorFilter = if (nightMode) ColorFilter.colorMatrix(
                                        ColorMatrix(
                                            floatArrayOf(
                                                -1f, 0f, 0f, 0f, 255f,
                                                0f, -1f, 0f, 0f, 255f,
                                                0f, 0f, -1f, 0f, 255f,
                                                0f, 0f, 0f, 1f, 0f
                                            )
                                        )
                                    ) else null,
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
                                val pageInk = remember(inkStrokes, safeIndex) {
                                    inkStrokes.filter { it.page == safeIndex }
                                }
                                if (pageInk.isNotEmpty() || tempInk.size >= 2) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        for (s in pageInk) {
                                            drawInkStroke(s.points, Color(s.color.toULong()), size)
                                        }
                                        if (tempInk.size >= 2) {
                                            drawInkStroke(
                                                tempInk.flatMap { listOf(it.x, it.y) },
                                                Color(inkColor.toULong()),
                                                size
                                            )
                                        }
                                    }
                                }
                                val selBoxes = remember(wordSel, pageWords) {
                                    wordSel?.let { r ->
                                        if (pageWords.isEmpty()) emptyList()
                                        else {
                                            val a = r.first.coerceIn(0, pageWords.size - 1)
                                            val b = r.last.coerceIn(0, pageWords.size - 1)
                                            if (a > b) emptyList()
                                            else pageWords.subList(a, b + 1).map { w ->
                                                android.graphics.RectF(w.left, w.top, w.right, w.bottom)
                                            }
                                        }
                                    }.orEmpty()
                                }
                                if (selBoxes.isNotEmpty()) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        for (r in selBoxes) {
                                            drawRect(
                                                color = Color.Blue.copy(alpha = 0.30f),
                                                topLeft = Offset(r.left * size.width, r.top * size.height),
                                                size = Size(r.width() * size.width, r.height() * size.height)
                                            )
                                        }
                                    }
                                }
                                if (pageLinks.isNotEmpty() && !drawMode) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        val density = LocalDensity.current
                                        for (l in pageLinks) {
                                            Box(
                                                modifier = Modifier
                                                    .offset {
                                                        IntOffset(
                                                            (l.left * boxPx.width).toInt(),
                                                            (l.top * boxPx.height).toInt()
                                                        )
                                                    }
                                                    .size(
                                                        width = with(density) { ((l.right - l.left) * boxPx.width).toDp() },
                                                        height = with(density) { ((l.bottom - l.top) * boxPx.height).toDp() }
                                                    )
                                                    .clickable {
                                                        scope.launch {
                                                            pagerState.animateScrollToPage(
                                                                l.targetPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                                                            )
                                                        }
                                                    }
                                            )
                                        }
                                    }
                                }
                                if (drawMode) {
                                    Canvas(
                                        modifier = Modifier.fillMaxSize().pointerInput(safeIndex) {
                                            detectDragGestures(
                                                onDragStart = { offset ->
                                                    tempInk = listOf(
                                                        Offset(
                                                            (offset.x / size.width).coerceIn(0f, 1f),
                                                            (offset.y / size.height).coerceIn(0f, 1f)
                                                        )
                                                    )
                                                },
                                                onDrag = { change, _ ->
                                                    val o = change.position
                                                    tempInk = tempInk + Offset(
                                                        (o.x / size.width).coerceIn(0f, 1f),
                                                        (o.y / size.height).coerceIn(0f, 1f)
                                                    )
                                                },
                                                onDragEnd = {
                                                    if (tempInk.size >= 2) {
                                                        onSaveInkStroke(
                                                            safeIndex,
                                                            InkStroke(
                                                                page = safeIndex,
                                                                color = inkColor,
                                                                points = tempInk.flatMap { listOf(it.x, it.y) }
                                                            )
                                                        )
                                                    }
                                                    tempInk = emptyList()
                                                },
                                                onDragCancel = { tempInk = emptyList() }
                                            )
                                        }
                                    ) { }
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
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = LearnSyncShapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        "Page illisible.",
                        modifier = Modifier.padding(LearnSyncSpacing.large),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            val selectedText = remember(wordSel, pageWords) {
                wordSel?.let { r ->
                    if (pageWords.isEmpty()) ""
                    else {
                        val a = r.first.coerceIn(0, pageWords.size - 1)
                        val b = r.last.coerceIn(0, pageWords.size - 1)
                        if (a > b) "" else pageWords.subList(a, b + 1).joinToString(" ") { it.text }
                    }
                }.orEmpty()
            }
            if (selectedText.isNotBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = LearnSyncShapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(LearnSyncSpacing.medium),
                            verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
                        ) {
                            Text(selectedText, style = MaterialTheme.typography.bodyMedium, maxLines = 5)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        wordSel = wordSel?.let { r ->
                                            val a = (r.first + 1).coerceAtMost(r.last)
                                            val b = (r.last - 1).coerceAtLeast(a)
                                            if (a > b) null else a..b
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("−") }
                                OutlinedButton(
                                    onClick = {
                                        wordSel = wordSel?.let { r ->
                                            (r.first - 1).coerceAtLeast(0)..(r.last + 1).coerceAtMost(pageWords.size - 1)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("+") }
                                TextButton(onClick = { wordSel = null }) { Text("Fermer") }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LearnSyncButton(
                                    text = "Noter",
                                    icon = Icons.Default.Add,
                                    onClick = {
                                        onAddAnnotation(safeIndex, selectedText, noteKind)
                                        wordSel = null
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                LearnSyncButton(
                                    text = "Carte",
                                    icon = Icons.Default.Bolt,
                                    onClick = {
                                        quickQuestion = selectedText
                                        quickAnswer = ""
                                        showQuickCard = true
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LearnSyncButton(
                                    text = "Surligner",
                                    icon = Icons.Default.Star,
                                    onClick = {
                                        onAddAnnotation(safeIndex, selectedText, PdfAnnotation.KIND_KEY)
                                        wordSel = null
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                LearnSyncButton(
                                    text = "Tuteur",
                                    icon = Icons.Default.SmartToy,
                                    onClick = { onAskTutor(selectedText) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
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
