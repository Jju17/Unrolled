package dev.rahier.unrolled

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsBottomSheet(
    settings: UserSettings,
    onIntent: (SwipeIntent) -> Unit,
    onReminderToggled: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            SectionLabel(stringResource(R.string.settings_section_deck))
            SwitchRow(
                label = stringResource(R.string.settings_include_videos),
                checked = settings.includeVideos,
                onChange = { onIntent(SwipeIntent.SettingsIncludeVideosToggled(it)) }
            )
            SwitchRow(
                label = stringResource(R.string.settings_detect_similar),
                supporting = stringResource(R.string.settings_detect_similar_hint),
                checked = settings.detectSimilar,
                onChange = { onIntent(SwipeIntent.SettingsDetectSimilarToggled(it)) }
            )

            SectionLabel(stringResource(R.string.settings_section_goals))
            SwitchRow(
                label = stringResource(R.string.settings_gamification),
                supporting = stringResource(R.string.settings_gamification_hint),
                checked = settings.gamificationEnabled,
                onChange = { onIntent(SwipeIntent.SettingsGamificationToggled(it)) }
            )
            if (settings.gamificationEnabled) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_daily_goal)) },
                    supportingContent = {
                        Row(
                            Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UserSettings.DAILY_GOAL_CHOICES.forEach { choice ->
                                FilterChip(
                                    selected = settings.dailyGoal == choice,
                                    onClick = { onIntent(SwipeIntent.SettingsDailyGoalChanged(choice)) },
                                    label = { Text(choice.toString()) }
                                )
                            }
                        }
                    }
                )
                SwitchRow(
                    label = stringResource(R.string.settings_reminder),
                    supporting = stringResource(R.string.settings_reminder_hint),
                    checked = settings.reminderEnabled,
                    onChange = onReminderToggled
                )
                if (settings.reminderEnabled) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_reminder_hour)) },
                        supportingContent = {
                            Row(
                                Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(8, 12, 19, 21).forEach { hour ->
                                    FilterChip(
                                        selected = settings.reminderHour == hour,
                                        onClick = { onIntent(SwipeIntent.SettingsReminderHourChanged(hour)) },
                                        label = { Text("%02d:00".format(hour)) }
                                    )
                                }
                            }
                        }
                    )
                }
            }

            SectionLabel(stringResource(R.string.settings_section_display))
            SwitchRow(
                label = stringResource(R.string.settings_show_location),
                supporting = stringResource(R.string.settings_show_location_hint),
                checked = settings.showLocation,
                onChange = { onIntent(SwipeIntent.SettingsShowLocationToggled(it)) }
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    supporting: String? = null
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onChange)
        },
        modifier = Modifier.clickable(role = Role.Switch) { onChange(!checked) }
    )
}
