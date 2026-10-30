//
//  PhotoMetadataCard.swift
//  Unrolled
//

import SwiftUI

struct PhotoMetadataCard: View {
    @Environment(\.colorScheme) private var colorScheme

    let photo: PhotoAsset
    let byteCount: Int64?
    let showLocation: Bool

    @State private var placeName: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let date = photo.creationDate {
                row(Text("Taken"), value: Text(verbatim: Self.dateFormatter.string(from: date)))
            }
            if showLocation, let placeName {
                row(Text("Place"), value: Text(verbatim: placeName))
            }
            if let byteCount {
                row(Text("Size"), value: Text(verbatim: PileTitles.bytes(byteCount)))
            }
            row(Text("Type"), value: typeLabel)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(DeckPalette.surface(colorScheme))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(DeckPalette.border(colorScheme), lineWidth: 1))
        )
        .task(id: "\(photo.id)|\(showLocation)") {
            placeName = nil
            guard showLocation,
                  let lat = photo.latitude, let lng = photo.longitude
            else { return }
            placeName = await LocationGeocoder.shared.placeName(latitude: lat, longitude: lng)
        }
    }

    private var typeLabel: Text {
        if photo.isVideo {
            return Text("Video, \(PileTitles.clock(photo.durationSeconds ?? 0))")
        }
        return photo.isLivePhoto ? Text("Live Photo") : Text("Photo")
    }

    private func row(_ label: Text, value: Text) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            label
                .font(.system(size: 13))
                .foregroundStyle(DeckPalette.textSecondary(colorScheme))
            Spacer(minLength: 8)
            value
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(DeckPalette.textPrimary(colorScheme))
                .multilineTextAlignment(.trailing)
        }
    }

    private static let dateFormatter: DateFormatter = {
        let df = DateFormatter()
        df.dateStyle = .medium
        df.timeStyle = .short
        return df
    }()
}
