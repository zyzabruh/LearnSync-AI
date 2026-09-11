package com.learnsyncai.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.learnsyncai.domain.model.Course
import com.learnsyncai.domain.model.SharedConcept
import com.learnsyncai.ui.components.*
import com.learnsyncai.ui.theme.*

/**
 * Graphe des connaissances : concepts [[liés]] partagés entre plusieurs
 * cours, avec accès direct aux pages concept de chaque cours.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeGraphScreen(
    courses: List<Course>,
    sharedConcepts: List<SharedConcept>,
    onOpenConcept: (courseId: String, name: String) -> Unit,
    onBackClick: () -> Unit
) {
    val titleById = remember(courses) { courses.associate { it.id to it.title } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Graphe des connaissances", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
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
        if (sharedConcepts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    title = "Aucun concept partagé",
                    description = "Lie tes notes avec des [[concepts]] dans au moins deux cours : ils apparaîtront ici avec leurs cartes.",
                    icon = Icons.Default.Share
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = LearnSyncSpacing.large),
                verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium),
                contentPadding = PaddingValues(bottom = LearnSyncSpacing.xxl)
            ) {
                item {
                    Text(
                        "${sharedConcepts.size} concept${if (sharedConcepts.size > 1) "s" else ""} reliant ${courses.size} cours",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                sharedConcepts.forEach { concept ->
                    item(key = "graph_${concept.name}") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = LearnSyncShapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(LearnSyncSpacing.medium),
                                verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = concept.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${concept.cardCount} carte${if (concept.cardCount > 1) "s" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    concept.courseIds.forEach { courseId ->
                                        AssistChip(
                                            onClick = { onOpenConcept(courseId, concept.name) },
                                            label = { Text(titleById[courseId] ?: "Cours") },
                                            leadingIcon = {
                                                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                        )
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
