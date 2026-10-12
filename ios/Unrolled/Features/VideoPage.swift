//
//  VideoPage.swift
//  Unrolled
//

import AVKit
import Photos
import SwiftUI

/// Fullscreen video playback for a PhotoKit asset.
struct VideoPage: View {
    let assetID: String

    @State private var player: AVPlayer?

    var body: some View {
        ZStack {
            Color.black
            if let player {
                VideoPlayer(player: player)
            } else {
                PhotoAssetImage(assetID: assetID, targetSize: CGSize(width: 1200, height: 1200), contentMode: .fit)
                    .overlay(ProgressView().tint(.white))
            }
        }
        .task(id: assetID) {
            player?.pause()
            player = nil
            guard let item = await playerItem(for: assetID) else { return }
            let player = AVPlayer(playerItem: item)
            self.player = player
            player.play()
        }
        .onDisappear {
            player?.pause()
        }
    }

    private func playerItem(for id: String) async -> AVPlayerItem? {
        guard let asset = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil).firstObject else {
            return nil
        }
        let options = PHVideoRequestOptions()
        options.isNetworkAccessAllowed = true
        options.deliveryMode = .automatic
        return await withCheckedContinuation { continuation in
            PHImageManager.default().requestPlayerItem(forVideo: asset, options: options) { item, _ in
                continuation.resume(returning: item)
            }
        }
    }
}
