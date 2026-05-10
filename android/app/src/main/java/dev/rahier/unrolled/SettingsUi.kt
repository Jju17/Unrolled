package dev.rahier.unrolled

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material 3 bottom sheet hosting the settings list — currently a single
 * toggle (show location). The "view in photos" action used to live here
 * but has been promoted to a single-tap header button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsBottomSheet(
    settings: UserSettings,
    onShowLocationToggled: (Boolean) -> Unit,
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
                headlineContent = { Text(stringResource(R.string.settings_show_location)) },
                trailingContent = {
                    Switch(
                        checked = settings.showLocation,
                        onCheckedChange = onShowLocationToggled
                    )
                },
                modifier = Modifier.clickable {
                    onShowLocationToggled(!settings.showLocation)
                }
            )
        }
    }
}
