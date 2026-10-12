package dev.rahier.unrolled

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Pinch-to-zoom with springy snap-back on finger lift. Only activates
 * when 2+ pointers are present, so a single-finger drag (e.g. swipe to
 * keep/delete on the stack card) is unaffected and reaches the
 * downstream `detectDragGestures` handler.
 *
 * Zoom is clamped in `[minScale, maxScale]`. The view returns to 1f
 * with a medium-stiffness spring once the gesture ends.
 */
fun Modifier.pinchable(
    minScale: Float = 1f,
    maxScale: Float = 3f
): Modifier = composed {
    var targetScale by remember { mutableFloatStateOf(1f) }
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        ),
        label = "pinch"
    )

    this
        .graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
        }
        .pointerInput(minScale, maxScale) {
            awaitEachGesture {
                // Start watching after a down, then drive zoom as long as
                // 2+ pointers are down. Single-finger events are left
                // unconsumed so other handlers (drag) still receive them.
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    val activePointers = event.changes.count { it.pressed }
                    if (activePointers >= 2) {
                        val zoom = event.calculateZoom()
                        if (zoom != 1f) {
                            targetScale = (targetScale * zoom)
                                .coerceIn(minScale, maxScale)
                            // Mark the pointers used so the drag handler
                            // below us doesn't interpret two-finger
                            // midpoint movement as a swipe.
                            event.changes.forEach { it.consume() }
                        }
                    }
                } while (event.changes.any { it.pressed })
                // All fingers lifted: snap back to 1f.
                targetScale = 1f
            }
        }
}
