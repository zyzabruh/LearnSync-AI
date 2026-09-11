package com.learnsyncai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.learnsyncai.domain.model.CourseMedia
import com.learnsyncai.domain.model.Flashcard
import com.learnsyncai.domain.model.QuizQuestion
import com.learnsyncai.domain.model.StudyMaterial
import com.learnsyncai.ui.theme.RoseError
import com.learnsyncai.ui.components.*
import com.learnsyncai.ui.theme.*

/** Onglet Résumé : synthèse du cours (lecture ou état vide). */
internal fun LazyListScope.CourseSummaryTab(
    latestMaterial: StudyMaterial?,
    onEditSummary: () -> Unit,
    onRegenerate: () -> Unit,
    onQuickCloze: (question: String, answer: String, excerpt: String) -> Unit = { _, _, _ -> },
    onGenerateFromExcerpt: (String) -> Unit = {},
    onOpenConcept: (String) -> Unit = {}
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
                        onGenerateFromExcerpt = onGenerateFromExcerpt,
                        onOpenConcept = onOpenConcept
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
    onRegenerate: () -> Unit,
    onAddImageFlashcard: () -> Unit = {}
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onAddImageFlashcard,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Image")
                }
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
    onGenerateFromExcerpt: (String) -> Unit,
    onOpenConcept: (String) -> Unit = {}
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
        val summaryConcepts = remember(summary) {
            com.learnsyncai.domain.usecase.Concepts.extract(summary)
        }
        if (summaryConcepts.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                summaryConcepts.forEach { concept ->
                    AssistChip(
                        onClick = { onOpenConcept(concept) },
                        label = { Text(concept) },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
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

/**
 * Onglet Notes : prise de notes libre (style RemNote). Chaque ligne marquée
 * (>>, <<, <>, ;;, ::, {{}}) devient une flashcard via « Créer les cartes ».
 */
internal fun LazyListScope.CourseNotesTab(
    note: com.learnsyncai.domain.model.CourseNote?,
    onSaveNote: (String) -> Unit,
    onConvertNotes: (String) -> Unit,
    onOpenConcept: (String) -> Unit = {},
    onConvertSingleLine: (String) -> Unit = {}
) {
    item {
        Text(
            text = "Mes notes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
    item {
        var field by remember(note?.id, note?.updatedAt) {
            mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(note?.content ?: ""))
        }
        var showHelp by remember { mutableStateOf(false) }
        val content = field.text
        val detectedCount = remember(content) {
            com.learnsyncai.domain.usecase.NoteCards.parse(content).size
        }
        val dirty = content != (note?.content ?: "")
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
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    label = { Text("Écrivez vos notes (une idée par ligne)") },
                    placeholder = { Text("Ex.\nMitochondrie >> centrale énergétique de la cellule\nLa {{mitochondrie}} produit l'ATP") },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                // Blocs hiérarchiques : indente/désindente la ligne du curseur.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { field = indentLine(field, true) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("⇥ Indenter", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { field = indentLine(field, false) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("⇤ Désindenter", style = MaterialTheme.typography.labelSmall)
                    }
                }
                TextButton(onClick = { showHelp = !showHelp }) {
                    Text(if (showHelp) "Masquer la syntaxe" else "Voir la syntaxe des cartes")
                }
                if (showHelp) {
                    Text(
                        text = ">> aller   •   << retour   •   <> les deux\n;; descripteur   •   :: concept (les deux)\n{{texte}} trou cloze   •   autre ligne = simple note",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onSaveNote(content) },
                        enabled = dirty
                    ) {
                        Text("Enregistrer")
                    }
                    Button(
                        onClick = { onConvertNotes(content) },
                        enabled = detectedCount > 0
                    ) {
                        Text("Créer les cartes ($detectedCount)")
                    }
                }
                val noteConcepts = remember(content) {
                    com.learnsyncai.domain.usecase.Concepts.extract(content)
                }
                if (noteConcepts.isNotEmpty()) {
                    Text(
                        text = "Concepts liés :",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        noteConcepts.forEach { concept ->
                            AssistChip(
                                onClick = { onOpenConcept(concept) },
                                label = { Text(concept) },
                                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
                // Blocs détectés : 1 tap = 1 carte (éditeur à blocs style RemNote).
                val cardLines = remember(content) {
                    content.lines().map { it.trim() }.filter { it.length >= 4 }
                        .mapNotNull { line ->
                            com.learnsyncai.domain.usecase.NoteCards.parseLine(line)?.let { line to it }
                        }.take(20)
                }
                if (cardLines.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = LearnSyncSpacing.small))
                    Text(
                        text = "Blocs détectés (1 tap = 1 carte) :",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    cardLines.forEach { (line, parsed) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = parsed.question,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2
                                )
                                Text(
                                    text = parsed.answer,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                            IconButton(onClick = { onConvertSingleLine(line) }) {
                                Icon(Icons.Default.Add, contentDescription = "Créer la carte")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Onglet Audio (LazyList) : enveloppe l'éditeur d'enregistrements. */
internal fun LazyListScope.CourseAudioTab(
    courseId: String,
    media: List<CourseMedia>,
    onAddAudio: (String) -> Unit,
    onUpdateTranscript: (CourseMedia, String) -> Unit,
    onDeleteMedia: (CourseMedia) -> Unit,
    onCardsFromTranscript: (String) -> Unit
) {
    item {
        Text(
            text = "Audio du cours",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
    item {
        CourseAudioTabContent(
            courseId = courseId,
            media = media,
            onAddAudio = onAddAudio,
            onUpdateTranscript = onUpdateTranscript,
            onDeleteMedia = onDeleteMedia,
            onCardsFromTranscript = onCardsFromTranscript
        )
    }
}

/** Blocs hiérarchiques : ajoute/retire 2 espaces en tête de la ligne du curseur. */
private fun indentLine(
    field: androidx.compose.ui.text.input.TextFieldValue,
    indent: Boolean
): androidx.compose.ui.text.input.TextFieldValue {
    val text = field.text
    val cursor = field.selection.start.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
    val line = text.substring(lineStart, lineEnd)
    return if (indent) {
        val newText = text.substring(0, lineStart) + "  " + text.substring(lineStart)
        field.copy(text = newText, selection = androidx.compose.ui.text.TextRange(cursor + 2))
    } else {
        val removed = when {
            line.startsWith("  ") -> 2
            line.startsWith(" ") || line.startsWith("\t") -> 1
            else -> 0
        }
        if (removed == 0) return field
        val newText = text.substring(0, lineStart) + line.drop(removed) + text.substring(lineEnd)
        field.copy(text = newText, selection = androidx.compose.ui.text.TextRange((cursor - removed).coerceAtLeast(lineStart)))
    }
}

/**
 * Onglet Audio : enregistrements de cours (MediaRecorder), réécoute,
 * transcription manuelle et cartes IA depuis la transcription.
 */
@Composable
private fun CourseAudioTabContent(
    courseId: String,
    media: List<CourseMedia>,
    onAddAudio: (String) -> Unit,
    onUpdateTranscript: (CourseMedia, String) -> Unit,
    onDeleteMedia: (CourseMedia) -> Unit,
    onCardsFromTranscript: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    var playingId by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var permissionOk by remember { mutableStateOf(false) }
    var pendingPath by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> permissionOk = granted }

    DisposableEffect(Unit) {
        onDispose {
            try { recorder?.release() } catch (_: Exception) { }
            try { player?.release() } catch (_: Exception) { }
        }
    }

    fun stopPlayback() {
        try { player?.stop(); player?.release() } catch (_: Exception) { }
        player = null
        playingId = null
    }

    fun togglePlay(item: CourseMedia) {
        if (playingId == item.id) { stopPlayback(); return }
        stopPlayback()
        try {
            player = android.media.MediaPlayer().apply {
                setDataSource(item.path)
                prepare()
                setOnCompletionListener { stopPlayback() }
                start()
            }
            playingId = item.id
        } catch (_: Exception) { stopPlayback() }
    }

    fun toggleRecord() {
        if (recording) {
            try { recorder?.stop(); recorder?.release() } catch (_: Exception) { }
            recorder = null
            recording = false
            return
        }
        if (!permissionOk) {
            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            return
        }
        try {
            val dir = java.io.File(context.filesDir, "courses/audio/$courseId").apply { mkdirs() }
            val target = java.io.File(dir, "${System.currentTimeMillis()}.m4a")
            recorder = android.media.MediaRecorder().apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                setOutputFile(target.absolutePath)
                prepare()
                start()
            }
            recording = true
            pendingPath = target.absolutePath
        } catch (_: Exception) {
            recording = false
        }
    }

    // Arrêt = sauvegarde du média.
    fun stopAndSave() {
        val path = pendingPath
        try { recorder?.stop(); recorder?.release() } catch (_: Exception) { }
        recorder = null
        recording = false
        pendingPath = null
        if (path != null && java.io.File(path).exists()) onAddAudio(path)
    }

    Column(verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (recording) stopAndSave() else toggleRecord() }) {
                Icon(
                    if (recording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (recording) "Arrêter" else "Enregistrer")
            }
            if (recording) {
                Text("Enregistrement en cours…", style = MaterialTheme.typography.bodySmall, color = RoseError)
            }
        }
        if (media.isEmpty()) {
            Text(
                "Aucun enregistrement : capte ton cours puis transcris-le (à la main ou via l'oral) pour en faire des cartes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        media.forEach { item ->
            var transcript by remember(item.id, item.transcript) { mutableStateOf(item.transcript) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = LearnSyncShapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(LearnSyncSpacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Enregistrement · ${java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(item.createdAt))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row {
                            IconButton(onClick = { togglePlay(item) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    if (playingId == item.id) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = "Écouter",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = { if (playingId == item.id) stopPlayback(); onDeleteMedia(item) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    OutlinedTextField(
                        value = transcript,
                        onValueChange = { transcript = it },
                        label = { Text("Transcription") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onUpdateTranscript(item, transcript) },
                            enabled = transcript != item.transcript
                        ) {
                            Text("Enregistrer le texte", style = MaterialTheme.typography.labelSmall)
                        }
                        if (transcript.trim().length >= 30) {
                            OutlinedButton(onClick = { onCardsFromTranscript(transcript) }) {
                                Text("Cartes IA", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

