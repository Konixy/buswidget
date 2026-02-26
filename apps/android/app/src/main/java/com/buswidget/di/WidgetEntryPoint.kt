package com.buswidget.di

import com.buswidget.data.api.BusWidgetApi
import com.buswidget.data.local.FavoritesStore
import com.squareup.moshi.Moshi
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * EntryPoint Hilt pour accéder aux dépendances depuis des contextes non-Hilt
 * (widget Glance, Worker sans @HiltWorker, etc.)
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun api(): BusWidgetApi
    fun favoritesStore(): FavoritesStore
    fun moshi(): Moshi
}
