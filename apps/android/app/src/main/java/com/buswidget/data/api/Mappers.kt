package com.buswidget.data.api

import com.buswidget.data.local.Departure
import com.buswidget.data.local.StopDeparturesResponse
import com.buswidget.data.local.StopInfo

// Mappers DTO → Domain models
fun StopDto.toStopInfo() = StopInfo(
    id = id,
    name = name,
    lat = lat,
    lon = lon,
    stopCode = stopCode,
    locationType = locationType,
    parentStationId = parentStationId,
    transportModes = transportModes,
    lineHints = lineHints,
    lineHintColors = lineHintColors ?: emptyMap(),
)

fun DepartureDto.toDeparture() = Departure(
    stopId = stopId,
    stopName = stopName,
    routeId = routeId,
    line = line,
    lineColor = lineColor,
    destination = destination,
    departureUnix = departureUnix,
    departureIso = departureIso,
    minutesUntilDeparture = minutesUntilDeparture,
    sourceUrl = sourceUrl,
    isRealtime = isRealtime,
)

fun StopDeparturesResponseDto.toStopDeparturesResponse() = StopDeparturesResponse(
    generatedAtUnix = generatedAtUnix,
    feedTimestampUnix = feedTimestampUnix,
    stop = stop?.toStopInfo(),
    departures = departures.map { it.toDeparture() },
)
