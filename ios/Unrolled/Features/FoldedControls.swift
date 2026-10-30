//
//  FoldedControls.swift
//  Unrolled
//

import ComposableArchitecture
import SwiftUI

/// The controls pane shown when iPhone Duo is partially folded: the deck
/// stays on the half away from the fold, everything tappable lives here.
struct FoldedControls<Title: View, Controls: View>: View {
    @Environment(\.colorScheme) private var colorScheme

    let store: StoreOf<SwipeFeature>
    let orientation: DeckLayout.FoldOrientation
    @ViewBuilder let title: () -> Title
    @ViewBuilder let controls: () -> Controls
    let animation: Animation

    private static var upNextCount: Int { 4 }

    private var textPrim: Color { DeckPalette.textPrimary(colorScheme) }
    private var textSec: Color { DeckPalette.textSecondary(colorScheme) }

    var body: some View {
        switch orientation {
        case .horizontal: tabletop
        case .vertical: book
        }
    }

    private var tabletop: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top) {
                title()
                Spacer(minLength: 12)
                controls()
            }
            if let photo = store.topPhoto {
                HStack(alignment: .top, spacing: 16) {
                    PhotoMetadataCard(
                        photo: photo,
                        byteCount: store.byteCounts[photo.id],
                        showLocation: store.settings.showLocation
                    )
                    .frame(width: 210)
                    VStack(spacing: 14) {
                        HStack(alignment: .top, spacing: 14) {
                            labelled(deleteButton(size: 72), Text("Delete"))
                            labelled(undoButton(size: 54), Text("Undo"))
                            labelled(keepButton(size: 72), Text("Keep"))
                        }
                        openInPhotosButton
                    }
                    .frame(maxWidth: .infinity)
                    Group {
                        if store.topSimilarGroup.count >= 2 {
                            similarGroup(thumbnail: 56)
                        } else {
                            upNext(count: 3, width: 56, height: 72)
                        }
                    }
                    .frame(width: 184, alignment: .leading)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(20)
    }

    private var book: some View {
        VStack(alignment: .leading, spacing: 16) {
            title()
            controls()
            if store.topPhoto != nil {
                pileProgress
                Spacer(minLength: 0)
                VStack(alignment: .trailing, spacing: 14) {
                    labelledRow(keepButton(size: 68), Text("Keep"))
                    labelledRow(undoButton(size: 50), Text("Undo"))
                    labelledRow(deleteButton(size: 68), Text("Delete"))
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
                Spacer(minLength: 0)
                if store.topSimilarGroup.count >= 2 {
                    similarGroup(thumbnail: 72)
                } else {
                    upNext(count: Self.upNextCount, width: 58, height: 76)
                }
                openInPhotosButton
            } else {
                Spacer(minLength: 0)
            }
        }
        .padding(20)
    }

    // MARK: - Pieces

    private func keepButton(size: CGFloat) -> some View {
        DeckActionButton(size: size, color: DeckPalette.keep, action: {
            store.send(.keepButtonTapped, animation: animation)
        }) {
            Image(systemName: "heart.fill")
                .font(.system(size: size * 0.38, weight: .semibold))
                .foregroundStyle(DeckPalette.keep)
        }
        .accessibilityLabel("Keep this photo")
    }

    private func deleteButton(size: CGFloat) -> some View {
        DeckActionButton(size: size, color: DeckPalette.drop, action: {
            store.send(.deleteButtonTapped, animation: animation)
        }) {
            Image(systemName: "xmark")
                .font(.system(size: size * 0.38, weight: .bold))
                .foregroundStyle(DeckPalette.drop)
        }
        .accessibilityLabel("Delete this photo")
        .accessibilityHint("The photo goes to the trash list")
    }

    private func undoButton(size: CGFloat) -> some View {
        DeckActionButton(size: size, color: DeckPalette.undo, disabled: store.history.isEmpty, action: {
            store.send(.undoButtonTapped, animation: animation)
        }) {
            Image(systemName: "arrow.uturn.backward")
                .font(.system(size: size * 0.36, weight: .semibold))
                .foregroundStyle(DeckPalette.undo)
        }
        .accessibilityLabel("Undo last choice")
    }

    private func labelled(_ button: some View, _ label: Text) -> some View {
        VStack(spacing: 8) {
            button
            label
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(textSec)
        }
        .accessibilityElement(children: .combine)
    }

    private func labelledRow(_ button: some View, _ label: Text) -> some View {
        HStack(spacing: 16) {
            label
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(textPrim)
            button
        }
        .accessibilityElement(children: .combine)
    }

    private var openInPhotosButton: some View {
        Button {
            store.send(.viewInPhotosTapped)
        } label: {
            Label("Open Photos app", systemImage: "photo.on.rectangle")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(textPrim)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(Capsule().fill(DeckPalette.chip(colorScheme)))
                .frame(minHeight: 44)
        }
        .buttonStyle(.plain)
    }

    private var pileProgress: some View {
        let total = store.pilePhotoCount
        let done = max(0, total - store.remainingCount)
        return VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("Pile progress")
                    .font(.system(size: 12))
                    .foregroundStyle(textSec)
                Spacer()
                Text(verbatim: "\(done) / \(total)")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(textPrim)
                    .monospacedDigit()
            }
            ProgressView(value: Double(done), total: Double(max(total, 1)))
                .tint(DeckPalette.keep)
        }
    }

    private func upNext(count: Int, width: CGFloat, height: CGFloat) -> some View {
        let start = store.activeIndex + 1
        let end = min(start + count, store.photos.count)
        let next = start < end ? Array(store.photos[start..<end]) : []
        return VStack(alignment: .leading, spacing: 8) {
            Text("Up next")
                .font(.system(size: 11, weight: .semibold))
                .tracking(2)
                .textCase(.uppercase)
                .foregroundStyle(textSec)
            HStack(spacing: 8) {
                ForEach(next) { photo in
                    thumbnail(photo, width: width, height: height, radius: 10)
                }
            }
        }
        .accessibilityHidden(true)
    }

    private func similarGroup(thumbnail side: CGFloat) -> some View {
        let group = Array(store.topSimilarGroup.prefix(3))
        return VStack(alignment: .leading, spacing: 8) {
            Text("\(store.topSimilarGroup.count) similar")
                .font(.system(size: 11, weight: .semibold))
                .tracking(2)
                .textCase(.uppercase)
                .foregroundStyle(textSec)
            HStack(spacing: 8) {
                ForEach(group) { photo in
                    thumbnail(photo, width: side, height: side, radius: 12)
                }
            }
            Button {
                store.send(.similarBadgeTapped)
            } label: {
                Text("Similar photos")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(textPrim)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Capsule().fill(DeckPalette.chip(colorScheme)))
                    .frame(minHeight: 44)
            }
            .buttonStyle(.plain)
            .accessibilityHint(Text("Review the similar photos together"))
        }
    }

    private func thumbnail(_ photo: PhotoAsset, width: CGFloat, height: CGFloat, radius: CGFloat) -> some View {
        PhotoAssetImage(assetID: photo.id, targetSize: CGSize(width: 240, height: 240))
            .frame(width: width, height: height)
            .clipShape(RoundedRectangle(cornerRadius: radius))
    }
}
