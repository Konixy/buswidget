package com.buswidget.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.buswidget.widget.WidgetUpdateWorker
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Modèles locaux (équivalents aux modèles Swift dans Shared/)
data class StopInfo(
    val id: String,
    val name: String,
    val lat: Double?,
    val lon: Double?,
    val stopCode: String?,
    val locationType: Int?,
    val parentStationId: String?,
    val transportModes: List<String>,
    val lineHints: List<String>,
)

data class FavoriteStop(
    val stop: StopInfo,
    val selectedLines: List<String>,
)

data class Departure(
    val stopId: String,
    val stopName: String,
    val routeId: String,
    val line: String,
    val destination: String,
    val departureUnix: Long,
    val departureIso: String,
    val minutesUntilDeparture: Int,
    val sourceUrl: String,
    val isRealtime: Boolean,
)

data class StopDeparturesResponse(
    val generatedAtUnix: Long,
    val feedTimestampUnix: Long,
    val stop: StopInfo?,
    val departures: List<Departure>,
)

// Moshi-serializable classes pour la persistance
// On utilise des data classes séparées pour éviter les dépendances circulaires
@JsonClass(generateAdapter = true)
data class FavoriteStopJson(
    val stopId: String,
    val stopName: String,
    val lat: Double?,
    val lon: Double?,
    val stopCode: String?,
    val locationType: Int?,
    val parentStationId: String?,
    val transportModes: List<String>,
    val lineHints: List<String>,
    val selectedLines: List<String>,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "buswidget_favorites")

@Singleton
class FavoritesStore @Inject constructor(
    @ApplicationContext private val context: Context,
    val moshi: Moshi,
) {
    private val favoritesKey = stringPreferencesKey("favorite_stops")

    private val listType = Types.newParameterizedType(List::class.java, FavoriteStopJson::class.java)
    private val adapter by lazy { moshi.adapter<List<FavoriteStopJson>>(listType) }

    val favoritesFlow: Flow<List<FavoriteStop>> = context.dataStore.data.map { prefs ->
        val json = prefs[favoritesKey] ?: return@map emptyList()
        try {
            adapter.fromJson(json)?.map { it.toFavoriteStop() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getAll(): List<FavoriteStop> = favoritesFlow.first()

    suspend fun upsert(stop: StopInfo, selectedLines: List<String>) {
        val current = getAll().toMutableList()
        val normalized = selectedLines.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        val favorite = FavoriteStop(stop = stop, selectedLines = normalized)
        val existingIndex = current.indexOfFirst { it.stop.id == stop.id }
        if (existingIndex >= 0) {
            current[existingIndex] = favorite
        } else {
            current.add(favorite)
        }
        save(current)
    }

    suspend fun remove(stopId: String) {
        val current = getAll().filter { it.stop.id != stopId }
        save(current)
    }

    suspend fun contains(stopId: String): Boolean = getAll().any { it.stop.id == stopId }

    suspend fun getById(stopId: String): FavoriteStop? = getAll().firstOrNull { it.stop.id == stopId }

    private suspend fun save(favorites: List<FavoriteStop>) {
        val jsonList = favorites.map { it.toJson() }
        val json = adapter.toJson(jsonList)
        context.dataStore.edit { prefs ->
            prefs[favoritesKey] = json
        }
        // Notifie le widget immédiatement après chaque modification des favoris
        WidgetUpdateWorker.runNow(context)
    }

    private fun FavoriteStop.toJson() = FavoriteStopJson(
        stopId = stop.id,
        stopName = stop.name,
        lat = stop.lat,
        lon = stop.lon,
        stopCode = stop.stopCode,
        locationType = stop.locationType,
        parentStationId = stop.parentStationId,
        transportModes = stop.transportModes,
        lineHints = stop.lineHints,
        selectedLines = selectedLines,
    )

    private fun FavoriteStopJson.toFavoriteStop() = FavoriteStop(
        stop = StopInfo(
            id = stopId,
            name = stopName,
            lat = lat,
            lon = lon,
            stopCode = stopCode,
            locationType = locationType,
            parentStationId = parentStationId,
            transportModes = transportModes,
            lineHints = lineHints,
        ),
        selectedLines = selectedLines,
    )
}
