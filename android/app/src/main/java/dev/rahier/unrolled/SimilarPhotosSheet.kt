package dev.rahier.unrolled

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

@Composable
internal fun SimilarPhotosSheet(
    group: List<PhotoAsset>,
    darkTheme: Boolean,
    onResolve: (keptIds: List<Long>, deletedIds: List<Long>) -> Unit,
    onClose: () -> Unit
) {
    val bgPage = if (darkTheme) Color(0xFF0E0E12) else Color(0xFFF6F5F1)
    val textPrim = if (darkTheme) Color.White else Color(0xFF1A1A1A)
    val textSec = (if (darkTheme) Color.White else Color.Black).copy(alpha = 0.55f)
    val border = (if (darkTheme) Color.White else Color.Black).copy(alpha = if (darkTheme) 0.08f else 0.06f)

    var keptIds by remember(group) { mutableStateOf(setOfNotNull(group.firstOrNull()?.id)) }
    val deletedCount = group.size - keptIds.size
    val canConfirm = deletedCount > 0

    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(bgPage)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.similar_title), color = textPrim, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.similar_hint), color = textSec, fontSize = 13.sp)
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.similar_cancel), tint = textPrim, modifier = Modifier.size(18.dp))
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(group, key = { it.id }) { photo ->
                    val kept = photo.id in keptIds
                    val label = stringResource(if (kept) R.string.similar_keep_a11y else R.string.similar_delete_a11y)
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .border(3.dp, if (kept) KeepColor else border, RoundedCornerShape(14.dp))
                            .toggleable(
                                value = kept,
                                role = Role.Checkbox,
                                onValueChange = { keptIds = if (it) keptIds + photo.id else keptIds - photo.id }
                            )
                    ) {
                        AsyncImage(
                            model = photo.uri,
                            contentDescription = label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().background(Color(0xFF222222))
                        )
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (kept) KeepColor else Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (kept) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (canConfirm) DropColor else DropColor.copy(alpha = 0.4f))
                        .clickable(enabled = canConfirm, role = Role.Button) {
                            onResolve(keptIds.toList(), group.map { it.id }.filter { it !in keptIds })
                        }
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        stringResource(R.string.similar_confirm, keptIds.size, deletedCount),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.size(6.dp))
                Text(
                    stringResource(R.string.similar_cancel),
                    color = textSec,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onClose)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}
