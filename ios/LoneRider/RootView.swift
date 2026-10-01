import SwiftUI

struct RootView: View {
    @EnvironmentObject var model: GameModel
    @EnvironmentObject var voice: VoiceManager
    @State private var tab = 0

    var body: some View {
        ZStack {
            LinearGradient(colors: [LRTheme.background, Color.black], startPoint: .topLeading, endPoint: .bottomTrailing).ignoresSafeArea()
            TabView(selection: $tab) {
                HomeView().tabItem { Label("Home", systemImage: "house.fill") }.tag(0)
                GamesView().tabItem { Label("Games", systemImage: "gamecontroller.fill") }.tag(1)
                StatsView().tabItem { Label("Stats", systemImage: "chart.bar.fill") }.tag(2)
                SettingsView().tabItem { Label("Settings", systemImage: "gearshape.fill") }.tag(3)
            }
            .tint(LRTheme.accent)
        }
    }
}

struct HomeView: View {
    @EnvironmentObject var model: GameModel
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 18) {
                    HStack {
                        Image(systemName: "moon.stars.fill").font(.system(size: 42)).foregroundStyle(LRTheme.accent)
                        VStack(alignment: .leading) {
                            Text("LONE RIDER").font(.system(size: 29, weight: .black, design: .rounded))
                            Text("VOICE GAMES FOR THE ROAD").font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                    }
                    NeonCard {
                        Text("ROAD VOICE").font(.caption.bold()).foregroundStyle(LRTheme.accent)
                        Text(model.currentPrompt).font(.title3.bold()).padding(.top, 4)
                        HStack { Text(model.status); Spacer(); Text("XP \(model.score)") }.font(.caption.monospaced()).foregroundStyle(.secondary)
                    }
                    GamesView(compact: true)
                }.padding()
            }.navigationTitle("")
        }
    }
}

struct GamesView: View {
    @EnvironmentObject var model: GameModel
    var compact = false
    var body: some View {
        let grid = [GridItem(.adaptive(minimum: compact ? 145 : 155), spacing: 14)]
        ScrollView {
            LazyVGrid(columns: grid, spacing: 14) {
                ForEach(GameKind.allCases) { game in
                    NavigationLink {
                        GamePlayView(game: game)
                    } label: {
                        NeonCard {
                            Image(systemName: game.icon).font(.system(size: 28)).foregroundStyle(LRTheme.accent)
                            Text(game.rawValue).font(.headline).foregroundStyle(.white)
                            Text("VOICE FIRST").font(.caption2.bold()).foregroundStyle(.secondary)
                        }
                    }.simultaneousGesture(TapGesture().onEnded { model.start(game) })
                }
            }.padding()
        }
    }
}

struct GamePlayView: View {
    @EnvironmentObject var model: GameModel
    @EnvironmentObject var voice: VoiceManager
    let game: GameKind

    var body: some View {
        VStack(spacing: 20) {
            Spacer()
            Image(systemName: game.icon).font(.system(size: 52)).foregroundStyle(LRTheme.accent)
            Text(game.rawValue.uppercased()).font(.title.bold())
            Text(model.currentPrompt).font(.title2.bold()).multilineTextAlignment(.center).padding(.horizontal)
            Text(model.status).font(.headline.monospaced()).foregroundStyle(model.status.hasPrefix("CORRECT") ? LRTheme.accent : .white)
            HStack(spacing: 24) {
                Label("\(model.score) XP", systemImage: "bolt.fill")
                Label("x\(model.streak)", systemImage: "flame.fill")
            }.foregroundStyle(.secondary)
            Button {
                Task {
                    if !voice.authorized { await voice.requestPermissions() }
                    voice.speakThenListen(model.currentPrompt) { text in
                        model.submit(text)
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                            model.nextQuestion()
                            voice.speakThenListen(model.currentPrompt) { answer in model.submit(answer) }
                        }
                    }
                }
            } label: {
                Label(voice.listening ? "LISTENING…" : "PLAY / ANSWER", systemImage: voice.listening ? "waveform" : "mic.fill")
                    .font(.headline.bold()).frame(maxWidth: .infinity).padding()
                    .background(LRTheme.accent).foregroundStyle(.black).clipShape(Capsule())
            }.padding(.horizontal)
            if !voice.transcript.isEmpty { Text("“\(voice.transcript)”").font(.caption).foregroundStyle(.secondary) }
            Spacer()
        }
        .padding()
        .background(LRTheme.background.ignoresSafeArea())
        .onAppear { model.start(game) }
    }
}

struct StatsView: View {
    @EnvironmentObject var model: GameModel
    var body: some View {
        VStack(spacing: 18) {
            Text("RIDER STATS").font(.largeTitle.bold())
            NeonCard { Label("\(model.score) XP", systemImage: "bolt.fill").font(.title2.bold()) }
            NeonCard { Label("Best live streak: \(model.streak)", systemImage: "flame.fill").font(.title2.bold()) }
            Spacer()
        }.padding().background(LRTheme.background.ignoresSafeArea())
    }
}

struct SettingsView: View {
    @EnvironmentObject var voice: VoiceManager
    var body: some View {
        Form {
            Section("VOICE") {
                LabeledContent("Microphone + Speech", value: voice.authorized ? "Allowed" : "Not allowed")
                Button("Request permissions") { Task { await voice.requestPermissions() } }
            }
            Section("CARPLAY") {
                Text("CarPlay UI is prepared. Apple CarPlay voice-based conversation entitlement is required for device distribution.")
            }
            Section("ABOUT") { Text("Lone Rider iOS v0.1") }
        }.scrollContentBackground(.hidden).background(LRTheme.background)
    }
}
