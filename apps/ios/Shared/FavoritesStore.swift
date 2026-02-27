import Foundation

public final class FavoritesStore {
    private let key = "favorite_stops"
    private let defaults: UserDefaults
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    public init() {
        self.defaults = UserDefaults(suiteName: AppGroup.identifier) ?? .standard
    }

    public init(defaults: UserDefaults) {
        self.defaults = defaults
    }

    public func all() -> [FavoriteStop] {
        guard let data = defaults.data(forKey: key) else {
            return []
        }

        if let favorites = try? decoder.decode([FavoriteStop].self, from: data) {
            return favorites
        }

        if let legacyStops = try? decoder.decode([StopInfo].self, from: data) {
            let migrated = legacyStops.map { FavoriteStop(stop: $0, selectedLines: []) }
            save(migrated)
            return migrated
        }

        return []
    }

    public func save(_ favorites: [FavoriteStop]) {
        guard let data = try? encoder.encode(favorites) else {
            return
        }
        defaults.set(data, forKey: key)
    }

    public func upsert(stop: StopInfo, selectedLines: [String]) {
        var current = all()
        let normalizedSelection = Array(Set(selectedLines)).sorted { lhs, rhs in
            lhs.localizedStandardCompare(rhs) == .orderedAscending
        }

        let favorite = FavoriteStop(stop: stop, selectedLines: normalizedSelection)
        if let index = current.firstIndex(where: { $0.id == stop.id }) {
            current[index] = favorite
        } else {
            current.append(favorite)
        }
        save(current)
    }

    public func contains(_ stop: StopInfo) -> Bool {
        all().contains(where: { $0.id == stop.id })
    }

    public func remove(_ stop: StopInfo) {
        remove(stopId: stop.id)
    }

    public func remove(stopId: String) {
        let filtered = all().filter { $0.id != stopId }
        save(filtered)
    }
}
