//
//  Pinchable.swift
//  Unrolled
//

import SwiftUI

/// View modifier that makes its target pinch-to-zoom-able. The zoom is
/// applied via `scaleEffect` on the modified view, clamped between
/// [minScale, maxScale]. When the gesture ends the view springs back to
/// its original scale: we're purely giving the user a moment to
/// inspect the image, not a persistent zoom mode.
///
/// `@GestureState` auto-resets to its initial value once the gesture
/// finishes, so we only need to drive a `.spring` animation on that
/// keyed value for the snap-back feel.
struct PinchableModifier: ViewModifier {
    let minScale: CGFloat
    let maxScale: CGFloat

    @GestureState private var rawMagnification: CGFloat = 1.0

    func body(content: Content) -> some View {
        content
            .scaleEffect(clampedScale)
            // `simultaneousGesture` lets this coexist with a drag gesture
            // the caller might already have (the top card has one for
            // swipe-to-keep/delete). SwiftUI routes two-finger events to
            // the magnification and one-finger events to the drag.
            .simultaneousGesture(
                MagnificationGesture()
                    .updating($rawMagnification) { value, state, _ in
                        state = value
                    }
            )
            .animation(.spring(response: 0.35, dampingFraction: 0.7),
                       value: rawMagnification)
    }

    private var clampedScale: CGFloat {
        // A raw `MagnificationGesture` value can technically be negative
        // (rare, malformed multi-touch). `max(0.5, ...)` avoids flipping
        // the image inside out before we clamp to the design range.
        min(max(max(0.5, rawMagnification), minScale), maxScale)
    }
}

extension View {
    /// Adds pinch-to-zoom on this view. Scales from `minScale` to
    /// `maxScale`; springs back to 1.0 when the fingers lift.
    func pinchable(minScale: CGFloat = 1.0, maxScale: CGFloat = 3.0) -> some View {
        modifier(PinchableModifier(minScale: minScale, maxScale: maxScale))
    }
}
