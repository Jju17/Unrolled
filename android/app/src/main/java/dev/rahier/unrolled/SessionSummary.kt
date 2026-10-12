package dev.rahier.unrolled

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SessionSummary(
    state: SwipeState,
    darkTheme: Boolean,
    textPrim: Color,
    textSec: Color,
    border: Color,
    onIntent: (SwipeIntent) -> Unit
) {
    val reviewed = state.sessionReviewed
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(reviewed > 0) {
        if (reviewed > 0) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .border(2.dp, border, RoundedCornerShape(24.dp))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = KeepColor,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(if (reviewed > 0) R.string.done_title else R.string.done_nothing_left_title),
                color = textPrim,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))

            if (reviewed > 0) {
                val tileBg = if (darkTheme) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(reviewed.toString(), stringResource(R.string.summary_reviewed), tileBg, textPrim, textSec, Modifier.weight(1f))
                    StatTile(state.sessionKeptIds.size.toString(), stringResource(R.string.summary_kept), tileBg, textPrim, textSec, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(state.sessionDeletedIds.size.toString(), stringResource(R.string.summary_deleted), tileBg, textPrim, textSec, Modifier.weight(1f))
                    StatTile(formatBytes(state.sessionBytesToFree), stringResource(R.string.summary_to_free), tileBg, textPrim, textSec, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    pluralStringResource(
                        R.plurals.summary_pace,
                        state.sessionPhotosPerMinute.toInt(),
                        formatSessionDuration(state.sessionDurationMs),
                        state.sessionPhotosPerMinute.toInt()
                    ),
                    color = textSec,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    stringResource(R.string.done_nothing_left_body),
                    color = textSec,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(20.dp))

            PrimaryPillButton(
                text = stringResource(R.string.done_change_pile),
                onClick = { onIntent(SwipeIntent.PileTitleTapped) }
            )
            if (state.selectedPile != PileSelector.All) {
                Spacer(Modifier.height(10.dp))
                SecondaryPillButton(
                    text = stringResource(R.string.done_view_all),
                    textColor = textPrim,
                    border = border,
                    onClick = { onIntent(SwipeIntent.ViewAllPhotosTapped) }
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.done_start_over),
                color = textSec,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onIntent(SwipeIntent.ResetButtonTapped) }
                    .minimumInteractiveComponentSize()
                    .padding(vertical = 6.dp, horizontal = 10.dp)
            )
        }
        if (reviewed > 0) Confetti()
    }
}

@Composable
internal fun StatTile(
    value: String,
    label: String,
    background: Color,
    textPrim: Color,
    textSec: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = textPrim, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(label, color = textSec, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

internal fun formatSessionDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000L
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "%d min %02d s".format(minutes, seconds) else "%d s".format(seconds)
}
