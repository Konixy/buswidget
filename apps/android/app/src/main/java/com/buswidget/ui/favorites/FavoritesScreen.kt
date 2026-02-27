package com.buswidget.ui.favorites

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
                                imageVector = Icons.Rounded.Star,
                                contentDescription = "Logo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(6.dp).size(28.dp)
                            )
                        }
                        Text("Mes favoris", fontWeight = FontWeight.ExtraBold)
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
                modifier = Modifier.padding(padding).fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(internalFavorites, key = { it.stop.id }) { favorite ->
                    ReorderableItem(reorderableState, key = favorite.stop.id) { isDragging ->
                        val elevation = if (isDragging) 8.dp else 2.dp
                        val scale = if (isDragging) 1.02f else 1f

                        Card(
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.extraLarge,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .scale(scale)
                        ) {
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
                                            .padding(12.dp)
                                            .size(28.dp)
                                    )
                                }
                            )
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
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            if (favorite.stop.transportModes.isNotEmpty()) {
                Text(
                    favorite.stop.transportModes.joinToString(" • "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (favorite.selectedLines.isEmpty()) {
                Text(
                    "Toutes lignes actives",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            } else {
                Text(
                    "Lignes filtrées : ${favorite.selectedLines.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
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
