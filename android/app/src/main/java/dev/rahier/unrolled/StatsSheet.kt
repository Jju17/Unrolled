package dev.rahier.unrolled

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun StatsSheet(
    state: SwipeState,
    darkTheme: Boolean,
    onIntent: (SwipeIntent) -> Unit
) {
    val bgPage = if (darkTheme) Color(0xFF0E0E12) else Color(0xFFF6F5F1)
    val textPrim = if (darkTheme) Color.White else Color(0xFF1A1A1A)
    val textSec = (if (darkTheme) Color.White else Color.Black).copy(alpha = 0.55f)
    val tileBg = if (darkTheme) Color.White.copy(alpha = 0.06f) else Color.White
    val border = (if (darkTheme) Color.White else Color.Black).copy(alpha = if (darkTheme) 0.08f else 0.06f)
    val stats = state.stats
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.share_card_stats_title)
    val shareSubtitle = pluralStringResource(R.plurals.share_card_stats_subtitle, stats.photosReviewed, stats.photosReviewed, stats.photosDeleted)
    val shareHeadline = formatBytes(stats.bytesFreed)
    var resetConfirmOpen by remember { mutableStateOf(false) }

    BackHandler { onIntent(SwipeIntent.StatsDismissed) }

    Box(Modifier.fillMaxSize().background(bgPage)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.stats_title),
                    color = textPrim,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                val shareLabel = stringResource(R.string.stats_share_a11y)
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) {
                            shareCard(
                                context,
                                ShareCardContent(title = shareTitle, headline = shareHeadline, subtitle = shareSubtitle)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Share, contentDescription = shareLabel, tint = textPrim, modifier = Modifier.size(20.dp))
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { onIntent(SwipeIntent.StatsDismissed) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.deleted_close_a11y),
                        tint = textPrim,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(tileBg)
                            .border(1.dp, border, RoundedCornerShape(18.dp))
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(formatBytes(stats.bytesFreed), color = textPrim, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.stats_freed_label), color = textSec, fontSize = 13.sp)
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(stats.photosReviewed.toString(), stringResource(R.string.stats_reviewed), tileBg, textPrim, textSec, Modifier.weight(1f))
                        StatTile(stats.photosDeleted.toString(), stringResource(R.string.stats_deleted), tileBg, textPrim, textSec, Modifier.weight(1f))
                    }
                }
                if (state.settings.gamificationEnabled) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(tileBg)
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = UndoColor, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    pluralStringResource(R.plurals.stats_streak_days, stats.currentStreak, stats.currentStreak),
                                    color = textPrim,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    pluralStringResource(R.plurals.stats_longest_streak, stats.longestStreak, stats.longestStreak),
                                    color = textSec,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(tileBg)
                                .padding(14.dp)
                        ) {
                            Text(
                                pluralStringResource(R.plurals.stats_today_progress, state.settings.dailyGoal, stats.todayCount.coerceAtMost(state.settings.dailyGoal), state.settings.dailyGoal),
                                color = textPrim,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                            ProgressBar(progress = (stats.todayCount.toFloat() / state.settings.dailyGoal).coerceIn(0f, 1f), track = border, fill = KeepColor)
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.stats_milestones),
                            color = textSec,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    items(Milestone.entries, key = { it.id }) { milestone ->
                        val reached = milestone.id in stats.reachedMilestones
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (reached) KeepColor.copy(alpha = 0.16f) else tileBg)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                milestoneLabel(milestone),
                                color = if (reached) textPrim else textSec,
                                fontSize = 14.sp,
                                fontWeight = if (reached) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (reached) {
                                Icon(Icons.Default.LocalFireDepartment, contentDescription = stringResource(R.string.stats_milestone_reached_a11y), tint = KeepColor, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                item {
                    Text(
                        stringResource(R.string.stats_reset),
                        color = DropColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { resetConfirmOpen = true }
                            .minimumInteractiveComponentSize()
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                    Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                }
            }
        }

        if (resetConfirmOpen) {
            AlertDialog(
                onDismissRequest = { resetConfirmOpen = false },
                title = { Text(stringResource(R.string.stats_reset_title)) },
                text = { Text(stringResource(R.string.stats_reset_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        resetConfirmOpen = false
                        onIntent(SwipeIntent.StatsResetTapped)
                    }) {
                        Text(stringResource(R.string.stats_reset_confirm), color = DropColor, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { resetConfirmOpen = false }) {
                        Text(stringResource(R.string.deleted_reset_cancel))
                    }
                }
            )
        }
    }
}

@Composable
internal fun ProgressBar(progress: Float, track: Color, fill: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(track)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(CircleShape)
                .background(fill)
        )
    }
}

@Composable
internal fun milestoneLabel(milestone: Milestone): String = when (milestone.kind) {
    Milestone.Kind.Reviewed -> pluralStringResource(R.plurals.milestone_reviewed, milestone.threshold.toInt(), milestone.threshold.toInt())
    Milestone.Kind.Freed -> stringResource(R.string.milestone_freed, formatBytes(milestone.threshold))
    Milestone.Kind.Streak -> stringResource(R.string.milestone_streak, milestone.threshold.toInt())
}
