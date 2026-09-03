package com.learnsyncai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.learnsyncai.ui.theme.*

/** Dialog de création manuelle d'une flashcard. */
@Composable
internal fun AddFlashcardDialog(
    onDismiss: () -> Unit,
    onConfirm: (question: String, answer: String, explanation: String) -> Unit
) {
    var cardQuestion by remember { mutableStateOf("") }
    var cardAnswer by remember { mutableStateOf("") }
    var cardExplanation by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle Flashcard", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.medium)
            ) {
                OutlinedTextField(
                    value = cardQuestion,
                    onValueChange = { cardQuestion = it },
                    label = { Text("Question *") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cardAnswer,
                    onValueChange = { cardAnswer = it },
                    label = { Text("Réponse *") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cardExplanation,
                    onValueChange = { cardExplanation = it },
                    label = { Text("Explication / Astuce (facultatif)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (cardQuestion.isNotBlank() && cardAnswer.isNotBlank()) {
                        onConfirm(cardQuestion, cardAnswer, cardExplanation)
                        onDismiss()
                    }
                },
                enabled = cardQuestion.isNotBlank() && cardAnswer.isNotBlank()
            ) {
                Text("Ajouter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        },
        shape = LearnSyncShapes.large
    )
}

/** Dialog de création manuelle d'une question de QCM (4 options distinctes). */
@Composable
internal fun AddQuizQuestionDialog(
    onDismiss: () -> Unit,
    onConfirm: (question: String, options: List<String>, correctAnswer: String, explanation: String) -> Unit
) {
    var quizQuestionText by remember { mutableStateOf("") }
    var optA by remember { mutableStateOf("") }
    var optB by remember { mutableStateOf("") }
    var optC by remember { mutableStateOf("") }
    var optD by remember { mutableStateOf("") }
    var correctOptionIndex by remember { mutableIntStateOf(0) }
    var quizExplanation by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouveau QCM", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
            ) {
                OutlinedTextField(
                    value = quizQuestionText,
                    onValueChange = { quizQuestionText = it },
                    label = { Text("Question *") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = optA,
                    onValueChange = { optA = it },
                    label = { Text("Option A *") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = optB,
                    onValueChange = { optB = it },
                    label = { Text("Option B *") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = optC,
                    onValueChange = { optC = it },
                    label = { Text("Option C *") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = optD,
                    onValueChange = { optD = it },
                    label = { Text("Option D *") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Bonne réponse :",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    listOf("A", "B", "C", "D").forEachIndexed { index, label ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(
                                selected = correctOptionIndex == index,
                                onClick = { correctOptionIndex = index }
                            )
                            Text(label)
                        }
                    }
                }

                OutlinedTextField(
                    value = quizExplanation,
                    onValueChange = { quizExplanation = it },
                    label = { Text("Explication (facultatif)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val opts = listOf(optA, optB, optC, optD)
            val isValid = quizQuestionText.isNotBlank() && opts.all { it.isNotBlank() } && opts.distinct().size == 4
            Button(
                onClick = {
                    if (isValid) {
                        val correctAnswer = opts[correctOptionIndex]
                        onConfirm(quizQuestionText, opts, correctAnswer, quizExplanation)
                        onDismiss()
                    }
                },
                enabled = isValid
            ) {
                Text("Ajouter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        },
        shape = LearnSyncShapes.large
    )
}

/** Dialog d'édition de la synthèse du cours. */
@Composable
internal fun EditSummaryDialog(
    initialSummary: String,
    onDismiss: () -> Unit,
    onConfirm: (summary: String) -> Unit
) {
    var summaryText by remember { mutableStateOf(initialSummary) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Synthèse du cours", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = summaryText,
                onValueChange = { summaryText = it },
                label = { Text("Texte du résumé") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                maxLines = 15
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(summaryText)
                    onDismiss()
                }
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        },
        shape = LearnSyncShapes.large
    )
}

/** Dialog d'ajout d'une notion clé. */
@Composable
internal fun AddKeyPointDialog(
    onDismiss: () -> Unit,
    onConfirm: (point: String) -> Unit
) {
    var keyPointText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle notion clé", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = keyPointText,
                onValueChange = { keyPointText = it },
                label = { Text("Point d'ancrage / Notion essentielle *") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (keyPointText.isNotBlank()) {
                        onConfirm(keyPointText)
                        onDismiss()
                    }
                },
                enabled = keyPointText.isNotBlank()
            ) {
                Text("Ajouter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        },
        shape = LearnSyncShapes.large
    )
}

/** Dialog de confirmation de suppression du cours. */
@Composable
internal fun DeleteCourseConfirmDialog(
    courseTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supprimer ce cours ?", fontWeight = FontWeight.Bold) },
        text = { Text("Cette action supprimera définitivement le cours « $courseTitle » ainsi que toutes ses flashcards et QCMs.") },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoseError,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Supprimer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        },
        shape = LearnSyncShapes.large
    )
}
