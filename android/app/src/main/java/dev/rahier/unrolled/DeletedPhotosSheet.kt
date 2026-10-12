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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

/**
 * Review sheet for the trash-staging list. Each row has a "Garder"
 * button to pull that photo back out; the big red footer hands the
 * remaining list to `MediaStore.createTrashRequest` (via the launcher
 * wired up in [SwipeScreen]). Presented as a full-screen overlay above
 * the card stack.
 */
@Composable
internal fun DeletedPhotosSheet(
    state: SwipeState,
    darkTheme: Boolean,
    onIntent: (SwipeIntent) -> Unit
) {
    val bgPage = if (darkTheme) Color(0xFF0E0E12) else Color(0xFFF6F5F1)
    val textPrim = if (darkTheme) Color.White else Color(0xFF1A1A1A)
    val textSec = (if (darkTheme) Color.White else Color.Black).copy(alpha = 0.55f)
    val rowBg = if (darkTheme) Color.White.copy(alpha = 0.05f) else Color.White
    val border = (if (darkTheme) Color.White else Color.Black)
        .copy(alpha = if (darkTheme) 0.08f else 0.06f)

    val rows = state.trashPhotos

    var resetConfirmOpen by remember { mutableStateOf(false) }
    val hasAnyProgress = state.kept > 0 || state.deleted > 0

    BackHandler {
        onIntent(SwipeIntent.DeletedSheetDismissed)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(bgPage)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.deleted_title),
                    color = textPrim,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (hasAnyProgress) {
                    Text(
                        text = stringResource(R.string.deleted_reset),
                        color = textSec,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button) { resetConfirmOpen = true }
                            .minimumInteractiveComponentSize()
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { onIntent(SwipeIntent.DeletedSheetDismissed) },
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
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.deleted_close_a11y),
                            tint = textPrim,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            if (resetConfirmOpen) {
                AlertDialog(
                    onDismissRequest = { resetConfirmOpen = false },
                    title = { Text(stringResource(R.string.deleted_reset_title)) },
                    text = { Text(stringResource(R.string.deleted_reset_body)) },
                    confirmButton = {
                        TextButton(onClick = {
                            resetConfirmOpen = false
                            onIntent(SwipeIntent.ResetProgressTapped)
                        }) {
                            Text(
                                text = stringResource(R.string.deleted_reset_confirm),
                                color = DropColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { resetConfirmOpen = false }) {
                            Text(stringResource(R.string.deleted_reset_cancel))
                        }
                    }
                )
            }

            state.deleteError?.let { err ->
                Text(
                    err,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DropColor)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            if (rows.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = textSec,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.deleted_empty_title),
                        color = textPrim,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.deleted_empty_body),
                        color = textSec,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout
                        .PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(rows, key = { it.id }) { photo ->
                        DeletedRow(
                            photo = photo,
                            rowBg = rowBg,
                            border = border,
                            textPrim = textPrim,
                            textSec = textSec,
                            onRestore = {
                                onIntent(SwipeIntent.DeletedPhotoRestoreTapped(photo.id))
                            }
                        )
                    }
                }

                TrashFooter(
                    count = rows.size,
                    inFlight = state.trashInFlight,
                    bgPage = bgPage,
                    border = border,
                    onClick = { onIntent(SwipeIntent.EmptyTrashButtonTapped) }
                )
            }
        }
    }

}

@Composable
private fun DeletedRow(
    photo: PhotoAsset,
    rowBg: Color,
    border: Color,
    textPrim: Color,
    textSec: Color,
    onRestore: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(rowBg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF222222))
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                photo.creationDate?.let(dateFormatter::format)
                    ?: stringResource(R.string.deleted_row_fallback_title),
                color = textPrim,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (photo.sizeBytes > 0) {
                    stringResource(R.string.deleted_row_status_size, formatBytes(photo.sizeBytes))
                } else {
                    stringResource(R.string.deleted_row_status)
                },
                color = textSec,
                fontSize = 12.sp
            )
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(KeepColor)
                .clickable(role = Role.Button, onClick = onRestore)
                .minimumInteractiveComponentSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(11.dp)
            )
            Text(
                stringResource(R.string.deleted_row_keep),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TrashFooter(
    count: Int,
    inFlight: Boolean,
    bgPage: Color,
    border: Color,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(bgPage)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(border)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(DropColor)
                .clickable(enabled = !inFlight && count > 0, role = Role.Button, onClick = onClick)
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (inFlight) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.deleted_footer_button, count),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private val dateFormatter: DateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
