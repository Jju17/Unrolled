package dev.rahier.unrolled

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import java.io.File

data class ShareCardContent(
    val title: String,
    val headline: String,
    val subtitle: String
)

private const val CARD_WIDTH = 1080
private const val CARD_HEIGHT = 1350

/** Draws a 1080x1350 card with no photo content and opens the system share sheet. */
fun shareCard(context: Context, content: ShareCardContent) {
    val bitmap = createBitmap(CARD_WIDTH, CARD_HEIGHT)
    val canvas = Canvas(bitmap)
    val background = Paint().apply {
        shader = LinearGradient(
            0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(),
            intArrayOf("#0E0E12".toColorInt(), "#1E1B2E".toColorInt()),
            null,
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), background)

    val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#30D28A".toColorInt() }
    canvas.drawRoundRect(RectF(80f, 120f, 200f, 136f), 8f, 8f, accent)

    val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 44f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText("Unrolled", 80f, 220f, brand)

    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
        textSize = 48f
    }
    drawWrapped(canvas, content.title, 80f, 520f, CARD_WIDTH - 160f, title, 60f)

    val headline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 160f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    drawWrapped(canvas, content.headline, 80f, 720f, CARD_WIDTH - 160f, headline, 180f)

    val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        textSize = 44f
    }
    drawWrapped(canvas, content.subtitle, 80f, 880f, CARD_WIDTH - 160f, subtitle, 56f)

    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, "unrolled-card.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, null).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    runCatching { context.startActivity(chooser) }
}

private fun drawWrapped(canvas: Canvas, text: String, x: Float, y: Float, maxWidth: Float, paint: Paint, lineHeight: Float) {
    var line = StringBuilder()
    var cursorY = y
    for (word in text.split(' ')) {
        val candidate = if (line.isEmpty()) word else "$line $word"
        if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
            canvas.drawText(line.toString(), x, cursorY, paint)
            cursorY += lineHeight
            line = StringBuilder(word)
        } else {
            line = StringBuilder(candidate)
        }
    }
    if (line.isNotEmpty()) canvas.drawText(line.toString(), x, cursorY, paint)
}
