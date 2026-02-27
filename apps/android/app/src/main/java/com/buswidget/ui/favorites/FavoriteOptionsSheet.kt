package com.buswidget.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buswidget.data.local.StopInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteOptionsSheet(
    stop: StopInfo,
    initialSelectedLines: Set<String>,
    isAlreadyFavorite: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
    onRemove: () -> Unit,
) {
    var selectedLines by remember(stop.id) { mutableStateOf(initialSelectedLines) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Annuler") }
                Text(
                    "Options favori",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = {
                    onSave(selectedLines.sorted())
                }) { Text("Enregistrer") }
            }

            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Stop info
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(stop.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stop.modeSummary(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Line selection
            if (stop.lineHints.isNotEmpty()) {
                Text(
                    "Filtrer par lignes",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Laissez vide pour toutes les lignes",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))

                // All lines option
                LineSelectionRow(
                    title = "Toutes les lignes",
                    isSelected = selectedLines.isEmpty(),
                    onClick = { selectedLines = emptySet() },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                stop.lineHints.forEach { line ->
                    LineSelectionRow(
                        title = line,
                        isSelected = selectedLines.contains(line),
                        onClick = {
                            selectedLines = if (selectedLines.contains(line)) {
                                selectedLines - line
                            } else {
                                selectedLines + line
                            }
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            } else {
                Text(
                    "Aucune ligne spécifique trouvée. Toutes les directions seront affichées.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isAlreadyFavorite) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Retirer des favoris")
                }
            }
        }
    }
}

@Composable
private fun LineSelectionRow(title: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

internal fun StopInfo.modeSummary(): String {
    val modes = transportModes.toMutableList()
    if (locationType == 1) modes.add("Station")
    return if (modes.isEmpty()) "Mode inconnu" else modes.joinToString(" | ")
}

