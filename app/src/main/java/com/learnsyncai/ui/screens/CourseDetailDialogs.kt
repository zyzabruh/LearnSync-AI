package com.learnsyncai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.learnsyncai.ui.theme.*

/** Dialog de création manuelle d'une flashcard ({{…}} = trou cloze). */
@Composable
internal fun AddFlashcardDialog(
    onDismiss: () -> Unit,
    onConfirm: (question: String, answer: String, explanation: String, direction: String, typeAnswer: Boolean) -> Unit
) {
    var cardQuestion by remember { mutableStateOf("") }
    var cardAnswer by remember { mutableStateOf("") }
    var cardExplanation by remember { mutableStateOf("") }
    var cardDirection by remember { mutableStateOf("forward") }
    var cardTypeAnswer by remember { mutableStateOf(false) }

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
                    label = { Text("Question * ({{…}} = trou)") },
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("forward" to "Aller", "reverse" to "Retour", "both" to "Les deux").forEach { (value, label) ->
                        FilterChip(
                            selected = cardDirection == value,
                            onClick = { cardDirection = value },
                            label = { Text(label) }
                        )
                    }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(
                        checked = cardTypeAnswer,
                        onCheckedChange = { cardTypeAnswer = it }
                    )
                    Text("Taper la réponse")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (cardQuestion.isNotBlank() && cardAnswer.isNotBlank()) {
                        onConfirm(cardQuestion, cardAnswer, cardExplanation, cardDirection, cardTypeAnswer)
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

/** Dialog de création d'une carte image / occlusion depuis la galerie. */
@Composable
internal fun AddImageCardDialog(
    onDismiss: () -> Unit,
    onConfirm: (uri: android.net.Uri, answer: String, maskX: Float, maskY: Float, maskW: Float, maskH: Float) -> Unit
) {
    var pickedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var answer by remember { mutableStateOf("") }
    var occlusion by remember { mutableStateOf(false) }
    var maskX by remember { mutableFloatStateOf(0.25f) }
    var maskY by remember { mutableFloatStateOf(0.25f) }
    var maskW by remember { mutableFloatStateOf(0.5f) }
    var maskH by remember { mutableFloatStateOf(0.3f) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? -> if (uri != null) pickedUri = uri }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Carte image", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small)
            ) {
                Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (pickedUri == null) "Choisir une image" else "Changer d'image")
                }
                pickedUri?.let { uri ->
                    val preview = remember(uri) {
                        try {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                android.graphics.BitmapFactory.decodeStream(input)
                            }
                        } catch (_: Exception) { null }
                    }
                    preview?.let {
                        androidx.compose.foundation.Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Aperçu",
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                }
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text("Réponse * (ex. nom de l'élément)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = occlusion, onCheckedChange = { occlusion = it })
                    Text("Masquer une zone (occlusion)", style = MaterialTheme.typography.bodySmall)
                }
                if (occlusion) {
                    listOf("X" to maskX, "Y" to maskY, "Largeur" to maskW, "Hauteur" to maskH).forEach { (label, value) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(64.dp))
                            Slider(
                                value = value,
                                onValueChange = {
                                    when (label) {
                                        "X" -> maskX = it
                                        "Y" -> maskY = it
                                        "Largeur" -> maskW = it
                                        else -> maskH = it
                                    }
                                },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val uri = pickedUri
                    if (uri != null && answer.isNotBlank()) {
                        onConfirm(
                            uri, answer,
                            if (occlusion) maskX else 0f, if (occlusion) maskY else 0f,
                            if (occlusion) maskW else 0f, if (occlusion) maskH else 0f
                        )
                        onDismiss()
                    }
                },
                enabled = pickedUri != null && answer.isNotBlank()
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
