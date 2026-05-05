//
//  AboutSheet.swift
//  Unrolled
//

import SwiftUI

/// Presented via `.sheet(isPresented:)` — shows the app icon, version
/// and build, with a system "Close" button.
struct AboutSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    // Served by Firebase Hosting (see web/src/pages/Privacy.tsx +
    // Support.tsx). Must be reachable — App Store reviewers tap these.
    private let privacyPolicyURL = URL(string: "https://picswipe-4c451.web.app/privacy")!
    private let supportURL = URL(string: "https://picswipe-4c451.web.app/support")!

    // Version + build are read from the bundle so they can't drift from
    // what's actually shipping. Hardcoded strings drifted by 4 builds
    // during development — bad look for a reviewer scanning for care.
    private var versionString: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—"
    }
    private var buildString: String {
        Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "—"
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                appIcon
                    .padding(.top, 32)
                Text(verbatim: "Unrolled")
                    .font(.system(size: 22, weight: .bold))
                    .tracking(-0.4)
                    .padding(.top, 14)
                Text("Swipe your camera roll clean.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .padding(.top, 4)

                VStack(spacing: 8) {
                    infoRow(String(localized: "Version"), value: versionString)
                    infoRow(String(localized: "Build"), value: buildString)
                    linkRow(String(localized: "Privacy Policy"), url: privacyPolicyURL)
                    linkRow(String(localized: "Support"), url: supportURL)
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
        Image("AppLogo")
            .resizable()
            .interpolation(.high)
            .frame(width: 72, height: 72)
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .shadow(color: Color.black.opacity(0.18), radius: 12, x: 0, y: 10)
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

    private func linkRow(_ label: String, url: URL) -> some View {
        Link(destination: url) {
            HStack {
                Text(label).foregroundStyle(.primary)
                Spacer()
                Image(systemName: "arrow.up.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            .font(.subheadline)
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(colorScheme == .dark ? Color.white.opacity(0.06) : Color.black.opacity(0.04))
            )
        }
        .buttonStyle(.plain)
    }
}
