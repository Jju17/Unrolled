package dev.rahier.unrolled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SwipeViewModel(
    private val photoRepository: PhotoRepository,
    private val settingsRepository: SettingsRepository,
    private val progressRepository: SwipeProgressRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        run {
            val progress = progressRepository.load()
            SwipeState(
                settings = settingsRepository.load(),
                deletedPhotoIDs = progress.deletedIDs,
                keptPhotoIDs = progress.keptIDs
            )
        }
    )
    val state: StateFlow<SwipeState> = _state.asStateFlow()

    private var animationJob: Job? = null
    private var photosJob: Job? = null
    private var albumsJob: Job? = null

    /** Snapshot the persisted lists from the *current* state and write
     *  them through. Called after every mutation that touches them so
     *  a relaunch picks up exactly the same trash + kept sets. */
    private fun persistProgress() {
        val s = _state.value
        progressRepository.save(
            SwipeProgress(deletedIDs = s.deletedPhotoIDs, keptIDs = s.keptPhotoIDs)
        )
    }

    fun onIntent(intent: SwipeIntent) {
        when (intent) {
            SwipeIntent.AboutDismissed ->
                _state.update { it.copy(aboutOpen = false) }

            SwipeIntent.AboutMenuItemTapped ->
                _state.update { it.copy(aboutOpen = true) }

            is SwipeIntent.AlbumPicked -> {
                cancelAnimation()
                _state.update {
                    resetProgress(it).copy(
                        selectedAlbumID = intent.albumId,
                        pickerOpen = false
                    )
                }
                loadPhotos(intent.albumId)
            }

            SwipeIntent.AlbumPickerDismissed ->
                _state.update { it.copy(pickerOpen = false) }

            SwipeIntent.AlbumTitleTapped ->
                _state.update { it.copy(pickerOpen = true) }

            is SwipeIntent.AlbumsLoaded -> {
                _state.update { current ->
                    val adoptedId = current.selectedAlbumID ?: intent.albums.firstOrNull()?.id
                    current.copy(albums = intent.albums, selectedAlbumID = adoptedId)
                }
            }

            is SwipeIntent.CardDragChanged ->
                _state.update {
                    it.copy(
                        isDragging = true,
                        dragX = it.dragX + intent.x,
                        dragY = it.dragY + intent.y
                    )
                }

            is SwipeIntent.CardDragEnded -> {
                val s = _state.value
                when {
                    s.dragX > intent.thresholdPx -> startSwipe(direction = 1)
                    s.dragX < -intent.thresholdPx -> startSwipe(direction = -1)
                    else -> _state.update { it.copy(dragX = 0f, dragY = 0f, isDragging = false) }
                }
            }

            SwipeIntent.DeleteButtonTapped -> startSwipe(direction = -1)

            is SwipeIntent.DeletedPhotoRestoreTapped -> {
                val id = intent.id
                _state.update {
                    if (!it.deletedPhotoIDs.contains(id)) return@update it
                    it.copy(
                        deletedPhotoIDs = it.deletedPhotoIDs - id,
                        deleted = (it.deleted - 1).coerceAtLeast(0),
                        kept = it.kept + 1
                    )
                }
            }

            SwipeIntent.DeletedSheetButtonTapped ->
                _state.update { it.copy(deletedSheetOpen = true) }

            SwipeIntent.DeletedSheetDismissed ->
                _state.update { it.copy(deletedSheetOpen = false, deleteError = null) }

            SwipeIntent.EmptyTrashButtonTapped -> {
                // No in-app confirmation: MediaStore.createTrashRequest
                // shows its own mandatory system dialog, so duplicating
                // it in-app was just an extra tap for the user.
                val ids = _state.value.deletedPhotoIDs
                if (ids.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            trashInFlight = true,
                            trashPendingIDs = ids,
                            deleteError = null
                        )
                    }
                }
            }

            SwipeIntent.FullscreenDismissed ->
                _state.update { it.copy(fullscreenOpen = false) }

            is SwipeIntent.FullscreenMarkAtRelativeIndex -> {
                val s = _state.value
                val targetIdx = s.activeIndex + intent.localIdx
                if (intent.localIdx < 0 || targetIdx >= s.photos.size) return
                val photo = s.photos[targetIdx]
                _state.update {
                    it.copy(
                        history = it.history + SwipeHistoryEntry(targetIdx, intent.kept),
                        kept = if (intent.kept) it.kept + 1 else it.kept,
                        deleted = if (!intent.kept) it.deleted + 1 else it.deleted,
                        deletedPhotoIDs = if (!intent.kept)
                            it.deletedPhotoIDs + photo.id
                        else it.deletedPhotoIDs,
                        activeIndex = targetIdx + 1,
                        fullscreenOpen = false
                    )
                }
            }

            SwipeIntent.KeepButtonTapped -> startSwipe(direction = 1)

            is SwipeIntent.PermissionResult -> {
                _state.update {
                    it.copy(
                        permissionStatus = intent.status,
                        hasPartialPhotoAccess = photoRepository.hasPartialPhotoAccess()
                    )
                }
                if (intent.status.grantsRead) loadLibrary()
            }

            is SwipeIntent.PhotosLoadFailed ->
                _state.update { it.copy(isLoading = false, loadError = intent.message) }

            is SwipeIntent.PhotosLoaded ->
                _state.update {
                    it.copy(
                        loadedPhotos = intent.photos,
                        photos = if (it.shuffleEnabled) intent.photos.shuffled() else intent.photos,
                        isLoading = false,
                        loadError = null
                    )
                }

            SwipeIntent.ResetButtonTapped -> {
                cancelAnimation()
                _state.update { resetProgress(it) }
            }

            SwipeIntent.RetryLoadTapped -> {
                _state.update { it.copy(loadError = null) }
                loadLibrary()
            }

            SwipeIntent.ScreenAppeared -> {
                val status = if (photoRepository.hasReadPermission()) {
                    PermissionStatus.Granted
                } else {
                    // We don't distinguish NotDetermined vs Denied here —
                    // the launcher callback will refine it after a tap.
                    _state.value.permissionStatus.takeIf { it != PermissionStatus.Granted }
                        ?: PermissionStatus.NotDetermined
                }
                _state.update {
                    it.copy(
                        permissionStatus = status,
                        hasPartialPhotoAccess = photoRepository.hasPartialPhotoAccess()
                    )
                }
                if (status.grantsRead) loadLibrary()
            }

            SwipeIntent.SettingsButtonTapped ->
                _state.update { it.copy(settingsOpen = true) }

            SwipeIntent.SettingsDismissed ->
                _state.update { it.copy(settingsOpen = false) }

            is SwipeIntent.SettingsShowLocationToggled -> {
                val updated = _state.value.settings.copy(showLocation = intent.value)
                _state.update { it.copy(settings = updated) }
                settingsRepository.save(updated)
            }

            SwipeIntent.ShuffleButtonTapped -> {
                cancelAnimation()
                _state.update {
                    val newShuffle = !it.shuffleEnabled
                    if (newShuffle) {
                        it.copy(
                            shuffleEnabled = true,
                            unshuffledIndex = it.activeIndex,
                            photos = it.loadedPhotos.shuffled(),
                            activeIndex = 0,
                            history = emptyList(),
                            dragX = 0f, dragY = 0f,
                            exitingDirection = 0,
                            exitingPhoto = null,
                            exitingStartDragX = 0f,
                            exitingStartDragY = 0f,
                            isDragging = false
                        )
                    } else {
                        it.copy(
                            shuffleEnabled = false,
                            photos = it.loadedPhotos,
                            activeIndex = it.unshuffledIndex.coerceIn(0, it.loadedPhotos.size),
                            history = emptyList(),
                            dragX = 0f, dragY = 0f,
                            exitingDirection = 0,
                            exitingPhoto = null,
                            exitingStartDragX = 0f,
                            exitingStartDragY = 0f,
                            isDragging = false
                        )
                    }
                }
            }

            SwipeIntent.SwipeExitAnimationCompleted ->
                _state.update {
                    it.copy(
                        exitingPhoto = null,
                        exitingDirection = 0,
                        exitingStartDragX = 0f,
                        exitingStartDragY = 0f
                    )
                }

            SwipeIntent.TrashPendingConsumed ->
                _state.update { it.copy(trashPendingIDs = null) }

            is SwipeIntent.TrashResult -> {
                if (intent.success) {
                    // Photos are now in the system trash. Prune them from
                    // our local list so the user doesn't land on an asset
                    // MediaStore no longer returns, and remap activeIndex
                    // by ID so the user keeps their place visually.
                    _state.update {
                        val deletedSet = it.deletedPhotoIDs.toSet()
                        val topId = it.photos.getOrNull(it.activeIndex)?.id
                        val newLoaded = it.loadedPhotos.filterNot { p -> p.id in deletedSet }
                        val newPhotos = it.photos.filterNot { p -> p.id in deletedSet }
                        val remapped = if (topId != null) {
                            newPhotos.indexOfFirst { p -> p.id == topId }
                                .takeIf { i -> i >= 0 }
                                ?: it.activeIndex.coerceAtMost(newPhotos.size)
                        } else {
                            it.activeIndex.coerceAtMost(newPhotos.size)
                        }
                        it.copy(
                            loadedPhotos = newLoaded,
                            photos = newPhotos,
                            activeIndex = remapped,
                            deletedPhotoIDs = emptyList(),
                            deletedSheetOpen = false,
                            trashInFlight = false,
                            trashPendingIDs = null,
                            deleteError = null,
                            kept = 0,
                            deleted = 0,
                            history = emptyList()
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            trashInFlight = false,
                            trashPendingIDs = null,
                            // Fallback only fires defensively — every failure path
                            // in SwipeScreen.kt already provides a localized message.
                            deleteError = intent.message ?: "Deletion cancelled."
                        )
                    }
                }
            }

            SwipeIntent.TopCardTapped ->
                _state.update {
                    // Clear transient drag state from the tap gesture.
                    it.copy(
                        fullscreenOpen = true,
                        dragX = 0f, dragY = 0f,
                        isDragging = false
                    )
                }

            SwipeIntent.UndoButtonTapped -> {
                val last = _state.value.history.lastOrNull() ?: return
                cancelAnimation()
                _state.update {
                    val undoneID = it.photos.getOrNull(last.index)?.id
                    it.copy(
                        history = it.history.dropLast(1),
                        kept = if (last.kept) (it.kept - 1).coerceAtLeast(0) else it.kept,
                        deleted = if (!last.kept) (it.deleted - 1).coerceAtLeast(0) else it.deleted,
                        deletedPhotoIDs = if (!last.kept && undoneID != null)
                            it.deletedPhotoIDs - undoneID
                        else it.deletedPhotoIDs,
                        activeIndex = last.index,
                        exitingPhoto = null,
                        exitingDirection = 0,
                        exitingStartDragX = 0f,
                        exitingStartDragY = 0f
                    )
                }
            }

            SwipeIntent.ViewAllPhotosTapped -> {
                val firstAlbumId = _state.value.albums.firstOrNull()?.id
                if (firstAlbumId != null) onIntent(SwipeIntent.AlbumPicked(firstAlbumId))
            }
        }
    }

    private fun loadLibrary() {
        loadPhotos(_state.value.selectedAlbumID)
        loadAlbums()
    }

    private fun loadAlbums() {
        albumsJob?.cancel()
        albumsJob = viewModelScope.launch {
            try {
                val albums = photoRepository.loadAlbums()
                onIntent(SwipeIntent.AlbumsLoaded(albums))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e  // structured-concurrency cancellation propagates
            } catch (e: Exception) {
                onIntent(SwipeIntent.PhotosLoadFailed(e.message ?: "Couldn't load albums"))
            }
        }
    }

    private fun loadPhotos(albumId: String?) {
        photosJob?.cancel()
        _state.update { it.copy(isLoading = true, loadError = null) }
        photosJob = viewModelScope.launch {
            try {
                val photos = photoRepository.loadPhotos(albumId)
                onIntent(SwipeIntent.PhotosLoaded(photos))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                onIntent(SwipeIntent.PhotosLoadFailed(e.message ?: "Couldn't load photos"))
            }
        }
    }

    private fun startSwipe(direction: Int) {
        val s = _state.value
        if (s.activeIndex >= s.photos.size) return
        val committedIndex = s.activeIndex
        val committedPhoto = s.photos[committedIndex]
        // Commit synchronously and capture what the fly-off overlay needs
        // (photo + drag-at-lift). The overlay renders that photo with
        // its own `Animatable`, independent of the stack shift, so the
        // animation always plays its full arc.
        _state.update {
            it.copy(
                history = it.history + SwipeHistoryEntry(committedIndex, direction == 1),
                kept = if (direction == 1) it.kept + 1 else it.kept,
                deleted = if (direction == -1) it.deleted + 1 else it.deleted,
                deletedPhotoIDs = if (direction == -1)
                    it.deletedPhotoIDs + committedPhoto.id
                else it.deletedPhotoIDs,
                activeIndex = committedIndex + 1,
                exitingPhoto = committedPhoto,
                exitingDirection = direction,
                exitingStartDragX = it.dragX,
                exitingStartDragY = it.dragY,
                dragX = 0f,
                dragY = 0f,
                isDragging = false
            )
        }
        cancelAnimation()
        animationJob = viewModelScope.launch {
            delay(EXIT_ANIMATION_DURATION_MS)
            onIntent(SwipeIntent.SwipeExitAnimationCompleted)
        }
    }

    private fun cancelAnimation() {
        animationJob?.cancel()
        animationJob = null
    }

    private fun resetProgress(state: SwipeState): SwipeState = state.copy(
        activeIndex = 0,
        history = emptyList(),
        kept = 0,
        deleted = 0,
        deletedPhotoIDs = emptyList(),
        deleteError = null,
        dragX = 0f,
        dragY = 0f,
        exitingDirection = 0,
        exitingPhoto = null,
        exitingStartDragX = 0f,
        exitingStartDragY = 0f,
        isDragging = false,
        unshuffledIndex = 0
    )

    class Factory(
        private val photoRepository: PhotoRepository,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SwipeViewModel(photoRepository, settingsRepository) as T
    }

    companion object {
        /** Reducer cleanup delay — slightly longer than the overlay's
         *  own tween (260 ms) so the animation always finishes before
         *  `exitingPhoto` is nulled out. Shorter ⇒ card stops mid-flight. */
        const val EXIT_ANIMATION_DURATION_MS = 320L
    }
}
