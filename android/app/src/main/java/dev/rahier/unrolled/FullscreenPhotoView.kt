package dev.rahier.unrolled

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import java.text.DateFormat

/**
 * Full-screen photo pager. Opens on the current top photo, lets the
 * user swipe horizontally between adjacent undecided photos, and
 * exposes keep/delete buttons that route the action back through the
 * reducer (skipping intermediate pages on commit).
 *
 * Per-page affordances:
 * - Pinch to zoom (4× max), pan once zoomed (delegated to [zoomableDismissable])
 * - Date + place name overlay, place name resolved lazily via the
 *   shared [LocalLocationDescriber]
 *
 * Pager pages are restricted to `photos[startIndex..]` so backward
 * scrolling never lands on a photo already routed through `history`.
 */
@Composable
internal fun FullscreenPhotoView(
    photos: List<PhotoAsset>,
    startIndex: Int,
    settings: UserSettings,
    onMarkAtRelativeIndex: (localIdx: Int, kept: Boolean) -> Unit,
    onClose: () -> Unit
) {
    val visiblePhotos = remember(photos, startIndex) {
        if (startIndex in photos.indices) photos.subList(startIndex, photos.size)
        else emptyList()
    }
    if (visiblePhotos.isEmpty()) {
        // Nothing left to show — close immediately.
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val pagerState = rememberPagerState(initialPage = 0) { visiblePhotos.size }
    val currentPhoto = visiblePhotos.getOrNull(pagerState.currentPage)

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val photo = visiblePhotos[page]
                AsyncImage(
                    model = photo.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .zoomableDismissable(
                            onDismiss = onClose,
                            onDismissProgress = {},
                            maxScale = 4f
                        )
                )
            }

            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (visiblePhotos.size > 1) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${visiblePhotos.size}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Box(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.fullscreen_close_a11y),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                currentPhoto?.let {
                    MetadataStrip(photo = it, settings = settings)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    ActionButton(
                        icon = Icons.Default.Close,
                        tint = DropColor,
                        contentDescription = stringResource(R.string.action_delete_a11y),
                        onClick = {
                            onMarkAtRelativeIndex(pagerState.currentPage, false)
                        }
                    )
                    ActionButton(
                        icon = Icons.Default.Favorite,
                        tint = KeepColor,
                        contentDescription = stringResource(R.string.action_keep_a11y),
                        onClick = {
                            onMarkAtRelativeIndex(pagerState.currentPage, true)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataStrip(photo: PhotoAsset, settings: UserSettings) {
    val describer = LocalLocationDescriber.current
    var placeName by remember(photo.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(photo.id, settings.showLocation, describer) {
        placeName = null
        if (describer != null && settings.showLocation) {
            placeName = describer.placeName(photo.id)
        }
    }
    val date = photo.creationDate?.let {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(it)
    }
    if (date == null && (placeName == null || !settings.showLocation)) return
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        date?.let {
            Text(
                it,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (settings.showLocation) {
            placeName?.let {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = it,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}
