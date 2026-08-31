package com.learnsyncai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.learnsyncai.domain.model.Course
import com.learnsyncai.domain.model.Flashcard
import com.learnsyncai.domain.model.QuizQuestion
import com.learnsyncai.domain.model.StudyMaterial
import com.learnsyncai.ui.theme.*

data class SearchResultItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val courseId: String
)

/**
 * Recherche globale : cours, flashcards, QCM et synthèses.
 * Filtrage client (les volumes restent raisonnables pour un usage mobile).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    courses: List<Course>,
    flashcards: List<Flashcard>,
    quizQuestions: List<QuizQuestion>,
    materials: List<StudyMaterial>,
    onBackClick: () -> Unit,
    onSelectResult: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }

    val results = remember(query, courses, flashcards, quizQuestions, materials) {
        val q = query.trim().lowercase()
        if (q.length < 2) {
            emptyList()
        } else {
            val courseTitleById = courses.associate { it.id to it.title }
            val items = mutableListOf<SearchResultItem>()

            courses.filter { it.title.lowercase().contains(q) || it.tag.lowercase().contains(q) }.forEach {
                items.add(SearchResultItem(Icons.AutoMirrored.Filled.MenuBook, it.title, "Cours${if (it.tag.isNotBlank()) " · ${it.tag}" else ""}", it.id))
            }
            flashcards.filter { it.question.lowercase().contains(q) || it.answer.lowercase().contains(q) }.take(40).forEach {
                items.add(SearchResultItem(Icons.Default.CreditCard, it.question, "Flashcard · ${courseTitleById[it.courseId] ?: ""}", it.courseId))
            }
            quizQuestions.filter { it.question.lowercase().contains(q) }.take(40).forEach {
                items.add(SearchResultItem(Icons.Default.Quiz, it.question, "QCM · ${courseTitleById[it.courseId] ?: ""}", it.courseId))
            }
            materials.filter { it.summary.lowercase().contains(q) }.take(20).forEach {
                val snippet = run {
                    val idx = it.summary.lowercase().indexOf(q)
                    val start = (idx - 40).coerceAtLeast(0)
                    it.summary.substring(start, (start + 90).coerceAtMost(it.summary.length)).replace("\n", " ")
                }
                items.add(SearchResultItem(Icons.Default.Description, snippet, "Synthèse · ${courseTitleById[it.courseId] ?: ""}", it.courseId))
            }
            items
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recherche", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = LearnSyncSpacing.large)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Cours, flashcards, QCM, synthèses...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Effacer")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(LearnSyncSpacing.medium))

            when {
                query.trim().length < 2 -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Tapez au moins 2 caractères pour rechercher dans tout votre contenu.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                results.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Aucun résultat pour « ${query.trim()} ».",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    Text(
                        text = "${results.size} résultat(s)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(LearnSyncSpacing.small))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small),
                        contentPadding = PaddingValues(bottom = LearnSyncSpacing.xxl)
                    ) {
                        items(results.size) { index ->
                            val result = results[index]
                            Surface(
                                onClick = { onSelectResult(result.courseId) },
                                shape = LearnSyncShapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(result.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = result.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = result.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
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
    }
}
