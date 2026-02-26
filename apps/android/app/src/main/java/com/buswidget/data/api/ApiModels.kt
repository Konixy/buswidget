package com.buswidget.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StopDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "lat") val lat: Double?,
    @Json(name = "lon") val lon: Double?,
    @Json(name = "stopCode") val stopCode: String?,
    @Json(name = "locationType") val locationType: Int?,
    @Json(name = "parentStationId") val parentStationId: String?,
    @Json(name = "transportModes") val transportModes: List<String>,
    @Json(name = "lineHints") val lineHints: List<String>,
)

@JsonClass(generateAdapter = true)
data class DepartureDto(
    @Json(name = "stopId") val stopId: String,
    @Json(name = "stopName") val stopName: String,
    @Json(name = "routeId") val routeId: String,
    @Json(name = "line") val line: String,
    @Json(name = "destination") val destination: String,
    @Json(name = "departureUnix") val departureUnix: Long,
    @Json(name = "departureIso") val departureIso: String,
    @Json(name = "minutesUntilDeparture") val minutesUntilDeparture: Int,
    @Json(name = "sourceUrl") val sourceUrl: String,
    @Json(name = "isRealtime") val isRealtime: Boolean,
)

@JsonClass(generateAdapter = true)
data class StopSearchResponseDto(
    @Json(name = "count") val count: Int,
    @Json(name = "results") val results: List<StopDto>,
)

@JsonClass(generateAdapter = true)
data class StopDeparturesResponseDto(
    @Json(name = "generatedAtUnix") val generatedAtUnix: Long,
    @Json(name = "feedTimestampUnix") val feedTimestampUnix: Long,
    @Json(name = "stop") val stop: StopDto?,
    @Json(name = "departures") val departures: List<DepartureDto>,
)
