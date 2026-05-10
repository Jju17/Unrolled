//
//  PhotoAsset.swift
//  Unrolled
//

import Foundation

/// A lightweight value type referencing a photo in the user's library.
/// Designed to live in TCA State — no framework types leak in.
struct PhotoAsset: Identifiable, Equatable, Hashable {
    let id: String            // PHAsset.localIdentifier on iOS
    let creationDate: Date?
    /// Optional GPS coordinates extracted from the asset's metadata.
    /// Both nil ⇔ no location attached. Stored as raw doubles so this
    /// type stays free of `CoreLocation` imports for testability.
    let latitude: Double?
    let longitude: Double?
    /// `true` when the underlying PHAsset is a Live Photo (`.photoLive`
    /// in `mediaSubtypes`). Used by the renderer to decide whether to
    /// instantiate a `PHLivePhotoView` instead of a static image.
    let isLivePhoto: Bool

    var hasLocation: Bool { latitude != nil && longitude != nil }

    init(
        id: String,
        creationDate: Date? = nil,
        latitude: Double? = nil,
        longitude: Double? = nil,
        isLivePhoto: Bool = false
    ) {
        self.id = id
        self.creationDate = creationDate
        self.latitude = latitude
        self.longitude = longitude
        self.isLivePhoto = isLivePhoto
    }
}

/// Our abstraction over `PHAuthorizationStatus` so feature State doesn't
/// need to import Photos. `PhotoLibraryClient` maps between the two.
enum PhotoAuthorizationStatus: Equatable {
    case notDetermined
    case denied       // also covers `.restricted`
    case authorized   // full access
    case limited      // user picked specific photos (iOS 14+)

    var grantsRead: Bool {
        self == .authorized || self == .limited
    }
}
