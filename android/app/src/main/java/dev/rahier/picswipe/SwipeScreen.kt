package dev.rahier.picswipe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage

// Accent palette — coral from the design.
private val KeepColor = Color(0xFF30D28A)
private val DropColor = Color(0xFFEF3E5B)
private val UndoColor = Color(0xFFF5B945)

private const val StackDepth = 4
private val DragThresholdDp = 110.dp

/**
 * Top-level stateful entry — grabs the ViewModel, collects its state,
 * and delegates rendering to the pure [SwipeScreenContent]. Keep this
 * wrapper tiny so previews and tests can supply their own state.
 */
@Composable
fun SwipeScreen(
    darkTheme: Boolean,
    viewModel: SwipeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SwipeScreenContent(
        state = state,
        darkTheme = darkTheme,
        onIntent = viewModel::onIntent
    )
}

@Composable
fun SwipeScreenContent(
    state: SwipeState,
    darkTheme: Boolean,
    onIntent: (SwipeIntent) -> Unit
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { DragThresholdDp.toPx() }
    val exitDistancePx = with(density) { 800.dp.toPx() }

    val bgPage = if (darkTheme) Color(0xFF0E0E12) else Color(0xFFF6F5F1)
    val textPrim = if (darkTheme) Color.White else Color(0xFF1A1A1A)
    val textSec = (if (darkTheme) Color.White else Color.Black).copy(alpha = 0.55f)
    val surface = if (darkTheme) Color(0xFF1A1A20) else Color.White
    val border = (if (darkTheme) Color.White else Color.Black)
        .copy(alpha = if (darkTheme) 0.08f else 0.06f)

    Box(
        Modifier
            .fillMaxSize()
            .background(bgPage)
    ) {
        Column(Modifier.fillMaxSize()) {
            Header(
                state = state,
                darkTheme = darkTheme,
                textPrim = textPrim,
                textSec = textSec,
                onIntent = onIntent
            )
            ProgressBar(state = state, border = border)

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                if (state.done) {
                    DoneView(
                        state = state,
                        textPrim = textPrim,
                        textSec = textSec,
                        border = border,
                        onIntent = onIntent
                    )
                } else {
                    CardStack(
                        state = state,
                        exitDistancePx = exitDistancePx,
                        onIntent = onIntent,
                        thresholdPx = thresholdPx
                    )
                }
            }

            if (!state.done) {
                ActionBar(
                    bgPage = bgPage,
                    surface = surface,
                    border = border,
                    historyEmpty = state.history.isEmpty(),
                    onIntent = onIntent
                )
            }
        }

        // Overlays
        AnimatedVisibility(state.pickerOpen, enter = fadeIn(), exit = fadeOut()) {
            AlbumPickerSheet(
                albums = AlbumLibrary.all,
                currentId = state.albumID,
                darkTheme = darkTheme,
                onPick = { onIntent(SwipeIntent.AlbumPicked(it)) },
                onClose = { onIntent(SwipeIntent.AlbumPickerDismissed) }
            )
        }

        if (state.menuOpen) {
            MenuPopover(
                darkTheme = darkTheme,
                onAbout = { onIntent(SwipeIntent.AboutMenuItemTapped) },
                onClose = { onIntent(SwipeIntent.MenuDismissed) }
            )
        }

        AnimatedVisibility(state.aboutOpen, enter = fadeIn(), exit = fadeOut()) {
            AboutSheet(darkTheme = darkTheme, onClose = { onIntent(SwipeIntent.AboutDismissed) })
        }
    }
}

@Composable
private fun Header(
    state: SwipeState,
    darkTheme: Boolean,
    textPrim: Color,
    textSec: Color,
    onIntent: (SwipeIntent) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "ALBUM",
                color = textSec,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onIntent(SwipeIntent.AlbumTitleTapped) }
            ) {
                Text(
                    state.currentAlbum.name,
                    color = textPrim,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Spacer(Modifier.width(6.dp))
                Text("▾", color = textPrim, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CounterPill("✕", DropColor, state.deleted, textPrim)
            CounterPill("♥", KeepColor, state.kept, textPrim)
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (darkTheme) Color.White.copy(alpha = 0.08f)
                        else Color.Black.copy(alpha = 0.05f)
                    )
                    .clickable { onIntent(SwipeIntent.MenuButtonTapped) },
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) {
                        Box(Modifier.size(4.dp).clip(CircleShape).background(textPrim))
                    }
                }
            }
        }
    }
}

@Composable
private fun CounterPill(symbol: String, color: Color, value: Int, textColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(symbol, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("$value", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProgressBar(state: SwipeState, border: Color) {
    val total = PhotoLibrary.samples.size
    Box(
        Modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(border)
    ) {
        val progress by animateFloatAsState(
            targetValue = state.activeIndex.toFloat() / total,
            animationSpec = tween(350),
            label = "progress"
        )
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(DropColor)
        )
    }
}

@Composable
private fun CardStack(
    state: SwipeState,
    exitDistancePx: Float,
    onIntent: (SwipeIntent) -> Unit,
    thresholdPx: Float
) {
    val photos = PhotoLibrary.samples
    val total = photos.size

    val visible = photos
        .mapIndexedNotNull { idx, photo ->
            val depth = idx - state.activeIndex
            if (depth in 0..StackDepth) Pair(photo, depth) else null
        }
        .sortedByDescending { it.second }  // back-to-front

    visible.forEach { (photo, depth) ->
        CardSlot(
            photo = photo,
            isTop = depth == 0,
            depth = depth,
            state = state,
            exitDistancePx = exitDistancePx,
            counterText = if (depth == 0) "${state.activeIndex + 1} / $total" else null,
            thresholdPx = thresholdPx,
            onIntent = onIntent
        )
    }
}

@Composable
private fun CardSlot(
    photo: Photo,
    isTop: Boolean,
    depth: Int,
    state: SwipeState,
    exitDistancePx: Float,
    counterText: String?,
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
                if (isTop) Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { onIntent(SwipeIntent.CardDragEnded(thresholdPx)) },
                        onDragCancel = { onIntent(SwipeIntent.CardDragEnded(thresholdPx)) }
                    ) { change, dragAmount ->
                        change.consume()
                        onIntent(SwipeIntent.CardDragChanged(dragAmount.x, dragAmount.y))
                    }
                } else Modifier
            )
    ) {
        PhotoCardContent(
            photo = photo,
            counterText = counterText,
            keepOpacity = keepOpacity,
            dropOpacity = dropOpacity,
            isTop = isTop
        )
    }
}

@Composable
private fun PhotoCardContent(
    photo: Photo,
    counterText: String?,
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
            model = photo.url,
            contentDescription = photo.location,
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
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                if (counterText != null) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.42f))
                            .padding(horizontal = 11.dp, vertical = 5.dp)
                    ) {
                        Text(counterText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Column {
                Text(photo.date, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(photo.location, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (isTop) {
            Stamp("KEEP", KeepColor, keepOpacity, -18f, Alignment.TopStart)
            Stamp("DELETE", DropColor, dropOpacity, 18f, Alignment.TopEnd)
        }
    }
}

@Composable
private fun BoxScope.Stamp(text: String, color: Color, opacity: Float, rotation: Float, alignment: Alignment) {
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

@Composable
private fun ActionBar(
    bgPage: Color,
    surface: Color,
    border: Color,
    historyEmpty: Boolean,
    onIntent: (SwipeIntent) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(bgPage.copy(alpha = 0f), bgPage.copy(alpha = 0.9f), bgPage)
                )
            )
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionButton(
            size = 58.dp,
            surface = surface,
            border = border,
            glow = DropColor,
            onClick = { onIntent(SwipeIntent.DeleteButtonTapped) }
        ) {
            Text("✕", color = DropColor, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        ActionButton(
            size = 46.dp,
            surface = surface,
            border = border,
            glow = UndoColor,
            enabled = !historyEmpty,
            onClick = { onIntent(SwipeIntent.UndoButtonTapped) }
        ) {
            Text("↺", color = UndoColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        ActionButton(
            size = 58.dp,
            surface = surface,
            border = border,
            glow = KeepColor,
            onClick = { onIntent(SwipeIntent.KeepButtonTapped) }
        ) {
            Text("♥", color = KeepColor, fontSize = 26.sp)
        }
    }
}

@Composable
private fun ActionButton(
    size: Dp,
    surface: Color,
    border: Color,
    glow: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.35f
                shadowElevation = if (enabled) 6.dp.toPx() else 0f
                shape = CircleShape
                clip = false
                ambientShadowColor = glow
                spotShadowColor = glow
            }
            .clip(CircleShape)
            .background(surface)
            .border(1.dp, border, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun DoneView(
    state: SwipeState,
    textPrim: Color, textSec: Color, border: Color,
    onIntent: (SwipeIntent) -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .border(2.dp, border, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("All done!", color = textPrim, fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
            Spacer(Modifier.height(8.dp))
            Text("${state.kept} kept · ${state.deleted} deleted", color = textSec, fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(DropColor)
                    .clickable { onIntent(SwipeIntent.ResetButtonTapped) }
                    .padding(horizontal = 22.dp, vertical = 12.dp)
            ) {
                Text("Start over", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
