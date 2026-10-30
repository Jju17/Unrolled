//
//  DeckLayout.swift
//  Unrolled
//

import Foundation

enum DeckLayout {
    static let maxColumnWidth: CGFloat = 600
    static let maxCardAspectRatio: CGFloat = 0.85
    static let minFoldedPaneLength: CGFloat = 180

    enum FoldOrientation: Equatable {
        case horizontal
        case vertical
    }

    struct FoldedPanes: Equatable {
        let orientation: FoldOrientation
        let content: CGRect
        let controls: CGRect
    }

    static func cardSize(fitting available: CGSize) -> CGSize {
        let width = max(0, available.width)
        let height = max(0, available.height)
        guard height > 0 else { return CGSize(width: width, height: 0) }
        return CGSize(width: min(width, height * maxCardAspectRatio), height: height)
    }

    static func foldedPanes(in bounds: CGRect, fold: CGRect) -> FoldedPanes? {
        let band = fold.intersection(bounds)
        guard !band.isNull, band.width > 0, band.height > 0 else { return nil }
        if band.width >= band.height {
            let content = CGRect(
                x: bounds.minX, y: bounds.minY,
                width: bounds.width, height: band.minY - bounds.minY
            )
            let controls = CGRect(
                x: bounds.minX, y: band.maxY,
                width: bounds.width, height: bounds.maxY - band.maxY
            )
            guard content.height >= minFoldedPaneLength, controls.height >= minFoldedPaneLength else { return nil }
            return FoldedPanes(orientation: .horizontal, content: content, controls: controls)
        }
        let content = CGRect(
            x: bounds.minX, y: bounds.minY,
            width: band.minX - bounds.minX, height: bounds.height
        )
        let controls = CGRect(
            x: band.maxX, y: bounds.minY,
            width: bounds.maxX - band.maxX, height: bounds.height
        )
        guard content.width >= minFoldedPaneLength, controls.width >= minFoldedPaneLength else { return nil }
        return FoldedPanes(orientation: .vertical, content: content, controls: controls)
    }
}
