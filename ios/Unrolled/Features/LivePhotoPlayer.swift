//
//  LivePhotoPlayer.swift
//  Unrolled
//

import Photos
import PhotosUI
import SwiftUI
import UIKit

/// Bridges `PHLivePhotoView` into SwiftUI. Loads the Live Photo for
/// `assetID` asynchronously through `PHImageManager`.
///
/// `autoPlay` controls the post-load behaviour:
/// - `true` (used in fullscreen) to starts the `.full` playback
///   immediately, then idles on the still image until the next reload.
/// - `false` (used on the swipe card) to only sets the live photo and
///   relies on `PHLivePhotoView`'s built-in long-press gesture: the
///   user holds the photo to play the motion portion, releases to
///   freeze. Same affordance as Photos.app.
///
/// Falls back to a plain image if the asset isn't actually a Live Photo
/// or the load fails: callers gate on `photo.isLivePhoto` to avoid
/// the load round-trip when not needed.
struct LivePhotoPlayer: UIViewRepresentable {
    let assetID: String
    var autoPlay: Bool = false
    var contentMode: UIView.ContentMode = .scaleAspectFit

    func makeUIView(context: Context) -> PHLivePhotoView {
        let view = PHLivePhotoView()
        view.contentMode = contentMode
        view.clipsToBounds = true
        return view
    }

    func updateUIView(_ uiView: PHLivePhotoView, context: Context) {
        uiView.contentMode = contentMode
        // SwiftUI re-runs this on every parent render (each drag frame
        // on the top card); only a new identifier warrants a request.
        guard context.coordinator.loadedAssetID != assetID else { return }
        context.coordinator.loadedAssetID = assetID
        if let previous = context.coordinator.requestID {
            PHImageManager.default().cancelImageRequest(previous)
        }

        let result = PHAsset.fetchAssets(withLocalIdentifiers: [assetID], options: nil)
        guard let asset = result.firstObject else { return }

        let options = PHLivePhotoRequestOptions()
        options.deliveryMode = .highQualityFormat
        options.isNetworkAccessAllowed = true

        let shouldAutoplay = autoPlay
        context.coordinator.requestID = PHImageManager.default().requestLivePhoto(
            for: asset,
            targetSize: PHImageManagerMaximumSize,
            contentMode: .aspectFit,
            options: options
        ) { livePhoto, _ in
            DispatchQueue.main.async {
                guard let livePhoto else { return }
                uiView.livePhoto = livePhoto
                if shouldAutoplay {
                    uiView.startPlayback(with: .full)
                }
            }
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator {
        var loadedAssetID: String?
        var requestID: PHImageRequestID?
    }
}
