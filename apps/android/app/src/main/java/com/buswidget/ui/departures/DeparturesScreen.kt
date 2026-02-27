package com.buswidget.ui.departures

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha
import com.buswidget.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.buswidget.data.local.Departure
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stopName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
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
                                DepartureRow(departure)
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataCard(state: DeparturesUiState.Success) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.FRANCE) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
