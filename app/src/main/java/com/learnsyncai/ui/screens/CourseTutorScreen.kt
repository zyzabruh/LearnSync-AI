package com.learnsyncai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.learnsyncai.ui.components.*
import com.learnsyncai.ui.theme.*
import com.learnsyncai.ui.viewmodels.TutorMessage
import kotlinx.coroutines.launch

/**
 * Tuteur IA scopé au cours : questions libres, explications, interrogation,
 * création de carte depuis une réponse.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseTutorScreen(
    courseTitle: String,
    messages: List<TutorMessage>,
    sending: Boolean,
    error: String?,
    onSend: (String) -> Unit,
    onCreateCard: (question: String, answer: String) -> Unit,
    onClearError: () -> Unit,
    onBackClick: () -> Unit,
    initialInput: String = ""
) {
    var input by remember(initialInput) { mutableStateOf(initialInput) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) scope.launch { listState.animateScrollToItem(messages.size - 1) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tuteur IA · $courseTitle", maxLines = 1, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = LearnSyncSpacing.large)
        ) {
            if (messages.isEmpty()) {
                Text(
                    text = "Pose une question sur ton cours : explication, interrogation, résumé d'un point précis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = LearnSyncSpacing.medium)
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(LearnSyncSpacing.small),
                contentPadding = PaddingValues(vertical = LearnSyncSpacing.small)
            ) {
                items(messages, key = { it.hashCode().toString() + it.text.take(20) }) { msg ->
                    val isUser = msg.role == TutorMessage.USER
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Card(
                            shape = LearnSyncShapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                            ),
                            border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth(0.88f)
                        ) {
                            Column(modifier = Modifier.padding(LearnSyncSpacing.medium)) {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                if (!isUser) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    TextButton(
                                        onClick = {
                                            val idx = messages.indexOf(msg)
                                            val q = messages.take(idx).lastOrNull { it.role == TutorMessage.USER }?.text
                                                ?: "Point du cours"
                                            onCreateCard(q, msg.text)
                                            feedback = "Carte créée !"
                                        },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Créer une carte", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
                if (sending) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Le tuteur réfléchit…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            feedback?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = EmeraldDark, modifier = Modifier.padding(bottom = 4.dp))
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 4.dp))
                LaunchedEffect(it) { onClearError() }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            ) {
                listOf(
                    "Explique simplement" to "Explique-moi ce point simplement, comme si j'avais 12 ans.",
                    "Interroge-moi" to "Pose-moi une question sur ce cours pour tester ma compréhension.",
                    "Plan de révision" to "Propose-moi un plan de révision efficace pour ce cours."
                ).forEach { (label, prompt) ->
                    FilterChip(
                        selected = false,
                        onClick = { if (!sending) onSend(prompt) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = LearnSyncSpacing.large)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; feedback = null },
                    label = { Text("Ta question…") },
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (input.isNotBlank() && !sending) {
                            onSend(input)
                            input = ""
                            feedback = null
                        }
                    },
                    enabled = input.isNotBlank() && !sending
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Envoyer", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
