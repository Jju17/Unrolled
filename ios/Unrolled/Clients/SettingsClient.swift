//
//  SettingsClient.swift
//  Unrolled
//

import ComposableArchitecture
import Foundation

struct UserSettings: Equatable, Codable {
    var dailyGoal: Int
    var detectSimilar: Bool
    var gamificationEnabled: Bool
    var includeVideos: Bool
    var playLivePhotos: Bool
    var reminderEnabled: Bool
    var reminderHour: Int
    var showLocation: Bool

    static let dailyGoalChoices = [25, 50, 100, 200]

    static let `default` = UserSettings(
        dailyGoal: 50,
        detectSimilar: true,
        gamificationEnabled: true,
        includeVideos: true,
        playLivePhotos: true,
        reminderEnabled: false,
        reminderHour: 19,
        showLocation: true
    )

    init(
        dailyGoal: Int = 50,
        detectSimilar: Bool = true,
        gamificationEnabled: Bool = true,
        includeVideos: Bool = true,
        playLivePhotos: Bool = true,
        reminderEnabled: Bool = false,
        reminderHour: Int = 19,
        showLocation: Bool = true
    ) {
        self.dailyGoal = dailyGoal
        self.detectSimilar = detectSimilar
        self.gamificationEnabled = gamificationEnabled
        self.includeVideos = includeVideos
        self.playLivePhotos = playLivePhotos
        self.reminderEnabled = reminderEnabled
        self.reminderHour = reminderHour
        self.showLocation = showLocation
    }

    // Blobs written by older builds lack the newer keys.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let defaults = UserSettings.default
        dailyGoal = try container.decodeIfPresent(Int.self, forKey: .dailyGoal) ?? defaults.dailyGoal
        detectSimilar = try container.decodeIfPresent(Bool.self, forKey: .detectSimilar) ?? defaults.detectSimilar
        gamificationEnabled = try container.decodeIfPresent(Bool.self, forKey: .gamificationEnabled) ?? defaults.gamificationEnabled
        includeVideos = try container.decodeIfPresent(Bool.self, forKey: .includeVideos) ?? defaults.includeVideos
        playLivePhotos = try container.decodeIfPresent(Bool.self, forKey: .playLivePhotos) ?? defaults.playLivePhotos
        reminderEnabled = try container.decodeIfPresent(Bool.self, forKey: .reminderEnabled) ?? defaults.reminderEnabled
        reminderHour = try container.decodeIfPresent(Int.self, forKey: .reminderHour) ?? defaults.reminderHour
        showLocation = try container.decodeIfPresent(Bool.self, forKey: .showLocation) ?? defaults.showLocation
    }
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
