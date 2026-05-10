//
//  SettingsClient.swift
//  Unrolled
//

import ComposableArchitecture
import Foundation

/// Persisted user preferences. Backed by `UserDefaults` in production,
/// in-memory in tests/previews. The whole settings blob is read at app
/// start and written back on every mutation — overkill is fine, the
/// blob is tiny.
struct UserSettings: Equatable, Codable {
    var playLivePhotos: Bool
    var showLocation: Bool

    static let `default` = UserSettings(
        playLivePhotos: true,
        showLocation: true
    )
}

struct SettingsClient: Sendable {
    var load: @Sendable () -> UserSettings
    var save: @Sendable (UserSettings) -> Void
}

extension SettingsClient: DependencyKey {
    static var liveValue: SettingsClient {
        let key = "userSettings.v1"
        return SettingsClient(
            load: {
                guard let data = UserDefaults.standard.data(forKey: key),
                      let decoded = try? JSONDecoder().decode(UserSettings.self, from: data)
                else { return .default }
                return decoded
            },
            save: { settings in
                if let data = try? JSONEncoder().encode(settings) {
                    UserDefaults.standard.set(data, forKey: key)
                }
            }
        )
    }

    static var testValue: SettingsClient {
        let storage = LockIsolated(UserSettings.default)
        return SettingsClient(
            load: { storage.value },
            save: { storage.setValue($0) }
        )
    }

    static var previewValue: SettingsClient {
        testValue
    }
}

extension DependencyValues {
    var settings: SettingsClient {
        get { self[SettingsClient.self] }
        set { self[SettingsClient.self] = newValue }
    }
}
