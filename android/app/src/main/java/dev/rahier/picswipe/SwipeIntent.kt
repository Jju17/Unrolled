package dev.rahier.picswipe

sealed interface SwipeIntent {
    data object AboutDismissed : SwipeIntent
    data object AboutMenuItemTapped : SwipeIntent
    data class AlbumPicked(val albumId: String) : SwipeIntent
    data object AlbumPickerDismissed : SwipeIntent
    data object AlbumTitleTapped : SwipeIntent
    data class AlbumsLoaded(val albums: List<Album>) : SwipeIntent
    data class CardDragChanged(val x: Float, val y: Float) : SwipeIntent
    data class CardDragEnded(val thresholdPx: Float) : SwipeIntent
    data object DeleteButtonTapped : SwipeIntent
    /** Tap on the "Garder" button next to a photo in the review sheet —
     *  pulls that photo back out of the trash-staging list. */
    data class DeletedPhotoRestoreTapped(val id: Long) : SwipeIntent
    data object DeletedSheetButtonTapped : SwipeIntent
    data object DeletedSheetDismissed : SwipeIntent
    /** User tapped the big red footer button in the sheet. Opens the
     *  in-app "Es-tu sûr ?" alert. */
    data object EmptyTrashButtonTapped : SwipeIntent
    data object FullscreenDismissed : SwipeIntent
    data object KeepButtonTapped : SwipeIntent
    data class PermissionResult(val status: PermissionStatus) : SwipeIntent
    data class PhotosLoadFailed(val message: String) : SwipeIntent
    data class PhotosLoaded(val photos: List<PhotoAsset>) : SwipeIntent
    data object ResetButtonTapped : SwipeIntent
    data object RetryLoadTapped : SwipeIntent
    data object ScreenAppeared : SwipeIntent
    data object SettingsButtonTapped : SwipeIntent
    data object SettingsDismissed : SwipeIntent
    data object ShuffleButtonTapped : SwipeIntent
    /** Fired from a delayed coroutine after a commit. Just clears
     *  [SwipeState.exitingPhoto] / [SwipeState.exitingDirection] — the
     *  actual commit (index bump, history, tallies) happened
     *  synchronously in [SwipeViewModel.startSwipe]. */
    data object SwipeExitAnimationCompleted : SwipeIntent
    /** User confirmed the in-app alert. Reducer sets `trashPendingIDs`
     *  so the screen launches the system PendingIntent. */
    data object TrashAlertConfirmed : SwipeIntent
    data object TrashAlertDismissed : SwipeIntent
    /** UI consumed `trashPendingIDs` — reducer clears it. Prevents the
     *  LaunchedEffect from re-firing on recomposition. */
    data object TrashPendingConsumed : SwipeIntent
    /** Fired by the Activity Result launcher after the system trash
     *  dialog resolves. `success = true` if the user confirmed. */
    data class TrashResult(val success: Boolean, val message: String? = null) : SwipeIntent
    data object TopCardTapped : SwipeIntent
    data object UndoButtonTapped : SwipeIntent
    data object ViewAllPhotosTapped : SwipeIntent
}
