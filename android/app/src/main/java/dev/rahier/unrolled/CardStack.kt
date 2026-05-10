package dev.rahier.unrolled

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.zIndex
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

@Composable
internal fun CardStack(
    state: SwipeState,
    exitDistancePx: Float,
    onIntent: (SwipeIntent) -> Unit,
    thresholdPx: Float
) {
    // Light haptic on every commit. `activeIndex` going up is the
    // authoritative signal (covers drag gesture + action buttons).
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state.activeIndex) {
        if (state.activeIndex > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // Render back-to-front: deepest stack card first, top card last.
    // The fly-off card is rendered separately below, so the stack shift
    // and the exit animation are fully decoupled.
    val lastIdx = (state.activeIndex + StackDepth).coerceAtMost(state.photos.lastIndex)
    for (idx in lastIdx downTo state.activeIndex) {
        if (idx < 0 || idx >= state.photos.size) continue
        val photo = state.photos[idx]
        key(photo.id) {
            StackCardSlot(
                photo = photo,
                depth = idx - state.activeIndex,
                state = state,
                thresholdPx = thresholdPx,
                showLocation = state.settings.showLocation,
                onIntent = onIntent
            )
        }
    }

    // Fly-off overlay. Fresh key per photo id = fresh `Animatable`, so
    // each commit plays a full arc from its captured drag-at-lift to
    // off-screen without ever being interrupted by stack recomposition.
    state.exitingPhoto?.let { photo ->
        key(photo.id) {
            ExitingCardOverlay(
                photo = photo,
                direction = state.exitingDirection,
                startDragX = state.exitingStartDragX,
                startDragY = state.exitingStartDragY,
                exitDistancePx = exitDistancePx
            )
        }
    }
}

@Composable
private fun StackCardSlot(
    photo: PhotoAsset,
    depth: Int,
    state: SwipeState,
    thresholdPx: Float,
    showLocation: Boolean,
    onIntent: (SwipeIntent) -> Unit
) {
    val density = LocalDensity.current
    val stackRotations = floatArrayOf(0f, -1.2f, 0.8f, -0.6f, 0.4f)
    val isTop = depth == 0
    val clampedDepth = depth.coerceAtLeast(0)
    val stackRotate = stackRotations.getOrElse(clampedDepth) { 0f }
    val stackScale = 1f - clampedDepth * 0.045f
    val stackYPx = with(density) { (clampedDepth * 10).dp.toPx() }

    val dragX = if (isTop) state.dragX else 0f
    val dragY = if (isTop) state.dragY else 0f

    val targetX = if (isTop) dragX else 0f
    val targetY = if (isTop) dragY else stackYPx
    val targetRotation = if (isTop) dragX / 15f else stackRotate
    val targetScale = if (isTop) 1f else stackScale

    // Live drag tracks the finger 1:1 (`snap`). Every other transition —
    // the stack rise on commit (depth 1 → 0, 2 → 1, …) and the
    // spring-back from an under-threshold drag — uses the same spring.
    val cardSpec = if (state.isDragging && isTop) {
        snap<Float>()
    } else {
        spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy,
            visibilityThreshold = 0.5f
        )
    }
    val animatedX by animateFloatAsState(targetX, cardSpec, label = "x")
    val animatedY by animateFloatAsState(targetY, cardSpec, label = "y")
    val animatedRot by animateFloatAsState(targetRotation, cardSpec, label = "rot")
    val animatedScale by animateFloatAsState(targetScale, cardSpec, label = "scale")

    val keepOpacity = if (isTop) (dragX / 80f).coerceIn(0f, 1f) else 0f
    val dropOpacity = if (isTop) (-dragX / 80f).coerceIn(0f, 1f) else 0f

    // Entrance animation: runs once per photo (enclosing `key(photo.id)`
    // gives each card a fresh scope). On app launch the whole stack
    // reveals itself; after a commit, only the newcomer at depth = 4
    // re-runs this — cards already in the composition just slide up.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300)
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(100f - clampedDepth)
            .graphicsLayer {
                translationX = animatedX
                translationY = animatedY
                rotationZ = animatedRot
                // Entrance: starts slightly smaller, scales up to the
                // stack-depth scale. Multiplicative so the deepest-card
                // slide-in still looks proportional.
                val entScale = 0.85f + 0.15f * entrance.value
                scaleX = animatedScale * entScale
                scaleY = animatedScale * entScale
                alpha = entrance.value
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .then(
                if (isTop) Modifier
                    .pinchable()
                    .clip(RoundedCornerShape(24.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onIntent(SwipeIntent.TopCardTapped) }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { onIntent(SwipeIntent.CardDragEnded(thresholdPx)) },
                            onDragCancel = { onIntent(SwipeIntent.CardDragEnded(thresholdPx)) }
                        ) { change, dragAmount ->
                            change.consume()
                            onIntent(SwipeIntent.CardDragChanged(dragAmount.x, dragAmount.y))
                        }
                    }
                else Modifier
            )
    ) {
        val placeName = rememberPlaceName(photo, showLocation, isTop)
        PhotoCardContent(
            photo = photo,
            keepOpacity = keepOpacity,
            dropOpacity = dropOpacity,
            isTop = isTop,
            placeName = placeName
        )
    }
}

@Composable
private fun rememberPlaceName(photo: PhotoAsset, showLocation: Boolean, isTop: Boolean): String? {
    val describer = LocalLocationDescriber.current
    var placeName by remember(photo.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(photo.id, showLocation, isTop, describer) {
        placeName = null
        if (describer != null && showLocation && isTop) {
            placeName = describer.placeName(photo.id)
        }
    }
    return placeName
}

@Composable
private fun ExitingCardOverlay(
    photo: PhotoAsset,
    direction: Int,
    startDragX: Float,
    startDragY: Float,
    exitDistancePx: Float
) {
    // One Animatable from 0 → 1 drives offset/rotation/opacity. Always
    // plays its full arc because it's owned by this standalone overlay,
    // not by the stack that's already shifting under it.
    val progress = remember(photo.id) { Animatable(0f) }
    LaunchedEffect(photo.id) {
        progress.animateTo(
            targetValue = 1f,
            // 260 ms: shorter than the reducer's cleanup delay so the
            // animation always finishes before `exitingPhoto` is cleared.
            animationSpec = tween(
                durationMillis = 260,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            )
        )
    }

    val endX = startDragX + direction * (exitDistancePx + 300f)

    // No alpha fade — pure translation. The card slides fully off
    // screen and disappears at its off-screen position, so there's no
    // "fade in/out" artefact visible where the new top lives.
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(200f)
            .graphicsLayer {
                translationX = lerp(startDragX, endX, progress.value)
                translationY = startDragY
                rotationZ = lerp(startDragX / 15f, direction * 30f, progress.value)
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .clip(RoundedCornerShape(24.dp))
    ) {
        PhotoCardContent(
            photo = photo,
            keepOpacity = if (direction > 0) 1f else 0f,
            dropOpacity = if (direction < 0) 1f else 0f,
            isTop = true,
            placeName = null
        )
    }
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

@Composable
private fun PhotoCardContent(
    photo: PhotoAsset,
    keepOpacity: Float,
    dropOpacity: Float,
    isTop: Boolean,
    placeName: String?
) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF222222))
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.55f)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            photo.creationDate?.let {
                Text(
                    formatDate(it),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            placeName?.let {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = it,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        if (isTop) {
            Stamp(stringResource(R.string.stamp_keep), KeepColor, keepOpacity, -18f, Alignment.TopStart)
            Stamp(stringResource(R.string.stamp_delete), DropColor, dropOpacity, 18f, Alignment.TopEnd)
        }
    }
}

@Composable
private fun BoxScope.Stamp(
    text: String,
    color: Color,
    opacity: Float,
    rotation: Float,
    alignment: Alignment
) {
    val scale = 0.6f + opacity * 0.4f
    Box(
        Modifier
            .align(alignment)
            .padding(22.dp)
            .graphicsLayer {
                rotationZ = rotation
                scaleX = scale
                scaleY = scale
                alpha = opacity
            }
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.92f))
            .border(3.dp, color, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp
        )
    }
}

private val dateFormatter: DateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

private fun formatDate(date: Date): String = dateFormatter.format(date)
