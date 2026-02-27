package com.buswidget.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tram
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.buswidget.data.local.StopInfo
import com.buswidget.ui.favorites.FavoriteOptionsSheet

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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DirectionsBus,
                                contentDescription = "Logo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(6.dp).size(28.dp)
                            )
                        }
                        Text("Arrêts du Réseau Astuce", fontWeight = FontWeight.ExtraBold)
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior
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
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(state.results, key = { it.id }) { stop ->
                                val isFavorite = favorites.any { it.stop.id == stop.id }
                                Card(
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = MaterialTheme.shapes.extraLarge,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    StopSearchRow(
                                        stop = stop,
                                        isFavorite = isFavorite,
                                        onClick = { onNavigateToDepartures(stop.id, stop.name) },
                                        onFavoriteTap = { viewModel.onFavoriteTap(stop) },
                                    )
                                }
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
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        placeholder = { Text("Nom d'arrêt, ligne ou ID...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        )
    )
    Spacer(modifier = Modifier.height(12.dp))
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



@Composable
private fun EmptyState(message: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
            Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun StopInfo.modeSummary(): String {
    val modes = transportModes.toMutableList()
    if (locationType == 1) modes.add("Station")
    return if (modes.isEmpty()) "Mode inconnu" else modes.joinToString(" | ")
}

internal fun StopInfo.stopLabel(): String {
    stopCode?.takeIf { it.isNotEmpty() }?.let { return "Code $it" }
    return id.split(":").lastOrNull() ?: id
}
