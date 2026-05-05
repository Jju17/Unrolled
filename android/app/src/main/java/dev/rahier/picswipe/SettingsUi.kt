package dev.rahier.picswipe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Subtle circular gear button — placed by the caller's Box alignment. */
@Composable
internal fun SettingsGearButton(
    darkTheme: Boolean,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                if (darkTheme) Color.White.copy(alpha = 0.06f)
                else Color.Black.copy(alpha = 0.04f)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = stringResource(R.string.settings_title),
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Material 3 bottom sheet hosting the settings list. Single row for now:
 * "Voir la photo dans la photothèque".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsBottomSheet(
    hasTopPhoto: Boolean,
    darkTheme: Boolean,
    onViewInPhotos: () -> Unit,
    onClose: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState
    ) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.settings_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_view_in_photos)) },
                leadingContent = {
                    Icon(Icons.Outlined.Photo, contentDescription = null)
                },
                modifier = Modifier.clickable(enabled = hasTopPhoto) { onViewInPhotos() },
                colors = ListItemDefaults.colors(
                    headlineColor = if (hasTopPhoto) {
                        if (darkTheme) Color.White else Color.Black
                    } else {
                        (if (darkTheme) Color.White else Color.Black).copy(alpha = 0.4f)
                    }
                )
            )
        }
    }
}
