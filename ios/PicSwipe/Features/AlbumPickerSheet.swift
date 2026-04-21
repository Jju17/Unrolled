//
//  AlbumPickerSheet.swift
//  PicSwipe
//

import SwiftUI

/// Native iOS list presented inside a `.sheet(isPresented:)`. Uses
/// `NavigationStack` + `List(.insetGrouped)` so presentation + styling
/// come from SwiftUI defaults rather than a hand-rolled overlay.
struct AlbumPickerView: View {
    @Environment(\.dismiss) private var dismiss

    let albums: [Album]
    let currentID: String
    let onPick: (String) -> Void

    var body: some View {
        NavigationStack {
            List {
                ForEach(albums) { album in
                    Button {
                        onPick(album.id)
                        dismiss()
                    } label: {
                        row(for: album)
                    }
                    .buttonStyle(.plain)
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Albums")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    private func row(for album: Album) -> some View {
        HStack(spacing: 14) {
            thumbnail(for: album)
                .frame(width: 44, height: 44)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            VStack(alignment: .leading, spacing: 2) {
                Text(album.name)
                    .font(.body)
                Text("\(album.count.formatted()) photos")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            if album.id == currentID {
                Image(systemName: "checkmark")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.tint)
            }
        }
        .contentShape(Rectangle())  // full row is tappable
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private func thumbnail(for album: Album) -> some View {
        if let keyAssetID = album.keyAssetID {
            PhotoAssetImage(
                assetID: keyAssetID,
                targetSize: CGSize(width: 132, height: 132)  // 44pt × 3x scale
            )
        } else {
            ZStack {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color(.secondarySystemFill))
                Text(album.icon)
                    .font(.system(size: 20))
            }
        }
    }
}
