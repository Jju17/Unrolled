package dev.rahier.unrolled

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
    /** User tapped the big red footer button in the sheet. Triggers
     *  the system trash dialog directly — no in-app confirmation step,
     *  the OS dialog is the single (and unavoidable) confirmation. */
    data object EmptyTrashButtonTapped : SwipeIntent
    data object FullscreenDismissed : SwipeIntent
    /** User tapped keep/delete inside the fullscreen pager. [localIdx]
     *  is the offset from `activeIndex` — i.e. how many pages forward
     *  they scrolled before deciding. Intermediate photos are skipped
     *  for this session; the chosen one lands in `history` like a
     *  normal commit so undo behaves identically. */
    data class FullscreenMarkAtRelativeIndex(val localIdx: Int, val kept: Boolean) : SwipeIntent
    data object KeepButtonTapped : SwipeIntent
    data class PermissionResult(val status: PermissionStatus) : SwipeIntent
    data class PhotosLoadFailed(val message: String) : SwipeIntent
    data class PhotosLoaded(val photos: List<PhotoAsset>) : SwipeIntent
    data object ResetButtonTapped : SwipeIntent
    /** Tapped from the deleted sheet — wipes the persisted
     *  [keptPhotoIDs] + [deletedPhotoIDs] so the deck shows the full
     *  library again as if from scratch. */
    data object ResetProgressTapped : SwipeIntent
    data object RetryLoadTapped : SwipeIntent
    data object ScreenAppeared : SwipeIntent
    data object SettingsButtonTapped : SwipeIntent
    data object SettingsDismissed : SwipeIntent
    /** Toggle for "show location metadata" in Settings. Persists via [SettingsRepository]. */
    data class SettingsShowLocationToggled(val value: Boolean) : SwipeIntent
    data object ShuffleButtonTapped : SwipeIntent
    /** Fired from a delayed coroutine after a commit. Just clears
     *  [SwipeState.exitingPhoto] / [SwipeState.exitingDirection] — the
     *  actual commit (index bump, history, tallies) happened
     *  synchronously in [SwipeViewModel.startSwipe]. */
    data object SwipeExitAnimationCompleted : SwipeIntent
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
