package com.buswidget.ui.departures

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.buswidget.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.buswidget.data.local.Departure
import com.buswidget.ui.favorites.FavoriteOptionsSheet
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeparturesScreen(
    stopName: String,
    onBack: () -> Unit,
    viewModel: DeparturesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val favoriteSetupStop by viewModel.favoriteSetupStop.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState !is DeparturesUiState.Loading) {
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            if (!isRefreshing) {
                viewModel.refresh()
            }
        }
    }

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
                                imageVector = Icons.Outlined.Place,
                                contentDescription = "Lieu",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(6.dp).size(28.dp)
                            )
                        }
                        Text(stopName, maxLines = 1, fontWeight = FontWeight.ExtraBold) 
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::onFavoriteTap) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Favori",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
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
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.refresh()
            },
            modifier = Modifier.padding(padding),
        ) {
            when (val state = uiState) {
                is DeparturesUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is DeparturesUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Schedule, null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Text("Erreur", style = MaterialTheme.typography.titleMedium)
                            Text(state.message, style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = viewModel::refresh) { Text("Réessayer") }
                        }
                    }
                }
                is DeparturesUiState.Success -> {
                    LazyColumn(Modifier.fillMaxSize()) {
                        // Metadata
                        item {
                            MetadataCard(state)
                        }

                        if (state.departures.isEmpty()) {
                            // ... Aucun changement pour la vue vide ...
                            item {
                                Box(
                                    Modifier.fillParentMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Schedule, null,
                                            modifier = Modifier.size(64.dp),
                                            tint = MaterialTheme.colorScheme.outline,
                                        )
                                        Text("Aucun départ imminent", style = MaterialTheme.typography.titleMedium)
                                        Text("Tirez pour rafraîchir", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        } else {
                            items(state.departures, key = { it.departureIso + it.line }) { departure ->
                                Card(
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = MaterialTheme.shapes.extraLarge,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    DepartureRow(departure)
                                }
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
    }

    favoriteSetupStop?.let { stop ->
        // On récupère les lignes initiales si déjà favori, sinon on propose "tout"
        val state = uiState as? DeparturesUiState.Success
        val initialLines = state?.preferredLines ?: emptySet()
        
        FavoriteOptionsSheet(
            stop = stop,
            initialSelectedLines = initialLines,
            isAlreadyFavorite = isFavorite,
            onDismiss = viewModel::dismissFavoriteSetup,
            onSave = { lines -> viewModel.saveFavorite(stop, lines) },
            onRemove = { viewModel.removeFavorite(stop.id) },
        )
    }
}

@Composable
private fun MetadataCard(state: DeparturesUiState.Success) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.FRANCE) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Mis à jour à ${timeFormat.format(Date(state.lastUpdated))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.preferredLines.isNotEmpty()) {
                Text(
                    "Lignes filtrées : ${state.preferredLines.sorted().joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Icône 🛜 animée = horaires en direct",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DepartureRow(departure: Departure) {
    var currentMinutes by remember(departure.departureUnix) { mutableStateOf(departure.minutesUntilDeparture) }
    
    LaunchedEffect(departure.departureUnix) {
        while (true) {
            val nowSecs = System.currentTimeMillis() / 1000
            val diffSecs = departure.departureUnix - nowSecs
            val newMinutes = kotlin.math.max(0, (diffSecs / 60).toInt())
            if (currentMinutes != newMinutes) {
                currentMinutes = newMinutes
            }
            kotlinx.coroutines.delay(5000) // On vérifie l'heure toutes les 5s
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val badgeColor = remember(departure.lineColor) {
        val hex = departure.lineColor
        if (hex != null) {
            try {
                androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
            } catch (e: Exception) {
                null
            }
        } else null
    } ?: primaryColor

    val textColor = remember(badgeColor) {
        if (badgeColor == primaryColor) {
            onPrimaryColor
        } else if (badgeColor.luminance() > 0.5f) {
            androidx.compose.ui.graphics.Color.Black
        } else {
            androidx.compose.ui.graphics.Color.White
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val realtimeAlpha by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2000
                1.0f at 0
                1.0f at 999
                0.3f at 1000
                0.3f at 2000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Badge ligne
        Surface(
            shape = MaterialTheme.shapes.small,
            color = badgeColor,
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 32.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                Text(
                    departure.line,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Destination
        Column(modifier = Modifier.weight(1f)) {
            Text(
                departure.destination,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            val timeStr = formatDepartureTime(departure.departureIso)
            Text(
                timeStr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(8.dp))

        // Icône Pulsante si Temps Réel
        if (departure.isRealtime) {
            Icon(
                painter = painterResource(id = R.drawable.rss_feed_24),
                contentDescription = "Temps réel",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(20.dp)
                    .alpha(realtimeAlpha) // Applique l'animation de respiration
            )
        }

        Spacer(Modifier.width(8.dp))

        // Minutes
        Text(
            "$currentMinutes min",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                currentMinutes <= 2 -> MaterialTheme.colorScheme.error
                currentMinutes <= 5 -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

private fun formatDepartureTime(isoStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        val date = sdf.parse(isoStr) ?: return isoStr
        SimpleDateFormat("HH:mm", Locale.FRANCE).format(date)
    } catch (e: Exception) {
        isoStr
    }
}
