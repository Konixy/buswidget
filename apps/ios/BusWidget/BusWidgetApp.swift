import SwiftUI

@main
struct BusWidgetApp: App {
    @StateObject private var appModel = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(appModel)
                .onOpenURL { url in
                    appModel.handleDeepLink(url)
                }
        }
    }
}
