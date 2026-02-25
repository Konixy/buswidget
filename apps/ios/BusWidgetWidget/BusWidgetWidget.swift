import WidgetKit
import SwiftUI
import AppIntents

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

struct BusWidgetProvider: TimelineProvider {
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

    func getSnapshot(in context: Context, completion: @escaping (BusWidgetEntry) -> Void) {
        completion(placeholder(in: context))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<BusWidgetEntry>) -> Void) {
        Task {
            let entry = await loadEntry()
            let refreshDate = Calendar.current.date(byAdding: .minute, value: 5, to: .now) ?? .now.addingTimeInterval(300)
            completion(Timeline(entries: [entry], policy: .after(refreshDate)))
        }
    }

    private func loadEntry() async -> BusWidgetEntry {
        let favoritesStore = FavoritesStore()
        let favorites = favoritesStore.all()

        guard let firstFavorite = favorites.first else {
            return BusWidgetEntry(
                date: .now,
                stop: nil,
                departures: [],
                errorMessage: "Add a favorite stop in the app"
            )
        }

        let client = APIClient(baseURL: AppConfiguration.baseURL(bundle: .main))

        do {
            let response = try await client.departures(
                stopId: firstFavorite.stop.id,
                limit: 6,
                maxMinutes: 240
            )
            let selectedLines = Set(firstFavorite.selectedLines)
            let filteredDepartures = selectedLines.isEmpty
                ? response.departures
                : response.departures.filter { selectedLines.contains($0.line) }

            return BusWidgetEntry(
                date: .now,
                stop: response.stop ?? firstFavorite.stop,
                departures: Array(filteredDepartures.prefix(4)),
                errorMessage: nil
            )
        } catch {
            return BusWidgetEntry(
                date: .now,
                stop: firstFavorite.stop,
                departures: [],
                errorMessage: "Unable to refresh departures"
            )
        }
    }
}

struct BusWidgetWidgetEntryView: View {
    @Environment(\.widgetFamily) private var family
    let entry: BusWidgetProvider.Entry

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
                let shown = family == .systemSmall ? Array(entry.departures.prefix(2)) : Array(entry.departures.prefix(4))
                ForEach(shown, id: \.departureIso) { departure in
                    HStack {
                        Text(departure.line)
                            .font(.subheadline)
                            .bold()
                        Text(departure.destination)
                            .font(.caption)
                            .lineLimit(1)
                        Spacer()
                        Text("\(departure.minutesUntilDeparture)m")
                            .font(.subheadline)
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
        StaticConfiguration(kind: kind, provider: BusWidgetProvider()) { entry in
            BusWidgetWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Rouen Departures")
        .description("Shows upcoming departures for your first favorite stop and selected lines.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
