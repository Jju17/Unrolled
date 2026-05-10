package dev.rahier.unrolled

import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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

    // Multi-permission contract — on API 34+ both READ_MEDIA_IMAGES AND
    // READ_MEDIA_VISUAL_USER_SELECTED are requested at once, and the system
    // returns a map. Any `true` entry means the user granted SOME scope
    // (full library or selected-only); either is enough for the repo.
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val anyGranted = grants.values.any { it }
        val status = when {
            anyGranted -> PermissionStatus.Granted
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

    // Trash launcher: when the user confirms our in-app alert, the VM
    // sets `trashPendingIDs`; this effect fires the system PendingIntent
    // and routes the RESULT_OK / RESULT_CANCELED back to the VM.
    val cancelledMessage = stringResource(R.string.trash_error_cancelled)
    val oldAndroidMessage = stringResource(R.string.trash_error_old_android)
    val genericMessage = stringResource(R.string.trash_error_generic)
    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val ok = result.resultCode == Activity.RESULT_OK
        viewModel.onIntent(
            SwipeIntent.TrashResult(
                success = ok,
                message = if (ok) null else cancelledMessage
            )
        )
    }
    LaunchedEffect(state.trashPendingIDs) {
        val ids = state.trashPendingIDs ?: return@LaunchedEffect
        // API 30+ has `createTrashRequest` (moves to Recently Deleted).
        // Below 30 the contract doesn't exist — fall back to a plain
        // failure so the banner surfaces the limitation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            viewModel.onIntent(SwipeIntent.TrashResult(
                success = false,
                message = oldAndroidMessage
            ))
            return@LaunchedEffect
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val uris = ids.map { ContentUris.withAppendedId(collection, it) }
        try {
            val pending = MediaStore.createTrashRequest(
                context.contentResolver, uris, true
            )
            trashLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
        } catch (e: Exception) {
            viewModel.onIntent(SwipeIntent.TrashResult(
                success = false,
                message = e.message ?: genericMessage
            ))
        } finally {
            // Mark the pending IDs as consumed regardless of outcome so
            // this effect doesn't re-fire on the next recomposition.
            viewModel.onIntent(SwipeIntent.TrashPendingConsumed)
        }
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
                else -> launcher.launch(ReadPermissions)
            }
        },
        onViewInPhotos = { photoUri ->
            // Opens the current photo in a system gallery. Resolving the
            // exact MIME via ContentResolver gives Google Photos the type
            // it expects (e.g. image/jpeg) — going through "image/*" makes
            // some viewers fall back to "show all images in this folder"
            // which is what the user reported as "doesn't deep-link".
            val uri = Uri.parse(photoUri)
            val mime = runCatching { context.contentResolver.getType(uri) }
                .getOrNull() ?: "image/*"
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // Always show the chooser so the user can pick Google Photos /
            // their preferred gallery — that's the "deep-link to this
            // exact photo" path the friend was missing on his test build.
            val chooser = Intent.createChooser(view, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(chooser) }
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
                    state.photos.isEmpty() -> EmptyState(
                        textPrim = textPrim,
                        textSec = textSec,
                        hasPartialAccess = state.hasPartialPhotoAccess,
                        onChangeSelection = onRequestPermission
                    )
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
                    textSec = textSec,
                    historyEmpty = state.history.isEmpty(),
                    showViewInPhotos = state.topPhoto != null,
                    onIntent = onIntent,
                    onViewInPhotos = {
                        state.topPhoto?.let { onViewInPhotos(it.uri) }
                    }
                )
            }
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
                photos = state.photos,
                startIndex = state.activeIndex,
                settings = state.settings,
                onMarkAtRelativeIndex = { localIdx, kept ->
                    onIntent(SwipeIntent.FullscreenMarkAtRelativeIndex(localIdx, kept))
                },
                onClose = { onIntent(SwipeIntent.FullscreenDismissed) }
            )
        }

        if (state.settingsOpen) {
            SettingsBottomSheet(
                settings = state.settings,
                onShowLocationToggled = { onIntent(SwipeIntent.SettingsShowLocationToggled(it)) },
                onClose = { onIntent(SwipeIntent.SettingsDismissed) }
            )
        }

        AnimatedVisibility(state.deletedSheetOpen, enter = fadeIn(), exit = fadeOut()) {
            DeletedPhotosSheet(
                state = state,
                darkTheme = darkTheme,
                onIntent = onIntent
            )
        }
    }
}
