package com.buswidget.data.api

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface BusWidgetApi {

    @GET("/health")
    suspend fun health(): Map<String, Any>

    @GET("/v1/rouen/stops/search")
    suspend fun searchStops(
        @Query("q") query: String,
        @Query("limit") limit: Int = 30,
    ): StopSearchResponseDto

    @GET("/v1/rouen/stops/nearby")
    suspend fun getNearbyStops(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("limit") limit: Int = 30,
    ): StopSearchResponseDto

    @GET("/v1/rouen/stops/{stopId}/departures")
    suspend fun getDepartures(
        @Path("stopId") stopId: String,
        @Query("limit") limit: Int = 10,
        @Query("maxMinutes") maxMinutes: Int = 240,
        @Query("lines") lines: String? = null,
    ): StopDeparturesResponseDto
}
