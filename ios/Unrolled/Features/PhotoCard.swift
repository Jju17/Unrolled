//
//  PhotoCard.swift
//  Unrolled
//

import SwiftUI

struct PhotoCard: View {
    let photo: PhotoAsset
    let keepColor: Color
    let dropColor: Color
    let keepOpacity: Double
    let dropOpacity: Double
    let showLabels: Bool
    /// Whether the user has opted-in to seeing GPS-derived place names
    /// on the card. When `false` we don't even kick off the geocode task.
    var showLocation: Bool = false
    /// When `true` AND this is the top card AND the photo is a Live
    /// Photo, render with `PHLivePhotoView` so the user can press-and-
    /// hold to play the motion portion (Photos.app-style affordance).
    /// Non-top cards always stay static for performance.
    var playLivePhotos: Bool = false

    /// Resolved place name, populated asynchronously by the geocoder
    /// task below. Only used by the top card (others wouldn't show
    /// labels anyway).
    @State private var placeName: String?

    /// Top card upgrades to `PHLivePhotoView` when the user has the
    /// toggle on and the asset is a Live Photo. The deeper-stack cards
    /// always stay static — they're not interactable and we don't want
    /// to spin up multiple `PHLivePhotoView` instances at once.
    private var renderAsLivePhoto: Bool {
        showLabels && playLivePhotos && photo.isLivePhoto
    }

    var body: some View {
        GeometryReader { geo in
            ZStack {
                // Static photo is **always** rendered as the base layer.
                // Its `@State image` is preserved across stack transitions
                // (depth 1 → 0) and the cache fallback ensures pixels
                // stay on screen even if SwiftUI tears the view down.
                //
                // For Live Photos (top card + toggle on), `PHLivePhotoView`
                // is overlaid above the static. While it loads its own
                // copy of the asset, the static below already shows the
                // exact same still — visually identical, no flash. Once
                // loaded, the user's long-press plays the motion through
                // the overlay.
                PhotoAssetImage(
                    assetID: photo.id,
                    targetSize: CGSize(
                        width: geo.size.width * UIScreen.main.scale,
                        height: geo.size.height * UIScreen.main.scale
                    )
                )
                .frame(width: geo.size.width, height: geo.size.height)
                .clipped()

                if renderAsLivePhoto {
                    LivePhotoPlayer(
                        assetID: photo.id,
                        autoPlay: false,
                        contentMode: .scaleAspectFill
                    )
                    .frame(width: geo.size.width, height: geo.size.height)
                    .clipped()
                    .allowsHitTesting(true)
                }

                LinearGradient(
                    colors: [.clear, .black.opacity(0.55)],
                    startPoint: .top, endPoint: .bottom
                )
                .allowsHitTesting(false)

                // Date + optional location (bottom left)
                VStack(alignment: .leading, spacing: 2) {
                    if let date = photo.creationDate {
                        Text(Self.dateFormatter.string(from: date))
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(.white)
                    }
                    if showLocation, let placeName {
                        HStack(spacing: 4) {
                            Image(systemName: "location.fill")
                                .font(.system(size: 11, weight: .semibold))
                            Text(placeName)
                                .font(.system(size: 13, weight: .medium))
                        }
                        .foregroundStyle(.white.opacity(0.92))
                    }
                }
                .padding(18)
                .frame(width: geo.size.width, height: geo.size.height,
                       alignment: .bottomLeading)

                // KEEP/DELETE stamps. Rendered unconditionally (opacity
                // already drives visibility through `keepOpacity`/`dropOpacity`)
                // so SwiftUI's ZStack child count doesn't fluctuate when
                // a card transitions from depth 1 → 0. Adding/removing
                // siblings during that transition has been observed to
                // re-key surrounding views and reset the `PhotoAssetImage`
                // `@State`, producing a half-second black flash.
                stamp(text: "KEEP", color: keepColor,
                      opacity: showLabels ? keepOpacity : 0, rotation: -18)
                    .padding(22)
                    .frame(width: geo.size.width, height: geo.size.height,
                           alignment: .topLeading)
                stamp(text: "DELETE", color: dropColor,
                      opacity: showLabels ? dropOpacity : 0, rotation: 18)
                    .padding(22)
                    .frame(width: geo.size.width, height: geo.size.height,
                           alignment: .topTrailing)
            }
            .frame(width: geo.size.width, height: geo.size.height)
            .background(
                RoundedRectangle(cornerRadius: 24).fill(Color(white: 0.13))
            )
            .clipShape(RoundedRectangle(cornerRadius: 24))
            .shadow(color: .black.opacity(0.18), radius: 14, x: 0, y: 14)
            .shadow(color: .black.opacity(0.10), radius: 4, x: 0, y: 4)
        }
        // Geocoding fires per-photo (not per-`showLabels` flip). The
        // resolver caches by quantized lat/lng so non-top cards either
        // hit the cache instantly or warm it for the moment they get
        // promoted — and we never re-fire the task on a depth 1 → 0
        // transition, which used to cascade enough state churn to
        // contribute to the half-second black flash on the new top card.
        .task(id: photo.id) {
            placeName = nil
            guard showLocation,
                  let lat = photo.latitude, let lng = photo.longitude
            else { return }
            placeName = await LocationGeocoder.shared.placeName(
                latitude: lat, longitude: lng
            )
        }
    }

    private func stamp(text: String, color: Color, opacity: Double, rotation: Double) -> some View {
        Text(text)
            .font(.system(size: 28, weight: .heavy))
            .tracking(2)
            .foregroundStyle(color)
            .padding(.horizontal, 18)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(Color.white.opacity(0.92))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(color, lineWidth: 3))
            )
            .rotationEffect(.degrees(rotation))
            .scaleEffect(0.6 + opacity * 0.4)
            .opacity(opacity)
            .allowsHitTesting(false)
    }

    private static let dateFormatter: DateFormatter = {
        let df = DateFormatter()
        df.dateStyle = .medium
        df.timeStyle = .none
        return df
    }()
}
