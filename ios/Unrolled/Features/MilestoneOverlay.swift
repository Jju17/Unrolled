//
//  MilestoneOverlay.swift
//  Unrolled
//

import SwiftUI

struct MilestoneOverlay: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    let milestone: Milestone
    let onDismiss: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.45)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)
            if !reduceMotion {
                ConfettiView()
                    .ignoresSafeArea()
            }
            VStack(spacing: 14) {
                Image(systemName: glyph)
                    .font(.system(size: 44))
                    .foregroundStyle(.yellow)
                Text("Milestone reached")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.secondary)
                    .textCase(.uppercase)
                Text(MilestoneText.headline(milestone))
                    .font(.system(size: 30, weight: .bold))
                    .multilineTextAlignment(.center)
                Text(MilestoneText.detail(milestone))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                HStack(spacing: 12) {
                    ShareCardButton(
                        headline: MilestoneText.headline(milestone),
                        detail: MilestoneText.detail(milestone),
                        footer: String(localized: "Swipe your camera roll clean.")
                    )
                    .buttonStyle(.borderedProminent)
                    Button("Continue", action: onDismiss)
                        .buttonStyle(.bordered)
                }
                .padding(.top, 6)
            }
            .padding(28)
            .frame(maxWidth: 340)
            .background(RoundedRectangle(cornerRadius: 24).fill(.regularMaterial))
            .padding(24)
        }
    }

    private var glyph: String {
        switch milestone.kind {
        case .reviewed: "checkmark.seal.fill"
        case .freed: "internaldrive.fill"
        case .streak: "flame.fill"
        }
    }
}

enum MilestoneText {
    static func headline(_ milestone: Milestone) -> String {
        switch milestone.kind {
        case .reviewed: String(localized: "\(Int(milestone.threshold)) photos sorted")
        case .freed: String(localized: "\(PileTitles.bytes(milestone.threshold)) freed")
        case .streak: String(localized: "\(Int(milestone.threshold)) day streak")
        }
    }

    static func detail(_ milestone: Milestone) -> String {
        switch milestone.kind {
        case .reviewed: String(localized: "Every one of them decided by you.")
        case .freed: String(localized: "Space your phone got back.")
        case .streak: String(localized: "A goal reached every day.")
        }
    }
}
