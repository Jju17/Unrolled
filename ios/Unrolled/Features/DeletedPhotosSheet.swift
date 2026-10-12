//
//  DeletedPhotosSheet.swift
//  Unrolled
//

import ComposableArchitecture
import SwiftUI

/// Review sheet for the trash-staging list (photos the user swiped
/// "delete" but haven't yet committed to the system Recently Deleted
/// bucket). Each row has a "Keep" button to pull that photo back out;
/// the big red footer actually hands the list over to PhotoKit.
struct DeletedPhotosSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme
    @Bindable var store: StoreOf<SwipeFeature>

    @State private var resetConfirmOpen = false

    private let keepColor = Color(red: 0.188, green: 0.824, blue: 0.541)
    private let dropColor = Color(red: 0.937, green: 0.243, blue: 0.357)

    private var textPrim: Color {
        colorScheme == .dark ? .white : Color(red: 0.102, green: 0.102, blue: 0.102)
    }
    private var textSec: Color {
        (colorScheme == .dark ? Color.white : Color.black).opacity(0.55)
    }
    private var bgPage: Color {
        colorScheme == .dark
            ? Color(red: 0.055, green: 0.055, blue: 0.071)
            : Color(red: 0.965, green: 0.961, blue: 0.945)
    }

    private var deletedPhotos: [PhotoAsset] { store.trashPhotos }

    private var trashBytes: Int64 {
        deletedPhotos.reduce(0) { $0 + (store.byteCounts[$1.id] ?? 0) }
    }

    var body: some View {
        NavigationStack {
            ZStack {
                bgPage.ignoresSafeArea()
                if deletedPhotos.isEmpty {
                    emptyState
                } else {
                    content
                }
            }
            .navigationTitle("Deleted photos")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Reset") { resetConfirmOpen = true }
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(textSec)
                        .disabled(store.kept == 0 && store.deleted == 0)
                        .accessibilityLabel(Text("Reset all progress"))
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Close") { dismiss() }
                        .foregroundStyle(textPrim)
                }
            }
            .confirmationDialog(
                "Reset all progress?",
                isPresented: $resetConfirmOpen,
                titleVisibility: .visible
            ) {
                Button("Reset", role: .destructive) {
                    store.send(.resetProgressTapped)
                }
            } message: {
                Text("Clears every kept/deleted marker so the deck shows the full library again. The photos themselves are untouched.")
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Image(systemName: "trash")
                .font(.system(size: 40))
                .foregroundStyle(textSec)
            Text("No photos to delete")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(textPrim)
            Text("Swipe left to fill the trash.")
                .font(.system(size: 13))
                .foregroundStyle(textSec)
        }
    }

    private var content: some View {
        VStack(spacing: 0) {
            if let err = store.deleteError {
                Text(err)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .frame(maxWidth: .infinity)
                    .background(dropColor)
            }
            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(deletedPhotos, id: \.id) { photo in
                        row(for: photo)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 24)
            }
            trashFooter
        }
    }

    private func row(for photo: PhotoAsset) -> some View {
        HStack(spacing: 12) {
            PhotoAssetImage(
                assetID: photo.id,
                targetSize: CGSize(width: 160, height: 160),
                contentMode: .fill
            )
            .frame(width: 64, height: 64)
            .clipShape(RoundedRectangle(cornerRadius: 10))

            VStack(alignment: .leading, spacing: 2) {
                if let date = photo.creationDate {
                    Text(Self.dateFormatter.string(from: date))
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(textPrim)
                } else {
                    Text("Photo")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(textPrim)
                }
                if let bytes = store.byteCounts[photo.id] {
                    Text(verbatim: PileTitles.bytes(bytes))
                        .font(.system(size: 12))
                        .foregroundStyle(textSec)
                } else {
                    Text("Ready to delete")
                        .font(.system(size: 12))
                        .foregroundStyle(textSec)
                }
            }
            Spacer()
            Button {
                store.send(.deletedPhotoRestoreTapped(id: photo.id))
            } label: {
                HStack(spacing: 4) {
                    Image(systemName: "heart.fill")
                        .font(.system(size: 11, weight: .bold))
                    Text("Keep")
                        .font(.system(size: 13, weight: .semibold))
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 14).padding(.vertical, 8)
                .background(Capsule().fill(keepColor))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("Keep this photo"))
        }
        .padding(10)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(colorScheme == .dark
                      ? Color.white.opacity(0.05)
                      : Color.white)
        )
    }

    private var trashFooter: some View {
        VStack(spacing: 0) {
            Divider().opacity(0.3)
            Button {
                store.send(.emptyTrashButtonTapped)
            } label: {
                HStack(spacing: 8) {
                    if store.trashInFlight {
                        ProgressView().tint(.white)
                    } else {
                        Image(systemName: "trash.fill")
                            .font(.system(size: 15, weight: .bold))
                    }
                    Text("Empty trash (\(deletedPhotos.count))")
                        .font(.system(size: 16, weight: .bold))
                    if trashBytes > 0 {
                        Text(verbatim: PileTitles.bytes(trashBytes))
                            .font(.system(size: 13, weight: .medium))
                            .opacity(0.85)
                    }
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(Capsule().fill(dropColor))
            }
            .buttonStyle(.plain)
            .disabled(store.trashInFlight || deletedPhotos.isEmpty)
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 24)
        }
        .background(bgPage)
    }

    private static let dateFormatter: DateFormatter = {
        let df = DateFormatter()
        df.dateStyle = .medium
        df.timeStyle = .none
        return df
    }()
}
