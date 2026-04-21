package dev.rahier.picswipe

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Accent palette — coral from the original PicSwipe design exploration.
internal val KeepColor = Color(0xFF30D28A)
internal val DropColor = Color(0xFFEF3E5B)
internal val UndoColor = Color(0xFFF5B945)

internal const val StackDepth = 4
internal val DragThresholdDp = 110.dp

internal val ReadPermission: String = if (Build.VERSION.SDK_INT >= 33) {
    Manifest.permission.READ_MEDIA_IMAGES
} else {
    Manifest.permission.READ_EXTERNAL_STORAGE
}

/**
 * Unwrap [ContextWrapper]s (the Compose context is typically wrapped)
 * to get at the hosting Activity. Needed for
 * `shouldShowRequestPermissionRationale`, which requires an Activity
 * reference.
 */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
