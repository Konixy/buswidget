import SwiftUI

struct FavoritesView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        NavigationStack {
            List {
                ForEach(model.favorites) { favorite in
                    NavigationLink {
                        StopDeparturesView(
                            stop: favorite.stop,
                            preferredLines: Set(favorite.selectedLines)
                        )
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(favorite.stop.name)
                                .font(.headline)

                            if !favorite.stop.transportModes.isEmpty {
                                Text(favorite.stop.transportModes.joined(separator: " | "))
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }

                            if favorite.selectedLines.isEmpty {
                                Text("All lines")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            } else {
                                Text("Lines \(favorite.selectedLines.joined(separator: ", "))")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }

                            Text(favorite.stop.id)
                                .font(.caption)
                                .foregroundStyle(.tertiary)
                        }
                    }
                }
                .onDelete(perform: removeStops)
            }
            .overlay {
                if model.favorites.isEmpty {
                    ContentUnavailableView(
                        "No favorites",
                        systemImage: "star",
                        description: Text("Add stops from Search to see departures and widget data.")
                    )
                }
            }
            .navigationTitle("Favorites")
        }
    }

    private func removeStops(at offsets: IndexSet) {
        for index in offsets {
            model.removeFavorite(model.favorites[index])
        }
    }
}
