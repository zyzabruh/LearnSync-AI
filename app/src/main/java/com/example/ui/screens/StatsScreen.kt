package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.Flashcard
import com.example.domain.model.ReviewLog
import com.example.domain.usecase.SpacedRepetition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(reviewLogs: List<ReviewLog>, allFlashcards: List<Flashcard>) {
    val streak = SpacedRepetition.calculateStreak(reviewLogs)
    val totalReviews = reviewLogs.size
    val totalCards = allFlashcards.size
    val masteredCards = allFlashcards.count { it.box >= 4 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistiques", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatCard(modifier = Modifier.weight(1f), title = "Streak actuel", value = "$streak jours", icon = Icons.Default.BarChart)
                    StatCard(modifier = Modifier.weight(1f), title = "Total révisions", value = "$totalReviews", icon = Icons.Default.BarChart)
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatCard(modifier = Modifier.weight(1f), title = "Cartes totales", value = "$totalCards", icon = Icons.Default.BarChart)
                    StatCard(modifier = Modifier.weight(1f), title = "Cartes maîtrisées", value = "$masteredCards", icon = Icons.Default.BarChart)
                }
            }
        }
    }
}
