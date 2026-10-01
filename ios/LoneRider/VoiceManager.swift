import AVFoundation
import Speech

@MainActor
final class VoiceManager: NSObject, ObservableObject, AVSpeechSynthesizerDelegate {
    static let shared = VoiceManager()

    @Published var authorized = false
    @Published var listening = false
    @Published var transcript = ""

    private let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
    private let audioEngine = AVAudioEngine()
    private let synthesizer = AVSpeechSynthesizer()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var completion: ((String) -> Void)?

    override private init() {
        super.init()
        synthesizer.delegate = self
    }

    func requestPermissions() async {
        let speech = await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { continuation.resume(returning: $0) }
        }
        let mic = await AVAudioApplication.requestRecordPermission()
        authorized = speech == .authorized && mic
    }

    func speakThenListen(_ text: String, onResult: @escaping (String) -> Void) {
        stopListening()
        completion = onResult
        let utterance = AVSpeechUtterance(string: text)
        utterance.rate = 0.46
        utterance.voice = AVSpeechSynthesisVoice(language: "en-US")
        synthesizer.speak(utterance)
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor in try? startListening() }
    }

    func startListening() throws {
        guard authorized else { return }
        stopListening()
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playAndRecord, mode: .spokenAudio, options: [.duckOthers, .defaultToSpeaker, .allowBluetoothHFP])
        try session.setActive(true, options: .notifyOthersOnDeactivation)

        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        self.request = request

        let input = audioEngine.inputNode
        let format = input.outputFormat(forBus: 0)
        input.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in request.append(buffer) }
        audioEngine.prepare()
        try audioEngine.start()
        listening = true

        task = recognizer?.recognitionTask(with: request) { [weak self] result, error in
            guard let self else { return }
            Task { @MainActor in
                if let result {
                    self.transcript = result.bestTranscription.formattedString
                    if result.isFinal {
                        let text = self.transcript
                        self.stopListening()
                        self.completion?(text)
                        self.completion = nil
                    }
                }
                if error != nil { self.stopListening() }
            }
        }
    }

    func stopListening() {
        task?.cancel()
        task = nil
        request?.endAudio()
        request = nil
        if audioEngine.isRunning { audioEngine.stop() }
        audioEngine.inputNode.removeTap(onBus: 0)
        listening = false
    }
}
