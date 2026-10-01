import SwiftUI

enum LRTheme {
    static let background = Color(red: 0.025, green: 0.035, blue: 0.05)
    static let panel = Color(red: 0.055, green: 0.075, blue: 0.105)
    static let accent = Color(red: 0.23, green: 0.86, blue: 0.80)
    static let secondary = Color(red: 0.32, green: 0.55, blue: 1.0)
}

struct NeonCard<Content: View>: View {
    let content: Content
    init(@ViewBuilder content: () -> Content) { self.content = content() }
    var body: some View {
        content
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(LRTheme.panel.opacity(0.94))
            .overlay(RoundedRectangle(cornerRadius: 22).stroke(LRTheme.accent.opacity(0.45), lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 22))
    }
}
