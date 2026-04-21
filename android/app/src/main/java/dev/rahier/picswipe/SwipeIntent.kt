package dev.rahier.picswipe

/**
 * All user-driven events the swipe screen can emit. Named after what
 * the user actually does (`DeleteButtonTapped`, `CardDragChanged`)
 * rather than what the reducer will do with them. Effect-completion
 * signals (`SwipeAnimationCompleted`) are also modelled as intents so
 * every state change is traceable through `onIntent`.
 */
sealed interface SwipeIntent {
    data object AboutDismissed : SwipeIntent
    data object AboutMenuItemTapped : SwipeIntent
    data class AlbumPicked(val albumId: String) : SwipeIntent
    data object AlbumPickerDismissed : SwipeIntent
    data object AlbumTitleTapped : SwipeIntent
    data class CardDragChanged(val x: Float, val y: Float) : SwipeIntent
    data class CardDragEnded(val thresholdPx: Float) : SwipeIntent
    data object DeleteButtonTapped : SwipeIntent
    data object KeepButtonTapped : SwipeIntent
    data object MenuButtonTapped : SwipeIntent
    data object MenuDismissed : SwipeIntent
    data object ResetButtonTapped : SwipeIntent
    data class SwipeAnimationCompleted(val direction: Int) : SwipeIntent
    data object UndoButtonTapped : SwipeIntent
}
