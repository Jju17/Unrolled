//
//  StatsSheet.swift
//  Unrolled
//

import SwiftUI

struct StatsSheet: View {
    @Environment(\.dismiss) private var dismiss

    let stats: ActivityStats
    let dailyGoal: Int
    let gamificationEnabled: Bool
    let onReset: () -> Void

    @State private var resetConfirmOpen = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(verbatim: PileTitles.bytes(stats.bytesFreed))
                            .font(.system(size: 40, weight: .bold))
                            .monospacedDigit()
                        Text("Storage freed")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 6)
                    .accessibilityElement(children: .combine)
                    row("Photos reviewed", value: "\(stats.photosReviewed)")
                    row("Photos deleted", value: "\(stats.photosDeleted)")
                }
                if gamificationEnabled {
                    Section("Goals and streaks") {
                        row("Today", value: "\(stats.todayCount) / \(dailyGoal)")
                        HStack {
                            Label("Current streak", systemImage: "flame.fill")
                            Spacer()
                            Text("\(stats.currentStreak) days")
                                .foregroundStyle(.secondary)
                                .monospacedDigit()
                        }
                        row("Longest streak", value: String(localized: "\(stats.longestStreak) days"))
                    }
                    Section("Milestones") {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 90), spacing: 10)], spacing: 10) {
                            ForEach(Milestone.allCases, id: \.rawValue) { milestone in
                                milestoneCell(milestone)
                            }
                        }
                        .padding(.vertical, 6)
                    }
                }
                Section {
                    if stats.photosReviewed > 0 || stats.bytesFreed > 0 || stats.photosDeleted > 0 {
                        ShareCardButton(
                            headline: PileTitles.bytes(stats.bytesFreed),
                            detail: String(localized: "\(stats.photosReviewed) photos sorted"),
                            footer: String(localized: "Swipe your camera roll clean.")
                        )
                    }
                    Button("Reset stats", role: .destructive) { resetConfirmOpen = true }
                }
            }
            .navigationTitle("Your stats")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
            .confirmationDialog("Reset stats?", isPresented: $resetConfirmOpen, titleVisibility: .visible) {
                Button("Reset stats", role: .destructive, action: onReset)
            } message: {
                Text("Storage freed, counters, streak and milestones start again from zero.")
            }
        }
        .presentationDragIndicator(.visible)
    }

    private func row(_ label: LocalizedStringKey, value: String) -> some View {
        HStack {
            Text(label)
            Spacer()
            Text(verbatim: value)
                .foregroundStyle(.secondary)
                .monospacedDigit()
        }
    }

    private func milestoneCell(_ milestone: Milestone) -> some View {
        let reached = stats.reachedMilestones.contains(milestone.rawValue)
        return VStack(spacing: 6) {
            Image(systemName: glyph(milestone))
                .font(.system(size: 20))
                .foregroundStyle(reached ? Color.yellow : Color.secondary.opacity(0.5))
            Text(MilestoneText.headline(milestone))
                .font(.system(size: 11, weight: .medium))
                .multilineTextAlignment(.center)
                .foregroundStyle(reached ? .primary : .secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(reached ? Color.yellow.opacity(0.15) : Color(.secondarySystemFill))
        )
        .accessibilityElement(children: .combine)
        .accessibilityValue(reached ? Text("Reached") : Text("Not yet"))
    }

    private func glyph(_ milestone: Milestone) -> String {
        switch milestone.kind {
        case .reviewed: "checkmark.seal.fill"
        case .freed: "internaldrive.fill"
        case .streak: "flame.fill"
        }
    }
}
