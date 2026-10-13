package dev.rahier.unrolled

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
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
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Top-level stateful entry: owns the ActivityResultLauncher for the
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

    // Multi-permission contract: on API 34+ both READ_MEDIA_IMAGES AND
    // READ_MEDIA_VISUAL_USER_SELECTED are requested at once, and the system
    // returns a map. Any `true` entry means the user granted SOME scope
    // (full library or selected-only); either is enough for the repo.
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val status = when {
            viewModel.hasReadPermission() -> PermissionStatus.Granted
            activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, ReadPermission) ->
                    PermissionStatus.PermanentlyDenied
            else -> PermissionStatus.Denied
        }
        viewModel.onIntent(SwipeIntent.PermissionResult(status))
    }

    // Re-check permission every time we resume: covers the "user went
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

    val oldAndroidMessage = stringResource(R.string.trash_error_old_android)
    val genericMessage = stringResource(R.string.trash_error_generic)
    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onIntent(
            SwipeIntent.TrashResult(success = result.resultCode == Activity.RESULT_OK)
        )
    }
    LaunchedEffect(state.trashPendingUris) {
        val uris = state.trashPendingUris ?: return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            viewModel.onIntent(SwipeIntent.TrashResult(
                success = false,
                message = oldAndroidMessage
            ))
            viewModel.onIntent(SwipeIntent.TrashPendingConsumed)
            return@LaunchedEffect
        }
        try {
            val pending = MediaStore.createTrashRequest(
                context.contentResolver, uris.map { it.toUri() }, true
            )
            trashLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
        } catch (e: Exception) {
            viewModel.onIntent(SwipeIntent.TrashResult(success = false, message = genericMessage))
        } finally {
            viewModel.onIntent(SwipeIntent.TrashPendingConsumed)
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.onIntent(SwipeIntent.SettingsReminderToggled(true))
    }

    SwipeScreenContent(
        state = state,
        onIntent = viewModel::onIntent,
        onReminderToggled = { enabled ->
            val needsPrompt = enabled && Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            if (needsPrompt) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.onIntent(SwipeIntent.SettingsReminderToggled(enabled))
            }
        },
        onRequestPermission = {
            when (state.permissionStatus) {
                PermissionStatus.PermanentlyDenied -> {
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
            // it expects (e.g. image/jpeg): going through "image/*" makes
            // some viewers fall back to "show all images in this folder"
            // which is what the user reported as "doesn't deep-link".
            val uri = photoUri.toUri()
            val mime = runCatching { context.contentResolver.getType(uri) }
                .getOrNull() ?: "image/*"
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // Always show the chooser so the user can pick Google Photos /
            // their preferred gallery: that's the "deep-link to this
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
    onReminderToggled: (Boolean) -> Unit,
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
                    state.loadFailed -> LoadErrorState(
                        textPrim = textPrim,
                        textSec = textSec,
                        onRetry = { onIntent(SwipeIntent.RetryLoadTapped) }
                    )
                    state.isLoading -> CircularProgressIndicator(color = textPrim)
                    state.isEmptyPile -> EmptyState(
                        textPrim = textPrim,
                        textSec = textSec,
                        hasPartialAccess = state.hasPartialPhotoAccess,
                        onChangeSelection = onRequestPermission
                    )
                    state.done -> SessionSummary(
                        state = state,
                        darkTheme = darkTheme,
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

            if (state.permissionStatus.grantsRead && !state.loadFailed && !state.isLoading && !state.done && state.photos.isNotEmpty()) {
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
            PilePickerSheet(
                piles = state.piles,
                currentId = state.selectedPile.id,
                darkTheme = darkTheme,
                onPick = { onIntent(SwipeIntent.PilePicked(it)) },
                onClose = { onIntent(SwipeIntent.PilePickerDismissed) }
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
                onKeepAtRelativeIndex = { onIntent(SwipeIntent.FullscreenMarkAtRelativeIndex(it, true)) },
                onDeleteAtRelativeIndex = { onIntent(SwipeIntent.FullscreenMarkAtRelativeIndex(it, false)) },
                onClose = { onIntent(SwipeIntent.FullscreenDismissed) }
            )
        }

        if (state.settingsOpen) {
            SettingsBottomSheet(
                settings = state.settings,
                onIntent = onIntent,
                onReminderToggled = onReminderToggled,
                onClose = { onIntent(SwipeIntent.SettingsDismissed) }
            )
        }

        AnimatedVisibility(state.similarSheetOpen, enter = fadeIn(), exit = fadeOut()) {
            SimilarPhotosSheet(
                group = state.topSimilarGroup,
                darkTheme = darkTheme,
                onResolve = { keptIds, deletedIds ->
                    onIntent(SwipeIntent.SimilarGroupResolved(keptIds, deletedIds))
                },
                onClose = { onIntent(SwipeIntent.SimilarSheetDismissed) }
            )
        }

        AnimatedVisibility(state.statsOpen, enter = fadeIn(), exit = fadeOut()) {
            StatsSheet(state = state, darkTheme = darkTheme, onIntent = onIntent)
        }

        state.milestoneReached?.let { milestone ->
            MilestoneOverlay(
                milestone = milestone,
                darkTheme = darkTheme,
                onDismiss = { onIntent(SwipeIntent.MilestoneDismissed) }
            )
        }

        AnimatedVisibility(state.deletedSheetOpen, enter = fadeIn(), exit = fadeOut()) {
            DeletedPhotosSheet(
                state = state,
                darkTheme = darkTheme,
                onIntent = onIntent
            )
        }

        state.trashPreviewIndex?.let { startIndex ->
            FullscreenPhotoView(
                photos = state.trashPhotos,
                startIndex = startIndex,
                settings = state.settings,
                onKeepAtRelativeIndex = { onIntent(SwipeIntent.TrashPreviewKeepTapped(it)) },
                onDeleteAtRelativeIndex = null,
                onClose = { onIntent(SwipeIntent.TrashPreviewDismissed) }
            )
        }
    }
}

@Composable
private fun MilestoneOverlay(milestone: Milestone, darkTheme: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val surface = if (darkTheme) androidx.compose.ui.graphics.Color(0xFF1A1A20) else androidx.compose.ui.graphics.Color.White
    val textPrim = if (darkTheme) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF1A1A1A)
    val textSec = (if (darkTheme) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black).copy(alpha = 0.55f)
    val label = milestoneLabel(milestone)
    val overlayTitle = stringResource(R.string.milestone_overlay_title)
    val shareTagline = stringResource(R.string.share_card_tagline)
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(milestone) { haptic.performHapticFeedback(HapticFeedbackType.Confirm) }

    BackHandler(onBack = onDismiss)

    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.milestone_overlay_title),
                color = textSec,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(label, color = textPrim, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            PrimaryPillButton(
                text = stringResource(R.string.milestone_share),
                onClick = {
                    shareCard(
                        context,
                        ShareCardContent(title = overlayTitle, headline = label, subtitle = shareTagline)
                    )
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.milestone_dismiss),
                color = textSec,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .minimumInteractiveComponentSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        Confetti()
    }
}
