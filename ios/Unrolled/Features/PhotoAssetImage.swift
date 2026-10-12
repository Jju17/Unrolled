//
//  PhotoAssetImage.swift
//  Unrolled
//

import Photos
import SwiftUI
import UIKit

/// Renders a `PHAsset` (looked up by local identifier) using a shared
/// `PHCachingImageManager`. Reloads when `assetID` changes. This is pure
/// presentation, no TCA dependency: given an ID, load.
///
/// Loading is **progressive**: the cache yields a thumbnail first,
/// then upgrades to the high-quality version as it arrives. For
/// iCloud-only photos this means the user sees *something* instantly
/// while the full file streams down (or stays as the thumbnail if
/// network is unreachable), instead of the previous black-card-while-
/// loading.
///
/// Resilience to SwiftUI re-rendering: the latest decoded image for
/// every asset is kept in `PhotoImageCache.cachedImage(id:)`, so even
/// if SwiftUI tears down this view's `@State` during a stack shift
/// (the depth-1 card becoming the top card has bitten us multiple
/// times), the body still renders the cached pixels synchronously
/// while the async task spins back up. No black flash.
struct PhotoAssetImage: View {
    let assetID: String
    let targetSize: CGSize
    /// `.fill` crops the image to cover the frame (used by the card
    /// stack so the card is always full-bleed). `.fit` letterboxes it
    /// so the whole photo is visible (used in fullscreen).
    var contentMode: ContentMode = .fill

    @State private var image: UIImage?

    /// Read the cached image once per body evaluation so the synchronous
    /// fallback path doesn't pay an `NSCache.object(forKey:)` call for
    /// the no-cache case (the lookup is cheap but `body` is hot).
    private var displayImage: UIImage? {
        image ?? PhotoImageCache.shared.cachedImage(id: assetID)
    }

    var body: some View {
        Group {
            if let displayImage {
                Image(uiImage: displayImage)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
            } else {
                Color(white: 0.13)
                    .overlay(ProgressView().tint(.white))
            }
        }
        .task(id: assetID) {
            // Don't blank `image` on task start: if SwiftUI reused this
            // view but re-fired the task (rare but observed), nuking the
            // local state would force a flash even though the cache has
            // the pixels. The async stream will overwrite progressively
            // anyway, and the moment we *do* have a new assetID, the
            // cached fallback ensures we still render *something*.
            let stream = PhotoImageCache.shared.images(
                id: assetID,
                size: targetSize,
                contentMode: contentMode == .fill ? .aspectFill : .aspectFit
            )
            for await frame in stream {
                image = frame
            }
        }
    }
}

/// Process-wide image cache. `PHCachingImageManager` keeps recently
/// requested thumbnails warm in memory so the card stack doesn't stall
/// when a new card enters the visible window.
///
/// On top of PhotoKit's own caching, we keep an `NSCache<assetID, UIImage>`
/// of decoded results. SwiftUI sometimes tears down a card view's
/// `@State` (a sibling re-renders, a parent's structural identity
/// shifts, …); the cache lets the rebuilt view re-display the same
/// pixels synchronously while the async load spins back up.
final class PhotoImageCache: @unchecked Sendable {
    static let shared = PhotoImageCache()

    private let manager = PHCachingImageManager()
    /// Options for fast-path requests where we just want *some* image
    /// quickly. Used as the second tier when prefetching.
    private let fastOptions: PHImageRequestOptions
    /// Options for the progressive stream: opportunistic delivery
    /// (degraded to high quality) with network access on for iCloud.
    private let progressiveOptions: PHImageRequestOptions
    private let prefetchLock = NSLock()
    private var cachedIDs: Set<String> = []
    /// Decoded `UIImage` cache. NSCache evicts under memory pressure
    /// and is internally thread-safe. `countLimit` keeps us from
    /// pinning the entire library in RAM on a long swipe session.
    private let memoryCache: NSCache<NSString, UIImage>

    init() {
        let fast = PHImageRequestOptions()
        fast.deliveryMode = .fastFormat
        fast.isNetworkAccessAllowed = true
        fast.resizeMode = .fast
        fast.isSynchronous = false
        self.fastOptions = fast

        let prog = PHImageRequestOptions()
        prog.deliveryMode = .opportunistic
        prog.isNetworkAccessAllowed = true
        prog.resizeMode = .fast
        prog.isSynchronous = false
        self.progressiveOptions = prog

        let cache = NSCache<NSString, UIImage>()
        cache.countLimit = 64  // ~24 MB at typical card sizes
        self.memoryCache = cache
    }

    /// Synchronous fallback used by views whose `@State` was just
    /// reset by SwiftUI. Returns whatever we last decoded for `id`,
    /// or `nil` if the asset hasn't been touched yet this session.
    func cachedImage(id: String) -> UIImage? {
        memoryCache.object(forKey: id as NSString)
    }

    /// Stream of images for `id`, yielded in delivery order:
    /// 1. A degraded thumbnail (fast: typically already on-device).
    /// 2. The full-quality version once decoded / downloaded.
    ///
    /// The stream finishes after the non-degraded result, or on
    /// cancel/error/cancellation of the consuming task. Cancelling the
    /// task tears down the underlying PhotoKit request.
    func images(
        id: String,
        size: CGSize,
        contentMode: PHImageContentMode = .aspectFill
    ) -> AsyncStream<UIImage> {
        AsyncStream { continuation in
            // Replay the latest cached frame the moment we start, so
            // the consumer paints *something* while PhotoKit decides
            // whether to deliver a fresher one.
            if let cached = self.cachedImage(id: id) {
                continuation.yield(cached)
            }

            let result = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil)
            guard let asset = result.firstObject else {
                continuation.finish()
                return
            }
            let resolved = clampSize(size)

            let requestID = manager.requestImage(
                for: asset,
                targetSize: resolved,
                contentMode: contentMode,
                options: progressiveOptions
            ) { [memoryCache] image, info in
                if let image {
                    // Always update the cache with the latest version
                    // (a degraded thumb is still better than nothing
                    // for a future cold render of this same card).
                    memoryCache.setObject(image, forKey: id as NSString)
                    continuation.yield(image)
                }
                let isDegraded = (info?[PHImageResultIsDegradedKey] as? Bool) ?? false
                let isCancelled = (info?[PHImageCancelledKey] as? Bool) ?? false
                let hasError = info?[PHImageErrorKey] != nil
                // Non-degraded delivery, or terminal failure to wrap up
                // the stream so the consumer's `for await` exits.
                if !isDegraded || isCancelled || hasError {
                    continuation.finish()
                }
            }
            continuation.onTermination = { [manager] _ in
                manager.cancelImageRequest(requestID)
            }
        }
    }

    /// Hint the cache to preload the given IDs at `size`. Idempotent ,
    /// IDs already in `cachedIDs` are skipped so scrolling forward 1
    /// photo only warms the newcomer, not the whole window.
    func prefetch(ids: [String], size: CGSize) {
        prefetchLock.lock()
        let newIDs = ids.filter { !cachedIDs.contains($0) }
        cachedIDs.formUnion(newIDs)
        prefetchLock.unlock()
        guard !newIDs.isEmpty else { return }
        let assets = PHAsset.fetchAssets(withLocalIdentifiers: newIDs, options: nil)
        var phAssets: [PHAsset] = []
        assets.enumerateObjects { asset, _, _ in phAssets.append(asset) }
        guard !phAssets.isEmpty else { return }
        manager.startCachingImages(
            for: phAssets,
            targetSize: size == .zero ? PHImageManagerMaximumSize : clampSize(size),
            contentMode: .aspectFill,
            options: fastOptions
        )
    }

    private func clampSize(_ size: CGSize) -> CGSize {
        // Caller is expected to pass a sensible point-size * screen scale.
        // Clamp below a safety ceiling anyway: a 50 MP photo at maximum
        // size is hundreds of MB of decoded UIImage and will OOM low-end
        // devices. 4K × scale is already overkill for any card/overlay.
        let cap: CGFloat = 4096
        if size == .zero { return CGSize(width: cap, height: cap) }
        return CGSize(
            width: min(size.width, cap),
            height: min(size.height, cap)
        )
    }
}
