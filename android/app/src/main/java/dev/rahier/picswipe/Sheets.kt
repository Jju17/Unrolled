package dev.rahier.picswipe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Tint = Color(0xFF007AFF)

@Composable
fun AlbumPickerSheet(
    albums: List<Album>,
    currentId: String,
    darkTheme: Boolean,
    onPick: (String) -> Unit,
    onClose: () -> Unit
) {
    val surface = if (darkTheme) Color(0xFF2C2C2E).copy(alpha = 0.95f)
                  else Color(0xFFFAFAFC).copy(alpha = 0.96f)
    val textPrim = if (darkTheme) Color.White else Color.Black
    val textSec = (if (darkTheme) Color(0xFFEBEBF5) else Color(0xFF3C3C43)).copy(alpha = 0.6f)
    val separator = (if (darkTheme) Color(0xFF545458) else Color(0xFF3C3C43))
        .copy(alpha = if (darkTheme) 0.5f else 0.14f)

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(onClick = onClose)
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Album list card
            Column(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(surface)
            ) {
                Text(
                    "Choose an album",
                    color = textSec,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(separator))

                albums.forEachIndexed { idx, album ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPick(album.id)
                                onClose()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (darkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(album.icon, color = textPrim, fontSize = 15.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(album.name, color = textPrim, fontSize = 16.sp, letterSpacing = (-0.3).sp)
                            Text("${"%,d".format(album.count)} photos", color = textSec, fontSize = 13.sp)
                        }
                        if (album.id == currentId) {
                            Text("✓", color = Tint, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (idx < albums.size - 1) {
                        Box(Modifier.fillMaxWidth().height(0.5.dp).background(separator))
                    }
                }
            }

            // Cancel
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(surface)
                    .clickable(onClick = onClose)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Cancel", color = Tint, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun AboutSheet(
    darkTheme: Boolean,
    onClose: () -> Unit
) {
    val surface = if (darkTheme) Color(0xFF1C1C1E).copy(alpha = 0.95f)
                  else Color(0xFFFAFAFC).copy(alpha = 0.96f)
    val textPrim = if (darkTheme) Color.White else Color.Black
    val textSec = (if (darkTheme) Color(0xFFEBEBF5) else Color(0xFF3C3C43)).copy(alpha = 0.6f)
    val separator = (if (darkTheme) Color(0xFF545458) else Color(0xFF3C3C43))
        .copy(alpha = if (darkTheme) 0.5f else 0.14f)

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(onClick = onClose)
        )

        Column(
            Modifier
                .align(Alignment.Center)
                .width(280.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(surface)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 28.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFEF3E5B), Color(0xFFF97316))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("♥", color = Color.White, fontSize = 30.sp)
                }
                Spacer(Modifier.height(14.dp))
                Text("PicSwipe", color = textPrim, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
                Spacer(Modifier.height(4.dp))
                Text("Swipe your camera roll clean.", color = textSec, fontSize = 13.sp)
                Spacer(Modifier.height(20.dp))
                AboutRow("Version", "1.0.2", darkTheme, textPrim, textSec)
                Spacer(Modifier.height(6.dp))
                AboutRow("Build", "2026.04.128", darkTheme, textPrim, textSec)
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(separator))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClose)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Close", color = Tint, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String, darkTheme: Boolean, textPrim: Color, textSec: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (darkTheme) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = textSec, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = textPrim, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

