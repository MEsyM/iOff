import CarPlay
import UIKit

final class CarPlaySceneDelegate: UIResponder, CPTemplateApplicationSceneDelegate {
    private var interfaceController: CPInterfaceController?
    private let voice = VoiceManager.shared

    func templateApplicationScene(_ templateApplicationScene: CPTemplateApplicationScene,
                                  didConnect interfaceController: CPInterfaceController) {
        self.interfaceController = interfaceController

        let items = GameKind.allCases.map { game in
            let item = CPListItem(text: game.rawValue, detailText: "Voice-first road game")
            item.handler = { [weak self] _, completion in
                self?.start(game)
                completion()
            }
            return item
        }

        let list = CPListTemplate(title: "Lone Rider", sections: [CPListSection(items: items)])
        interfaceController.setRootTemplate(list, animated: true)
    }

    func templateApplicationScene(_ templateApplicationScene: CPTemplateApplicationScene,
                                  didDisconnectInterfaceController interfaceController: CPInterfaceController) {
        self.interfaceController = nil
        Task { @MainActor in voice.stopListening() }
    }

    private func start(_ game: GameKind) {
        Task { @MainActor in
            let model = GameModel()
            model.start(game)
            if !voice.authorized { await voice.requestPermissions() }

            let nowPlaying = CPInformationTemplate(
                title: game.rawValue,
                layout: .leading,
                items: [
                    CPInformationItem(title: "Question", detail: model.currentPrompt),
                    CPInformationItem(title: "Mode", detail: "Voice answer")
                ],
                actions: [
                    CPTextButton(title: "ASK", textStyle: .confirm) { [weak self] _ in
                        guard let self else { return }
                        self.voice.speakThenListen(model.currentPrompt) { answer in
                            model.submit(answer)
                            let alert = CPAlertTemplate(titleVariants: [model.status], actions: [
                                CPAlertAction(title: "Next", style: .default) { _ in
                                    model.nextQuestion()
                                    self.start(game)
                                }
                            ])
                            self.interfaceController?.presentTemplate(alert, animated: true)
                        }
                    }
                ])
            interfaceController?.pushTemplate(nowPlaying, animated: true)
        }
    }
}
