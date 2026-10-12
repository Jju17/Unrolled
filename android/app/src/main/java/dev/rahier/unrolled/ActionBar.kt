package dev.rahier.unrolled

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(
                size = 58.dp, surface = surface, border = border, glow = DropColor,
                icon = Icons.Default.Close, iconSize = 26.dp,
                description = stringResource(R.string.action_delete_a11y),
                onClick = { onIntent(SwipeIntent.DeleteButtonTapped) }
            )
            ActionButton(
                size = 48.dp, surface = surface, border = border, glow = UndoColor,
                icon = Icons.AutoMirrored.Filled.Undo, iconSize = 20.dp,
                enabled = !historyEmpty,
                description = stringResource(R.string.action_undo_a11y),
                onClick = { onIntent(SwipeIntent.UndoButtonTapped) }
            )
            ActionButton(
                size = 58.dp, surface = surface, border = border, glow = KeepColor,
                icon = Icons.Default.Favorite, iconSize = 26.dp,
                description = stringResource(R.string.action_keep_a11y),
                onClick = { onIntent(SwipeIntent.KeepButtonTapped) }
            )
        }

        if (showViewInPhotos) {
            val viewInPhotosLabel = stringResource(R.string.header_view_in_photos_a11y)
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onViewInPhotos)
                    .semantics {
                        role = Role.Button
                        contentDescription = viewInPhotosLabel
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(surface)
                        .border(1.dp, border, CircleShape),
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
}

@Composable
private fun ActionButton(
    size: Dp,
    surface: Color,
    border: Color,
    glow: Color,
    icon: ImageVector,
    iconSize: Dp,
    enabled: Boolean = true,
    description: String,
    onClick: () -> Unit
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
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = glow,
            modifier = Modifier.size(iconSize)
        )
    }
}
