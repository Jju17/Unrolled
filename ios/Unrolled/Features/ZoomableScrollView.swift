//
//  ZoomableScrollView.swift
//  Unrolled
//

import SwiftUI
import UIKit

/// Wraps any SwiftUI content inside a `UIScrollView` that does the
/// classic pinch-at-fingers-midpoint zoom + pan that SwiftUI's own
/// `MagnificationGesture` can't express. Also bouncy overscroll free.
struct ZoomableScrollView<Content: View>: UIViewRepresentable {
    let maxScale: CGFloat
    let onZoomChanged: (Bool) -> Void
    let content: () -> Content

    init(
        maxScale: CGFloat = 4,
        onZoomChanged: @escaping (Bool) -> Void = { _ in },
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.maxScale = maxScale
        self.onZoomChanged = onZoomChanged
        self.content = content
    }

    func makeUIView(context: Context) -> UIScrollView {
        let scrollView = UIScrollView()
        scrollView.delegate = context.coordinator
        scrollView.minimumZoomScale = 1
        scrollView.maximumZoomScale = maxScale
        scrollView.bouncesZoom = true
        scrollView.showsVerticalScrollIndicator = false
        scrollView.showsHorizontalScrollIndicator = false
        scrollView.backgroundColor = .clear
        scrollView.contentInsetAdjustmentBehavior = .never
        // Pan is gated via `isScrollEnabled` based on zoomScale (see
        // delegate below). Reassigning the pan recognizer's delegate is
        // explicitly forbidden by UIKit and crashes the app.
        scrollView.isScrollEnabled = false

        let host = UIHostingController(rootView: content())
        host.view.translatesAutoresizingMaskIntoConstraints = false
        host.view.backgroundColor = .clear
        scrollView.addSubview(host.view)
        context.coordinator.hostingController = host
        context.coordinator.onZoomChanged = onZoomChanged

        NSLayoutConstraint.activate([
            host.view.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
            host.view.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
            host.view.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
            host.view.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
            host.view.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor),
            host.view.heightAnchor.constraint(equalTo: scrollView.frameLayoutGuide.heightAnchor),
        ])

        return scrollView
    }

    func updateUIView(_ uiView: UIScrollView, context: Context) {
        context.coordinator.hostingController?.rootView = content()
        context.coordinator.onZoomChanged = onZoomChanged
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator: NSObject, UIScrollViewDelegate {
        var hostingController: UIHostingController<Content>?
        var onZoomChanged: (Bool) -> Void = { _ in }

        func viewForZooming(in scrollView: UIScrollView) -> UIView? {
            hostingController?.view
        }

        func scrollViewDidZoom(_ scrollView: UIScrollView) {
            // Pan only makes sense once we're actually zoomed in. Toggle
            // `isScrollEnabled` rather than hijacking the pan gesture's
            // delegate (which UIKit forbids and crashes on).
            scrollView.isScrollEnabled = scrollView.zoomScale > 1.0
            onZoomChanged(scrollView.zoomScale > 1.0)
            // Keep the content centered when smaller than the viewport.
            let bounds = scrollView.bounds
            let size = scrollView.contentSize
            let offsetX = max((bounds.width - size.width) / 2, 0)
            let offsetY = max((bounds.height - size.height) / 2, 0)
            scrollView.contentInset = UIEdgeInsets(
                top: offsetY, left: offsetX, bottom: 0, right: 0
            )
        }

        func scrollViewDidEndZooming(
            _ scrollView: UIScrollView,
            with view: UIView?,
            atScale scale: CGFloat
        ) {
            scrollView.isScrollEnabled = scale > 1.0
            onZoomChanged(scale > 1.0)
        }
    }
}
