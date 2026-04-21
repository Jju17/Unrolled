package dev.rahier.picswipe

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Top-level stateful entry — owns the ActivityResultLauncher for the
 * permission prompt, the lifecycle observer that re-checks permission
 * on resume, and the Activity-scoped side-effect for "view in Photos".
 *
 * Delegates actual rendering to [SwipeScreenContent], which is pure
 * `(state, onIntent, …)` so previews and tests can supply hand-crafted
 * states.
 */
@Composable
fun SwipeScreen(viewModel: SwipeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val status = when {
            granted -> PermissionStatus.Granted
            // After a denial, if the system says it won't show the rationale
            // either, the user picked "Don't ask again" / has permanently
            // denied. The "Open Settings" fallback becomes the only path.
            activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, ReadPermission) ->
                    PermissionStatus.PermanentlyDenied
            else -> PermissionStatus.Denied
        }
        viewModel.onIntent(SwipeIntent.PermissionResult(status))
    }

    // Re-check permission every time we resume — covers the "user went
    // to Settings, granted, came back" flow. The initial composition
    // also hits ON_RESUME so no separate onAppear LaunchedEffect needed.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onIntent(SwipeIntent.ScreenAppeared)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SwipeScreenContent(
        state = state,
        onIntent = viewModel::onIntent,
        onRequestPermission = {
            when (state.permissionStatus) {
                PermissionStatus.PermanentlyDenied, PermissionStatus.Denied -> {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                }
                else -> launcher.launch(ReadPermission)
            }
        },
        onViewInPhotos = { photoUri ->
            // Opens the photo in the system gallery (Google Photos, etc.)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(photoUri), "image/*")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK
            }
            runCatching { context.startActivity(intent) }
            viewModel.onIntent(SwipeIntent.SettingsDismissed)
        }
    )
}

@Composable
fun SwipeScreenContent(
    state: SwipeState,
    onIntent: (SwipeIntent) -> Unit,
    onRequestPermission: () -> Unit,
    onViewInPhotos: (String) -> Unit,
    darkTheme: Boolean = isSystemInDarkTheme()
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { DragThresholdDp.toPx() }
    val exitDistancePx = with(density) { 800.dp.toPx() }

    val bgPage = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF0E0E12) else androidx.compose.ui.graphics.Color(0xFFF6F5F1)
    val textPrim = if (darkTheme) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF1A1A1A)
    val textSec = (if (darkTheme) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black).copy(alpha = 0.55f)
    val surface = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF1A1A20) else androidx.compose.ui.graphics.Color.White
    val border = (if (darkTheme) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black)
        .copy(alpha = if (darkTheme) 0.08f else 0.06f)

    Box(
        Modifier
            .fillMaxSize()
            .background(bgPage)
    ) {
        Column(Modifier.fillMaxSize()) {
            Header(
                state = state,
                darkTheme = darkTheme,
                textPrim = textPrim,
                textSec = textSec,
                onIntent = onIntent
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    !state.permissionStatus.grantsRead -> PermissionGate(
                        status = state.permissionStatus,
                        textPrim = textPrim,
                        textSec = textSec,
                        onRequest = onRequestPermission
                    )
                    state.loadError != null -> LoadErrorState(
                        message = state.loadError,
                        textPrim = textPrim,
                        textSec = textSec,
                        onRetry = { onIntent(SwipeIntent.RetryLoadTapped) }
                    )
                    state.isLoading -> CircularProgressIndicator(color = textPrim)
                    state.photos.isEmpty() -> EmptyState(textPrim = textPrim, textSec = textSec)
                    state.done -> DoneView(
                        state = state,
                        textPrim = textPrim,
                        textSec = textSec,
                        border = border,
                        onIntent = onIntent
                    )
                    else -> CardStack(
                        state = state,
                        exitDistancePx = exitDistancePx,
                        onIntent = onIntent,
                        thresholdPx = thresholdPx
                    )
                }
            }

            if (state.permissionStatus.grantsRead && state.loadError == null && !state.done && state.photos.isNotEmpty()) {
                ActionBar(
                    bgPage = bgPage,
                    surface = surface,
                    border = border,
                    historyEmpty = state.history.isEmpty(),
                    onIntent = onIntent
                )
            }
        }

        // Subtle gear in the bottom-left corner — only while cards are
        // visible, so it doesn't clutter loading/permission/done states.
        val showGear = state.permissionStatus.grantsRead &&
                state.loadError == null &&
                !state.isLoading &&
                !state.done &&
                state.photos.isNotEmpty()
        if (showGear) {
            SettingsGearButton(
                darkTheme = darkTheme,
                tint = textSec,
                onClick = { onIntent(SwipeIntent.SettingsButtonTapped) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 48.dp)
            )
        }

        // Overlays
        AnimatedVisibility(state.pickerOpen, enter = fadeIn(), exit = fadeOut()) {
            AlbumPickerSheet(
                albums = state.albums,
                currentId = state.currentAlbum?.id.orEmpty(),
                darkTheme = darkTheme,
                onPick = { onIntent(SwipeIntent.AlbumPicked(it)) },
                onClose = { onIntent(SwipeIntent.AlbumPickerDismissed) }
            )
        }

        AnimatedVisibility(state.aboutOpen, enter = fadeIn(), exit = fadeOut()) {
            AboutSheet(darkTheme = darkTheme, onClose = { onIntent(SwipeIntent.AboutDismissed) })
        }

        if (state.fullscreenOpen && state.topPhoto != null) {
            FullscreenPhotoView(
                photo = state.topPhoto!!,
                onClose = { onIntent(SwipeIntent.FullscreenDismissed) }
            )
        }

        if (state.settingsOpen) {
            SettingsBottomSheet(
                hasTopPhoto = state.topPhoto != null,
                darkTheme = darkTheme,
                onViewInPhotos = {
                    state.topPhoto?.let { onViewInPhotos(it.uri) }
                },
                onClose = { onIntent(SwipeIntent.SettingsDismissed) }
            )
        }
    }
}
