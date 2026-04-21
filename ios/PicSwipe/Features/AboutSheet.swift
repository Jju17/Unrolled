//
//  AboutSheet.swift
//  PicSwipe
//

import SwiftUI

/// Presented via `.sheet(isPresented:)` — shows the app icon, version
/// and build, with a system "Close" button.
struct AboutSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                appIcon
                    .padding(.top, 32)
                Text("PicSwipe")
                    .font(.system(size: 22, weight: .bold))
                    .tracking(-0.4)
                    .padding(.top, 14)
                Text("Swipe your camera roll clean.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .padding(.top, 4)

                VStack(spacing: 8) {
                    infoRow("Version", value: "1.0.2")
                    infoRow("Build", value: "2026.04.128")
                }
                .padding(.horizontal, 20)
                .padding(.top, 24)

                Spacer()
            }
            .frame(maxWidth: .infinity)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
    }

    private var appIcon: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 18)
                .fill(
                    LinearGradient(
                        colors: [
                            Color(red: 0.937, green: 0.243, blue: 0.357),
                            Color(red: 0.976, green: 0.451, blue: 0.086)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .frame(width: 72, height: 72)
                .shadow(color: Color(red: 0.937, green: 0.243, blue: 0.357).opacity(0.35),
                        radius: 12, x: 0, y: 10)
            Image(systemName: "heart.fill")
                .font(.system(size: 34))
                .foregroundStyle(.white)
        }
    }

    private func infoRow(_ label: String, value: String) -> some View {
        HStack {
            Text(label).foregroundStyle(.secondary)
            Spacer()
            Text(value)
                .monospacedDigit()
                .fontWeight(.medium)
        }
        .font(.subheadline)
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(colorScheme == .dark ? Color.white.opacity(0.06) : Color.black.opacity(0.04))
        )
    }
}
