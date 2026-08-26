package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Flashcard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    allFlashcards: List<Flashcard>,
    onSyncCalendar: () -> Unit
) {
    val today = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)

    val groupedCards = allFlashcards.groupBy { card ->
        Instant.ofEpochMilli(card.dueDate).atZone(ZoneId.systemDefault()).toLocalDate()
    }.toSortedMap()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendrier des Révisions", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSyncCalendar) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Calendrier")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { innerPadding ->
        if (allFlashcards.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Aucune révision planifiée", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Générez du matériel depuis vos cours pour alimenter votre calendrier de répétition espacée.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Synchronisation Système", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Ajoutez ces sessions à votre calendrier Android pour recevoir des alarmes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Button(
                                onClick = onSyncCalendar,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Sync")
                            }
                        }
                    }
                }

                items(groupedCards.entries.toList()) { (date, cards) ->
                    val isToday = date == today
                    val isPast = date.isBefore(today)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                isPast -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val label = when {
                                    isToday -> "Aujourd'hui (${date.format(formatter)})"
                                    isPast -> "En retard (${date.format(formatter)})"
                                    date == today.plusDays(1) -> "Demain (${date.format(formatter)})"
                                    else -> date.format(formatter)
                                }
                                Text(
                                    text = label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = when {
                                        isToday -> MaterialTheme.colorScheme.primary
                                        isPast -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${cards.size} flashcards à réviser",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BadgeContainer(
                                icon = Icons.Default.Event,
                                label = "${cards.size} cartes"
                            )
                        }
                    }
                }
            }
        }
    }
}
