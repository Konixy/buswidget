package com.buswidget.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buswidget.data.local.FavoriteStop
import com.buswidget.data.local.FavoritesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoritesStore: FavoritesStore,
) : ViewModel() {

    val favorites: StateFlow<List<FavoriteStop>> = favoritesStore.favoritesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun remove(stopId: String) {
        viewModelScope.launch {
            favoritesStore.remove(stopId)
        }
    }

    fun onReorder(orderedFavorites: List<FavoriteStop>) {
        viewModelScope.launch {
            favoritesStore.updateOrder(orderedFavorites)
        }
    }
}
