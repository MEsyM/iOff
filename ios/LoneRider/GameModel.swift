import Foundation

enum GameKind: String, CaseIterable, Identifiable {
    case trivia = "Quick Trivia"
    case guessWho = "Guess WHO"
    case spelling = "Spelling Bee"
    case family = "Family Battle"
    var id: String { rawValue }
    var icon: String {
        switch self {
        case .trivia: return "brain.head.profile"
        case .guessWho: return "person.crop.circle.badge.questionmark"
        case .spelling: return "textformat.abc"
        case .family: return "person.3.fill"
        }
    }
}

struct GameQuestion {
    let prompt: String
    let answer: String
    let aliases: [String]
}

@MainActor
final class GameModel: ObservableObject {
    @Published var selectedGame: GameKind?
    @Published var currentPrompt = "Choose a game"
    @Published var score = 0
    @Published var streak = 0
    @Published var status = "READY"
    @Published var lastTranscript = ""
    @Published var contentVersion = "bundled"
    @Published var contentStatus = "OFFLINE FALLBACK"

    private var index = 0
    private var banks: [GameKind:[GameQuestion]] = Self.fallbackBanks

    init() {
        Task { await refreshContent() }
    }

    func refreshContent() async {
        do {
            let (version, loaded) = try await LiveContentRepository.shared.load()
            guard loaded.values.contains(where: { !$0.isEmpty }) else { return }
            banks = loaded
            contentVersion = version
            contentStatus = "LIVE"
            if selectedGame != nil { nextQuestion() }
        } catch {
            contentStatus = "CACHED / BUNDLED"
        }
    }

    func start(_ game: GameKind) {
        selectedGame = game
        index = 0
        score = 0
        streak = 0
        status = "READY"
        nextQuestion()
    }

    func nextQuestion() {
        guard let game = selectedGame, let bank = banks[game], !bank.isEmpty else { return }
        let q = bank[index % bank.count]
        currentPrompt = q.prompt
        status = "LISTENING"
    }

    func submit(_ transcript: String) {
        lastTranscript = transcript
        guard let game = selectedGame, let bank = banks[game], !bank.isEmpty else { return }
        let q = bank[index % bank.count]
        let normalized = normalize(transcript)
        let accepted = ([q.answer] + q.aliases).map(normalize)
        if accepted.contains(where: { !$0.isEmpty && (normalized.contains($0) || $0.contains(normalized)) }) {
            streak += 1
            let points = 100 + max(0, streak - 1) * 25
            score += points
            status = "CORRECT +\(points)"
        } else {
            streak = 0
            status = "WRONG · \(q.answer)"
        }
        index += 1
    }

    private func normalize(_ text: String) -> String {
        text.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
            .lowercased()
            .filter { $0.isLetter || $0.isNumber || $0 == " " }
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static let fallbackBanks: [GameKind:[GameQuestion]] = [
        .trivia: [
            .init(prompt: "Which planet is known as the Red Planet?", answer: "Mars", aliases: []),
            .init(prompt: "How many players are on the field for one soccer team?", answer: "11", aliases: ["eleven"])
        ],
        .guessWho: [
            .init(prompt: "I am a Czech-born hockey legend, famous for number 68. Who am I?", answer: "Jaromir Jagr", aliases: ["Jagr", "Jaromír Jágr"])
        ],
        .spelling: [
            .init(prompt: "Spell the word adventure.", answer: "adventure", aliases: [])
        ],
        .family: [
            .init(prompt: "Family Battle. Name the largest ocean on Earth.", answer: "Pacific", aliases: ["Pacific Ocean"])
        ]
    ]
}
