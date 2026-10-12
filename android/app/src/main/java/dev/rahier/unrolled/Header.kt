package dev.rahier.unrolled

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
internal fun Header(
    state: SwipeState,
    darkTheme: Boolean,
    textPrim: Color,
    textSec: Color,
    onIntent: (SwipeIntent) -> Unit
) {
    Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.header_pile_label),
                    color = textSec,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp
                )
                val openPickerLabel = stringResource(R.string.header_open_picker_a11y)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClickLabel = openPickerLabel
                        ) { onIntent(SwipeIntent.PileTitleTapped) }
                        .minimumInteractiveComponentSize()
                ) {
                    Text(
                        pileTitle(state.selectedPile, state.currentPile?.name),
                        color = textPrim,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = textPrim, modifier = Modifier.size(18.dp))
                }
                if (state.libraryLoaded) {
                    val remaining = pluralStringResource(R.plurals.header_remaining, state.remainingInDeck, state.remainingInDeck)
                    val freed = if (state.stats.bytesFreed > 0) {
                        stringResource(R.string.header_freed, formatBytes(state.stats.bytesFreed))
                    } else null
                    Text(
                        listOfNotNull(remaining, freed).joinToString(stringResource(R.string.header_subtitle_separator)),
                        color = textSec,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.settings.gamificationEnabled) {
                    GoalRing(
                        count = state.stats.todayCount,
                        goal = state.settings.dailyGoal,
                        darkTheme = darkTheme,
                        onClick = { onIntent(SwipeIntent.StatsButtonTapped) }
                    )
                }
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
                    contentDescription = stringResource(R.string.header_shuffle_a11y),
                    onClick = { onIntent(SwipeIntent.ShuffleButtonTapped) }
                )
                OverflowMenu(darkTheme = darkTheme, textPrim = textPrim, onIntent = onIntent)
            }
        }
        GoalToast(state = state, darkTheme = darkTheme, textPrim = textPrim, onIntent = onIntent)
    }
}

@Composable
private fun GoalRing(count: Int, goal: Int, darkTheme: Boolean, onClick: () -> Unit) {
    val fraction = (count.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, tween(500), label = "goalRing")
    val track = if (darkTheme) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    val label = pluralStringResource(R.plurals.header_goal_ring_a11y, goal, count.coerceAtMost(goal), goal)
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(30.dp)) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(KeepColor, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        if (fraction >= 1f) {
            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = KeepColor, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun GoalToast(state: SwipeState, darkTheme: Boolean, textPrim: Color, onIntent: (SwipeIntent) -> Unit) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state.goalCelebration) {
        if (state.goalCelebration) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            delay(2_500)
            onIntent(SwipeIntent.GoalCelebrationDismissed)
        }
    }
    AnimatedVisibility(state.goalCelebration, enter = fadeIn(), exit = fadeOut()) {
        val streak = state.stats.currentStreak
        val text = if (streak > 1) {
            stringResource(R.string.goal_reached_with_streak, streak)
        } else {
            stringResource(R.string.goal_reached)
        }
        Row(
            Modifier
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (darkTheme) KeepColor.copy(alpha = 0.2f) else KeepColor.copy(alpha = 0.14f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = KeepColor, modifier = Modifier.size(16.dp))
            Text(text, color = textPrim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Trash tally: kept / deleted. Tapping opens the review sheet. */
@Composable
private fun StatsPill(
    state: SwipeState,
    darkTheme: Boolean,
    textPrim: Color,
    textSec: Color,
    onClick: () -> Unit
) {
    if (state.kept == 0 && state.deleted == 0) return
    val pillDescription = pluralStringResource(R.plurals.header_stats_pill_a11y_kept, state.kept, state.kept) +
        ", " +
        pluralStringResource(R.plurals.header_stats_pill_a11y_deleted, state.deleted, state.deleted) +
        ". " +
        stringResource(R.string.header_stats_pill_a11y_open_hint)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (darkTheme) Color.White.copy(alpha = 0.08f)
                else Color.Black.copy(alpha = 0.05f)
            )
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = pillDescription
            }
            .minimumInteractiveComponentSize()
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
            color = textPrim,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

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
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(background),
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
}

@Composable
internal fun OverflowMenu(
    darkTheme: Boolean,
    textPrim: Color,
    onIntent: (SwipeIntent) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val moreLabel = stringResource(R.string.header_more_a11y)
    Box {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable { expanded = true }
                .semantics {
                    role = Role.Button
                    contentDescription = moreLabel
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (darkTheme) Color.White.copy(alpha = 0.08f)
                        else Color.Black.copy(alpha = 0.05f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) {
                        Box(Modifier.size(4.dp).clip(CircleShape).background(textPrim))
                    }
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.stats_title)) },
                leadingIcon = { Icon(Icons.Outlined.Insights, contentDescription = null) },
                onClick = {
                    expanded = false
                    onIntent(SwipeIntent.StatsButtonTapped)
                }
            )
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
