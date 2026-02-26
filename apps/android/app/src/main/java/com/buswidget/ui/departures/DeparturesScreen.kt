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
                "RT = temps réel  •  SCH = horaire prévu",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DepartureRow(departure: Departure) {
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
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(width = 48.dp, height = 32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    departure.line,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimary,
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

        // RT / SCH badge
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (departure.isRealtime) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ) {
            Text(
                if (departure.isRealtime) "RT" else "SCH",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (departure.isRealtime) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        Spacer(Modifier.width(8.dp))

        // Minutes
        Text(
            "${departure.minutesUntilDeparture} min",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                departure.minutesUntilDeparture <= 2 -> MaterialTheme.colorScheme.error
                departure.minutesUntilDeparture <= 5 -> MaterialTheme.colorScheme.primary
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
