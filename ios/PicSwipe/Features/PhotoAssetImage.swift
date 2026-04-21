//
//  PhotoAssetImage.swift
//  PicSwipe
//

import Photos
import SwiftUI
import UIKit

/// Renders a `PHAsset` (looked up by local identifier) using
/// `PHImageManager`. Reloads when `assetID` or `targetSize` changes.
/// This is pure presentation — no TCA dependency: given an ID, load.
struct PhotoAssetImage: View {
    let assetID: String
    let targetSize: CGSize

    @State private var image: UIImage?

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                Color(white: 0.13)
                    .overlay(ProgressView().tint(.white))
            }
        }
        .task(id: assetID) {
            image = await loadImage(id: assetID, size: targetSize)
        }
    }
}

private func loadImage(id: String, size: CGSize) async -> UIImage? {
    let result = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil)
    guard let asset = result.firstObject else { return nil }

    let options = PHImageRequestOptions()
    options.deliveryMode = .highQualityFormat  // single callback
    options.isNetworkAccessAllowed = true
    options.resizeMode = .exact
    options.isSynchronous = false

    return await withCheckedContinuation { continuation in
        PHImageManager.default().requestImage(
            for: asset,
            targetSize: size == .zero ? PHImageManagerMaximumSize : size,
            contentMode: .aspectFill,
            options: options
        ) { image, _ in
            continuation.resume(returning: image)
        }
    }
}
