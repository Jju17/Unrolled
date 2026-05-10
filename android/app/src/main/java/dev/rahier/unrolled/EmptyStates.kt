package dev.rahier.unrolled

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PermissionGate(
    status: PermissionStatus,
    textPrim: Color,
    textSec: Color,
    onRequest: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📷", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.permission_gate_title), color = textPrim, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        // Copy written for Google's Photo & Video Permissions policy
        // review: explicit about (a) what we need, (b) why, (c) the
        // purely-local nature, (d) the alternative (partial access).
        Text(
            stringResource(R.string.permission_gate_body),
            color = textSec,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 24.dp),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        val label = stringResource(when (status) {
            PermissionStatus.Denied, PermissionStatus.PermanentlyDenied -> R.string.permission_gate_open_settings
            else -> R.string.permission_gate_allow
        })
        PrimaryPillButton(text = label, onClick = onRequest)
    }
}

@Composable
internal fun EmptyState(
    textPrim: Color,
    textSec: Color,
    hasPartialAccess: Boolean = false,
    onChangeSelection: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (hasPartialAccess) {
            // User is on Android 14+ and granted only READ_MEDIA_VISUAL_USER_SELECTED
            // — either they picked zero photos, or they picked a few that
            // all got swiped. Offer a re-pick affordance so the session
            // isn't a dead end.
            Text("📷", fontSize = 42.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.empty_partial_title),
                color = textPrim,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.empty_partial_body),
                color = textSec,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            PrimaryPillButton(
                text = stringResource(R.string.empty_partial_change),
                onClick = onChangeSelection
            )
        } else {
            Text(stringResource(R.string.empty_state_title), color = textPrim, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.empty_state_body), color = textSec, fontSize = 14.sp)
        }
    }
}

@Composable
internal fun LoadErrorState(
    message: String,
    textPrim: Color,
    textSec: Color,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("⚠️", fontSize = 40.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.load_error_title),
            color = textPrim,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            color = textSec,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        PrimaryPillButton(text = stringResource(R.string.load_error_retry), onClick = onRetry)
    }
}

@Composable
internal fun DoneView(
    state: SwipeState,
    textPrim: Color, textSec: Color, border: Color,
    onIntent: (SwipeIntent) -> Unit
) {
    // Hide "View all photos" if we're already on the synthetic all-photos
    // album so the CTA never fires a no-op album switch.
    val isOnAllPhotos = state.currentAlbum?.id == state.albums.firstOrNull()?.id

    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .border(2.dp, border, RoundedCornerShape(24.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = KeepColor,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.done_title),
                color = textPrim,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                pluralStringResource(R.plurals.done_subtitle_kept, state.kept, state.kept) +
                    stringResource(R.string.done_subtitle_separator) +
                    pluralStringResource(R.plurals.done_subtitle_deleted, state.deleted, state.deleted),
                color = textSec,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(20.dp))

            if (!isOnAllPhotos) {
                PrimaryPillButton(
                    text = stringResource(R.string.done_view_all),
                    onClick = { onIntent(SwipeIntent.ViewAllPhotosTapped) }
                )
                Spacer(Modifier.height(10.dp))
            }
            SecondaryPillButton(
                text = stringResource(R.string.done_change_album),
                textColor = textPrim,
                border = border,
                onClick = { onIntent(SwipeIntent.AlbumTitleTapped) }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.done_start_over),
                color = textSec,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable { onIntent(SwipeIntent.ResetButtonTapped) }
                    .padding(vertical = 6.dp, horizontal = 10.dp)
            )
        }
    }
}

@Composable
internal fun PrimaryPillButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(DropColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun SecondaryPillButton(
    text: String,
    textColor: Color,
    border: Color,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .clip(CircleShape)
            .border(1.dp, border, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        Text(text, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
