//
//  SettingsSheet.swift
//  Unrolled
//

import SwiftUI

struct SettingsSheet: View {
    @Environment(\.dismiss) private var dismiss

    let settings: UserSettings
    let onDailyGoalChanged: (Int) -> Void
    let onDetectSimilarToggled: (Bool) -> Void
    let onGamificationToggled: (Bool) -> Void
    let onIncludeVideosToggled: (Bool) -> Void
    let onPlayLivePhotosToggled: (Bool) -> Void
    let onReminderHourChanged: (Int) -> Void
    let onReminderToggled: (Bool) -> Void
    let onShowLocationToggled: (Bool) -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Toggle(
                        String(localized: "Include videos"),
                        isOn: Binding(get: { settings.includeVideos }, set: { onIncludeVideosToggled($0) })
                    )
                    Toggle(
                        String(localized: "Detect similar photos"),
                        isOn: Binding(get: { settings.detectSimilar }, set: { onDetectSimilarToggled($0) })
                    )
                } header: {
                    Text("Deck")
                } footer: {
                    Text("Similar shots taken seconds apart are grouped so you can keep the best one. Detection runs on your device.")
                }
                Section {
                    Toggle(
                        String(localized: "Goals and streaks"),
                        isOn: Binding(get: { settings.gamificationEnabled }, set: { onGamificationToggled($0) })
                    )
                    if settings.gamificationEnabled {
                        Picker(
                            String(localized: "Daily goal"),
                            selection: Binding(get: { settings.dailyGoal }, set: { onDailyGoalChanged($0) })
                        ) {
                            ForEach(UserSettings.dailyGoalChoices, id: \.self) { choice in
                                Text("\(choice) photos").tag(choice)
                            }
                        }
                        Toggle(
                            String(localized: "Daily reminder"),
                            isOn: Binding(get: { settings.reminderEnabled }, set: { onReminderToggled($0) })
                        )
                        if settings.reminderEnabled {
                            Picker(
                                String(localized: "Reminder time"),
                                selection: Binding(get: { settings.reminderHour }, set: { onReminderHourChanged($0) })
                            ) {
                                ForEach(Array(6..<23), id: \.self) { hour in
                                    Text(verbatim: hourLabel(hour)).tag(hour)
                                }
                            }
                        }
                    }
                } header: {
                    Text("Goals and streaks")
                } footer: {
                    Text("A small ring in the header tracks today's photos. Turning this off hides goals, streaks and milestones; the storage counter stays.")
                }
                Section {
                    Toggle(
                        String(localized: "Show location"),
                        isOn: Binding(get: { settings.showLocation }, set: { onShowLocationToggled($0) })
                    )
                    Toggle(
                        String(localized: "Play Live Photos"),
                        isOn: Binding(get: { settings.playLivePhotos }, set: { onPlayLivePhotosToggled($0) })
                    )
                } header: {
                    Text("Photo display")
                } footer: {
                    Text("Choose which info to show on each photo, and whether Live Photos play automatically when viewed full-screen.")
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }

    private func hourLabel(_ hour: Int) -> String {
        var parts = DateComponents()
        parts.hour = hour
        guard let date = Calendar.current.date(from: parts) else { return "\(hour)" }
        let formatter = DateFormatter()
        formatter.setLocalizedDateFormatFromTemplate("j")
        return formatter.string(from: date)
    }
}
