import SwiftUI

struct RootView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        TabView(selection: $model.selectedTab) {
            SearchStopsView()
                .tag(AppModel.Tab.search)
                .tabItem {
                    Label("Search", systemImage: "magnifyingglass")
                }

            FavoritesView()
                .tag(AppModel.Tab.favorites)
                .tabItem {
                    Label("Favorites", systemImage: "star.fill")
                }
        }
    }
}
