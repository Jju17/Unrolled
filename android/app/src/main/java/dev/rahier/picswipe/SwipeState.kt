package dev.rahier.picswipe

data class SwipeState(
    val aboutOpen: Boolean = false,
    val activeIndex: Int = 0,
    val albums: List<Album> = emptyList(),
    val deleted: Int = 0,
    /** IDs of photos staged for deletion via swipe-left. Kept in
     *  deletion order. The big red "Mettre à la poubelle" button in the
     *  review sheet hands this list to `MediaStore.createTrashRequest`. */
    val deletedPhotoIDs: List<Long> = emptyList(),
    val deletedSheetOpen: Boolean = false,
    /** Surfaced when the Recently-Deleted round-trip fails (user declined
     *  the system dialog, permission lost, etc.). Shown as a banner. */
    val deleteError: String? = null,
    val dragX: Float = 0f,
    val dragY: Float = 0f,
    val exitingDirection: Int = 0,   // -1 delete, +1 keep, 0 none
    /** Photo currently flying off as an overlay, or `null`. Rendered in
     *  a dedicated `ExitingCardOverlay` with its own `Animatable` so the
     *  fly-off can't be interrupted by the stack shifting underneath. */
    val exitingPhoto: PhotoAsset? = null,
    /** Finger X position captured at commit time. The overlay starts
     *  here so the fly-off continues the gesture arc. */
    val exitingStartDragX: Float = 0f,
    val exitingStartDragY: Float = 0f,
    val fullscreenOpen: Boolean = false,
    val history: List<SwipeHistoryEntry> = emptyList(),
    val isDragging: Boolean = false,
    val isLoading: Boolean = false,
    val kept: Int = 0,
    /** Set when the last `loadPhotos` / `loadAlbums` call threw. Shown
     *  in the UI with a Retry button. */
    val loadError: String? = null,
    /** Photos as returned from the repository (unshuffled source of truth). */
    val loadedPhotos: List<PhotoAsset> = emptyList(),
    val permissionStatus: PermissionStatus = PermissionStatus.NotDetermined,
    /** Android 14+: `true` when only `READ_MEDIA_VISUAL_USER_SELECTED`
     *  is granted (user picked a subset via the system picker). The
     *  empty state branches on this to explain why the list is short /
     *  empty and how to change the selection. */
    val hasPartialPhotoAccess: Boolean = false,
    /** What the screen displays — either [loadedPhotos] or a freshly-
     *  shuffled copy depending on [shuffleEnabled]. */
    val photos: List<PhotoAsset> = emptyList(),
    val pickerOpen: Boolean = false,
    val selectedAlbumID: String? = null,  // null = All Photos (default)
    val settingsOpen: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val trashAlertOpen: Boolean = false,
    /** `true` while the system `createTrashRequest` intent is in flight
     *  (user hasn't yet confirmed the OS dialog). Keeps the button
     *  disabled against a double-tap. */
    val trashInFlight: Boolean = false,
    /** Signal to the UI layer: when non-null, the screen launches the
     *  `MediaStore.createTrashRequest` PendingIntent for these IDs and
     *  sends back a `TrashResult` when the system dialog resolves.
     *  UI must set it back to `null` once consumed. */
    val trashPendingIDs: List<Long>? = null,
    /** Position in the unshuffled list — saved when enabling shuffle
     *  so disabling it resumes at the same photo. */
    val unshuffledIndex: Int = 0
) {
    val done: Boolean get() = photos.isNotEmpty() && activeIndex >= photos.size

    val topPhoto: PhotoAsset?
        get() = photos.getOrNull(activeIndex)

    /**
     * Currently-selected album, resolved from [selectedAlbumID] against
     * [albums]. Falls back to the first album. `null` while albums are
     * still loading — the view renders a neutral placeholder instead
     * of a fake album in that case.
     */
    val currentAlbum: Album?
        get() = selectedAlbumID?.let { id -> albums.firstOrNull { it.id == id } }
            ?: albums.firstOrNull()
}

data class SwipeHistoryEntry(val index: Int, val kept: Boolean)

enum class PermissionStatus {
    NotDetermined,
    Granted,
    Denied,
    PermanentlyDenied;

    val grantsRead: Boolean get() = this == Granted
}
