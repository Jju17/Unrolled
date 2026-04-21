//
//  FullscreenPhotoView.swift
//  PicSwipe
//

import SwiftUI
import UIKit

/// Full-screen photo preview shown when the user taps the top card.
/// Tap anywhere (or the close button) dismisses. The underlying swipe
/// screen stays paused because `.fullScreenCover` covers it entirely.
struct FullscreenPhotoView: View {
    @Environment(\.dismiss) private var dismiss

    let photo: PhotoAsset

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            GeometryReader { geo in
                PhotoAssetImage(
                    assetID: photo.id,
                    targetSize: CGSize(
                        width: geo.size.width * UIScreen.main.scale,
                        height: geo.size.height * UIScreen.main.scale
                    )
                )
                .frame(width: geo.size.width, height: geo.size.height)
                .contentShape(Rectangle())
                .onTapGesture { dismiss() }
            }

            VStack {
                HStack {
                    Spacer()
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 34, height: 34)
                            .background(Circle().fill(Color.black.opacity(0.5)))
                    }
                    .buttonStyle(.plain)
                }
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
        }
        .statusBarHidden()
    }
}
