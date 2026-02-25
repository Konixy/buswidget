import SwiftUI

struct SearchStopsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var query = "gare"
    @State private var results: [StopInfo] = []
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var favoriteSetupStop: StopInfo?

    private var isShowingError: Binding<Bool> {
        Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )
    }

    var body: some View {
        NavigationStack {
            List(results) { stop in
                NavigationLink {
                    StopDeparturesView(stop: stop)
                } label: {
                    StopSearchResultRow(
                        stop: stop,
                        isFavorite: model.isFavorite(stop),
                        onFavoriteTap: { favoriteSetupStop = stop }
                    )
                }
            }
            .overlay {
                if results.isEmpty, !isLoading {
                    ContentUnavailableView(
                        "No stops",
                        systemImage: "tram",
                        description: Text("Try searching for another stop name.")
                    )
                }
            }
            .navigationTitle("Rouen Stops")
        }
        .searchable(text: $query, prompt: "Search stop name, line or id")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                if isLoading {
                    ProgressView()
                }
            }
        }
        .task(id: query) {
            await search(debounced: true)
        }
        .onSubmit(of: .search) {
            Task { await search(debounced: false) }
        }
        .refreshable {
            await search(debounced: false)
        }
        .alert("Search failed", isPresented: isShowingError, actions: {
            Button("OK") {
                errorMessage = nil
            }
        }, message: {
            Text(errorMessage ?? "Unknown error")
        })
        .sheet(item: $favoriteSetupStop) { stop in
            FavoriteStopOptionsSheet(
                stop: stop,
                initialSelection: Set(model.favorite(for: stop)?.selectedLines ?? []),
                isAlreadyFavorite: model.isFavorite(stop),
                onCancel: { favoriteSetupStop = nil },
                onSave: { selectedLines in
                    model.saveFavorite(stop, selectedLines: selectedLines)
                    favoriteSetupStop = nil
                },
                onRemove: {
                    model.removeFavorite(stop)
                    favoriteSetupStop = nil
                }
            )
        }
    }

    private func search(debounced: Bool) async {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 2 else {
            results = []
            return
        }

        if debounced {
            do {
                try await Task.sleep(nanoseconds: 250_000_000)
                try Task.checkCancellation()
            } catch {
                return
            }
        }

        isLoading = true
        defer { isLoading = false }

        do {
            let response = try await model.api.searchStops(query: trimmed, limit: 30)
            results = response.results
            errorMessage = nil
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

private struct StopSearchResultRow: View {
    let stop: StopInfo
    let isFavorite: Bool
    let onFavoriteTap: () -> Void

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            Button(action: onFavoriteTap) {
                Image(systemName: isFavorite ? "star.fill" : "star")
                    .foregroundStyle(.yellow)
            }
            .buttonStyle(.borderless)

            VStack(alignment: .leading, spacing: 4) {
                Text(stop.name)
                    .font(.headline)

                Text(StopPresentation.modeSummary(for: stop))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                if !stop.lineHints.isEmpty {
                    Text("Lines \(Array(stop.lineHints.prefix(4)).joined(separator: ", "))")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer()
        }
    }
}

struct FavoriteStopOptionsSheet: View {
    let stop: StopInfo
    let isAlreadyFavorite: Bool
    let onCancel: () -> Void
    let onSave: ([String]) -> Void
    let onRemove: () -> Void

    @State private var selectedLines: Set<String>

    init(
        stop: StopInfo,
        initialSelection: Set<String>,
        isAlreadyFavorite: Bool,
        onCancel: @escaping () -> Void,
        onSave: @escaping ([String]) -> Void,
        onRemove: @escaping () -> Void
    ) {
        self.stop = stop
        self.isAlreadyFavorite = isAlreadyFavorite
        self.onCancel = onCancel
        self.onSave = onSave
        self.onRemove = onRemove
        self._selectedLines = State(initialValue: initialSelection)
    }

    var body: some View {
        NavigationStack {
            List {
                headerSection
                lineSelectionSection

                if isAlreadyFavorite {
                    Section {
                        Button(role: .destructive, action: onRemove) {
                            Text("Remove favorite")
                        }
                    }
                }
            }
            .navigationTitle("Favorite options")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", action: onCancel)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        onSave(selectedLinesForSave)
                    }
                }
            }
        }
    }

    private var headerSection: some View {
        Section {
            Text(stop.name)
                .font(.headline)
            Text(StopPresentation.modeSummary(for: stop))
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }

    @ViewBuilder
    private var lineSelectionSection: some View {
        if stop.lineHints.isEmpty {
            Section {
                Text("No specific lines found for this stop. Favorite will show all available departures.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        } else {
            Section {
                Button {
                    selectedLines = []
                } label: {
                    lineRow(title: "All lines", isSelected: selectedLines.isEmpty)
                }

                ForEach(stop.lineHints, id: \.self) { line in
                    Button {
                        toggleSelection(for: line)
                    } label: {
                        lineRow(title: line, isSelected: selectedLines.contains(line))
                    }
                }
            } header: {
                Text("Select line(s)")
            } footer: {
                Text("Choose one or many lines. Leave empty to keep all lines.")
            }
        }
    }

    private var selectedLinesForSave: [String] {
        if selectedLines.isEmpty {
            return []
        }

        return stop.lineHints.filter { selectedLines.contains($0) }
    }

    private func toggleSelection(for line: String) {
        if selectedLines.contains(line) {
            selectedLines.remove(line)
        } else {
            selectedLines.insert(line)
        }
    }

    private func lineRow(title: String, isSelected: Bool) -> some View {
        HStack {
            Text(title)
            Spacer()
            if isSelected {
                Image(systemName: "checkmark")
                    .foregroundStyle(Color.accentColor)
            }
        }
    }
}

private enum StopPresentation {
    static func modeSummary(for stop: StopInfo) -> String {
        var modes = stop.transportModes
        if stop.locationType == 1 {
            modes.append("Station")
        }

        if modes.isEmpty {
            return "Mode unknown"
        }

        return modes.joined(separator: " | ")
    }

}
