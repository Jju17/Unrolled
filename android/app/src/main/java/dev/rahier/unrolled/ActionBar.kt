package dev.rahier.unrolled

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ActionBar(
    bgPage: Color,
    surface: Color,
    border: Color,
    textSec: Color,
    historyEmpty: Boolean,
    showViewInPhotos: Boolean,
    onIntent: (SwipeIntent) -> Unit,
    onViewInPhotos: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(bgPage.copy(alpha = 0f), bgPage.copy(alpha = 0.9f), bgPage)
                )
            )
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 36.dp)
    ) {
        // Centered keep / undo / delete trio. Stays geometrically
        // centered regardless of whether the side button is shown,
        // because that one is overlaid as a Box-aligned sibling.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(
                size = 58.dp, surface = surface, border = border, glow = DropColor,
                description = stringResource(R.string.action_delete_a11y),
                onClick = { onIntent(SwipeIntent.DeleteButtonTapped) }
            ) { Text("✕", color = DropColor, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            ActionButton(
                size = 46.dp, surface = surface, border = border, glow = UndoColor,
                enabled = !historyEmpty,
                description = stringResource(R.string.action_undo_a11y),
                onClick = { onIntent(SwipeIntent.UndoButtonTapped) }
            ) { Text("↺", color = UndoColor, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            ActionButton(
                size = 58.dp, surface = surface, border = border, glow = KeepColor,
                description = stringResource(R.string.action_keep_a11y),
                onClick = { onIntent(SwipeIntent.KeepButtonTapped) }
            ) { Text("♥", color = KeepColor, fontSize = 26.sp) }
        }

        // Side affordance: open the current photo in the system gallery.
        // Aligned to the leading edge, vertically centered relative to
        // the action trio so it reads as the "secondary" action of the row.
        if (showViewInPhotos) {
            val viewInPhotosLabel = stringResource(R.string.header_view_in_photos_a11y)
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(surface)
                    .border(1.dp, border, CircleShape)
                    .clickable(onClick = onViewInPhotos)
                    .semantics {
                        role = Role.Button
                        contentDescription = viewInPhotosLabel
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = textSec,
                    modifier = Modifier.size(18.dp)
                )
            }
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
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        Modifier
            .size(size)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
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
