package com.learnsyncai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsyncai.domain.model.Flashcard
import com.learnsyncai.domain.model.QuizQuestion
import com.learnsyncai.domain.model.StudyMaterial
import com.learnsyncai.ui.components.*
import com.learnsyncai.ui.theme.*

/** Onglet Résumé : synthèse du cours (lecture ou état vide). */
internal fun LazyListScope.CourseSummaryTab(
    latestMaterial: StudyMaterial?,
    onEditSummary: () -> Unit,
    onRegenerate: () -> Unit,
    onQuickCloze: (question: String, answer: String, excerpt: String) -> Unit = { _, _, _ -> },
    onGenerateFromExcerpt: (String) -> Unit = {}
) {
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Synthèse du cours",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            FilledTonalButton(
                onClick = onEditSummary,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (latestMaterial?.summary?.isNotBlank() == true) "Modifier" else "Rédiger")
            }
        }
    }

    if (latestMaterial != null && latestMaterial.summary.isNotBlank()) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = LearnSyncShapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(LearnSyncSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Article,
                            contentDescription = null,
                            tint = IndigoPrimary
                        )
                        Text(
                            text = "Contenu de la synthèse",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    SelectableSummary(
                        summary = latestMaterial.summary,
                        onQuickCloze = onQuickCloze,
                        onGenerateFromExcerpt = onGenerateFromExcerpt
                    )
                }
            }
        }
    } else {
        item {
            EmptyState(
                title = "Aucun résumé",
                description = "Rédigez manuellement votre synthèse ou lancez la génération IA.",
                icon = Icons.AutoMirrored.Filled.Article,
                actionLabel = "Générer avec l'IA",
                onActionClick = onRegenerate
            )
        }
    }
}

/** Onglet Notions clés : liste des points d'ancrage essentiels. */
internal fun LazyListScope.CourseKeyPointsTab(
    keyPoints: List<String>,
    onAddKeyPoint: () -> Unit,
    onRemoveKeyPoint: (String) -> Unit,
    onRegenerate: () -> Unit
) {
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Points d'ancrage essentiels",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            FilledTonalButton(
                onClick = onAddKeyPoint,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ajouter")
            }
        }
    }

    if (keyPoints.isNotEmpty()) {
        items(keyPoints) { point ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = LearnSyncShapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(LearnSyncSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(IndigoPrimary)
                    )
                    Text(
                        text = point,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onRemoveKeyPoint(point) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Supprimer la notion",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    } else {
        item {
            EmptyState(
                title = "Aucune notion clé",
                description = "Ajoutez manuellement vos points clés ou laissez l'IA les extraire.",
                icon = Icons.Default.Lightbulb,
                actionLabel = "Générer avec l'IA",
                onActionClick = onRegenerate
            )
        }
    }
}

/** Onglet Flashcards : liste dépliable des cartes avec badge « Due ». */
internal fun LazyListScope.CourseFlashcardsTab(
    flashcards: List<Flashcard>,
    onAddFlashcard: () -> Unit,
    onDeleteFlashcard: (String) -> Unit,
    onRegenerate: () -> Unit
) {
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Cartes de révision (${flashcards.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            FilledTonalButton(
                onClick = onAddFlashcard,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ajouter")
            }
        }
    }

    if (flashcards.isNotEmpty()) {
        items(flashcards) { card ->
            var expanded by remember { mutableStateOf(false) }
            val isDue = card.dueDate <= System.currentTimeMillis()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                shape = LearnSyncShapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(LearnSyncSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Q: ${card.question}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isDue) {
                                Surface(
                                    shape = LearnSyncShapes.pill,
                                    color = AmberSoftBg
                                ) {
                                    Text(
                                        text = "Due",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberDark,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            IconButton(
                                onClick = { onDeleteFlashcard(card.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Supprimer",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (expanded) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Text(
                            text = "R: ${card.answer}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = EmeraldDark
                        )
                        if (card.explanation.isNotBlank()) {
                            Text(
                                text = "Note: ${card.explanation}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    } else {
        item {
            EmptyState(
                title = "Aucune flashcard",
                description = "Créez vos propres flashcards ou générez-les avec l'IA.",
                icon = Icons.Default.School,
                actionLabel = "Générer avec l'IA",
                onActionClick = onRegenerate
            )
        }
    }
}

/** Onglet QCM : liste des questions avec bonne réponse. */
internal fun LazyListScope.CourseQuizTab(
    quizQuestions: List<QuizQuestion>,
    onAddQuizQuestion: () -> Unit,
    onDeleteQuizQuestion: (String) -> Unit,
    onRegenerate: () -> Unit
) {
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Questions de QCM (${quizQuestions.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            FilledTonalButton(
                onClick = onAddQuizQuestion,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ajouter")
            }
        }
    }

    if (quizQuestions.isNotEmpty()) {
        items(quizQuestions) { qcm ->
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = qcm.question,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onDeleteQuizQuestion(qcm.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Supprimer",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Bonne réponse : ${qcm.correctAnswer}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = EmeraldDark
                        )
                    }
                }
            }
        }
    } else {
        item {
            EmptyState(
                title = "Aucun QCM",
                description = "Créez vos propres questions ou laissez l'IA générer l'évaluation.",
                icon = Icons.Default.Quiz,
                actionLabel = "Générer avec l'IA",
                onActionClick = onRegenerate
            )
        }
    }
}

/**
 * Résumé sélectionnable (style RemNote) : un appui long sélectionne du
 * texte, puis deux actions créent des cartes — cloze direct ou via l'IA.
 */
@Composable
private fun SelectableSummary(
    summary: String,
    onQuickCloze: (question: String, answer: String, excerpt: String) -> Unit,
    onGenerateFromExcerpt: (String) -> Unit
) {
    var fieldValue by remember(summary) {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(summary))
    }
    val selection = fieldValue.selection
    val selectedText = remember(fieldValue) {
        if (selection.collapsed) "" else fieldValue.text.substring(
            selection.start.coerceIn(0, fieldValue.text.length),
            selection.end.coerceIn(0, fieldValue.text.length)
        ).trim()
    }

    Column(verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)) {
        androidx.compose.foundation.text.BasicTextField(
            value = fieldValue,
            onValueChange = { fieldValue = it },
            readOnly = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (selectedText.length >= 3) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {
                        val sentence = sentenceAround(fieldValue.text, selection.start, selection.end)
                        val clozeQuestion = sentence.replaceRange(
                            sentence.indexOf(selectedText).takeIf { it >= 0 } ?: 0,
                            (sentence.indexOf(selectedText).takeIf { it >= 0 } ?: 0) + selectedText.length,
                            "{{${selectedText}}}"
                        )
                        onQuickCloze(clozeQuestion, selectedText, sentence)
                    },
                    label = { Text("Cloze") },
                    leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = { onGenerateFromExcerpt(selectedText) },
                    label = { Text("Cartes IA") },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
        } else {
            Text(
                text = "Astuce : sélectionnez un passage pour en faire des cartes.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Phrase (délimitée par ponctuation/sauts) contenant la sélection. */
private fun sentenceAround(text: String, selStart: Int, selEnd: Int): String {
    val start = text.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'), selStart).let { if (it < 0) 0 else it + 1 }
    val end = text.indexOfAny(charArrayOf('.', '!', '?', '\n'), selEnd).let { if (it < 0) text.length else it + 1 }
    return text.substring(start.coerceIn(0, text.length), end.coerceIn(0, text.length)).trim().ifBlank {
        text.substring(selStart.coerceIn(0, text.length), selEnd.coerceIn(0, text.length)).trim()
    }
}
