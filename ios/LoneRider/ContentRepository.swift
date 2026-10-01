import Foundation

struct LRManifest: Decodable {
    struct GameRef: Decodable { let path: String; let version: String }
    let schemaVersion: Int
    let contentVersion: String
    let games: [String: GameRef]
}

struct LRTriviaDocument: Decodable {
    struct Item: Decodable {
        struct Localized: Decodable {
            struct Value: Decodable {
                let prompt: String
                let answers: [String]
                let explanation: String
            }
            let en: Value
            let cs: Value
        }
        let id: String
        let difficulty: Int
        let category: String
        let localized: Localized
        let enabled: Bool
    }
    let schemaVersion: Int
    let version: String
    let items: [Item]
}

struct LRGuessWhoDocument: Decodable {
    struct Item: Decodable {
        struct Clues: Decodable { let en: [String]; let cs: [String] }
        let id: String
        let name: String
        let aliases: [String]
        let region: String
        let difficulty: Int
        let clues: Clues
        let enabled: Bool
    }
    let schemaVersion: Int
    let version: String
    let items: [Item]
}

struct LRSpellingDocument: Decodable {
    struct Item: Decodable {
        struct Hints: Decodable { let en: String; let cs: String }
        let id: String
        let difficulty: Int
        let word: String
        let hints: Hints
        let enabled: Bool
    }
    let schemaVersion: Int
    let version: String
    let items: [Item]
}

actor LiveContentRepository {
    static let shared = LiveContentRepository()

    private let decoder = JSONDecoder()
    private let session: URLSession
    private let baseURL: URL
    private let cacheDir: URL

    init(
        baseURL: URL = URL(string: ProcessInfo.processInfo.environment["LONE_RIDER_CONTENT_BASE_URL"] ?? "https://raw.githubusercontent.com/MEsyM/iOff/codex/lone-rider-content-core-v1/shared/content/v1/")!
    ) {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 8
        config.timeoutIntervalForResource = 15
        self.session = URLSession(configuration: config)
        self.baseURL = baseURL
        let root = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        self.cacheDir = root.appendingPathComponent("LoneRiderContent", isDirectory: true)
        try? FileManager.default.createDirectory(at: cacheDir, withIntermediateDirectories: true)
    }

    func load() async throws -> (String, [GameKind:[GameQuestion]]) {
        let manifestData = try await loadData(path: "manifest.json", cacheName: "manifest.json")
        let manifest = try decoder.decode(LRManifest.self, from: manifestData)
        guard manifest.schemaVersion == 1 else { throw ContentError.unsupportedSchema }

        async let triviaData = loadData(path: manifest.games["trivia"]!.path, cacheName: "trivia.json")
        async let guessData = loadData(path: manifest.games["guessWho"]!.path, cacheName: "guess-who.json")
        async let spellingData = loadData(path: manifest.games["spellingBee"]!.path, cacheName: "spelling-bee.json")

        let trivia = try decoder.decode(LRTriviaDocument.self, from: await triviaData)
        let guess = try decoder.decode(LRGuessWhoDocument.self, from: await guessData)
        let spelling = try decoder.decode(LRSpellingDocument.self, from: await spellingData)

        let triviaBank = trivia.items.filter(\.enabled).map {
            GameQuestion(prompt: $0.localized.en.prompt,
                         answer: $0.localized.en.answers.first ?? "",
                         aliases: Array($0.localized.en.answers.dropFirst()))
        }
        let guessBank = guess.items.filter(\.enabled).map {
            GameQuestion(prompt: ($0.clues.en.first ?? "Who am I?") + " Who am I?",
                         answer: $0.name,
                         aliases: $0.aliases)
        }
        let spellingBank = spelling.items.filter(\.enabled).map {
            GameQuestion(prompt: "Spell the word \($0.word).",
                         answer: $0.word,
                         aliases: [])
        }

        return (manifest.contentVersion, [
            .trivia: triviaBank,
            .guessWho: guessBank,
            .spelling: spellingBank,
            .family: triviaBank
        ])
    }

    private func loadData(path: String, cacheName: String) async throws -> Data {
        let cache = cacheDir.appendingPathComponent(cacheName)
        do {
            let url = baseURL.appendingPathComponent(path)
            var request = URLRequest(url: url)
            request.cachePolicy = .reloadIgnoringLocalCacheData
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode), !data.isEmpty else {
                throw ContentError.badResponse
            }
            try data.write(to: cache, options: .atomic)
            return data
        } catch {
            if let cached = try? Data(contentsOf: cache), !cached.isEmpty { return cached }
            throw error
        }
    }

    enum ContentError: Error { case unsupportedSchema, badResponse }
}
