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
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.*
import com.buswidget.MainActivity
import android.content.Intent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import com.buswidget.widget.WidgetConfigurationActivity
import com.buswidget.R
import com.buswidget.data.local.Departure
import com.buswidget.di.WidgetEntryPoint
import com.squareup.moshi.Moshi
import dagger.hilt.android.EntryPointAccessors
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.graphics.luminance

@Composable
fun BusWidgetContent() {
    val context = LocalContext.current
    val prefs = currentState<Preferences>()
    val moshi = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        ).moshi()
    }

    val rawJson = prefs[BusGlanceWidget.WIDGET_DATA_KEY]
    val data: WidgetData = rawJson?.let { WidgetData.fromJson(it, moshi) }
        ?: WidgetData.noFavorite()

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.FRANCE) }
    val updatedTime = timeFormat.format(Date(data.updatedAtMs))

    GlanceTheme {
        val glanceId = LocalGlanceId.current
        
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .padding(12.dp),
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
            ) {
                // Header : nom de l'arrêt
                Row(
                    modifier = GlanceModifier.fillMaxWidth()
                        .clickable(actionRunCallback<ConfigWidgetCallback>()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = data.stopName,
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Image(
                        provider = ImageProvider(android.R.drawable.ic_menu_edit),
                        contentDescription = "Éditer",
                        modifier = GlanceModifier.size(16.dp).padding(start = 4.dp),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onBackground)
                    )
                }

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
                    // Calcul discret basé sur le nombre de cellules (documentation Android)
                    val size = LocalSize.current
                    val maxDeparturesCount = when {
                        size.height < 150.dp -> 3 // Hauteur ~1 cellule
                        size.height < 300.dp -> 6 // Hauteur ~2 cellules
                        else -> 10 // Hauteur ~4 cellules ou plus
                    }

                    // Liste des départs adaptés à la grille du smartphone
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        data.departures.take(maxDeparturesCount).forEach { departure ->
                            DepartureGlanceRow(departure)
                            Spacer(GlanceModifier.height(4.dp))
                        }
                    }
                }

                // Footer : heure de mise à jour (fixé en bas)
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
    val badgeColorHex = departure.lineColor
    val parsedColor = remember(badgeColorHex) {
        if (badgeColorHex != null) {
            try {
                androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(badgeColorHex))
            } catch (e: Exception) {
                null
            }
        } else null
    }

    val badgeColorProvider = parsedColor?.let { androidx.glance.color.ColorProvider(day = it, night = it) } ?: GlanceTheme.colors.primary

    val textColorProvider = remember(parsedColor) {
        parsedColor?.let {
            if (it.luminance() > 0.5f) {
                androidx.glance.color.ColorProvider(day = androidx.compose.ui.graphics.Color.Black, night = androidx.compose.ui.graphics.Color.Black)
            } else {
                androidx.glance.color.ColorProvider(day = androidx.compose.ui.graphics.Color.White, night = androidx.compose.ui.graphics.Color.White)
            }
        }
    } ?: GlanceTheme.colors.onPrimary

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Badge ligne
        Box(
            modifier = GlanceModifier
                .width(36.dp)
                .height(22.dp)
                .background(
                    imageProvider = ImageProvider(R.drawable.rounded_bg),
                    colorFilter = ColorFilter.tint(badgeColorProvider)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = departure.line,
                style = TextStyle(
                    color = textColorProvider,
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

        // RT icône (seulement si temps réel)
        if (departure.isRealtime) {
            Image(
                provider = ImageProvider(R.drawable.rss_feed_24),
                contentDescription = "Temps réel",
                modifier = GlanceModifier.size(14.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
            )
        } else {
             Spacer(GlanceModifier.width(14.dp))
        }

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
