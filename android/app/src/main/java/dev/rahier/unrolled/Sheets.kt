package dev.rahier.unrolled

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Tint = Color(0xFF007AFF)

@Composable
fun PilePickerSheet(
    piles: List<Pile>,
    currentId: String,
    darkTheme: Boolean,
    onPick: (PileSelector) -> Unit,
    onClose: () -> Unit
) {
    val surface = if (darkTheme) Color(0xFF2C2C2E).copy(alpha = 0.95f)
                  else Color(0xFFFAFAFC).copy(alpha = 0.96f)
    val textPrim = if (darkTheme) Color.White else Color.Black
    val textSec = (if (darkTheme) Color(0xFFEBEBF5) else Color(0xFF3C3C43)).copy(alpha = 0.6f)
    val separator = (if (darkTheme) Color(0xFF545458) else Color(0xFF3C3C43))
        .copy(alpha = if (darkTheme) 0.5f else 0.14f)

    val sections = remember(piles) {
        listOf(
            R.string.pile_section_sessions to piles.filter { it.selector.isSession },
            R.string.pile_section_months to piles.filter { it.selector is PileSelector.Month && !it.selector.isSession },
            R.string.pile_section_albums to piles.filter { it.selector is PileSelector.Album }
        ).filter { it.second.isNotEmpty() }
    }

    BackHandler(onBack = onClose)

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
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(14.dp))
                    .background(surface)
            ) {
                Text(
                    stringResource(R.string.pile_picker_title),
                    color = textSec,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(separator))

                LazyColumn(Modifier.weight(1f, fill = false)) {
                    sections.forEach { (titleRes, items) ->
                        item(key = "section:$titleRes") {
                            Text(
                                stringResource(titleRes),
                                color = textSec,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                        itemsIndexed(items, key = { _, pile -> pile.id }) { idx, pile ->
                            PileRow(
                                pile = pile,
                                selected = pile.id == currentId,
                                darkTheme = darkTheme,
                                textPrim = textPrim,
                                textSec = textSec,
                                onClick = {
                                    onPick(pile.selector)
                                    onClose()
                                }
                            )
                            if (idx < items.size - 1) {
                                Box(Modifier.fillMaxWidth().height(0.5.dp).background(separator))
                            }
                        }
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(surface)
                    .clickable(role = Role.Button, onClick = onClose)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.pile_picker_cancel), color = Tint, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private val PileSelector.isSession: Boolean
    get() = this !is PileSelector.Album && (this !is PileSelector.Month || this == currentMonthSelector())

private fun currentMonthSelector(): PileSelector.Month {
    val now = java.time.YearMonth.now()
    return PileSelector.Month(now.year, now.monthValue)
}

@Composable
private fun PileRow(
    pile: Pile,
    selected: Boolean,
    darkTheme: Boolean,
    textPrim: Color,
    textSec: Color,
    onClick: () -> Unit
) {
    val title = pileTitle(pile.selector, pile.name)
    val track = if (darkTheme) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f)
    val summary = if (pile.remainingCount == 0) {
        stringResource(R.string.pile_done)
    } else {
        stringResource(R.string.pile_count_summary, "%,d".format(pile.photoCount), "%,d".format(pile.remainingCount))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$title, $summary" }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (darkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            if (pile.keyUri != null) {
                AsyncImage(
                    model = pile.keyUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = textPrim, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = textPrim, fontSize = 16.sp, letterSpacing = (-0.3).sp)
            Text(summary, color = textSec, fontSize = 13.sp)
            ProgressBar(progress = pile.progress.toFloat(), track = track, fill = KeepColor)
        }
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.pile_selected_a11y), tint = Tint, modifier = Modifier.size(18.dp))
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
    val context = LocalContext.current
    val openLink: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }

    BackHandler(onBack = onClose)

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
                Image(
                    painter = painterResource(R.mipmap.ic_launcher),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Spacer(Modifier.height(14.dp))
                Text("Unrolled", color = textPrim, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.about_tagline), color = textSec, fontSize = 13.sp)
                Spacer(Modifier.height(20.dp))
                AboutRow(stringResource(R.string.about_version), BuildConfig.VERSION_NAME, darkTheme, textPrim, textSec)
                Spacer(Modifier.height(6.dp))
                AboutRow(stringResource(R.string.about_build), BuildConfig.VERSION_CODE.toString(), darkTheme, textPrim, textSec)
                Spacer(Modifier.height(6.dp))
                AboutLinkRow(stringResource(R.string.about_privacy_policy), darkTheme, textPrim, textSec) {
                    openLink(PRIVACY_POLICY_URL)
                }
                Spacer(Modifier.height(6.dp))
                AboutLinkRow(stringResource(R.string.about_support), darkTheme, textPrim, textSec) {
                    openLink(SUPPORT_URL)
                }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(separator))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onClose)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.about_close), color = Tint, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private const val PRIVACY_POLICY_URL = "https://unrolled-e4775.web.app/privacy"
private const val SUPPORT_URL = "https://unrolled-e4775.web.app/support"

@Composable
private fun AboutLinkRow(
    label: String,
    darkTheme: Boolean,
    textPrim: Color,
    textSec: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (darkTheme) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f))
            .clickable(role = Role.Button, onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = textPrim, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = textSec,
            modifier = Modifier.size(16.dp)
        )
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

