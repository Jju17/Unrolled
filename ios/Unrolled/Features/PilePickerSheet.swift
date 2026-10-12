//
//  PilePickerSheet.swift
//  Unrolled
//

import SwiftUI

struct PilePickerView: View {
    @Environment(\.dismiss) private var dismiss

    let piles: [Pile]
    let current: PileSelector
    let onPick: (PileSelector) -> Void

    private var sessions: [Pile] {
        piles.filter { pile in
            switch pile.selector {
            case .all, .onThisDay, .oneYearAgo, .screenshots, .videos: true
            case .month: PileTitles.isCurrentMonth(pile.selector)
            case .album: false
            }
        }
    }

    private var months: [Pile] {
        piles.filter {
            if case .month = $0.selector { return !PileTitles.isCurrentMonth($0.selector) }
            return false
        }
    }

    private var albums: [Pile] {
        piles.filter {
            if case .album = $0.selector { return true }
            return false
        }
    }

    var body: some View {
        NavigationStack {
            List {
                section("Sessions", sessions)
                section("Months", months)
                section("Albums", albums)
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Piles")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    @ViewBuilder
    private func section(_ title: LocalizedStringKey, _ piles: [Pile]) -> some View {
        if !piles.isEmpty {
            Section(title) {
                ForEach(piles) { pile in
                    Button {
                        onPick(pile.selector)
                        dismiss()
                    } label: {
                        row(for: pile)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func row(for pile: Pile) -> some View {
        HStack(spacing: 14) {
            thumbnail(for: pile)
                .frame(width: 44, height: 44)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            VStack(alignment: .leading, spacing: 4) {
                Text(verbatim: PileTitles.title(for: pile.selector, name: pile.name))
                    .font(.body)
                if pile.remainingCount == 0 {
                    Text("\(pile.photoCount) photos, done")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else {
                    Text("\(pile.photoCount) photos, \(pile.remainingCount) left")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                ProgressView(value: pile.progress)
                    .tint(pile.remainingCount == 0 ? .green : .accentColor)
            }
            Spacer()
            if pile.selector == current {
                Image(systemName: "checkmark")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.tint)
            }
        }
        .contentShape(Rectangle())
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private func thumbnail(for pile: Pile) -> some View {
        if let keyAssetID = pile.keyAssetID {
            PhotoAssetImage(assetID: keyAssetID, targetSize: CGSize(width: 132, height: 132))
        } else {
            ZStack {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color(.secondarySystemFill))
                Image(systemName: glyph(for: pile.selector))
                    .font(.system(size: 18))
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func glyph(for selector: PileSelector) -> String {
        switch selector {
        case .all: "photo.on.rectangle"
        case .album: "folder"
        case .month: "calendar"
        case .onThisDay: "clock.arrow.circlepath"
        case .oneYearAgo: "calendar.badge.clock"
        case .screenshots: "rectangle.dashed"
        case .videos: "video"
        }
    }
}
