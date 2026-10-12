//
//  SwipeProgressClient.swift
//  Unrolled
//

import ComposableArchitecture
import Foundation

/// Persistent record of which photos the user has already classified.
/// Survives app relaunches so the deck doesn't re-present photos the
/// user has already kept or staged for deletion. The "Empty trash"
/// action clears `deletedIDs` (those assets are gone from the system
/// library); only the explicit `Reset progress` button in the trash
/// sheet wipes `keptIDs` as well.
struct SwipeProgress: Equatable, Codable {
    var deletedIDs: [String]
    var keptIDs: [String]

    static let empty = SwipeProgress(deletedIDs: [], keptIDs: [])
}

struct SwipeProgressClient: Sendable {
    var load: @Sendable () -> SwipeProgress
    var save: @Sendable (SwipeProgress) -> Void
}

extension SwipeProgressClient: DependencyKey {
    static var liveValue: SwipeProgressClient {
        let key = "swipeProgress.v1"
        return SwipeProgressClient(
            load: {
                guard let data = UserDefaults.standard.data(forKey: key),
                      let decoded = try? JSONDecoder().decode(SwipeProgress.self, from: data)
                else { return .empty }
                return decoded
            },
            save: { progress in
                if let data = try? JSONEncoder().encode(progress) {
                    UserDefaults.standard.set(data, forKey: key)
                }
            }
        )
    }

    static var testValue: SwipeProgressClient {
        let storage = LockIsolated(SwipeProgress.empty)
        return SwipeProgressClient(
            load: { storage.value },
            save: { storage.setValue($0) }
        )
    }

    static var previewValue: SwipeProgressClient { testValue }
}

extension DependencyValues {
    var swipeProgress: SwipeProgressClient {
        get { self[SwipeProgressClient.self] }
        set { self[SwipeProgressClient.self] = newValue }
    }
}
