import SwiftUI

@main
struct LoneRiderApp: App {
    @StateObject private var model = GameModel()
    @StateObject private var voice = VoiceManager.shared

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .environmentObject(voice)
                .preferredColorScheme(.dark)
        }
    }
}
