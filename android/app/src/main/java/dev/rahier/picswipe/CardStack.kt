package dev.rahier.picswipe

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
    val visible = state.photos
        .mapIndexedNotNull { idx, photo ->
            val depth = idx - state.activeIndex
            if (depth in 0..StackDepth) Pair(photo, depth) else null
        }
        .sortedByDescending { it.second }

    visible.forEach { (photo, depth) ->
        CardSlot(
            photo = photo,
            isTop = depth == 0,
            depth = depth,
            state = state,
            exitDistancePx = exitDistancePx,
            thresholdPx = thresholdPx,
            onIntent = onIntent
        )
    }
}

@Composable
private fun CardSlot(
    photo: PhotoAsset,
    isTop: Boolean,
    depth: Int,
    state: SwipeState,
    exitDistancePx: Float,
    thresholdPx: Float,
    onIntent: (SwipeIntent) -> Unit
) {
    val density = LocalDensity.current
    val stackRotations = floatArrayOf(0f, -1.2f, 0.8f, -0.6f, 0.4f)
    val stackRotate = stackRotations.getOrElse(depth) { 0f }
    val stackScale = 1f - depth * 0.045f
    val stackYPx = with(density) { (depth * 10).dp.toPx() }

    val exiting = isTop && state.exitingDirection != 0
    val dragX = if (isTop) state.dragX else 0f
    val dragY = if (isTop) state.dragY else 0f

    val targetX = when {
        exiting -> state.exitingDirection * exitDistancePx
        isTop -> dragX
        else -> 0f
    }
    val targetY = (if (exiting || !isTop) 0f else dragY) + stackYPx
    val targetRotation = when {
        exiting -> state.exitingDirection * 30f
        isTop -> dragX / 15f
        else -> stackRotate
    }

    val animatedX by animateFloatAsState(
        targetValue = targetX,
        animationSpec = if (state.isDragging && isTop) tween(0) else tween(350),
        label = "x"
    )
    val animatedY by animateFloatAsState(
        targetValue = targetY,
        animationSpec = if (state.isDragging && isTop) tween(0) else tween(350),
        label = "y"
    )
    val animatedRot by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = if (state.isDragging && isTop) tween(0) else tween(350),
        label = "rot"
    )

    val keepOpacity = if (isTop) (dragX / 80f).coerceIn(0f, 1f) else 0f
    val dropOpacity = if (isTop) (-dragX / 80f).coerceIn(0f, 1f) else 0f

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = animatedX
                translationY = animatedY
                rotationZ = animatedRot
                scaleX = if (isTop) 1f else stackScale
                scaleY = if (isTop) 1f else stackScale
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .then(
                if (isTop) Modifier
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
        PhotoCardContent(
            photo = photo,
            keepOpacity = keepOpacity,
            dropOpacity = dropOpacity,
            isTop = isTop
        )
    }
}

@Composable
private fun PhotoCardContent(
    photo: PhotoAsset,
    keepOpacity: Float,
    dropOpacity: Float,
    isTop: Boolean
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
        photo.creationDate?.let {
            Text(
                formatDate(it),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(18.dp)
            )
        }
        if (isTop) {
            Stamp("KEEP", KeepColor, keepOpacity, -18f, Alignment.TopStart)
            Stamp("DELETE", DropColor, dropOpacity, 18f, Alignment.TopEnd)
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
