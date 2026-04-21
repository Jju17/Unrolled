package dev.rahier.picswipe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun Header(
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
                    state.currentAlbum?.name ?: "Photos",
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
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TopbarIconButton(
                icon = Icons.Default.Shuffle,
                darkTheme = darkTheme,
                tint = textPrim,
                active = state.shuffleEnabled,
                onClick = { onIntent(SwipeIntent.ShuffleButtonTapped) }
            )
            OverflowMenu(darkTheme = darkTheme, textPrim = textPrim, onIntent = onIntent)
        }
    }
}

/**
 * Small circular topbar button. Renders with the accent fill when
 * [active] is true so toggled-on state (e.g. shuffle enabled) is
 * visually distinct.
 */
@Composable
internal fun TopbarIconButton(
    icon: ImageVector,
    darkTheme: Boolean,
    tint: Color,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val background = when {
        active -> DropColor
        darkTheme -> Color.White.copy(alpha = 0.08f)
        else -> Color.Black.copy(alpha = 0.05f)
    }
    val iconTint = if (active) Color.White else tint

    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * "⋯" trigger + Material 3 [DropdownMenu]. Open state is local to this
 * composable — the VM doesn't need to track it because it's pure UI and
 * the menu closes on config change which is fine.
 */
@Composable
internal fun OverflowMenu(
    darkTheme: Boolean,
    textPrim: Color,
    onIntent: (SwipeIntent) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (darkTheme) Color.White.copy(alpha = 0.08f)
                    else Color.Black.copy(alpha = 0.05f)
                )
                .clickable { expanded = true },
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) {
                    Box(Modifier.size(4.dp).clip(CircleShape).background(textPrim))
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("About PicSwipe") },
                leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                onClick = {
                    expanded = false
                    onIntent(SwipeIntent.AboutMenuItemTapped)
                }
            )
        }
    }
}
