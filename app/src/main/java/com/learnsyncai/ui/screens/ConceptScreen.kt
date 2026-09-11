package com.learnsyncai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.learnsyncai.domain.model.Flashcard
import com.learnsyncai.ui.components.*
import com.learnsyncai.ui.theme.*

/**
 * Page concept (knowledge base) : occurrences dans les notes, cartes liées,
 * maîtrise estimée et révision ciblée.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConceptScreen(
    conceptName: String,
    courseTitle: String,
    snippets: List<String>,
    cards: List<Flashcard>,
    masteryPercent: Int,
    onBackClick: () -> Unit,
    onReviewCards: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(conceptName, maxLines = 1, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
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
                        Text("Cours : $courseTitle", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Maîtrise estimée", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("$masteryPercent%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = IndigoPrimary)
                        }
                        LinearProgressIndicator(
                            progress = { (masteryPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(androidx.compose.foundation.shape.CircleShape),
                            color = EmeraldSuccess,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(
                            "${cards.size} carte(s) liée(s) · ${snippets.size} mention(s) dans les notes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (cards.isNotEmpty()) {
                            LearnSyncButton(
                                text = "Réviser ce concept (${cards.size})",
                                icon = Icons.Default.PlayArrow,
                                onClick = onReviewCards,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            if (snippets.isNotEmpty()) {
                item {
                    Text("Mentionné dans les notes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                snippets.forEach { snippet ->
                    item(key = "snip_${snippet.hashCode()}") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = LearnSyncShapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = "« $snippet »",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(LearnSyncSpacing.medium)
                            )
                        }
                    }
                }
            }
            if (cards.isNotEmpty()) {
                item {
                    Text("Cartes liées", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                cards.take(20).forEach { card ->
                    item(key = "card_${card.id}") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = LearnSyncShapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(LearnSyncSpacing.medium)) {
                                Text(card.question.take(160), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text(
                                    "${card.lapses} oublis · dû ${if (card.dueDate <= System.currentTimeMillis()) "maintenant" else "plus tard"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
