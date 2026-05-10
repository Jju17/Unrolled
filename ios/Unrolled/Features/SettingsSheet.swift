//
//  SettingsSheet.swift
//  Unrolled
//

import SwiftUI

/// Native `.sheet`-presented list of metadata-display preferences.
struct SettingsSheet: View {
    @Environment(\.dismiss) private var dismiss

    let settings: UserSettings
    let onPlayLivePhotosToggled: (Bool) -> Void
    let onShowLocationToggled: (Bool) -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Toggle(
                        String(localized: "Show location"),
                        isOn: Binding(
                            get: { settings.showLocation },
                            set: { onShowLocationToggled($0) }
                        )
                    )
                    Toggle(
                        String(localized: "Play Live Photos"),
                        isOn: Binding(
                            get: { settings.playLivePhotos },
                            set: { onPlayLivePhotosToggled($0) }
                        )
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
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}
