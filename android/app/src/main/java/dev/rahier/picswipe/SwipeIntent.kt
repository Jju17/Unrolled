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
    data object TopCardTapped : SwipeIntent
    data object UndoButtonTapped : SwipeIntent
    data object ViewAllPhotosTapped : SwipeIntent
}
