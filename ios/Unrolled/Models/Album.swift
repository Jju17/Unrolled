//
//  Album.swift
//  Unrolled
//

import Foundation

/// Reference to a device photo album. The `id` is the PhotoKit
/// `PHAssetCollection.localIdentifier`; pass it back to
/// `PhotoLibraryClient.fetchPhotos(_:)` to get that album's photos.
struct Album: Identifiable, Equatable, Hashable {
    let id: String
    let name: String
    let count: Int
    let icon: String
    /// Local identifier of the album's representative photo (newest asset),
    /// used as the row thumbnail. `nil` when the album is empty.
    let keyAssetID: String?
}
