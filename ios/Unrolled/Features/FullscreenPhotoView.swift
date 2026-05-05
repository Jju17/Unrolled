//
//  FullscreenPhotoView.swift
//  Unrolled
//

import SwiftUI
import UIKit

/// Full-screen photo preview.
///
/// Gestures (delegated to UIKit for a proper feel):
/// - **Pinch + pan** is handled by the embedded `UIScrollView` so the
///   zoom anchors on the fingers' midpoint ("aim and zoom") and the
///   image can be panned once zoomed.
/// - **Swipe-to-dismiss** is a SwiftUI `DragGesture` overlay that
///   activates only while `zoomScale == 1`; once zoomed, the overlay
///   disables itself so the scrollView receives the touches.
struct FullscreenPhotoView: View {
    @Environment(\.dismiss) private var dismiss

    let photo: PhotoAsset

    @State private var zoomScale: CGFloat = 1.0
    @GestureState private var dismissDrag: CGSize = .zero

    private let dismissThreshold: CGFloat = 120

    private var dismissProgress: CGFloat {
        let distance = hypot(dismissDrag.width, dismissDrag.height)
        return min(1, distance / dismissThreshold)
    }

    var body: some View {
        ZStack {
            Color.black
                .opacity(1 - dismissProgress * 0.7)
                .ignoresSafeArea()

            ZoomableScrollView(zoomScale: $zoomScale, maxScale: 4) {
                GeometryReader { geo in
                    PhotoAssetImage(
                        assetID: photo.id,
                        targetSize: CGSize(
                            width: geo.size.width * UIScreen.main.scale,
                            height: geo.size.height * UIScreen.main.scale
                        ),
                        contentMode: .fit
                    )
                    .frame(width: geo.size.width, height: geo.size.height)
                }
            }
            .ignoresSafeArea()
            .offset(dismissDrag)
            .animation(.spring(response: 0.35, dampingFraction: 0.8),
                       value: dismissDrag)

            // Dismiss-drag catcher. Only reaches the user's finger while
            // zoom is 1 — otherwise `allowsHitTesting(false)` lets the
            // scrollView receive pan input for actual panning.
            Color.clear
                .contentShape(Rectangle())
                .allowsHitTesting(zoomScale == 1)
                .gesture(
                    DragGesture(minimumDistance: 10)
                        .updating($dismissDrag) { value, state, _ in
                            state = value.translation
                        }
                        .onEnded { value in
                            let distance = hypot(
                                value.translation.width,
                                value.translation.height
                            )
                            if distance > dismissThreshold {
                                dismiss()
                            }
                        }
                )
                .onTapGesture {
                    if zoomScale == 1 { dismiss() }
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
            .opacity(1 - dismissProgress)
        }
        .statusBarHidden()
    }
}
