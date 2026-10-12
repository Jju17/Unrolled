//
//  StatsClient.swift
//  Unrolled
//

import ComposableArchitecture
import Foundation

struct StatsClient: Sendable {
    var load: @Sendable () -> ActivityStats
    var save: @Sendable (ActivityStats) -> Void
}

extension StatsClient: DependencyKey {
    static var liveValue: StatsClient {
        let key = "activityStats.v1"
        return StatsClient(
            load: {
                guard let data = UserDefaults.standard.data(forKey: key),
                      let decoded = try? JSONDecoder().decode(ActivityStats.self, from: data)
                else { return .empty }
                return decoded
            },
            save: { stats in
                if let data = try? JSONEncoder().encode(stats) {
                    UserDefaults.standard.set(data, forKey: key)
                }
            }
        )
    }

    static var testValue: StatsClient {
        let storage = LockIsolated(ActivityStats.empty)
        return StatsClient(
            load: { storage.value },
            save: { storage.setValue($0) }
        )
    }

    static var previewValue: StatsClient { testValue }
}

extension DependencyValues {
    var stats: StatsClient {
        get { self[StatsClient.self] }
        set { self[StatsClient.self] = newValue }
    }
}
