package dev.rahier.unrolled

sealed interface SwipeIntent {
    data object AboutDismissed : SwipeIntent
    data object AboutMenuItemTapped : SwipeIntent
    data class CardDragChanged(val x: Float, val y: Float) : SwipeIntent
    data class CardDragEnded(val thresholdPx: Float) : SwipeIntent
    data object DeleteButtonTapped : SwipeIntent
    /** "Keep" button next to a photo in the review sheet. */
    data class DeletedPhotoRestoreTapped(val id: Long) : SwipeIntent
    data object DeletedSheetButtonTapped : SwipeIntent
    data object DeletedSheetDismissed : SwipeIntent
    /** Big red footer button in the sheet. The OS dialog is the single confirmation. */
    data object EmptyTrashButtonTapped : SwipeIntent
    data object FullscreenDismissed : SwipeIntent
    /** Keep/delete inside the fullscreen pager; [localIdx] is the offset from `activeIndex`. */
    data class FullscreenMarkAtRelativeIndex(val localIdx: Int, val kept: Boolean) : SwipeIntent
    data object GoalCelebrationDismissed : SwipeIntent
    data object KeepButtonTapped : SwipeIntent
    data object MilestoneDismissed : SwipeIntent
    data class PermissionResult(val status: PermissionStatus) : SwipeIntent
    data object PhotosLoadFailed : SwipeIntent
    data class PhotosLoaded(
        val photos: List<PhotoAsset>,
        val purpose: LoadPurpose = LoadPurpose.Initial
    ) : SwipeIntent
    data class PilePicked(val selector: PileSelector) : SwipeIntent
    data object PilePickerDismissed : SwipeIntent
    data class PilesLoaded(val piles: List<Pile>) : SwipeIntent
    data object PileTitleTapped : SwipeIntent
    /** "Start over" on the done view: forgets the decisions on this pile's photos and refills. */
    data object ResetButtonTapped : SwipeIntent
    /** Reset button in the deleted sheet: wipes both persisted lists. */
    data object ResetProgressTapped : SwipeIntent
    data object RetryLoadTapped : SwipeIntent
    /** Sent on every ON_RESUME: first time a full load, afterwards a silent refresh. */
    data object ScreenAppeared : SwipeIntent
    data object SettingsButtonTapped : SwipeIntent
    data class SettingsDailyGoalChanged(val value: Int) : SwipeIntent
    data class SettingsDetectSimilarToggled(val value: Boolean) : SwipeIntent
    data object SettingsDismissed : SwipeIntent
    data class SettingsGamificationToggled(val value: Boolean) : SwipeIntent
    data class SettingsIncludeVideosToggled(val value: Boolean) : SwipeIntent
    data class SettingsReminderHourChanged(val value: Int) : SwipeIntent
    data class SettingsReminderToggled(val value: Boolean) : SwipeIntent
    data class SettingsShowLocationToggled(val value: Boolean) : SwipeIntent
    data object ShuffleButtonTapped : SwipeIntent
    data object SimilarBadgeTapped : SwipeIntent
    data class SimilarGroupResolved(val keptIds: List<Long>, val deletedIds: List<Long>) : SwipeIntent
    data class SimilarityHashesLoaded(val hashes: Map<Long, Long>) : SwipeIntent
    data object SimilarSheetDismissed : SwipeIntent
    data object StatsButtonTapped : SwipeIntent
    data object StatsDismissed : SwipeIntent
    data object StatsResetTapped : SwipeIntent
    data object SwipeExitAnimationCompleted : SwipeIntent
    data object TopCardTapped : SwipeIntent
    /** UI consumed `trashPendingUris`. */
    data object TrashPendingConsumed : SwipeIntent
    /** Assets resolved for the persisted trash ids. */
    data class TrashPhotosLoaded(val photos: List<PhotoAsset>) : SwipeIntent
    data class TrashPhotoTapped(val id: Long) : SwipeIntent
    data object TrashPreviewDismissed : SwipeIntent
    data class TrashPreviewKeepTapped(val localIdx: Int) : SwipeIntent
    /** Result of the system trash dialog. `message == null` with `success == false` is a user cancel. */
    data class TrashResult(val success: Boolean, val message: String? = null) : SwipeIntent
    data object UndoButtonTapped : SwipeIntent
    data object ViewAllPhotosTapped : SwipeIntent
}
