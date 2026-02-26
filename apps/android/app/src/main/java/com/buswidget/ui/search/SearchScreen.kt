package com.buswidget.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tram
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.buswidget.data.local.StopInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToDepartures: (stopId: String, stopName: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val favorites by viewModel.favoritesFlow.collectAsState()
    val favoriteSetupStop by viewModel.favoriteSetupStop.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Arrêts Rouen") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Search bar
            SearchBar(query = query, onQueryChange = viewModel::onQueryChange)

            when (val state = uiState) {
                is SearchUiState.Idle -> EmptyState("Tapez au moins 2 caractères", Icons.Outlined.Tram)
                is SearchUiState.Loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                is SearchUiState.Error -> EmptyState("Erreur : ${state.message}", Icons.Default.Search)
                is SearchUiState.Success -> {
                    if (state.results.isEmpty()) {
                        EmptyState("Aucun arrêt trouvé", Icons.Outlined.Tram)
                    } else {
                        LazyColumn {
                            items(state.results, key = { it.id }) { stop ->
                                val isFavorite = favorites.any { it.stop.id == stop.id }
                                StopSearchRow(
                                    stop = stop,
                                    isFavorite = isFavorite,
                                    onClick = { onNavigateToDepartures(stop.id, stop.name) },
                                    onFavoriteTap = { viewModel.onFavoriteTap(stop) },
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Sheet de configuration favori
    favoriteSetupStop?.let { stop ->
        val currentLines = viewModel.getSelectedLines(stop.id)
        val isAlreadyFavorite = viewModel.isFavorite(stop.id)
        FavoriteOptionsSheet(
            stop = stop,
            initialSelectedLines = currentLines.toSet(),
            isAlreadyFavorite = isAlreadyFavorite,
            onDismiss = viewModel::dismissFavoriteSetup,
            onSave = { lines -> viewModel.saveFavorite(stop, lines) },
            onRemove = { viewModel.removeFavorite(stop.id) },
        )
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        placeholder = { Text("Nom d'arrêt, ligne ou ID…") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun StopSearchRow(
    stop: StopInfo,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stop.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stop.modeSummary(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (stop.lineHints.isNotEmpty()) {
                Text(
                    text = "Lignes : ${stop.lineHints.take(4).joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stop.stopLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        IconButton(onClick = onFavoriteTap) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoriteOptionsSheet(
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

@Composable
private fun EmptyState(message: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
            Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun StopInfo.modeSummary(): String {
    val modes = transportModes.toMutableList()
    if (locationType == 1) modes.add("Station")
    return if (modes.isEmpty()) "Mode inconnu" else modes.joinToString(" | ")
}

private fun StopInfo.stopLabel(): String {
    stopCode?.takeIf { it.isNotEmpty() }?.let { return "Code $it" }
    return id.split(":").lastOrNull() ?: id
}
