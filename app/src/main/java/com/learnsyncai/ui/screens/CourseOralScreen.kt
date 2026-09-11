package com.learnsyncai.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.learnsyncai.domain.model.ReviewItem
import com.learnsyncai.domain.usecase.CardContent
import com.learnsyncai.ui.components.*
import com.learnsyncai.ui.theme.*
import java.util.Locale

/**
 * Interrogation orale : l'app lit la question (TTS), l'élève répond à voix
 * haute, la transcription est comparée aux mots-clés de la réponse,
 * puis l'élève se note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseOralScreen(
    items: List<ReviewItem>,
    onSpeak: (String) -> Unit,
    onRate: (ReviewItem, Int) -> Unit,
    onQuit: () -> Unit
) {
    val queue = remember(items) { items.take(10) }
    var index by remember { mutableIntStateOf(0) }
    var transcript by remember { mutableStateOf("") }
    var permissionGranted by remember { mutableStateOf(false) }
    val item = queue.getOrNull(index)
    val prompt = remember(item) { item?.let { CardContent.resolvePrompt(it) } ?: "" }
    val expected = remember(item) { item?.let { CardContent.resolveAnswer(it) } ?: "" }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            transcript = matches?.firstOrNull().orEmpty()
        }
    }

    val keywords = remember(expected) {
        expected.lowercase().split(Regex("[^\\p{L}0-9]+")).filter { it.length > 3 }.distinct()
    }
    val hits = remember(transcript, keywords) {
        val spoken = transcript.lowercase()
        keywords.count { spoken.contains(it) }
    }

    fun listen() {
        if (!permissionGranted) {
            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Explique ta réponse à voix haute")
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            transcript = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Interro orale${if (queue.isNotEmpty()) " (${index + 1}/${queue.size})" else ""}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onQuit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quitter")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (item == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding).padding(LearnSyncSpacing.xxl), contentAlignment = Alignment.Center) {
                Text(
                    if (items.isEmpty()) "Aucune carte due pour l'oral : reviens quand des cartes seront dues."
                    else "Session terminée, bravo !",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(LearnSyncSpacing.large),
            verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.large)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = LearnSyncShapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(LearnSyncSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(prompt, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onSpeak(prompt) }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Écouter")
                        }
                        FilledTonalButton(onClick = {
                            transcript = ""
                            listen()
                        }) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Répondre")
                        }
                    }
                    if (transcript.isNotBlank()) {
                        Text(
                            "« $transcript »",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Mots-clés : $hits/${keywords.size}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (keywords.isNotEmpty() && hits * 2 >= keywords.size) EmeraldDark else AmberDark
                        )
                    }
                }
            }
            Text("Note-toi :", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Oublié" to com.learnsyncai.domain.usecase.SpacedRepetition.RATING_AGAIN,
                    "Dur" to com.learnsyncai.domain.usecase.SpacedRepetition.RATING_HARD,
                    "OK" to com.learnsyncai.domain.usecase.SpacedRepetition.RATING_GOOD,
                    "Facile" to com.learnsyncai.domain.usecase.SpacedRepetition.RATING_EASY
                ).forEach { (label, rating) ->
                    Button(
                        onClick = {
                            onRate(item, rating)
                            transcript = ""
                            index++
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
