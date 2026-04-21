package dev.rahier.picswipe

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.hypot

/**
 * Full-screen photo interaction built from raw pointer events:
 * - **Pinch**: zoom stays anchored on the fingers' centroid so the
 *   user can aim at a detail while scaling in (see the transform math
 *   inside the gesture loop).
 * - **Pan during pinch**: two-finger midpoint drag moves the image.
 * - **Single-finger drag** (not pinched): pulls the photo and reports
 *   progress via [onDismissProgress]; past [dismissThresholdPx],
 *   triggers [onDismiss] on release.
 * - **Release**: springs back to scale 1 and offset (0, 0).
 *
 * The view uses `transformOrigin = (0, 0)` so the zoom-around-centroid
 * formula stays simple: `newOffset = centroid * (1 - ratio) + oldOffset * ratio`
 * where `ratio = newScale / oldScale`.
 */
fun Modifier.zoomableDismissable(
    onDismiss: () -> Unit,
    onDismissProgress: (Float) -> Unit = {},
    minScale: Float = 1f,
    maxScale: Float = 4f,
    dismissThresholdPx: Float = 300f
): Modifier = composed {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        ),
        label = "scale"
    )
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        ),
        label = "offsetX"
    )
    val animatedOffsetY by animateFloatAsState(
        targetValue = offsetY,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        ),
        label = "offsetY"
    )

    this
        .graphicsLayer {
            // Origin at top-left so `translation` and `scale` compose
            // cleanly in the pinch-at-centroid formula below.
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = animatedScale
            scaleY = animatedScale
            translationX = animatedOffsetX
            translationY = animatedOffsetY
        }
        .pointerInput(minScale, maxScale, dismissThresholdPx) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var becamePinch = false
                var singleDragX = 0f
                var singleDragY = 0f

                while (true) {
                    val event = awaitPointerEvent()
                    val pressedCount = event.changes.count { it.pressed }
                    if (pressedCount == 0) break

                    if (pressedCount >= 2) {
                        becamePinch = true
                        val zoomChange = event.calculateZoom()
                        val pan = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = false)

                        if (zoomChange != 1f || pan != Offset.Zero) {
                            val newScale = (scale * zoomChange)
                                .coerceIn(minScale, maxScale)
                            val ratio = if (scale == 0f) 1f else newScale / scale
                            // Keep the image point under `centroid` at the
                            // same screen position across the frame's scale
                            // change, then add this frame's pan.
                            offsetX = centroid.x * (1f - ratio) +
                                    offsetX * ratio + pan.x
                            offsetY = centroid.y * (1f - ratio) +
                                    offsetY * ratio + pan.y
                            scale = newScale
                            event.changes.forEach { it.consume() }
                        }
                    } else if (pressedCount == 1 && !becamePinch) {
                        val change = event.changes.first()
                        val delta = change.positionChange()
                        singleDragX += delta.x
                        singleDragY += delta.y
                        offsetX = singleDragX
                        offsetY = singleDragY
                        onDismissProgress(
                            (hypot(singleDragX, singleDragY) / dismissThresholdPx)
                                .coerceIn(0f, 1f)
                        )
                        change.consume()
                    }
                }

                val singleDragDistance = hypot(singleDragX, singleDragY)
                if (!becamePinch && singleDragDistance > dismissThresholdPx) {
                    onDismiss()
                }
                // Spring back. The animateFloatAsState above handles the
                // actual motion; these assignments just set the target.
                scale = 1f
                offsetX = 0f
                offsetY = 0f
                onDismissProgress(0f)
            }
        }
}
