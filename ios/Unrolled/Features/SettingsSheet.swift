//
//  SettingsSheet.swift
//  Unrolled
//

import SwiftUI

/// Native `.sheet`-presented list of settings. For now the single row
/// is "Voir la photo dans la photothèque" which opens the Photos app.
struct SettingsSheet: View {
    @Environment(\.dismiss) private var dismiss

    let hasTopPhoto: Bool
    let onViewInPhotos: () -> Void

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Button {
                        onViewInPhotos()
                        dismiss()
                    } label: {
                        Label(String(localized: "View this photo in Photos"),
                              systemImage: "photo.on.rectangle")
                            .foregroundStyle(hasTopPhoto ? .primary : .secondary)
                    }
                    .disabled(!hasTopPhoto)
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
