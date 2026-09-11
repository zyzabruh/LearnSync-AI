package com.learnsyncai.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.learnsyncai.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

private data class MindNode(val name: String, val center: Offset, val radius: Float, val count: Int)

/**
 * Carte mentale du cours : nœud central + concepts [[...]] reliés,
 * taille selon le nombre de cartes. Tap = ouvrir la page concept.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseMindMapScreen(
    courseTitle: String,
    concepts: List<Pair<String, Int>>,
    onOpenConcept: (String) -> Unit,
    onBackClick: () -> Unit
) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val shown = remember(concepts) { concepts.take(12) }
    val nodes = remember(viewport, shown, courseTitle) {
        if (viewport == IntSize.Zero || shown.isEmpty()) return@remember emptyList<MindNode>()
        val w = viewport.width.toFloat()
        val h = viewport.height.toFloat()
        val cx = w / 2f
        val cy = h / 2.4f
        val radius = (minOf(w, h) / 2.6f).coerceAtLeast(220f)
        buildList {
            add(MindNode("", Offset(cx, cy), 110f, 0))
            shown.forEachIndexed { i, (name, count) ->
                val angle = (2 * Math.PI * i / shown.size - Math.PI / 2).toFloat()
                val nx = cx + radius * cos(angle)
                val ny = cy + radius * 0.85f * sin(angle)
                val nodeR = (46f + count.coerceAtMost(10) * 5f).coerceAtMost(110f)
                add(MindNode(name, Offset(nx, ny), nodeR, count))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Carte mentale · $courseTitle", maxLines = 1, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
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
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding).padding(LearnSyncSpacing.xxl), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(
                    "Aucun concept [[...]] détecté : écris des liens dans tes notes (ex. [[ATP]]) pour voir la carte.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }
        val primary = IndigoPrimary
        val lineColor = MaterialTheme.colorScheme.outlineVariant
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .onSizeChanged { viewport = it }
                .pointerInput(nodes) {
                    detectTapGestures { tap ->
                        nodes.firstOrNull { it.name.isNotEmpty() && (tap - it.center).getDistance() <= it.radius }
                            ?.let { onOpenConcept(it.name) }
                    }
                }
        ) {
            val center = nodes.firstOrNull() ?: return@Canvas
            drawCircle(color = primary, radius = center.radius, center = center.center)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = 34f
                isFakeBoldText = true
            }
            val title = if (courseTitle.length > 18) courseTitle.take(17) + "…" else courseTitle
            drawContext.canvas.nativeCanvas.drawText(title, center.center.x, center.center.y + 12f, paint)
            val labelPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.DKGRAY
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = 30f
            }
            nodes.drop(1).forEach { node ->
                drawLine(lineColor, center.center, node.center, strokeWidth = 4f)
                drawCircle(Color.White, node.radius, node.center)
                drawCircle(primary, node.radius, node.center, style = Stroke(width = 5f))
                val short = if (node.name.length > 14) node.name.take(13) + "…" else node.name
                drawContext.canvas.nativeCanvas.drawText(short, node.center.x, node.center.y - 4f, labelPaint)
                drawContext.canvas.nativeCanvas.drawText("×${node.count}", node.center.x, node.center.y + 30f, labelPaint)
            }
        }
    }
}
