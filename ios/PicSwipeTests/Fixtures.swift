//
//  Fixtures.swift
//  PicSwipeTests
//

import ComposableArchitecture

// MARK: - Value fixtures

extension PhotoAsset {
    static let p1 = PhotoAsset(id: "a1", creationDate: nil)
    static let p2 = PhotoAsset(id: "a2", creationDate: nil)
    static let p3 = PhotoAsset(id: "a3", creationDate: nil)
    static let p4 = PhotoAsset(id: "a4", creationDate: nil)

    /// Five photos, stable order. Good enough for non-shuffle tests.
    static let five: [PhotoAsset] = [p1, p2, p3, p4, PhotoAsset(id: "a5", creationDate: nil)]
}

extension Album {
    static let recents = Album(id: "recents", name: "Recents", count: 100, icon: "🕐", keyAssetID: "a1")
    static let favorites = Album(id: "fav", name: "Favorites", count: 50, icon: "♡", keyAssetID: "a2")
    static let trip = Album(id: "trip", name: "Europe", count: 30, icon: "📁", keyAssetID: "a3")

    static let three: [Album] = [.recents, .favorites, .trip]
}

// MARK: - Client stubs

extension PhotoLibraryClient {
    /// Fully-authorized stub with deterministic photos + albums.
    static func stubAuthorized(
        photos: [PhotoAsset] = PhotoAsset.five,
        albums: [Album] = Album.three,
        photosByAlbum: [String: [PhotoAsset]] = [:]
    ) -> PhotoLibraryClient {
        PhotoLibraryClient(
            authorizationStatus: { .authorized },
            fetchAlbums: { albums },
            fetchPhotos: { albumID in
                if let albumID, let mapped = photosByAlbum[albumID] { return mapped }
                return photos
            },
            requestAuthorization: { .authorized }
        )
    }

    /// User hasn't been asked yet. `requestAuthorization` grants.
    static let stubNotDeterminedThenGrants = PhotoLibraryClient(
        authorizationStatus: { .notDetermined },
        fetchAlbums: { Album.three },
        fetchPhotos: { _ in PhotoAsset.five },
        requestAuthorization: { .authorized }
    )

    /// User hasn't been asked yet. `requestAuthorization` denies.
    static let stubNotDeterminedThenDenies = PhotoLibraryClient(
        authorizationStatus: { .notDetermined },
        fetchAlbums: { [] },
        fetchPhotos: { _ in [] },
        requestAuthorization: { .denied }
    )

    /// Already denied. `authorizationStatus` returns denied. `fetch*` empty.
    static let stubDenied = PhotoLibraryClient(
        authorizationStatus: { .denied },
        fetchAlbums: { [] },
        fetchPhotos: { _ in [] },
        requestAuthorization: { .denied }
    )
}
