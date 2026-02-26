package com.buswidget.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buswidget.data.api.BusWidgetApi
import com.buswidget.data.api.toStopInfo
import com.buswidget.data.local.FavoritesStore
import com.buswidget.data.local.StopInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    data class Success(val results: List<StopInfo>) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val api: BusWidgetApi,
    private val favoritesStore: FavoritesStore,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // Sheet de configuration d'un favori
    private val _favoriteSetupStop = MutableStateFlow<StopInfo?>(null)
    val favoriteSetupStop: StateFlow<StopInfo?> = _favoriteSetupStop.asStateFlow()

    val favoritesFlow = favoritesStore.favoritesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _query
                .debounce(250)
                .distinctUntilChanged()
                .collect { q -> search(q) }
        }
        // Recherche initiale
        search("")
    }

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun onFavoriteTap(stop: StopInfo) {
        _favoriteSetupStop.value = stop
    }

    fun dismissFavoriteSetup() {
        _favoriteSetupStop.value = null
    }

    fun saveFavorite(stop: StopInfo, selectedLines: List<String>) {
        viewModelScope.launch {
            favoritesStore.upsert(stop, selectedLines)
            _favoriteSetupStop.value = null
        }
    }

    fun removeFavorite(stopId: String) {
        viewModelScope.launch {
            favoritesStore.remove(stopId)
            _favoriteSetupStop.value = null
        }
    }

    fun isFavorite(stopId: String): Boolean =
        favoritesFlow.value.any { it.stop.id == stopId }

    fun getSelectedLines(stopId: String): List<String> =
        favoritesFlow.value.firstOrNull { it.stop.id == stopId }?.selectedLines ?: emptyList()

    private fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiState.value = SearchUiState.Idle
            return
        }
        viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            try {
                val response = api.searchStops(query = trimmed, limit = 30)
                _uiState.value = SearchUiState.Success(response.results.map { it.toStopInfo() })
            } catch (e: Exception) {
                _uiState.value = SearchUiState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }
}
