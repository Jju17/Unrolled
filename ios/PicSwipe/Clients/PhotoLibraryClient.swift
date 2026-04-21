//
//  PhotoLibraryClient.swift
//  PicSwipe
//

import ComposableArchitecture
import Photos
import UIKit

/// TCA-injectable PhotoKit wrapper. Feature code depends on this struct,
/// never on `Photos` directly, so tests can stub the whole library.
///
/// `fetchPhotos(albumID)` returns photos in the specified album
/// (`PHAssetCollection.localIdentifier`). Passing `nil` falls back to the
/// `.smartAlbumUserLibrary` — the "Recents" / all-photos collection.
struct PhotoLibraryClient: Sendable {
    var authorizationStatus: @Sendable () -> PhotoAuthorizationStatus
    var fetchAlbums: @Sendable () async -> [Album]
    var fetchPhotos: @Sendable (_ albumID: String?) async -> [PhotoAsset]
    var requestAuthorization: @Sendable () async -> PhotoAuthorizationStatus
}

extension PhotoLibraryClient: DependencyKey {
    static let liveValue: PhotoLibraryClient = PhotoLibraryClient(
        authorizationStatus: {
            map(PHPhotoLibrary.authorizationStatus(for: .readWrite))
        },
        fetchAlbums: {
            var albums: [Album] = []

            // Smart albums (known subtypes) in display order.
            let smart: [(subtype: PHAssetCollectionSubtype, icon: String)] = [
                (.smartAlbumUserLibrary,   "🕐"),   // "Recents" / camera roll
                (.smartAlbumFavorites,     "♡"),
                (.smartAlbumRecentlyAdded, "⏱"),
                (.smartAlbumSelfPortraits, "☺"),
                (.smartAlbumScreenshots,   "▢"),
                (.smartAlbumPanoramas,     "▭"),
                (.smartAlbumLivePhotos,    "●"),
                (.smartAlbumBursts,        "◷"),
                (.smartAlbumAllHidden,     "⦸"),
            ]
            for entry in smart {
                let result = PHAssetCollection.fetchAssetCollections(
                    with: .smartAlbum, subtype: entry.subtype, options: nil
                )
                if let collection = result.firstObject,
                   let album = makeAlbum(from: collection, icon: entry.icon) {
                    albums.append(album)
                }
            }

            // User-created albums (enumerated in user's order).
            let userResult = PHAssetCollection.fetchAssetCollections(
                with: .album, subtype: .any, options: nil
            )
            userResult.enumerateObjects { collection, _, _ in
                if let album = makeAlbum(from: collection, icon: "📁") {
                    albums.append(album)
                }
            }

            return albums
        },
        fetchPhotos: { albumID in
            let options = imageOnlyFetchOptions()
            options.sortDescriptors = [
                NSSortDescriptor(key: "creationDate", ascending: false)
            ]

            let result: PHFetchResult<PHAsset>
            if let albumID {
                let collectionResult = PHAssetCollection.fetchAssetCollections(
                    withLocalIdentifiers: [albumID], options: nil
                )
                guard let collection = collectionResult.firstObject else { return [] }
                result = PHAsset.fetchAssets(in: collection, options: options)
            } else {
                // Default: "Recents" / camera roll
                let recentsResult = PHAssetCollection.fetchAssetCollections(
                    with: .smartAlbum, subtype: .smartAlbumUserLibrary, options: nil
                )
                if let recents = recentsResult.firstObject {
                    result = PHAsset.fetchAssets(in: recents, options: options)
                } else {
                    result = PHAsset.fetchAssets(with: .image, options: options)
                }
            }

            var assets: [PhotoAsset] = []
            assets.reserveCapacity(result.count)
            result.enumerateObjects { asset, _, _ in
                assets.append(
                    PhotoAsset(
                        id: asset.localIdentifier,
                        creationDate: asset.creationDate
                    )
                )
            }
            return assets
        },
        requestAuthorization: {
            await withCheckedContinuation { continuation in
                PHPhotoLibrary.requestAuthorization(for: .readWrite) { status in
                    continuation.resume(returning: map(status))
                }
            }
        }
    )

    static let testValue = PhotoLibraryClient(
        authorizationStatus: { .notDetermined },
        fetchAlbums: { [] },
        fetchPhotos: { _ in [] },
        requestAuthorization: { .notDetermined }
    )

    static let previewValue = PhotoLibraryClient(
        authorizationStatus: { .authorized },
        fetchAlbums: { [] },
        fetchPhotos: { _ in [] },
        requestAuthorization: { .authorized }
    )
}

extension DependencyValues {
    var photoLibrary: PhotoLibraryClient {
        get { self[PhotoLibraryClient.self] }
        set { self[PhotoLibraryClient.self] = newValue }
    }
}

private func imageOnlyFetchOptions() -> PHFetchOptions {
    let options = PHFetchOptions()
    options.predicate = NSPredicate(
        format: "mediaType == %d", PHAssetMediaType.image.rawValue
    )
    return options
}

/// Returns an `Album` value for `collection` if it contains at least one
/// image, with `keyAssetID` set to the newest image (used as the picker
/// row thumbnail). Returns `nil` for empty collections so the caller
/// can skip them.
private func makeAlbum(
    from collection: PHAssetCollection,
    icon: String
) -> Album? {
    // Total count — cheap, uses the metadata index.
    let countOptions = imageOnlyFetchOptions()
    let count = PHAsset.fetchAssets(in: collection, options: countOptions).count
    guard count > 0 else { return nil }

    // One-row fetch sorted newest-first for the thumbnail.
    let thumbOptions = imageOnlyFetchOptions()
    thumbOptions.sortDescriptors = [
        NSSortDescriptor(key: "creationDate", ascending: false)
    ]
    thumbOptions.fetchLimit = 1
    let thumbResult = PHAsset.fetchAssets(in: collection, options: thumbOptions)
    let keyAssetID = thumbResult.firstObject?.localIdentifier

    return Album(
        id: collection.localIdentifier,
        name: collection.localizedTitle ?? "Album",
        count: count,
        icon: icon,
        keyAssetID: keyAssetID
    )
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
