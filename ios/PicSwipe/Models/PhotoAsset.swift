//
//  PhotoAsset.swift
//  PicSwipe
//

import Foundation

/// A lightweight value type referencing a photo in the user's library.
/// Designed to live in TCA State — no framework types leak in.
struct PhotoAsset: Identifiable, Equatable, Hashable {
    let id: String            // PHAsset.localIdentifier on iOS
    let creationDate: Date?
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
