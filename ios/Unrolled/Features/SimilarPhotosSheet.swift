//
//  SimilarPhotosSheet.swift
//  Unrolled
//

import SwiftUI

/// Grid of near-duplicate shots. Checked photos are kept, the others go
/// to the trash list in one decision.
struct SimilarPhotosSheet: View {
    @Environment(\.dismiss) private var dismiss

    let photos: [PhotoAsset]
    let onResolve: (_ keptIDs: [String], _ deletedIDs: [String]) -> Void

    @State private var keptIDs: Set<String> = []

    private let keepColor = Color(red: 0.188, green: 0.824, blue: 0.541)
    private let dropColor = Color(red: 0.937, green: 0.243, blue: 0.357)

    private var deletedCount: Int { photos.count - keptIDs.count }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                    ForEach(photos) { photo in
                        cell(photo)
                    }
                }
                .padding(16)
            }
            .safeAreaInset(edge: .bottom) {
                Button {
                    let kept = photos.map(\.id).filter { keptIDs.contains($0) }
                    let deleted = photos.map(\.id).filter { !keptIDs.contains($0) }
                    onResolve(kept, deleted)
                    dismiss()
                } label: {
                    Text("Keep \(keptIDs.count), delete \(deletedCount)")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(Capsule().fill(deletedCount > 0 ? dropColor : keepColor))
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(.bar)
            }
            .navigationTitle("Similar photos")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
        .onAppear {
            if keptIDs.isEmpty, let first = photos.first {
                keptIDs = [first.id]
            }
        }
    }

    private func cell(_ photo: PhotoAsset) -> some View {
        let kept = keptIDs.contains(photo.id)
        return Button {
            if kept { keptIDs.remove(photo.id) } else { keptIDs.insert(photo.id) }
        } label: {
            ZStack(alignment: .topTrailing) {
                PhotoAssetImage(assetID: photo.id, targetSize: CGSize(width: 600, height: 600))
                    .aspectRatio(1, contentMode: .fill)
                    .frame(minWidth: 0)
                    .clipped()
                Image(systemName: kept ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 26))
                    .symbolRenderingMode(.palette)
                    .foregroundStyle(.white, kept ? keepColor : Color.black.opacity(0.35))
                    .padding(8)
            }
            .aspectRatio(1, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(kept ? keepColor : Color.clear, lineWidth: 3)
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel(kept ? Text("Kept photo") : Text("Photo to delete"))
        .accessibilityHint(Text("Tap to toggle"))
    }
}
