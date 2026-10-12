//
//  PhotoLibraryClient.swift
//  Unrolled
//

import ComposableArchitecture
import CoreGraphics
import Photos
import PhotosUI
import UIKit

/// TCA-injectable PhotoKit wrapper. Feature code depends on this struct,
/// never on `Photos` directly, so tests can stub the whole library.
struct PhotoLibraryClient: Sendable {
    var authorizationStatus: @Sendable () -> PhotoAuthorizationStatus
    /// Moves the given assets to the system "Recently Deleted" album.
    /// PhotoKit shows its own confirmation sheet; a user cancel throws
    /// `PhotoLibraryError.cancelled`, any other failure rethrows.
    var deleteAssets: @Sendable (_ ids: [String]) async throws -> Void
    /// Byte size per asset id. Ids whose size is unknown are absent.
    var fetchFileSizes: @Sendable (_ ids: [String]) async -> [String: Int64]
    var fetchPhotos: @Sendable (_ selector: PileSelector, _ includeVideos: Bool) async -> [PhotoAsset]
    /// Resolves assets by local identifier, in the order given. IDs
    /// that no longer exist in the library are simply absent.
    var fetchPhotosByIDs: @Sendable (_ ids: [String]) async -> [PhotoAsset]
    /// Every pile with its size and how many photos are still undecided
    /// given the classified ids.
    var fetchPiles: @Sendable (
        _ now: Date, _ classifiedIDs: Set<String>, _ includeVideos: Bool
    ) async -> [Pile]
    /// dHash per asset id, computed from a tiny grayscale thumbnail.
    var fetchSimilarityHashes: @Sendable (_ ids: [String]) async -> [String: UInt64]
    /// Shows the system picker that lets a `.limited` user extend the
    /// selection. Returns once the picker is dismissed.
    var presentLimitedLibraryPicker: @Sendable () async -> Void
    var requestAuthorization: @Sendable () async -> PhotoAuthorizationStatus
}

extension PhotoLibraryClient: DependencyKey {
    static var liveValue: PhotoLibraryClient {
        PhotoLibraryClient(
            authorizationStatus: {
                map(PHPhotoLibrary.authorizationStatus(for: .readWrite))
            },
            deleteAssets: { ids in
                guard !ids.isEmpty else { return }
                let fetch = PHAsset.fetchAssets(withLocalIdentifiers: ids, options: nil)
                guard fetch.count > 0 else { return }
                var assets: [PHAsset] = []
                fetch.enumerateObjects { asset, _, _ in assets.append(asset) }
                try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                    PHPhotoLibrary.shared().performChanges({
                        PHAssetChangeRequest.deleteAssets(assets as NSArray)
                    }, completionHandler: { success, error in
                        if success {
                            continuation.resume(returning: ())
                        } else if let error, (error as? PHPhotosError)?.code != .userCancelled {
                            continuation.resume(throwing: error)
                        } else {
                            continuation.resume(throwing: PhotoLibraryError.cancelled)
                        }
                    })
                }
            },
            fetchFileSizes: { ids in
                await onBackground { fileSizes(for: ids) }
            },
            fetchPhotos: { selector, includeVideos in
                await onBackground { photos(for: selector, includeVideos: includeVideos, now: Date()) }
            },
            fetchPhotosByIDs: { ids in
                guard !ids.isEmpty else { return [] }
                let result = PHAsset.fetchAssets(withLocalIdentifiers: ids, options: mediaFetchOptions(includeVideos: true))
                let byID = Dictionary(
                    photoAssets(from: result).map { ($0.id, $0) },
                    uniquingKeysWith: { first, _ in first }
                )
                return ids.compactMap { byID[$0] }
            },
            fetchPiles: { now, classifiedIDs, includeVideos in
                await onBackground { piles(now: now, classifiedIDs: classifiedIDs, includeVideos: includeVideos) }
            },
            fetchSimilarityHashes: { ids in
                await onBackground { similarityHashes(for: ids) }
            },
            presentLimitedLibraryPicker: {
                await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
                    DispatchQueue.main.async {
                        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
                        guard let root = scenes.flatMap(\.windows).first(where: \.isKeyWindow)?.rootViewController
                        else {
                            continuation.resume()
                            return
                        }
                        PHPhotoLibrary.shared().presentLimitedLibraryPicker(from: root) { _ in
                            continuation.resume()
                        }
                    }
                }
            },
            requestAuthorization: {
                await withCheckedContinuation { continuation in
                    PHPhotoLibrary.requestAuthorization(for: .readWrite) { status in
                        continuation.resume(returning: map(status))
                    }
                }
            }
        )
    }

    static var testValue: PhotoLibraryClient {
        PhotoLibraryClient(
            authorizationStatus: { .notDetermined },
            deleteAssets: { _ in },
            fetchFileSizes: { _ in [:] },
            fetchPhotos: { _, _ in [] },
            fetchPhotosByIDs: { _ in [] },
            fetchPiles: { _, _, _ in [] },
            fetchSimilarityHashes: { _ in [:] },
            presentLimitedLibraryPicker: {},
            requestAuthorization: { .notDetermined }
        )
    }

    static var previewValue: PhotoLibraryClient {
        PhotoLibraryClient(
            authorizationStatus: { .authorized },
            deleteAssets: { _ in },
            fetchFileSizes: { _ in [:] },
            fetchPhotos: { _, _ in [] },
            fetchPhotosByIDs: { _ in [] },
            fetchPiles: { _, _, _ in [] },
            fetchSimilarityHashes: { _ in [:] },
            presentLimitedLibraryPicker: {},
            requestAuthorization: { .authorized }
        )
    }
}

extension DependencyValues {
    var photoLibrary: PhotoLibraryClient {
        get { self[PhotoLibraryClient.self] }
        set { self[PhotoLibraryClient.self] = newValue }
    }
}

// MARK: - Fetching

private func onBackground<T: Sendable>(_ work: @escaping @Sendable () -> T) async -> T {
    await withCheckedContinuation { continuation in
        DispatchQueue.global(qos: .userInitiated).async {
            continuation.resume(returning: work())
        }
    }
}

private func photos(for selector: PileSelector, includeVideos: Bool, now: Date) -> [PhotoAsset] {
    let calendar = Calendar.current
    let options = mediaFetchOptions(includeVideos: includeVideos)
    options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]

    switch selector {
    case .all:
        return photoAssets(from: userLibraryAssets(options))
    case .album(let albumID):
        let collectionResult = PHAssetCollection.fetchAssetCollections(
            withLocalIdentifiers: [albumID], options: nil
        )
        guard let collection = collectionResult.firstObject else { return [] }
        return photoAssets(from: PHAsset.fetchAssets(in: collection, options: options))
    case .month(let year, let month):
        guard let range = monthRange(year: year, month: month, calendar: calendar) else { return [] }
        options.predicate = andPredicate(options.predicate, datePredicate(range))
        return photoAssets(from: PHAsset.fetchAssets(with: options))
    case .oneYearAgo:
        let target = calendar.date(byAdding: .month, value: -12, to: now) ?? now
        let parts = calendar.dateComponents([.year, .month], from: target)
        guard let year = parts.year, let month = parts.month,
              let range = monthRange(year: year, month: month, calendar: calendar)
        else { return [] }
        options.predicate = andPredicate(options.predicate, datePredicate(range))
        return photoAssets(from: PHAsset.fetchAssets(with: options))
    case .onThisDay:
        let today = calendar.dateComponents([.year, .month, .day], from: now)
        return photoAssets(from: userLibraryAssets(options)).filter { photo in
            guard let date = photo.creationDate else { return false }
            let parts = calendar.dateComponents([.year, .month, .day], from: date)
            return parts.month == today.month && parts.day == today.day
                && (parts.year ?? 0) < (today.year ?? 0)
        }
    case .screenshots:
        guard let collection = smartAlbum(.smartAlbumScreenshots) else { return [] }
        return photoAssets(from: PHAsset.fetchAssets(in: collection, options: options))
    case .videos:
        let videoOptions = PHFetchOptions()
        videoOptions.predicate = NSPredicate(format: "mediaType == %d", PHAssetMediaType.video.rawValue)
        videoOptions.sortDescriptors = options.sortDescriptors
        return photoAssets(from: PHAsset.fetchAssets(with: videoOptions))
    }
}

private func piles(now: Date, classifiedIDs: Set<String>, includeVideos: Bool) -> [Pile] {
    let calendar = Calendar.current
    let options = mediaFetchOptions(includeVideos: includeVideos)
    options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]

    let today = calendar.dateComponents([.year, .month, .day], from: now)
    let yearAgo = calendar.date(byAdding: .month, value: -12, to: now) ?? now
    let yearAgoParts = calendar.dateComponents([.year, .month], from: yearAgo)
    var recentMonths: [MonthKey] = []
    for offset in 0..<24 {
        if let date = calendar.date(byAdding: .month, value: -offset, to: now) {
            let parts = calendar.dateComponents([.year, .month], from: date)
            recentMonths.append(MonthKey(year: parts.year ?? 0, month: parts.month ?? 0))
        }
    }

    var all = Tally()
    var onThisDay = Tally()
    var oneYearAgo = Tally()
    var videos = Tally()
    var months: [MonthKey: Tally] = [:]

    userLibraryAssets(options).enumerateObjects { asset, _, _ in
        let id = asset.localIdentifier
        let classified = classifiedIDs.contains(id)
        all.add(id, classified: classified)
        if asset.mediaType == .video {
            videos.add(id, classified: classified)
        }
        guard let date = asset.creationDate else { return }
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        let key = MonthKey(year: parts.year ?? 0, month: parts.month ?? 0)
        months[key, default: Tally()].add(id, classified: classified)
        if parts.month == today.month, parts.day == today.day, (parts.year ?? 0) < (today.year ?? 0) {
            onThisDay.add(id, classified: classified)
        }
        if parts.year == yearAgoParts.year, parts.month == yearAgoParts.month {
            oneYearAgo.add(id, classified: classified)
        }
    }

    var result: [Pile] = []
    func append(_ selector: PileSelector, _ tally: Tally, name: String? = nil) {
        guard tally.count > 0 || selector == .all else { return }
        result.append(
            Pile(
                selector: selector, name: name, photoCount: tally.count,
                remainingCount: tally.remaining, keyAssetID: tally.key
            )
        )
    }

    if let current = recentMonths.first {
        append(.month(year: current.year, month: current.month), months[current] ?? Tally())
    }
    append(.onThisDay, onThisDay)
    append(.oneYearAgo, oneYearAgo)
    if let screenshots = smartAlbum(.smartAlbumScreenshots) {
        append(.screenshots, tally(in: screenshots, options: options, classifiedIDs: classifiedIDs))
    }
    if includeVideos {
        append(.videos, videos)
    }
    append(.all, all)
    for key in recentMonths.dropFirst() {
        if let tally = months[key] {
            append(.month(year: key.year, month: key.month), tally)
        }
    }

    let smartSubtypes: [PHAssetCollectionSubtype] = [
        .smartAlbumFavorites, .smartAlbumRecentlyAdded, .smartAlbumSelfPortraits,
        .smartAlbumPanoramas, .smartAlbumLivePhotos, .smartAlbumBursts,
    ]
    for subtype in smartSubtypes {
        if let collection = smartAlbum(subtype) {
            append(
                .album(collection.localIdentifier),
                tally(in: collection, options: options, classifiedIDs: classifiedIDs),
                name: collection.localizedTitle ?? String(localized: "Album")
            )
        }
    }
    let userAlbums = PHAssetCollection.fetchAssetCollections(with: .album, subtype: .any, options: nil)
    userAlbums.enumerateObjects { collection, _, _ in
        append(
            .album(collection.localIdentifier),
            tally(in: collection, options: options, classifiedIDs: classifiedIDs),
            name: collection.localizedTitle ?? String(localized: "Album")
        )
    }

    return result
}

private func tally(in collection: PHAssetCollection, options: PHFetchOptions, classifiedIDs: Set<String>) -> Tally {
    var tally = Tally()
    PHAsset.fetchAssets(in: collection, options: options).enumerateObjects { asset, _, _ in
        tally.add(asset.localIdentifier, classified: classifiedIDs.contains(asset.localIdentifier))
    }
    return tally
}

private struct MonthKey: Hashable {
    let year: Int
    let month: Int
}

private struct Tally {
    var count = 0
    var remaining = 0
    var key: String?

    mutating func add(_ id: String, classified: Bool) {
        count += 1
        if !classified { remaining += 1 }
        if key == nil { key = id }
    }
}

private func fileSizes(for ids: [String]) -> [String: Int64] {
    var sizes: [String: Int64] = [:]
    for chunk in stride(from: 0, to: ids.count, by: 200).map({ Array(ids[$0..<min($0 + 200, ids.count)]) }) {
        let result = PHAsset.fetchAssets(withLocalIdentifiers: chunk, options: nil)
        result.enumerateObjects { asset, _, _ in
            guard let resource = PHAssetResource.assetResources(for: asset).first,
                  resource.responds(to: NSSelectorFromString("fileSize")),
                  let size = (resource.value(forKey: "fileSize") as? NSNumber)?.int64Value
            else { return }
            sizes[asset.localIdentifier] = size
        }
    }
    return sizes
}

private func similarityHashes(for ids: [String]) -> [String: UInt64] {
    let width = SimilarityGrouper.hashWidth
    let height = SimilarityGrouper.hashHeight
    let options = PHImageRequestOptions()
    options.deliveryMode = .fastFormat
    options.resizeMode = .fast
    options.isSynchronous = true
    options.isNetworkAccessAllowed = false

    var hashes: [String: UInt64] = [:]
    let result = PHAsset.fetchAssets(withLocalIdentifiers: ids, options: nil)
    result.enumerateObjects { asset, _, _ in
        var image: UIImage?
        PHImageManager.default().requestImage(
            for: asset, targetSize: CGSize(width: 32, height: 32),
            contentMode: .aspectFill, options: options
        ) { delivered, _ in
            image = delivered
        }
        guard let cgImage = image?.cgImage,
              let context = CGContext(
                data: nil, width: width, height: height, bitsPerComponent: 8,
                bytesPerRow: width, space: CGColorSpaceCreateDeviceGray(),
                bitmapInfo: CGImageAlphaInfo.none.rawValue
              )
        else { return }
        context.interpolationQuality = .medium
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
        guard let data = context.data else { return }
        let gray = Array(UnsafeBufferPointer(start: data.assumingMemoryBound(to: UInt8.self), count: width * height))
        hashes[asset.localIdentifier] = SimilarityGrouper.dHash(gray: gray)
    }
    return hashes
}

private func photoAssets(from result: PHFetchResult<PHAsset>) -> [PhotoAsset] {
    var assets: [PhotoAsset] = []
    assets.reserveCapacity(result.count)
    result.enumerateObjects { asset, _, _ in
        let isVideo = asset.mediaType == .video
        assets.append(
            PhotoAsset(
                id: asset.localIdentifier,
                creationDate: asset.creationDate,
                latitude: asset.location?.coordinate.latitude,
                longitude: asset.location?.coordinate.longitude,
                isLivePhoto: asset.mediaSubtypes.contains(.photoLive),
                isVideo: isVideo,
                durationSeconds: isVideo ? asset.duration : nil
            )
        )
    }
    return assets
}

private func userLibraryAssets(_ options: PHFetchOptions) -> PHFetchResult<PHAsset> {
    if let recents = smartAlbum(.smartAlbumUserLibrary) {
        return PHAsset.fetchAssets(in: recents, options: options)
    }
    return PHAsset.fetchAssets(with: options)
}

private func smartAlbum(_ subtype: PHAssetCollectionSubtype) -> PHAssetCollection? {
    PHAssetCollection.fetchAssetCollections(with: .smartAlbum, subtype: subtype, options: nil).firstObject
}

private func mediaFetchOptions(includeVideos: Bool) -> PHFetchOptions {
    let options = PHFetchOptions()
    if includeVideos {
        options.predicate = NSPredicate(
            format: "mediaType == %d OR mediaType == %d",
            PHAssetMediaType.image.rawValue, PHAssetMediaType.video.rawValue
        )
    } else {
        options.predicate = NSPredicate(format: "mediaType == %d", PHAssetMediaType.image.rawValue)
    }
    return options
}

private func monthRange(year: Int, month: Int, calendar: Calendar) -> Range<Date>? {
    var start = DateComponents()
    start.year = year
    start.month = month
    start.day = 1
    guard let startDate = calendar.date(from: start),
          let endDate = calendar.date(byAdding: .month, value: 1, to: startDate)
    else { return nil }
    return startDate..<endDate
}

private func datePredicate(_ range: Range<Date>) -> NSPredicate {
    NSPredicate(
        format: "creationDate >= %@ AND creationDate < %@",
        range.lowerBound as NSDate, range.upperBound as NSDate
    )
}

private func andPredicate(_ a: NSPredicate?, _ b: NSPredicate) -> NSPredicate {
    guard let a else { return b }
    return NSCompoundPredicate(andPredicateWithSubpredicates: [a, b])
}

private func map(_ status: PHAuthorizationStatus) -> PhotoAuthorizationStatus {
    switch status {
    case .notDetermined: .notDetermined
    case .restricted, .denied: .denied
    case .authorized: .authorized
    case .limited: .limited
    @unknown default: .denied
    }
}
