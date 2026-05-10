package dev.rahier.unrolled

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Accent palette — coral from the original Unrolled design exploration.
internal val KeepColor = Color(0xFF30D28A)
internal val DropColor = Color(0xFFEF3E5B)
internal val UndoColor = Color(0xFFF5B945)

internal const val StackDepth = 4
internal val DragThresholdDp = 110.dp

/**
 * Set of read-photos permissions appropriate for the current API level.
 *
 * - **API 34+ (Upside Down Cake)** : request both `READ_MEDIA_IMAGES`
 *   AND `READ_MEDIA_VISUAL_USER_SELECTED`. The system then shows its
 *   three-way picker (full / selected / deny); any grant unlocks the
 *   library. Declaring partial access is required by Google Play's
 *   Photo & Video Permissions policy when keeping full-library access.
 * - **API 33 (Tiramisu)** : only `READ_MEDIA_IMAGES` exists.
 * - **API ≤32** : legacy `READ_EXTERNAL_STORAGE`.
 */
internal val ReadPermissions: Array<String> = buildList {
    when {
        Build.VERSION.SDK_INT >= 34 -> {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }
        Build.VERSION.SDK_INT >= 33 -> add(Manifest.permission.READ_MEDIA_IMAGES)
        else -> add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    // ACCESS_MEDIA_LOCATION (API 29+) unlocks the EXIF GPS metadata
    // we display under each photo. Bundling it with the read perms
    // means the user sees a single combined system prompt — the
    // toggle in Settings still gates whether the data is ever shown.
    if (Build.VERSION.SDK_INT >= 29) {
        add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }
}.toTypedArray()

/** Canonical permission used for "should show rationale" checks — the
 *  most-requested scope, not the fallback. Any grant from `ReadPermissions`
 *  is enough for the repo query. */
internal val ReadPermission: String = ReadPermissions.first()

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
