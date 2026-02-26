package com.buswidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.datastore.preferences.core.stringPreferencesKey
import com.buswidget.data.api.BusWidgetApi
import com.buswidget.data.api.toStopDeparturesResponse
import com.buswidget.data.local.Departure
import com.buswidget.data.local.FavoritesStore
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

class BusGlanceWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    companion object {
        val STOP_ID_KEY = stringPreferencesKey("selected_stop_id")
        val WIDGET_DATA_KEY = stringPreferencesKey("widget_json_data")

        // Appelé depuis le Worker pour mettre à jour les données
        suspend fun updateData(
            context: Context,
            glanceId: GlanceId,
            api: BusWidgetApi,
            favoritesStore: FavoritesStore,
            moshi: Moshi,
        ) {
            // Lecture correcte de l'état courant du widget
            val currentPrefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
            val selectedStopId = currentPrefs[STOP_ID_KEY]

            val favorites = favoritesStore.getAll()
            if (favorites.isEmpty()) {
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { p ->
                    p.toMutablePreferences().apply {
                        this[WIDGET_DATA_KEY] = WidgetData.noFavorite().toJson(moshi)
                    }
                }
                return
            }

            val favorite = if (selectedStopId != null) {
                favorites.firstOrNull { it.stop.id == selectedStopId } ?: favorites.first()
            } else {
                favorites.first()
            }

            try {
                android.util.Log.d("BusGlanceWidget", "Fetching departures for ${favorite.stop.name}")
                val lines = favorite.selectedLines.joinToString(",").takeIf { it.isNotBlank() }
                val response = api.getDepartures(
                    stopId = favorite.stop.id,
                    limit = 6,
                    maxMinutes = 240,
                    lines = lines,
                ).toStopDeparturesResponse()

                android.util.Log.d("BusGlanceWidget", "Received ${response.departures.size} departures")

                val widgetData = WidgetData(
                    stopName = response.stop?.name ?: favorite.stop.name,
                    departures = response.departures.take(3),
                    errorMessage = null,
                    updatedAtMs = System.currentTimeMillis(),
                )

                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { p ->
                    p.toMutablePreferences().apply {
                        this[WIDGET_DATA_KEY] = widgetData.toJson(moshi)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("BusGlanceWidget", "Error fetching departures", e)
                val errorMsg = when {
                    e.message?.contains("timeout", ignoreCase = true) == true ->
                        "Connection timed out"
                    e.message?.contains("resolve host", ignoreCase = true) == true ||
                    e is java.net.UnknownHostException ->
                        "Network unavailable"
                    else ->
                        "Unable to load departures"
                }
                val widgetData = WidgetData(
                    stopName = favorite.stop.name,
                    departures = emptyList(),
                    errorMessage = errorMsg,
                    updatedAtMs = System.currentTimeMillis(),
                )
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { p ->
                    p.toMutablePreferences().apply {
                        this[WIDGET_DATA_KEY] = widgetData.toJson(moshi)
                    }
                }
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Déclenche une mise à jour dès que le widget est affiché ou rafraîchi
        // (par exemple quand l'utilisateur réveille son écran)
        WidgetUpdateWorker.runNow(context)

        provideContent {
            BusWidgetContent()
        }
    }
}

// Modèle de données pour le widget (sérialisé en JSON dans les Preferences)
data class WidgetData(
    val stopName: String,
    val departures: List<Departure>,
    val errorMessage: String?,
    val updatedAtMs: Long,
) {
    companion object {
        fun noFavorite() = WidgetData(
            stopName = "BusWidget",
            departures = emptyList(),
            errorMessage = "Chargement ou aucun favori...",
            updatedAtMs = System.currentTimeMillis(),
        )

        fun fromJson(json: String, moshi: Moshi): WidgetData? {
            return try {
                val adapter = moshi.adapter(WidgetDataJson::class.java)
                adapter.fromJson(json)?.toWidgetData()
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toJson(moshi: Moshi): String {
        val adapter = moshi.adapter(WidgetDataJson::class.java)
        return adapter.toJson(toJson())
    }

    private fun toJson() = WidgetDataJson(
        stopName = stopName,
        departures = departures.map {
            DepartureJson(it.line, it.destination, it.minutesUntilDeparture, it.isRealtime)
        },
        errorMessage = errorMessage,
        updatedAtMs = updatedAtMs,
    )
}

@JsonClass(generateAdapter = true)
data class DepartureJson(
    val line: String,
    val destination: String,
    val minutesUntilDeparture: Int,
    val isRealtime: Boolean,
)

@JsonClass(generateAdapter = true)
data class WidgetDataJson(
    val stopName: String,
    val departures: List<DepartureJson>,
    val errorMessage: String?,
    val updatedAtMs: Long,
)

private fun WidgetDataJson.toWidgetData() = WidgetData(
    stopName = stopName,
    departures = departures.map {
        Departure(
            stopId = "", stopName = stopName, routeId = "",
            line = it.line, destination = it.destination,
            departureUnix = 0, departureIso = "",
            minutesUntilDeparture = it.minutesUntilDeparture,
            sourceUrl = "", isRealtime = it.isRealtime,
        )
    },
    errorMessage = errorMessage,
    updatedAtMs = updatedAtMs,
)
