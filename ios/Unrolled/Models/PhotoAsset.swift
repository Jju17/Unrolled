//
//  PhotoAsset.swift
//  Unrolled
//

import Foundation

/// A lightweight value type referencing a photo or video in the user's
/// library. Designed to live in TCA State: no framework types leak in.
struct PhotoAsset: Identifiable, Equatable, Hashable {
    let id: String
    let creationDate: Date?
    let latitude: Double?
    let longitude: Double?
    let isLivePhoto: Bool
    let isVideo: Bool
    let durationSeconds: Double?

    var hasLocation: Bool { latitude != nil && longitude != nil }

    init(
        id: String,
        creationDate: Date? = nil,
        latitude: Double? = nil,
        longitude: Double? = nil,
        isLivePhoto: Bool = false,
        isVideo: Bool = false,
        durationSeconds: Double? = nil
    ) {
        self.id = id
        self.creationDate = creationDate
        self.latitude = latitude
        self.longitude = longitude
        self.isLivePhoto = isLivePhoto
        self.isVideo = isVideo
        self.durationSeconds = durationSeconds
    }
}

/// Our abstraction over `PHAuthorizationStatus` so feature State doesn't
/// need to import Photos. `PhotoLibraryClient` maps between the two.
enum PhotoAuthorizationStatus: Equatable {
    case notDetermined
    case denied
    case authorized
    case limited

    var grantsRead: Bool {
        self == .authorized || self == .limited
    }
}

enum PhotoLibraryError: Error, Equatable {
    case cancelled
}

struct PhotoDecision: Equatable, Hashable {
    let photoID: String
    let kept: Bool
}

struct SwipeHistoryEntry: Equatable {
    let decisions: [PhotoDecision]

    init(decisions: [PhotoDecision]) {
        self.decisions = decisions
    }

    init(photoID: String, kept: Bool) {
        self.decisions = [PhotoDecision(photoID: photoID, kept: kept)]
    }
}
