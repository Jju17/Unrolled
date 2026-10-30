//
//  DeckControls.swift
//  Unrolled
//

import SwiftUI

enum DeckPalette {
    static let keep = Color(red: 0.188, green: 0.824, blue: 0.541)
    static let drop = Color(red: 0.937, green: 0.243, blue: 0.357)
    static let undo = Color(red: 0.961, green: 0.725, blue: 0.271)

    static func page(_ scheme: ColorScheme) -> Color {
        scheme == .dark
            ? Color(red: 0.055, green: 0.055, blue: 0.071)
            : Color(red: 0.965, green: 0.961, blue: 0.945)
    }

    static func textPrimary(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? .white : Color(red: 0.102, green: 0.102, blue: 0.102)
    }

    static func textSecondary(_ scheme: ColorScheme) -> Color {
        (scheme == .dark ? Color.white : Color.black).opacity(0.55)
    }

    static func surface(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.102, green: 0.102, blue: 0.125) : .white
    }

    static func border(_ scheme: ColorScheme) -> Color {
        (scheme == .dark ? Color.white : Color.black).opacity(scheme == .dark ? 0.08 : 0.06)
    }

    static func chip(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? Color.white.opacity(0.08) : Color.black.opacity(0.05)
    }
}

struct ActionButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.92 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

struct DeckActionButton<Content: View>: View {
    @Environment(\.colorScheme) private var colorScheme

    let size: CGFloat
    let color: Color
    var disabled = false
    let action: () -> Void
    @ViewBuilder let content: () -> Content

    var body: some View {
        Button(action: action) {
            ZStack {
                Circle()
                    .fill(DeckPalette.surface(colorScheme))
                    .overlay(Circle().stroke(DeckPalette.border(colorScheme), lineWidth: 1))
                    .shadow(color: color.opacity(disabled ? 0 : 0.2), radius: 9, x: 0, y: 6)
                content()
            }
            .frame(width: size, height: size)
        }
        .buttonStyle(ActionButtonStyle())
        .disabled(disabled)
        .opacity(disabled ? 0.35 : 1)
    }
}
