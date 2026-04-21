package dev.rahier.picswipe

/**
 * Immutable state for the swipe screen. All fields alphabetised. The
 * ViewModel emits new instances via `copy(...)`; the composable renders
 * purely from this state and never mutates it directly.
 */
data class SwipeState(
    val aboutOpen: Boolean = false,
    val activeIndex: Int = 0,
    val albumID: String = "recents",
    val deleted: Int = 0,
    val dragX: Float = 0f,
    val dragY: Float = 0f,
    val exitingDirection: Int = 0,   // -1 delete, +1 keep, 0 none
    val history: List<SwipeHistoryEntry> = emptyList(),
    val isDragging: Boolean = false,
    val kept: Int = 0,
    val menuOpen: Boolean = false,
    val pickerOpen: Boolean = false
) {
    val done: Boolean get() = activeIndex >= PhotoLibrary.samples.size
    val currentAlbum: Album
        get() = AlbumLibrary.all.firstOrNull { it.id == albumID } ?: AlbumLibrary.all[0]
}

data class SwipeHistoryEntry(val index: Int, val kept: Boolean)
