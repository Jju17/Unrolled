//
//  Pile.swift
//  Unrolled
//

import Foundation

/// What the deck is built from. Albums come from the library, the other
/// cases are computed from creation dates and media types.
enum PileSelector: Equatable, Hashable, Codable {
    case all
    case album(String)
    case month(year: Int, month: Int)
    case onThisDay
    case oneYearAgo
    case screenshots
    case videos

    var id: String {
        switch self {
        case .all: "all"
        case .album(let id): "album:\(id)"
        case .month(let year, let month): "month:\(year)-" + String(format: "%02d", month)
        case .onThisDay: "onThisDay"
        case .oneYearAgo: "oneYearAgo"
        case .screenshots: "screenshots"
        case .videos: "videos"
        }
    }
}

struct Pile: Identifiable, Equatable, Hashable {
    let selector: PileSelector
    let name: String?
    let photoCount: Int
    let remainingCount: Int
    let keyAssetID: String?

    init(
        selector: PileSelector,
        name: String? = nil,
        photoCount: Int,
        remainingCount: Int,
        keyAssetID: String? = nil
    ) {
        self.selector = selector
        self.name = name
        self.photoCount = photoCount
        self.remainingCount = remainingCount
        self.keyAssetID = keyAssetID
    }

    var id: String { selector.id }

    var progress: Double {
        guard photoCount > 0 else { return 0 }
        return 1 - Double(remainingCount) / Double(photoCount)
    }
}
