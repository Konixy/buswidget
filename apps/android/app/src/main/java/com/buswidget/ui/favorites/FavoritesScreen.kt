package com.buswidget.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.buswidget.data.local.FavoriteStop
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onNavigateToDepartures: (stopId: String, stopName: String) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mes favoris") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    ) { padding ->
        if (favorites.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Star, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Text("Aucun favori", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Ajoutez des arrêts depuis l'onglet Recherche",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            var internalFavorites by remember(favorites) { mutableStateOf(favorites) }
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
                internalFavorites = internalFavorites.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                }
            }

            LazyColumn(
                modifier = Modifier.padding(padding),
                state = listState,
            ) {
                items(internalFavorites, key = { it.stop.id }) { favorite ->
                    ReorderableItem(reorderableState, key = favorite.stop.id) { isDragging ->
                        val elevation = if (isDragging) 8.dp else 0.dp
                        Surface(
                            shadowElevation = elevation,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                FavoriteRow(
                                    favorite = favorite,
                                    onClick = { onNavigateToDepartures(favorite.stop.id, favorite.stop.name) },
                                    onDelete = { viewModel.remove(favorite.stop.id) },
                                    trailingIcon = {
                                        Icon(
                                            painter = painterResource(id = android.R.drawable.ic_menu_sort_by_size),
                                            contentDescription = "Réorganiser",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier
                                                .draggableHandle()
                                                .padding(8.dp)
                                                .size(24.dp)
                                        )
                                    }
                                )
                                if (!isDragging) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Mettre à jour la base de données après un drag and drop
            LaunchedEffect(internalFavorites) {
                if (internalFavorites != favorites) {
                    viewModel.onReorder(internalFavorites)
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    favorite: FavoriteStop,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                favorite.stop.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (favorite.stop.transportModes.isNotEmpty()) {
                Text(
                    favorite.stop.transportModes.joinToString(" | "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (favorite.selectedLines.isEmpty()) {
                Text(
                    "Toutes les lignes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Lignes : ${favorite.selectedLines.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Supprimer",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            if (trailingIcon != null) {
                trailingIcon()
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Retirer le favori ?") },
            text = { Text("\"${favorite.stop.name}\" sera retiré de vos favoris.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Annuler") }
            },
        )
    }
}
