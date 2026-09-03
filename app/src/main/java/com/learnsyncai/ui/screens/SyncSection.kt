package com.learnsyncai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.learnsyncai.ui.theme.LearnSyncShapes
import com.learnsyncai.ui.theme.LearnSyncSpacing
import com.learnsyncai.domain.model.SyncStatus

/** Carte « Synchronisation » : Google Agenda et Cloud Firestore. */
@Composable
internal fun SyncSection(
    onSyncCloud: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    syncStatus: SyncStatus = SyncStatus(),
    periodicSyncEnabled: Boolean = false,
    onUpdatePeriodicSync: (Boolean) -> Unit = {}
) {
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
            ProfileSectionTitle(icon = Icons.Default.CloudSync, title = "Synchronisation")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Google Agenda",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Planifier les sessions de révision dans le calendrier",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(onClick = onNavigateToCalendar) {
                    Text("Voir")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Synchronisation quotidienne",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (syncStatus.pending) "Synchronisation en attente" else syncStatus.lastSyncAt?.let { "Dernière synchronisation : ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))}" } ?: "Aucune synchronisation réussie",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (syncStatus.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = periodicSyncEnabled,
                    onCheckedChange = onUpdatePeriodicSync
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cloud Firestore",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Sauvegarder et synchroniser les cours",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(onClick = onSyncCloud) {
                    Text("Sync")
                }
            }
        }
    }
}
