//
//  SessionSummaryView.swift
//  Unrolled
//

import SwiftUI

/// Done view: a session recap when photos were reviewed, a plain
/// "nothing left" otherwise.
struct SessionSummaryView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    let reviewed: Int
    let kept: Int
    let deleted: Int
    let bytesToFree: Int64
    let duration: TimeInterval
    let photosPerMinute: Double
    let isOnAllPhotos: Bool
    let keepColor: Color
    let dropColor: Color
    let textPrim: Color
    let textSec: Color
    let border: Color
    let onChangePile: () -> Void
    let onViewAll: () -> Void
    let onStartOver: () -> Void

    var body: some View {
        ZStack {
            VStack(spacing: 8) {
                Image(systemName: reviewed > 0 ? "checkmark.seal.fill" : "tray")
                    .font(.system(size: 44))
                    .foregroundStyle(reviewed > 0 ? keepColor : textSec)
                    .padding(.bottom, 4)
                if reviewed > 0 {
                    Text("Pile finished")
                        .font(.system(size: 28, weight: .bold))
                        .tracking(-0.8)
                        .foregroundStyle(textPrim)
                    grid
                        .padding(.top, 8)
                    Text("\(PileTitles.duration(duration)) at \(photosPerMinute, specifier: "%.0f") photos/min")
                        .font(.system(size: 13))
                        .foregroundStyle(textSec)
                        .padding(.top, 4)
                } else {
                    Text("Nothing left here")
                        .font(.system(size: 28, weight: .bold))
                        .tracking(-0.8)
                        .foregroundStyle(textPrim)
                    Text("Every photo of this pile is already sorted.")
                        .font(.system(size: 14))
                        .foregroundStyle(textSec)
                        .multilineTextAlignment(.center)
                }

                VStack(spacing: 10) {
                    Button("Change pile", action: onChangePile)
                        .buttonStyle(SummaryPrimaryButtonStyle(color: dropColor))
                    if !isOnAllPhotos {
                        Button("View all my photos", action: onViewAll)
                            .buttonStyle(SummarySecondaryButtonStyle(color: textPrim, border: border))
                    }
                    Button("Start over", action: onStartOver)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(textSec)
                        .padding(.top, 2)
                }
                .padding(.top, 16)
            }
            .padding(24)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .overlay(
                RoundedRectangle(cornerRadius: 24)
                    .strokeBorder(border, style: StrokeStyle(lineWidth: 2, dash: [8, 6]))
            )
            if reviewed > 0, !reduceMotion {
                ConfettiView()
            }
        }
    }

    private var grid: some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
            stat(value: "\(reviewed)", label: "Reviewed", color: textPrim)
            stat(value: "\(kept)", label: "Kept", color: keepColor)
            stat(value: "\(deleted)", label: "Deleted", color: dropColor)
            stat(value: PileTitles.bytes(bytesToFree), label: "To free", color: textPrim)
        }
    }

    private func stat(value: String, label: LocalizedStringKey, color: Color) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.system(size: 22, weight: .bold))
                .monospacedDigit()
                .foregroundStyle(color)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(textSec)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(RoundedRectangle(cornerRadius: 12).fill(border.opacity(0.6)))
        .accessibilityElement(children: .combine)
    }
}

struct SummaryPrimaryButtonStyle: ButtonStyle {
    let color: Color
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, 22)
            .padding(.vertical, 12)
            .background(Capsule().fill(color))
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

struct SummarySecondaryButtonStyle: ButtonStyle {
    let color: Color
    let border: Color
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(color)
            .padding(.horizontal, 22)
            .padding(.vertical, 12)
            .background(Capsule().stroke(border, lineWidth: 1))
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}
