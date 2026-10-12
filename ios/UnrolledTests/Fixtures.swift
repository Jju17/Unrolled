//
//  Fixtures.swift
//  UnrolledTests
//

import ComposableArchitecture
import Foundation

// MARK: - Value fixtures

extension PhotoAsset {
    static let p1 = PhotoAsset(id: "a1", creationDate: nil)
    static let p2 = PhotoAsset(id: "a2", creationDate: nil)
    static let p3 = PhotoAsset(id: "a3", creationDate: nil)
    static let p4 = PhotoAsset(id: "a4", creationDate: nil)

    /// Five photos, stable order. Good enough for non-shuffle tests.
    static let five: [PhotoAsset] = [p1, p2, p3, p4, PhotoAsset(id: "a5", creationDate: nil)]

    static func shot(_ id: String, at seconds: TimeInterval, isVideo: Bool = false) -> PhotoAsset {
        PhotoAsset(id: id, creationDate: Date(timeIntervalSince1970: seconds), isVideo: isVideo)
    }
}

extension Pile {
    static let all = Pile(selector: .all, photoCount: 100, remainingCount: 90, keyAssetID: "a1")
    static let thisMonth = Pile(selector: .month(year: 2026, month: 10), photoCount: 12, remainingCount: 12, keyAssetID: "a2")
    static let trip = Pile(selector: .album("trip"), name: "Europe", photoCount: 30, remainingCount: 3, keyAssetID: "a3")

    static let three: [Pile] = [.thisMonth, .all, .trip]
}

enum TestDates {
    static let now = Date(timeIntervalSince1970: 1_760_000_000)
    static let today = ActivityTracker.dayKey(now)
}

extension ActivityStats {
    /// What `rollover` produces from an empty blob on `TestDates.now`.
    static let fresh = ActivityStats(todayKey: TestDates.today)

    static func reviewed(_ count: Int) -> ActivityStats {
        ActivityStats(photosReviewed: count, todayCount: count, todayKey: TestDates.today)
    }
}

// MARK: - Client stubs

extension PhotoLibraryClient {
    /// Fully-authorized stub with deterministic photos + piles.
    static func stubAuthorized(
        photos: [PhotoAsset] = PhotoAsset.five,
        piles: [Pile] = Pile.three,
        photosByPile: [PileSelector: [PhotoAsset]] = [:],
        fileSizes: [String: Int64] = [:],
        hashes: [String: UInt64] = [:],
        status: PhotoAuthorizationStatus = .authorized
    ) -> PhotoLibraryClient {
        PhotoLibraryClient(
            authorizationStatus: { status },
            deleteAssets: { _ in },
            fetchFileSizes: { ids in fileSizes.filter { ids.contains($0.key) } },
            fetchPhotos: { selector, _ in
                if let mapped = photosByPile[selector] { return mapped }
                return photos
            },
            fetchPhotosByIDs: { ids in
                let all = photos + photosByPile.values.flatMap { $0 }
                return ids.compactMap { id in all.first { $0.id == id } }
            },
            fetchPiles: { _, _, _ in piles },
            fetchSimilarityHashes: { ids in hashes.filter { ids.contains($0.key) } },
            presentLimitedLibraryPicker: {},
            requestAuthorization: { status }
        )
    }

    /// User hasn't been asked yet. `requestAuthorization` grants.
    static let stubNotDeterminedThenGrants: PhotoLibraryClient = {
        var client = stubAuthorized()
        client.authorizationStatus = { .notDetermined }
        return client
    }()

    /// User hasn't been asked yet. `requestAuthorization` denies.
    static let stubNotDeterminedThenDenies: PhotoLibraryClient = {
        var client = stubAuthorized(photos: [], piles: [], status: .denied)
        client.authorizationStatus = { .notDetermined }
        return client
    }()

    /// Already denied. `fetch*` empty.
    static let stubDenied = stubAuthorized(photos: [], piles: [], status: .denied)
}

extension SwipeProgressClient {
    /// Records every save so tests can assert on the persisted blob.
    static func recording(
        initial: SwipeProgress = .empty,
        into saved: LockIsolated<[SwipeProgress]>
    ) -> SwipeProgressClient {
        SwipeProgressClient(
            load: { initial },
            save: { progress in saved.withValue { $0.append(progress) } }
        )
    }
}

extension StatsClient {
    static func recording(
        initial: ActivityStats = .empty,
        into saved: LockIsolated<[ActivityStats]>
    ) -> StatsClient {
        StatsClient(
            load: { initial },
            save: { stats in saved.withValue { $0.append(stats) } }
        )
    }
}
