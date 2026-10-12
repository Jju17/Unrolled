package dev.rahier.unrolled

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.random.Random

private val ConfettiColors = listOf(KeepColor, DropColor, UndoColor, Color(0xFF5B8DEF), Color(0xFFB36BFF))

private class Particle(
    val x: Float,
    val color: Color,
    val size: Float,
    val speed: Float,
    val drift: Float,
    val spin: Float,
    val delay: Float
)

/** Two seconds of falling paper; skipped when the user has animations turned off. */
@Composable
internal fun Confetti(modifier: Modifier = Modifier, durationMs: Int = 2_000) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    if (reduceMotion) return
    val particles = remember {
        val random = Random(42)
        List(90) {
            Particle(
                x = random.nextFloat(),
                color = ConfettiColors[random.nextInt(ConfettiColors.size)],
                size = 6f + random.nextFloat() * 8f,
                speed = 0.7f + random.nextFloat() * 0.6f,
                drift = (random.nextFloat() - 0.5f) * 0.3f,
                spin = random.nextFloat() * 360f,
                delay = random.nextFloat() * 0.3f
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
    }
    if (progress.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        val density = 1.dp.toPx()
        particles.forEach { particle ->
            val local = ((t - particle.delay) / (1f - particle.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val y = local * particle.speed * (size.height + 40f * density) - 20f * density
            val x = (particle.x + particle.drift * local) * size.width
            val angle = particle.spin + local * 720f
            val alpha = if (local > 0.8f) (1f - local) / 0.2f else 1f
            val radius = particle.size * density / 2f
            rotate(angle, pivot = Offset(x, y)) {
                drawRect(
                    color = particle.color.copy(alpha = alpha),
                    topLeft = Offset(x - radius, y - radius / 2f),
                    size = Size(radius * 2f, radius)
                )
            }
        }
    }
}
