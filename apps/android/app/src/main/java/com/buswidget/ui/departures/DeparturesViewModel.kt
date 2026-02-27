package com.buswidget.ui.departures

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buswidget.data.api.BusWidgetApi
import com.buswidget.data.api.toStopDeparturesResponse
import com.buswidget.data.api.toStopInfo
import com.buswidget.data.local.Departure
import com.buswidget.data.local.FavoritesStore
import com.buswidget.data.local.StopDeparturesResponse
import com.buswidget.data.local.StopInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DeparturesUiState {
    object Loading : DeparturesUiState()
    data class Success(
        val stopName: String,
        val departures: List<Departure>,
        val preferredLines: Set<String>,
        val lastUpdated: Long,
        val isRealData: Boolean,
        val stopInfo: StopInfo? = null,
    ) : DeparturesUiState()
    data class Error(val message: String) : DeparturesUiState()
}

@HiltViewModel
class DeparturesViewModel @Inject constructor(
    private val api: BusWidgetApi,
    private val favoritesStore: FavoritesStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val stopId: String = checkNotNull(savedStateHandle["stopId"])
    private val stopName: String = checkNotNull(savedStateHandle["stopName"])

    private val _uiState = MutableStateFlow<DeparturesUiState>(DeparturesUiState.Loading)
    val uiState: StateFlow<DeparturesUiState> = _uiState.asStateFlow()

    private val _preferredLines = MutableStateFlow<Set<String>>(emptySet())

    val isFavorite: StateFlow<Boolean> = favoritesStore.favoritesFlow
        .map { list -> list.any { it.stop.id == stopId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _favoriteSetupStop = MutableStateFlow<StopInfo?>(null)
    val favoriteSetupStop: StateFlow<StopInfo?> = _favoriteSetupStop.asStateFlow()

    private var currentStopInfo: StopInfo? = null

    init {
        viewModelScope.launch {
            // Charger les lignes préférées depuis les favoris
            val fav = favoritesStore.getById(stopId)
            _preferredLines.value = fav?.selectedLines?.toSet() ?: emptySet()
            loadDepartures()
        }
    }

    fun refresh() {
        viewModelScope.launch { loadDepartures() }
    }

    private suspend fun loadDepartures() {
        _uiState.value = DeparturesUiState.Loading
        try {
            var response = api.getDepartures(
                stopId = stopId,
                limit = 12,
                maxMinutes = 240,
            ).toStopDeparturesResponse()

            val preferred = _preferredLines.value
            var filtered = response.filterByLines(preferred)

            // Fallback sur le parent si vide
            if (filtered.isEmpty()) {
                val fav = favoritesStore.getById(stopId)
                val stop = response.stop
                val parentId = stop?.parentStationId
                if (!parentId.isNullOrBlank() && parentId != stopId) {
                    val parentResponse = api.getDepartures(
                        stopId = parentId, limit = 12, maxMinutes = 240
                    ).toStopDeparturesResponse()
                    val parentFiltered = parentResponse.filterByLines(preferred)
                    if (parentFiltered.isNotEmpty()) {
                        response = parentResponse
                        filtered = parentFiltered
                    }
                }
            }

            val unwrappedStopInfo = response.stop
            currentStopInfo = unwrappedStopInfo

            _uiState.value = DeparturesUiState.Success(
                stopName = response.stop?.name ?: stopName,
                departures = filtered,
                preferredLines = preferred,
                lastUpdated = System.currentTimeMillis(),
                isRealData = true,
                stopInfo = unwrappedStopInfo
            )
        } catch (e: Exception) {
            _uiState.value = DeparturesUiState.Error(e.message ?: "Erreur inconnue")
        }
    }

    fun onFavoriteTap() {
        // En favoris, on a besoin du stop model. Si pas là, on essaie d'en simuler un basique.
        val info = currentStopInfo ?: StopInfo(
            id = stopId,
            name = stopName,
            lat = null,
            lon = null,
            stopCode = null,
            locationType = 0,
            parentStationId = null,
            transportModes = emptyList(),
            lineHints = emptyList(),
            lineHintColors = emptyMap()
        )
        _favoriteSetupStop.value = info
    }

    fun dismissFavoriteSetup() {
        _favoriteSetupStop.value = null
    }

    fun saveFavorite(stop: StopInfo, selectedLines: List<String>) {
        viewModelScope.launch {
            favoritesStore.upsert(stop, selectedLines)
            // Met à jour les lignes préférées dans la session courante si c'est pour l'arrêt actuel
            if (stop.id == stopId) {
                _preferredLines.value = selectedLines.toSet()
                loadDepartures() // Rafraîchir les départs filtrés
            }
            _favoriteSetupStop.value = null
        }
    }

    fun removeFavorite(stopId: String) {
        viewModelScope.launch {
            favoritesStore.remove(stopId)
            if (stopId == this@DeparturesViewModel.stopId) {
                _preferredLines.value = emptySet()
            }
            _favoriteSetupStop.value = null
        }
    }

    private fun StopDeparturesResponse.filterByLines(lines: Set<String>): List<Departure> {
        return if (lines.isEmpty()) departures else departures.filter { lines.contains(it.line) }
    }
}
