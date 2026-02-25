import Foundation

public struct StopInfo: Codable, Identifiable, Hashable {
    public let id: String
    public let name: String
    public let lat: Double?
    public let lon: Double?
    public let stopCode: String?
    public let locationType: Int?
    public let parentStationId: String?
    public let transportModes: [String]
    public let lineHints: [String]

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case lat
        case lon
        case stopCode
        case locationType
        case parentStationId
        case transportModes
        case lineHints
    }

    public init(
        id: String,
        name: String,
        lat: Double?,
        lon: Double?,
        stopCode: String? = nil,
        locationType: Int? = nil,
        parentStationId: String? = nil,
        transportModes: [String] = [],
        lineHints: [String] = []
    ) {
        self.id = id
        self.name = name
        self.lat = lat
        self.lon = lon
        self.stopCode = stopCode
        self.locationType = locationType
        self.parentStationId = parentStationId
        self.transportModes = transportModes
        self.lineHints = lineHints
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        name = try container.decode(String.self, forKey: .name)
        lat = try container.decodeIfPresent(Double.self, forKey: .lat)
        lon = try container.decodeIfPresent(Double.self, forKey: .lon)
        stopCode = try container.decodeIfPresent(String.self, forKey: .stopCode)
        locationType = try container.decodeIfPresent(Int.self, forKey: .locationType)
        parentStationId = try container.decodeIfPresent(String.self, forKey: .parentStationId)
        transportModes = try container.decodeIfPresent([String].self, forKey: .transportModes) ?? []
        lineHints = try container.decodeIfPresent([String].self, forKey: .lineHints) ?? []
    }
}

public struct FavoriteStop: Codable, Identifiable, Hashable {
    public let stop: StopInfo
    public let selectedLines: [String]

    public var id: String { stop.id }

    public init(stop: StopInfo, selectedLines: [String] = []) {
        self.stop = stop
        self.selectedLines = selectedLines
    }
}

public struct Departure: Codable, Hashable {
    public let stopId: String
    public let stopName: String
    public let routeId: String
    public let line: String
    public let destination: String
    public let departureUnix: Int
    public let departureIso: String
    public let minutesUntilDeparture: Int
    public let sourceUrl: String

    public init(
        stopId: String,
        stopName: String,
        routeId: String,
        line: String,
        destination: String,
        departureUnix: Int,
        departureIso: String,
        minutesUntilDeparture: Int,
        sourceUrl: String
    ) {
        self.stopId = stopId
        self.stopName = stopName
        self.routeId = routeId
        self.line = line
        self.destination = destination
        self.departureUnix = departureUnix
        self.departureIso = departureIso
        self.minutesUntilDeparture = minutesUntilDeparture
        self.sourceUrl = sourceUrl
    }
}

public struct StopSearchResponse: Codable {
    public let count: Int
    public let results: [StopInfo]
}

public struct StopDeparturesResponse: Codable {
    public let generatedAtUnix: Int
    public let feedTimestampUnix: Int
    public let stop: StopInfo?
    public let departures: [Departure]
}
