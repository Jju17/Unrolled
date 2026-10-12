//
//  FullscreenPhotoView.swift
//  Unrolled
//

import Photos
import PhotosUI
import SwiftUI
import UIKit

/// Full-screen pager. Opens on the current top photo (`startIndex`),
/// lets the user swipe horizontally between adjacent photos in the
/// undecided slice (`photos[startIndex...]`), and exposes keep/delete
/// buttons that route through the reducer via `onMarkAtRelativeIndex`.
///
/// Per-photo affordances:
/// - Pinch to zoom (4x max), pan once zoomed (delegated to `UIScrollView`)
/// - Live Photo playback when `playLivePhotos` is on and the asset is one
/// - Date + place name overlay (place name resolved lazily via the
///   shared `LocationGeocoder`)
struct FullscreenPhotoView: View {
    @Environment(\.dismiss) private var dismiss

    let photos: [PhotoAsset]
    let startIndex: Int
    let settings: UserSettings
    let onMarkAtRelativeIndex: (_ localIdx: Int, _ kept: Bool) -> Void

    @State private var currentPage: Int = 0

    /// The paged `TabView` builds every page eagerly, so only a short
    /// window of the undecided tail is handed to it.
    private static let pageWindow = 20

    private let keepColor = Color(red: 0.188, green: 0.824, blue: 0.541)
    private let dropColor = Color(red: 0.937, green: 0.243, blue: 0.357)

    /// Slice of photos the pager renders: the undecided tail only, so
    /// backward scrolling never lands on a photo already in `history`.
    private var visiblePhotos: [PhotoAsset] {
        guard startIndex >= 0, startIndex < photos.count else { return [] }
        let end = min(startIndex + Self.pageWindow, photos.count)
        return Array(photos[startIndex..<end])
    }

    private var remainingCount: Int { max(0, photos.count - startIndex) }

    private var currentPhoto: PhotoAsset? {
        guard !visiblePhotos.isEmpty else { return nil }
        let clamped = max(0, min(currentPage, visiblePhotos.count - 1))
        return visiblePhotos[clamped]
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            TabView(selection: $currentPage) {
                ForEach(Array(visiblePhotos.enumerated()), id: \.element.id) { offset, photo in
                    FullscreenPhotoPage(photo: photo, playLivePhotos: settings.playLivePhotos)
                        .tag(offset)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .ignoresSafeArea()

            VStack {
                topBar
                Spacer()
                metadataOverlay
                actionBar
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 28)
        }
        .statusBarHidden()
    }

    private var topBar: some View {
        HStack {
            if remainingCount > 1 {
                Text("\(currentPage + 1) / \(remainingCount)")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.white.opacity(0.8))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Capsule().fill(Color.black.opacity(0.5)))
            }
            Spacer()
            Button { dismiss() } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 34, height: 34)
                    .background(Circle().fill(Color.black.opacity(0.5)))
                    .frame(width: 44, height: 44)
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("Close"))
        }
    }

    @ViewBuilder
    private var metadataOverlay: some View {
        if let photo = currentPhoto {
            FullscreenMetadataStrip(photo: photo, settings: settings)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 4)
                .padding(.bottom, 14)
        }
    }

    private var actionBar: some View {
        HStack(spacing: 18) {
            Spacer()
            actionButton(systemImage: "xmark", color: dropColor) {
                onMarkAtRelativeIndex(currentPage, false)
            }
            .accessibilityLabel(Text("Delete this photo"))
            actionButton(systemImage: "heart.fill", color: keepColor) {
                onMarkAtRelativeIndex(currentPage, true)
            }
            .accessibilityLabel(Text("Keep this photo"))
            Spacer()
        }
    }

    private func actionButton(
        systemImage: String, color: Color, action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(color)
                .frame(width: 60, height: 60)
                .background(
                    Circle()
                        .fill(.white)
                        .shadow(color: color.opacity(0.2), radius: 8, x: 0, y: 4)
                )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Single page

/// One TabView page. Owns its own zoom state so each photo can be
/// pinched independently: switching pages resets to fit-to-screen.
private struct FullscreenPhotoPage: View {
    let photo: PhotoAsset
    let playLivePhotos: Bool

    var body: some View {
        ZoomableScrollView(maxScale: 4) {
            GeometryReader { geo in
                Group {
                    if photo.isVideo {
                        VideoPage(assetID: photo.id)
                    } else if playLivePhotos && photo.isLivePhoto {
                        LivePhotoPlayer(assetID: photo.id)
                    } else {
                        PhotoAssetImage(
                            assetID: photo.id,
                            targetSize: CGSize(
                                width: geo.size.width * UIScreen.main.scale,
                                height: geo.size.height * UIScreen.main.scale
                            ),
                            contentMode: .fit
                        )
                    }
                }
                .frame(width: geo.size.width, height: geo.size.height)
            }
        }
        .ignoresSafeArea()
    }
}

// MARK: - Metadata strip

private struct FullscreenMetadataStrip: View {
    let photo: PhotoAsset
    let settings: UserSettings

    @State private var placeName: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            if let date = photo.creationDate {
                Text(Self.dateFormatter.string(from: date))
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(.white)
            }
            if photo.isVideo {
                Text("Video, \(PileTitles.clock(photo.durationSeconds ?? 0))")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.white.opacity(0.92))
            }
            if settings.showLocation, let placeName {
                HStack(spacing: 4) {
                    Image(systemName: "location.fill")
                        .font(.system(size: 11, weight: .semibold))
                    Text(placeName)
                        .font(.system(size: 13, weight: .medium))
                }
                .foregroundStyle(.white.opacity(0.92))
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color.black.opacity(0.45))
        )
        .task(id: geocodeKey) {
            placeName = nil
            guard settings.showLocation,
                  let lat = photo.latitude, let lng = photo.longitude
            else { return }
            placeName = await LocationGeocoder.shared.placeName(
                latitude: lat, longitude: lng
            )
        }
    }

    private var geocodeKey: String {
        "\(photo.id)|\(settings.showLocation ? 1 : 0)"
    }

    private static let dateFormatter: DateFormatter = {
        let df = DateFormatter()
        df.dateStyle = .medium
        df.timeStyle = .short
        return df
    }()
}
