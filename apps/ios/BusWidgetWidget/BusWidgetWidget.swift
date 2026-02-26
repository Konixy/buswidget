import WidgetKit
import SwiftUI
import AppIntents

struct FavoriteStopEntity: AppEntity {
    static var typeDisplayRepresentation = TypeDisplayRepresentation(name: "Favorite Stop")
    static var defaultQuery = FavoriteStopEntityQuery()

    let id: String
    let name: String

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(name)", subtitle: "\(id)")
    }

    init(id: String, name: String) {
        self.id = id
        self.name = name
    }

    init(favorite: FavoriteStop) {
        self.id = favorite.id
        self.name = favorite.stop.name
    }
}

struct FavoriteStopEntityQuery: EntityQuery {
    func entities(for identifiers: [FavoriteStopEntity.ID]) async throws -> [FavoriteStopEntity] {
        let favorites = FavoritesStore().all()
        let favoritesById = Dictionary(uniqueKeysWithValues: favorites.map { ($0.id, $0) })

        return identifiers.compactMap { identifier in
            guard let favorite = favoritesById[identifier] else { return nil }
            return FavoriteStopEntity(favorite: favorite)
        }
    }

    func suggestedEntities() async throws -> [FavoriteStopEntity] {
        FavoritesStore().all().map(FavoriteStopEntity.init(favorite:))
    }

    func defaultResult() async -> FavoriteStopEntity? {
        FavoritesStore().all().first.map(FavoriteStopEntity.init(favorite:))
    }
}

struct BusWidgetConfigurationIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Favorite Stop"
    static var description = IntentDescription("Choose which favorite stop this widget should display.")

    @Parameter(title: "Stop")
    var stop: FavoriteStopEntity?
}

struct RefreshDeparturesIntent: AppIntent {
    static var title: LocalizedStringResource = "Refresh departures"
    static var openAppWhenRun = false

    func perform() async throws -> some IntentResult {
        WidgetCenter.shared.reloadTimelines(ofKind: "BusWidgetWidget")
        return .result()
    }
}

struct BusWidgetEntry: TimelineEntry {
    let date: Date
    let stop: StopInfo?
    let departures: [Departure]
    let errorMessage: String?
}

struct BusWidgetProvider: AppIntentTimelineProvider {
    typealias Intent = BusWidgetConfigurationIntent

    func placeholder(in context: Context) -> BusWidgetEntry {
        BusWidgetEntry(
            date: .now,
            stop: StopInfo(id: "TAE:1131", name: "GARE DE ST AUBIN", lat: nil, lon: nil),
            departures: [
                Departure(
                    stopId: "TAE:1131",
                    stopName: "GARE DE ST AUBIN",
                    routeId: "TAE:115",
                    line: "F",
                    destination: "Z.I. L OISON",
                    departureUnix: Int(Date.now.timeIntervalSince1970 + 6 * 60),
                    departureIso: Date.now.addingTimeInterval(6 * 60).ISO8601Format(),
                    minutesUntilDeparture: 6,
                    sourceUrl: ""
                )
            ],
            errorMessage: nil
        )
    }

    func snapshot(for configuration: BusWidgetConfigurationIntent, in context: Context) async -> BusWidgetEntry {
        await loadEntry(configuration: configuration)
    }

    func timeline(for configuration: BusWidgetConfigurationIntent, in context: Context) async -> Timeline<BusWidgetEntry> {
        let entry = await loadEntry(configuration: configuration)
        let refreshDate = Calendar.current.date(byAdding: .minute, value: 5, to: .now) ?? .now.addingTimeInterval(300)
        return Timeline(entries: [entry], policy: .after(refreshDate))
    }

    private func loadEntry(configuration: BusWidgetConfigurationIntent) async -> BusWidgetEntry {
        let favoritesStore = FavoritesStore()
        let favorites = favoritesStore.all()

        guard !favorites.isEmpty else {
            return BusWidgetEntry(
                date: .now,
                stop: nil,
                departures: [],
                errorMessage: "Add a favorite stop in the app"
            )
        }

        let selectedFavorite: FavoriteStop
        if let selectedStopId = configuration.stop?.id {
            guard let favorite = favorites.first(where: { $0.id == selectedStopId }) else {
                return BusWidgetEntry(
                    date: .now,
                    stop: nil,
                    departures: [],
                    errorMessage: "Selected favorite is no longer available"
                )
            }
            selectedFavorite = favorite
        } else {
            selectedFavorite = favorites[0]
        }

        let client = APIClient(baseURL: AppConfiguration.baseURL(bundle: .main))

        do {
            let selectedLines = Array(
                Set(
                    selectedFavorite.selectedLines
                        .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                        .filter { !$0.isEmpty }
                )
            ).sorted()
            let lines = selectedLines.isEmpty ? nil : selectedLines
            let response: StopDeparturesResponse

            if let logicalStopId = selectedFavorite.logicalStopId {
                let logicalResponse = try await client.departures(
                    logicalStopId: logicalStopId,
                    limit: 6,
                    maxMinutes: 240,
                    lines: lines
                )
                if logicalResponse.departures.isEmpty {
                    response = try await client.departures(
                        stopId: selectedFavorite.stop.id,
                        limit: 6,
                        maxMinutes: 240,
                        lines: lines
                    )
                } else {
                    response = logicalResponse
                }
            } else {
                response = try await client.departures(
                    stopId: selectedFavorite.stop.id,
                    limit: 6,
                    maxMinutes: 240,
                    lines: lines
                )
            }

            favoritesStore.updateLogicalStopId(
                stopId: selectedFavorite.stop.id,
                logicalStopId: response.logicalStopId
            )

            return BusWidgetEntry(
                date: .now,
                stop: response.stop ?? selectedFavorite.stop,
                departures: Array(response.departures.prefix(3)),
                errorMessage: nil
            )
        } catch {
            return BusWidgetEntry(
                date: .now,
                stop: selectedFavorite.stop,
                departures: [],
                errorMessage: "Unable to refresh departures"
            )
        }
    }
}

struct BusWidgetWidgetEntryView: View {
    let entry: BusWidgetEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(entry.stop?.name ?? "BusWidget")
                .font(.headline)
                .lineLimit(1)

            if let errorMessage = entry.errorMessage {
                Text(errorMessage)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            } else if entry.departures.isEmpty {
                Text("No upcoming departures")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            } else {
                let shown = Array(entry.departures.prefix(3))
                ForEach(shown, id: \.departureIso) { departure in
                    HStack {
                        LineBadge(
                            line: departure.line,
                            colorHex: departure.lineColor,
                            font: .caption,
                            horizontalPadding: 7,
                            verticalPadding: 2,
                            minWidth: 24
                        )
                        Text(departure.destination)
                            .font(.caption)
                            .lineLimit(1)
                        Spacer()
                        HStack(spacing: 3) {
                            if departure.isRealtime {
                                Image(systemName: "wave.3.left")
                                    .font(.caption2)
                                    .foregroundStyle(.green)
                            }
                            Text("\(departure.minutesUntilDeparture)m")
                                .font(.subheadline)
                        }
                    }
                }
            }

            Spacer(minLength: 0)

            HStack {
                Text("Updated \(entry.date.formatted(date: .omitted, time: .shortened))")
                    .font(.caption2)
                    .foregroundStyle(.secondary)

                Spacer()

                Button(intent: RefreshDeparturesIntent()) {
                    Image(systemName: "arrow.clockwise")
                        .font(.caption)
                }
                .buttonStyle(.plain)
            }
        }
        .widgetURL(widgetURL)
        .containerBackground(.fill.tertiary, for: .widget)
    }

    private var widgetURL: URL? {
        guard let stop = entry.stop else {
            return URL(string: "buswidget://favorites")
        }
        return URL(string: "buswidget://stop/\(stop.id)")
    }
}

struct BusWidgetWidget: Widget {
    let kind: String = "BusWidgetWidget"

    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: kind, intent: BusWidgetConfigurationIntent.self, provider: BusWidgetProvider()) { entry in
            BusWidgetWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Rouen Departures")
        .description("Shows upcoming departures for a selected favorite stop and selected lines.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
