import SwiftUI

struct StopDeparturesView: View {
    @EnvironmentObject private var model: AppModel
    let stop: StopInfo
    let preferredLines: Set<String>

    @State private var departures: [Departure] = []
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var favoriteSetupStop: StopInfo?
    private var isShowingError: Binding<Bool> {
        Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )
    }

    init(stop: StopInfo, preferredLines: Set<String> = []) {
        self.stop = stop
        self.preferredLines = preferredLines
    }

    var body: some View {
        List {
            ForEach(departures, id: \.departureIso) { departure in
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        LineBadge(
                            line: departure.line,
                            colorHex: departure.lineColor,
                            font: .headline,
                            horizontalPadding: 10,
                            verticalPadding: 4,
                            minWidth: 34
                        )
                        Spacer()
                        HStack(spacing: 4) {
                            if departure.isRealtime {
                                Image(systemName: "wave.3.left")
                                    .font(.caption2)
                                    .foregroundStyle(.green)
                            }
                            Text("\(departure.minutesUntilDeparture) min")
                                .font(.headline)
                        }
                    }

                    Text(departure.destination)
                        .font(.subheadline)

                    if departure.stopId != stop.id {
                        Text(departure.stopId)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }

                    Text(formattedTime(for: departure))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .overlay {
            if departures.isEmpty, !isLoading {
                ContentUnavailableView(
                    "No upcoming departures",
                    systemImage: "clock",
                    description: Text("Try pull to refresh.")
                )
            }
        }
        .navigationTitle(stop.name)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    favoriteSetupStop = stop
                } label: {
                    Image(systemName: model.isFavorite(stop) ? "star.fill" : "star")
                        .foregroundStyle(.yellow)
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                if isLoading {
                    ProgressView()
                }
            }
        }
        .task {
            await loadDepartures()
        }
        .refreshable {
            await loadDepartures()
        }
        .alert("Could not load departures", isPresented: isShowingError, actions: {
            Button("OK") {
                errorMessage = nil
            }
        }, message: {
            Text(errorMessage ?? "Unknown error")
        })
        .sheet(item: $favoriteSetupStop) { favoriteStop in
            FavoriteStopOptionsSheet(
                stop: favoriteStop,
                initialSelection: Set(model.favorite(for: favoriteStop)?.selectedLines ?? []),
                isAlreadyFavorite: model.isFavorite(favoriteStop),
                onCancel: { favoriteSetupStop = nil },
                onSave: { selectedLines in
                    model.saveFavorite(favoriteStop, selectedLines: selectedLines)
                    favoriteSetupStop = nil
                },
                onRemove: {
                    model.removeFavorite(favoriteStop)
                    favoriteSetupStop = nil
                }
            )
        }
    }

    private func loadDepartures() async {
        isLoading = true
        defer { isLoading = false }

        do {
            var response = try await model.api.departures(stopId: stop.id, limit: 10, maxMinutes: 240)

            if filteredDepartures(from: response).isEmpty,
               let parentStationId = stop.parentStationId,
               parentStationId != stop.id {
                response = try await model.api.departures(
                    stopId: parentStationId,
                    limit: 10,
                    maxMinutes: 240
                )
            }

            if filteredDepartures(from: response).isEmpty {
                let fallback = try await fallbackDeparturesForSiblings()
                if let fallback {
                    response = fallback
                }
            }

            departures = filteredDepartures(from: response)
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func fallbackDeparturesForSiblings() async throws -> StopDeparturesResponse? {
        let search = try await model.api.searchStops(query: stop.name, limit: 12)
        let normalizedName = stop.name.folding(options: .diacriticInsensitive, locale: .current).lowercased()

        let candidates = search.results
            .filter { $0.id != stop.id }
            .filter {
                $0.name.folding(options: .diacriticInsensitive, locale: .current).lowercased() == normalizedName
            }
            .sorted { lhs, rhs in
                score(for: lhs) > score(for: rhs)
            }

        for candidate in candidates.prefix(4) {
            let response = try await model.api.departures(stopId: candidate.id, limit: 10, maxMinutes: 240)
            if !filteredDepartures(from: response).isEmpty {
                return response
            }
        }

        return nil
    }

    private func score(for candidate: StopInfo) -> Int {
        var total = 0

        if candidate.parentStationId == stop.parentStationId, candidate.parentStationId != nil {
            total += 50
        }

        if candidate.parentStationId == stop.id || stop.parentStationId == candidate.id {
            total += 40
        }

        total += candidate.transportModes.count * 10
        total += candidate.lineHints.count

        if candidate.locationType == 0 {
            total += 5
        }

        return total
    }

    private func filteredDepartures(from response: StopDeparturesResponse) -> [Departure] {
        if preferredLines.isEmpty {
            return response.departures
        }

        return response.departures.filter { preferredLines.contains($0.line) }
    }

    private func formattedTime(for departure: Departure) -> String {
        if let date = ISO8601DateFormatter().date(from: departure.departureIso) {
            return date.formatted(date: .omitted, time: .shortened)
        }
        return departure.departureIso
    }
}
