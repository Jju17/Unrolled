package dev.rahier.unrolled

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
                stringResource(R.string.header_album_label),
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
                // album.name comes from MediaStore (already locale-agnostic
                // bucket name); fallback when none loaded yet.
                Text(
                    state.currentAlbum?.name ?: stringResource(R.string.header_album_fallback),
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
            StatsPill(
                state = state,
                darkTheme = darkTheme,
                textPrim = textPrim,
                textSec = textSec,
                onClick = { onIntent(SwipeIntent.DeletedSheetButtonTapped) }
            )
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
 * Tally pill: ♡kept / ✗deleted. Hidden until the user has acted on at
 * least one photo (keeps a fresh album uncluttered). Disabled when
 * there's nothing in the trash staging list — tapping it with only
 * kept photos would open an empty sheet.
 */
@Composable
private fun StatsPill(
    state: SwipeState,
    darkTheme: Boolean,
    textPrim: Color,
    textSec: Color,
    onClick: () -> Unit
) {
    if (state.kept == 0 && state.deleted == 0) return
    val enabled = state.deleted > 0
    val pillBase = stringResource(R.string.header_stats_pill_a11y, state.kept, state.deleted)
    val pillHint = stringResource(
        if (enabled) R.string.header_stats_pill_a11y_open_hint
        else R.string.header_stats_pill_a11y_empty_hint
    )
    val pillDescription = pillBase + pillHint
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (darkTheme) Color.White.copy(alpha = 0.08f)
                else Color.Black.copy(alpha = 0.05f)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = pillDescription
            }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = KeepColor,
            modifier = Modifier.size(10.dp)
        )
        Text(
            state.kept.toString(),
            color = textPrim,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Box(
            Modifier
                .size(1.dp, 12.dp)
                .background(textSec.copy(alpha = 0.4f))
        )
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            tint = DropColor,
            modifier = Modifier.size(10.dp)
        )
        Text(
            state.deleted.toString(),
            color = textPrim.copy(alpha = if (enabled) 1f else 0.6f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
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
    contentDescription: String? = null,
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
            contentDescription = contentDescription,
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
                text = { Text(stringResource(R.string.settings_title)) },
                leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                onClick = {
                    expanded = false
                    onIntent(SwipeIntent.SettingsButtonTapped)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.header_about_menu_item)) },
                leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                onClick = {
                    expanded = false
                    onIntent(SwipeIntent.AboutMenuItemTapped)
                }
            )
        }
    }
}
