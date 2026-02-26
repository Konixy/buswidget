package com.buswidget.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.*
import androidx.glance.text.*
import com.buswidget.MainActivity
import com.buswidget.data.local.Departure
import com.squareup.moshi.Moshi
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BusWidgetContent() {
    val context = LocalContext.current
    val prefs = currentState<Preferences>()
    val moshi = remember { Moshi.Builder().build() }

    val rawJson = prefs[BusGlanceWidget.WIDGET_DATA_KEY]
    val data: WidgetData = rawJson?.let { WidgetData.fromJson(it, moshi) }
        ?: WidgetData.noFavorite()

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.FRANCE) }
    val updatedTime = timeFormat.format(Date(data.updatedAtMs))

    GlanceTheme {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .clickable(actionStartActivity<MainActivity>())
                .padding(12.dp),
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
            ) {
                // Header : nom de l'arrêt
                Text(
                    text = data.stopName,
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )

                Spacer(GlanceModifier.height(4.dp))

                if (data.errorMessage != null) {
                    // Message d'erreur
                    Text(
                        text = data.errorMessage,
                        style = TextStyle(
                            color = GlanceTheme.colors.secondary,
                            fontSize = 11.sp,
                        ),
                        maxLines = 2,
                    )
                } else if (data.departures.isEmpty()) {
                    Text(
                        text = "Aucun départ imminent",
                        style = TextStyle(
                            color = GlanceTheme.colors.secondary,
                            fontSize = 11.sp,
                        ),
                    )
                } else {
                    // Liste des départs (max 3)
                    data.departures.take(3).forEach { departure ->
                        DepartureGlanceRow(departure)
                        Spacer(GlanceModifier.height(4.dp))
                    }
                }

                // Spacer pour pousser le footer en bas
                Spacer(GlanceModifier.defaultWeight())

                // Footer : heure de mise à jour
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Mis à jour $updatedTime",
                        style = TextStyle(
                            color = GlanceTheme.colors.secondary,
                            fontSize = 9.sp,
                        ),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    // Bouton refresh
                    Box(
                    modifier = GlanceModifier
                        .padding(4.dp)
                        .clickable(actionRunCallback<RefreshWidgetCallback>())
                ) {
                    Image(
                        provider = ImageProvider(android.R.drawable.ic_menu_rotate),
                        contentDescription = "Rafraîchir",
                        modifier = GlanceModifier.size(20.dp),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
                    )
                }
                }
            }
        }
    }
}

@Composable
private fun DepartureGlanceRow(departure: Departure) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Badge ligne
        Box(
            modifier = GlanceModifier
                .width(36.dp)
                .height(22.dp)
                .background(GlanceTheme.colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = departure.line,
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }

        Spacer(GlanceModifier.width(6.dp))

        // Destination
        Text(
            text = departure.destination,
            style = TextStyle(
                color = GlanceTheme.colors.onBackground,
                fontSize = 11.sp,
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )

        Spacer(GlanceModifier.width(4.dp))

        // RT/SCH badge
        Text(
            text = if (departure.isRealtime) "RT" else "SCH",
            style = TextStyle(
                color = if (departure.isRealtime) GlanceTheme.colors.primary else GlanceTheme.colors.secondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        Spacer(GlanceModifier.width(4.dp))

        // Minutes
        Text(
            text = "${departure.minutesUntilDeparture}m",
            style = TextStyle(
                color = GlanceTheme.colors.onBackground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}
