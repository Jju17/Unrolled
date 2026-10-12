package dev.rahier.unrolled

data class SwipeState(
    val aboutOpen: Boolean = false,
    val activeIndex: Int = 0,
    /** Staged for deletion by swipe-left, in deletion order. Persisted. */
    val deletedPhotoIDs: List<Long> = emptyList(),
    val deletedSheetOpen: Boolean = false,
    val deleteError: String? = null,
    val dragX: Float = 0f,
    val dragY: Float = 0f,
    val exitingDirection: Int = 0,
    val exitingPhoto: PhotoAsset? = null,
    val exitingStartDragX: Float = 0f,
    val exitingStartDragY: Float = 0f,
    val fullscreenOpen: Boolean = false,
    val goalCelebration: Boolean = false,
    /** Android 14+: only `READ_MEDIA_VISUAL_USER_SELECTED` is granted. */
    val hasPartialPhotoAccess: Boolean = false,
    val history: List<SwipeHistoryEntry> = emptyList(),
    val isDragging: Boolean = false,
    val isLoading: Boolean = false,
    /** Kept across sessions. Persisted next to [deletedPhotoIDs]. */
    val keptPhotoIDs: List<Long> = emptyList(),
    val libraryLoaded: Boolean = false,
    /** Pile photos minus the ids classified at load time (unshuffled). */
    val loadedPhotos: List<PhotoAsset> = emptyList(),
    val loadFailed: Boolean = false,
    val milestoneReached: Milestone? = null,
    val permissionStatus: PermissionStatus = PermissionStatus.NotDetermined,
    /** The deck: [loadedPhotos] or a shuffled copy. Classified photos stay in place. */
    val photos: List<PhotoAsset> = emptyList(),
    val pickerOpen: Boolean = false,
    /** Unfiltered size of the last pile load. */
    val pilePhotoCount: Int = 0,
    val piles: List<Pile> = emptyList(),
    val selectedPile: PileSelector = PileSelector.All,
    val sessionDeletedIds: Set<Long> = emptySet(),
    val sessionFinishedAt: Long? = null,
    val sessionKeptIds: Set<Long> = emptySet(),
    val sessionStartedAt: Long? = null,
    val settings: UserSettings = UserSettings(),
    val settingsOpen: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val similarGroups: List<List<Long>> = emptyList(),
    val similarityHashes: Map<Long, Long> = emptyMap(),
    val similarSheetOpen: Boolean = false,
    val stats: ActivityStats = ActivityStats(),
    val statsOpen: Boolean = false,
    /** `true` while the system trash dialog is in flight. */
    val trashInFlight: Boolean = false,
    /** Content uris the screen must hand to `MediaStore.createTrashRequest`. */
    val trashPendingUris: List<String>? = null,
    /** Resolved assets for [deletedPhotoIDs], same order. */
    val trashPhotos: List<PhotoAsset> = emptyList(),
    val unshuffledIndex: Int = 0
) {
    val done: Boolean get() = pilePhotoCount > 0 && activeIndex >= photos.size

    val isEmptyPile: Boolean get() = libraryLoaded && pilePhotoCount == 0

    val topPhoto: PhotoAsset?
        get() = photos.getOrNull(activeIndex)

    val deleted: Int get() = deletedPhotoIDs.size
    val kept: Int get() = keptPhotoIDs.size

    val remainingInDeck: Int get() = (photos.size - activeIndex).coerceAtLeast(0)

    val currentPile: Pile?
        get() = piles.firstOrNull { it.selector == selectedPile }

    val sessionReviewed: Int get() = sessionKeptIds.size + sessionDeletedIds.size

    val sessionBytesToFree: Long
        get() = photos.filter { it.id in sessionDeletedIds }.sumOf { it.sizeBytes }

    val sessionDurationMs: Long
        get() {
            val start = sessionStartedAt ?: return 0L
            val end = sessionFinishedAt ?: return 0L
            return (end - start).coerceAtLeast(0L)
        }

    val sessionPhotosPerMinute: Double
        get() {
            val durationMs = sessionDurationMs
            if (durationMs < 1_000L) return 0.0
            return sessionReviewed / (durationMs / 60_000.0)
        }

    val goalReachedToday: Boolean
        get() = settings.gamificationEnabled && stats.todayCount >= settings.dailyGoal

    val topSimilarGroup: List<PhotoAsset>
        get() {
            val top = topPhoto ?: return emptyList()
            val group = similarGroups.firstOrNull { top.id in it } ?: return emptyList()
            val classified = (keptPhotoIDs + deletedPhotoIDs).toHashSet()
            val byId = photos.associateBy { it.id }
            val members = group.mapNotNull { byId[it] }.filter { it.id !in classified }
            return if (members.size >= 2) members else emptyList()
        }
}

data class PhotoDecision(val photoId: Long, val kept: Boolean)

data class SwipeHistoryEntry(val decisions: List<PhotoDecision>) {
    constructor(photoId: Long, kept: Boolean) : this(listOf(PhotoDecision(photoId, kept)))

    val photoIds: List<Long> get() = decisions.map { it.photoId }
}

enum class LoadPurpose { Initial, Refresh, StartOver }

enum class PermissionStatus {
    NotDetermined,
    Granted,
    Denied,
    PermanentlyDenied;

    val grantsRead: Boolean get() = this == Granted
}
