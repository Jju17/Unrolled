//
//  PhotoAssetImage.swift
//  PicSwipe
//

import Photos
import SwiftUI
import UIKit

/// Renders a `PHAsset` (looked up by local identifier) using a shared
/// `PHCachingImageManager`. Reloads when `assetID` or `targetSize`
/// changes. This is pure presentation — no TCA dependency: given an
/// ID, load.
struct PhotoAssetImage: View {
    let assetID: String
    let targetSize: CGSize
    /// `.fill` crops the image to cover the frame (used by the card
    /// stack so the card is always full-bleed). `.fit` letterboxes it
    /// so the whole photo is visible (used in fullscreen).
    var contentMode: ContentMode = .fill

    @State private var image: UIImage?

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
            } else {
                Color(white: 0.13)
                    .overlay(ProgressView().tint(.white))
            }
        }
        .task(id: assetID) {
            image = await PhotoImageCache.shared.loadImage(
                id: assetID,
                size: targetSize,
                contentMode: contentMode == .fill ? .aspectFill : .aspectFit
            )
        }
    }
}

/// Process-wide image cache. `PHCachingImageManager` keeps recently
/// requested thumbnails warm in memory so the card stack doesn't stall
/// when a new card enters the visible window.
///
/// `startCachingImages` is a hint — we call it from the feature on
/// `activeIndex` changes so the next few photos are decoded ahead of
/// time. Missing hints just fall back to on-demand loading.
actor PhotoImageCache {
    static let shared = PhotoImageCache()

    private let manager = PHCachingImageManager()
    private let options: PHImageRequestOptions
    private var cachedIDs: Set<String> = []

    init() {
        let opts = PHImageRequestOptions()
        opts.deliveryMode = .highQualityFormat
        opts.isNetworkAccessAllowed = true
        opts.resizeMode = .exact
        opts.isSynchronous = false
        self.options = opts
    }

    func loadImage(
        id: String,
        size: CGSize,
        contentMode: PHImageContentMode = .aspectFill
    ) async -> UIImage? {
        let result = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil)
        guard let asset = result.firstObject else { return nil }

        return await withCheckedContinuation { continuation in
            manager.requestImage(
                for: asset,
                targetSize: size == .zero ? PHImageManagerMaximumSize : size,
                contentMode: contentMode,
                options: options
            ) { image, _ in
                continuation.resume(returning: image)
            }
        }
    }

    /// Hint the cache to preload the given IDs at `size`. Idempotent —
    /// IDs already in `cachedIDs` are skipped so scrolling forward 1
    /// photo only warms the newcomer, not the whole window.
    func prefetch(ids: [String], size: CGSize) {
        let newIDs = ids.filter { !cachedIDs.contains($0) }
        guard !newIDs.isEmpty else { return }
        let assets = PHAsset.fetchAssets(withLocalIdentifiers: newIDs, options: nil)
        var phAssets: [PHAsset] = []
        assets.enumerateObjects { asset, _, _ in phAssets.append(asset) }
        guard !phAssets.isEmpty else { return }
        manager.startCachingImages(
            for: phAssets,
            targetSize: size == .zero ? PHImageManagerMaximumSize : size,
            contentMode: .aspectFill,
            options: options
        )
        cachedIDs.formUnion(newIDs)
    }
}
