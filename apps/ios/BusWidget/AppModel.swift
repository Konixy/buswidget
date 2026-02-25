import Foundation
import WidgetKit

@MainActor
final class AppModel: ObservableObject {
    enum Tab: Hashable {
        case search
        case favorites
    }

    @Published var favorites: [FavoriteStop] = []
    @Published var selectedTab: Tab = .search

    let api: APIClient
    private let favoritesStore: FavoritesStore

    init(
        api: APIClient = APIClient(baseURL: AppConfiguration.baseURL()),
        favoritesStore: FavoritesStore = FavoritesStore()
    ) {
        self.api = api
        self.favoritesStore = favoritesStore
        self.favorites = favoritesStore.all()
    }

    func isFavorite(_ stop: StopInfo) -> Bool {
        favorites.contains(where: { $0.id == stop.id })
    }

    func favorite(for stop: StopInfo) -> FavoriteStop? {
        favorites.first(where: { $0.id == stop.id })
    }

    func saveFavorite(_ stop: StopInfo, selectedLines: [String]) {
        favoritesStore.upsert(stop: stop, selectedLines: selectedLines)
        favorites = favoritesStore.all()
        WidgetCenter.shared.reloadAllTimelines()
    }

    func removeFavorite(_ favorite: FavoriteStop) {
        favoritesStore.remove(stopId: favorite.id)
        favorites = favoritesStore.all()
        WidgetCenter.shared.reloadAllTimelines()
    }

    func removeFavorite(_ stop: StopInfo) {
        favoritesStore.remove(stopId: stop.id)
        favorites = favoritesStore.all()
        WidgetCenter.shared.reloadAllTimelines()
    }

    func handleDeepLink(_ url: URL) {
        guard url.scheme == "buswidget" else {
            return
        }

        selectedTab = .favorites
    }
}
