package dev.rahier.unrolled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
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
    private val progressRepository: SwipeProgressRepository,
    private val statsRepository: StatsRepository,
    private val reminderScheduler: ReminderScheduler,
    private val logger: Logger = AndroidLogger,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _state = MutableStateFlow(
        run {
            val progress = progressRepository.load()
            SwipeState(
                settings = settingsRepository.load(),
                deletedPhotoIDs = progress.deletedIDs,
                keptPhotoIDs = progress.keptIDs,
                stats = ActivityTracker.rollover(statsRepository.load(), clock())
            )
        }
    )
    val state: StateFlow<SwipeState> = _state.asStateFlow()

    private var animationJob: Job? = null
    private var photosJob: Job? = null
    private var pilesJob: Job? = null
    private var trashJob: Job? = null
    private val requestedHashIds = HashSet<Long>()

    private fun persistProgress() {
        val s = _state.value
        progressRepository.save(
            SwipeProgress(deletedIDs = s.deletedPhotoIDs, keptIDs = s.keptPhotoIDs)
        )
    }

    private fun persistStats() {
        statsRepository.save(_state.value.stats)
    }

    private fun persistSettings() {
        settingsRepository.save(_state.value.settings)
    }

    fun hasReadPermission(): Boolean = photoRepository.hasReadPermission()

    fun onIntent(intent: SwipeIntent) {
        when (intent) {
            SwipeIntent.AboutDismissed ->
                _state.update { it.copy(aboutOpen = false) }

            SwipeIntent.AboutMenuItemTapped ->
                _state.update { it.copy(aboutOpen = true) }

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

            is SwipeIntent.DeletedPhotoRestoreTapped -> restoreFromTrash(intent.id)

            SwipeIntent.DeletedSheetButtonTapped ->
                _state.update { it.copy(deletedSheetOpen = true) }

            SwipeIntent.DeletedSheetDismissed ->
                _state.update { it.copy(deletedSheetOpen = false, deleteError = null, trashPreviewIndex = null) }

            SwipeIntent.EmptyTrashButtonTapped -> {
                val s = _state.value
                if (s.trashInFlight || s.deletedPhotoIDs.isEmpty()) return
                _state.update {
                    it.copy(
                        trashInFlight = true,
                        trashPendingUris = it.trashPhotos.map { p -> p.uri },
                        deleteError = null
                    )
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
                    classify(it, photo, intent.kept).copy(
                        history = it.history + SwipeHistoryEntry(photo.id, intent.kept),
                        activeIndex = targetIdx + 1,
                        fullscreenOpen = false
                    )
                }
                afterDecisions(count = 1)
            }

            SwipeIntent.GoalCelebrationDismissed ->
                _state.update { it.copy(goalCelebration = false) }

            SwipeIntent.KeepButtonTapped -> startSwipe(direction = 1)

            SwipeIntent.MilestoneDismissed ->
                _state.update { it.copy(milestoneReached = null) }

            is SwipeIntent.PermissionResult -> {
                _state.update {
                    it.copy(
                        permissionStatus = intent.status,
                        hasPartialPhotoAccess = photoRepository.hasPartialPhotoAccess()
                    )
                }
                if (intent.status.grantsRead) loadLibrary()
            }

            SwipeIntent.PhotosLoadFailed ->
                _state.update { it.copy(isLoading = false, loadFailed = true) }

            is SwipeIntent.PhotosLoaded -> {
                when (intent.purpose) {
                    LoadPurpose.Initial -> _state.update { applyInitialLoad(it, intent.photos) }
                    LoadPurpose.Refresh -> _state.update { applyRefresh(it, intent.photos) }
                    LoadPurpose.StartOver -> {
                        _state.update { applyInitialLoad(forgetPhotos(it, intent.photos), intent.photos) }
                        persistProgress()
                    }
                }
                requestHashes()
            }

            is SwipeIntent.PilePicked -> {
                cancelAnimation()
                _state.update {
                    resetSession(it).copy(selectedPile = intent.selector, pickerOpen = false)
                }
                loadPhotos(intent.selector, LoadPurpose.Initial)
            }

            SwipeIntent.PilePickerDismissed ->
                _state.update { it.copy(pickerOpen = false) }

            is SwipeIntent.PilesLoaded ->
                _state.update { it.copy(piles = intent.piles) }

            SwipeIntent.PileTitleTapped ->
                _state.update { it.copy(pickerOpen = true) }

            SwipeIntent.ResetButtonTapped -> {
                cancelAnimation()
                _state.update { resetSession(it) }
                loadPhotos(_state.value.selectedPile, LoadPurpose.StartOver)
            }

            SwipeIntent.ResetProgressTapped -> {
                cancelAnimation()
                trashJob?.cancel()
                _state.update {
                    resetSession(
                        it.copy(
                            deletedPhotoIDs = emptyList(),
                            keptPhotoIDs = emptyList(),
                            trashPhotos = emptyList(),
                            trashPreviewIndex = null,
                            deletedSheetOpen = false
                        )
                    )
                }
                persistProgress()
                loadPhotos(_state.value.selectedPile, LoadPurpose.Initial)
                loadPiles()
            }

            SwipeIntent.RetryLoadTapped -> {
                _state.update { it.copy(loadFailed = false) }
                loadLibrary()
            }

            SwipeIntent.ScreenAppeared -> {
                val previous = _state.value
                val status = if (photoRepository.hasReadPermission()) {
                    PermissionStatus.Granted
                } else {
                    previous.permissionStatus.takeIf { it != PermissionStatus.Granted }
                        ?: PermissionStatus.NotDetermined
                }
                val rolled = ActivityTracker.rollover(previous.stats, clock())
                _state.update {
                    it.copy(
                        permissionStatus = status,
                        hasPartialPhotoAccess = photoRepository.hasPartialPhotoAccess(),
                        stats = rolled
                    )
                }
                if (rolled != previous.stats) persistStats()
                if (!status.grantsRead) return
                val needsFullLoad =
                    !previous.libraryLoaded || !previous.permissionStatus.grantsRead
                if (needsFullLoad) loadLibrary() else refreshLibrary()
            }

            SwipeIntent.SettingsButtonTapped ->
                _state.update { it.copy(settingsOpen = true) }

            is SwipeIntent.SettingsDailyGoalChanged -> {
                _state.update { it.copy(settings = it.settings.copy(dailyGoal = intent.value)) }
                persistSettings()
            }

            is SwipeIntent.SettingsDetectSimilarToggled -> {
                _state.update {
                    it.copy(
                        settings = it.settings.copy(detectSimilar = intent.value),
                        similarGroups = if (intent.value) it.similarGroups else emptyList(),
                        similarSheetOpen = if (intent.value) it.similarSheetOpen else false
                    )
                }
                persistSettings()
                if (intent.value) requestHashes()
            }

            SwipeIntent.SettingsDismissed ->
                _state.update { it.copy(settingsOpen = false) }

            is SwipeIntent.SettingsGamificationToggled -> {
                _state.update {
                    it.copy(
                        settings = it.settings.copy(gamificationEnabled = intent.value),
                        goalCelebration = false,
                        milestoneReached = null
                    )
                }
                persistSettings()
            }

            is SwipeIntent.SettingsIncludeVideosToggled -> {
                cancelAnimation()
                _state.update {
                    resetSession(it).copy(settings = it.settings.copy(includeVideos = intent.value))
                }
                persistSettings()
                if (_state.value.permissionStatus.grantsRead) {
                    loadPhotos(_state.value.selectedPile, LoadPurpose.Initial)
                    loadPiles()
                }
            }

            is SwipeIntent.SettingsReminderHourChanged -> {
                _state.update { it.copy(settings = it.settings.copy(reminderHour = intent.value)) }
                persistSettings()
                if (_state.value.settings.reminderEnabled) reminderScheduler.schedule(intent.value)
            }

            is SwipeIntent.SettingsReminderToggled -> {
                _state.update { it.copy(settings = it.settings.copy(reminderEnabled = intent.value)) }
                persistSettings()
                if (intent.value) reminderScheduler.schedule(_state.value.settings.reminderHour)
                else reminderScheduler.cancel()
            }

            is SwipeIntent.SettingsShowLocationToggled -> {
                _state.update { it.copy(settings = it.settings.copy(showLocation = intent.value)) }
                persistSettings()
            }

            SwipeIntent.ShuffleButtonTapped -> {
                cancelAnimation()
                _state.update { toggleShuffle(it) }
                requestHashes()
            }

            SwipeIntent.SimilarBadgeTapped -> {
                if (_state.value.topSimilarGroup.size < 2) return
                _state.update { it.copy(similarSheetOpen = true, dragX = 0f, dragY = 0f, isDragging = false) }
            }

            is SwipeIntent.SimilarGroupResolved -> resolveSimilarGroup(intent.keptIds, intent.deletedIds)

            is SwipeIntent.SimilarityHashesLoaded ->
                _state.update { withSimilarGroups(it.copy(similarityHashes = it.similarityHashes + intent.hashes)) }

            SwipeIntent.SimilarSheetDismissed ->
                _state.update { it.copy(similarSheetOpen = false) }

            SwipeIntent.StatsButtonTapped ->
                _state.update { it.copy(statsOpen = true) }

            SwipeIntent.StatsDismissed ->
                _state.update { it.copy(statsOpen = false) }

            SwipeIntent.StatsResetTapped -> {
                _state.update { it.copy(stats = ActivityTracker.rollover(ActivityStats(), clock()), milestoneReached = null, goalCelebration = false) }
                persistStats()
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
                _state.update { it.copy(trashPendingUris = null) }

            is SwipeIntent.TrashPhotoTapped -> {
                val index = _state.value.trashPhotos.indexOfFirst { it.id == intent.id }
                if (index < 0) return
                _state.update { it.copy(trashPreviewIndex = index) }
            }

            SwipeIntent.TrashPreviewDismissed ->
                _state.update { it.copy(trashPreviewIndex = null) }

            is SwipeIntent.TrashPreviewKeepTapped -> {
                val s = _state.value
                val base = s.trashPreviewIndex ?: return
                val target = base + intent.localIdx
                if (intent.localIdx < 0 || target >= s.trashPhotos.size) return
                val id = s.trashPhotos[target].id
                _state.update { it.copy(trashPreviewIndex = null) }
                restoreFromTrash(id)
            }

            is SwipeIntent.TrashPhotosLoaded -> {
                val before = _state.value.deletedPhotoIDs
                _state.update {
                    val byId = intent.photos.associateBy { p -> p.id }
                    val resolved = it.deletedPhotoIDs.mapNotNull { id -> byId[id] }
                    it.copy(
                        trashPhotos = resolved,
                        deletedPhotoIDs = resolved.map { p -> p.id }
                    )
                }
                if (_state.value.deletedPhotoIDs != before) persistProgress()
            }

            is SwipeIntent.TrashResult -> {
                if (intent.success) {
                    val trashed = _state.value.trashPhotos
                    val outcome = ActivityTracker.recordTrash(
                        _state.value.stats,
                        count = _state.value.deletedPhotoIDs.size,
                        bytes = trashed.sumOf { it.sizeBytes },
                        nowMillis = clock(),
                        trackGoals = _state.value.settings.gamificationEnabled
                    )
                    _state.update {
                        pruneTrashedPhotos(it).copy(
                            stats = outcome.stats,
                            milestoneReached = outcome.newMilestones.firstOrNull() ?: it.milestoneReached
                        )
                    }
                    persistProgress()
                    persistStats()
                    loadPiles()
                } else {
                    _state.update {
                        it.copy(
                            trashInFlight = false,
                            trashPendingUris = null,
                            deleteError = intent.message
                        )
                    }
                }
            }

            SwipeIntent.TopCardTapped ->
                _state.update {
                    it.copy(
                        fullscreenOpen = true,
                        dragX = 0f, dragY = 0f,
                        isDragging = false
                    )
                }

            SwipeIntent.UndoButtonTapped -> {
                val last = _state.value.history.lastOrNull() ?: return
                cancelAnimation()
                _state.update { undo(it, last) }
                persistProgress()
            }

            SwipeIntent.ViewAllPhotosTapped -> onIntent(SwipeIntent.PilePicked(PileSelector.All))
        }
    }

    private fun loadLibrary() {
        loadPhotos(_state.value.selectedPile, LoadPurpose.Initial)
        loadPiles()
        loadTrashPhotos()
    }

    private fun refreshLibrary() {
        loadPhotos(_state.value.selectedPile, LoadPurpose.Refresh)
        loadPiles()
        loadTrashPhotos()
    }

    private fun loadPiles() {
        pilesJob?.cancel()
        val s = _state.value
        val classified = classifiedIds(s)
        val includeVideos = s.settings.includeVideos
        pilesJob = viewModelScope.launch {
            try {
                val piles = photoRepository.loadPiles(clock(), classified, includeVideos)
                onIntent(SwipeIntent.PilesLoaded(piles))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(LOG_TAG, "loadPiles failed", e)
                onIntent(SwipeIntent.PhotosLoadFailed)
            }
        }
    }

    private fun loadPhotos(selector: PileSelector, purpose: LoadPurpose) {
        photosJob?.cancel()
        if (purpose != LoadPurpose.Refresh) {
            _state.update { it.copy(isLoading = true, loadFailed = false) }
        }
        val includeVideos = _state.value.settings.includeVideos
        photosJob = viewModelScope.launch {
            try {
                val photos = photoRepository.loadPhotos(selector, includeVideos)
                onIntent(SwipeIntent.PhotosLoaded(photos, purpose))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(LOG_TAG, "loadPhotos failed", e)
                if (purpose != LoadPurpose.Refresh) onIntent(SwipeIntent.PhotosLoadFailed)
            }
        }
    }

    private fun loadTrashPhotos() {
        trashJob?.cancel()
        val ids = _state.value.deletedPhotoIDs
        if (ids.isEmpty()) {
            _state.update { it.copy(trashPhotos = emptyList(), trashPreviewIndex = null) }
            return
        }
        trashJob = viewModelScope.launch {
            try {
                val photos = photoRepository.loadPhotosByIds(ids)
                onIntent(SwipeIntent.TrashPhotosLoaded(photos))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(LOG_TAG, "loadPhotosByIds failed", e)
            }
        }
    }

    private fun requestHashes() {
        val s = _state.value
        if (!s.settings.detectSimilar) return
        val window = s.photos
            .drop(s.activeIndex)
            .take(HASH_WINDOW)
            .mapTo(HashSet()) { it.id }
        if (window.isEmpty()) return
        val wanted = SimilarityGrouper.candidateRuns(s.loadedPhotos)
            .filter { run -> run.any { it in window } }
            .flatten()
            .filter { it !in s.similarityHashes && it !in requestedHashIds }
            .distinct()
        if (wanted.isEmpty()) return
        requestedHashIds += wanted
        viewModelScope.launch {
            try {
                val hashes = photoRepository.loadSimilarityHashes(wanted)
                onIntent(SwipeIntent.SimilarityHashesLoaded(hashes))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(LOG_TAG, "loadSimilarityHashes failed", e)
            } finally {
                requestedHashIds -= wanted.toSet()
            }
        }
    }

    private fun withSimilarGroups(state: SwipeState): SwipeState {
        if (!state.settings.detectSimilar) return state.copy(similarGroups = emptyList())
        val groups = SimilarityGrouper.candidateRuns(state.loadedPhotos)
            .flatMap { SimilarityGrouper.confirmGroups(it, state.similarityHashes) }
        return state.copy(similarGroups = groups)
    }

    private fun resolveSimilarGroup(keptIds: List<Long>, deletedIds: List<Long>) {
        val s = _state.value
        val byId = s.photos.associateBy { it.id }
        val decisions = keptIds.mapNotNull { id -> byId[id]?.let { PhotoDecision(id, kept = true) } } +
            deletedIds.mapNotNull { id -> byId[id]?.let { PhotoDecision(id, kept = false) } }
        if (decisions.isEmpty()) {
            _state.update { it.copy(similarSheetOpen = false) }
            return
        }
        cancelAnimation()
        _state.update { current ->
            var next = current
            for (decision in decisions) {
                next = classify(next, byId.getValue(decision.photoId), decision.kept)
            }
            val classified = classifiedIds(next)
            var index = next.activeIndex
            while (index < next.photos.size && next.photos[index].id in classified) index++
            next.copy(
                history = next.history + SwipeHistoryEntry(decisions),
                activeIndex = index,
                similarSheetOpen = false,
                exitingPhoto = null,
                exitingDirection = 0,
                exitingStartDragX = 0f,
                exitingStartDragY = 0f,
                dragX = 0f,
                dragY = 0f,
                isDragging = false
            )
        }
        afterDecisions(count = decisions.size)
    }

    /** Session timestamps, stats and persistence shared by every decision path. */
    private fun afterDecisions(count: Int) {
        val now = clock()
        val outcome = ActivityTracker.recordDecisions(
            _state.value.stats,
            count = count,
            nowMillis = now,
            dailyGoal = _state.value.settings.dailyGoal,
            trackGoals = _state.value.settings.gamificationEnabled
        )
        _state.update {
            it.copy(
                sessionStartedAt = it.sessionStartedAt ?: now,
                sessionFinishedAt = if (it.activeIndex >= it.photos.size) now else null,
                stats = outcome.stats,
                goalCelebration = it.goalCelebration || outcome.goalJustReached,
                milestoneReached = outcome.newMilestones.firstOrNull() ?: it.milestoneReached
            )
        }
        persistProgress()
        persistStats()
        requestHashes()
    }

    private fun applyInitialLoad(state: SwipeState, incoming: List<PhotoAsset>): SwipeState {
        val classified = classifiedIds(state)
        val filtered = incoming.filterNot { it.id in classified }
        return withSimilarGroups(
            clearSession(state).copy(
                loadedPhotos = filtered,
                photos = if (state.shuffleEnabled) filtered.shuffled() else filtered,
                activeIndex = 0,
                history = emptyList(),
                pilePhotoCount = incoming.size,
                isLoading = false,
                libraryLoaded = true,
                loadFailed = false
            )
        )
    }

    private fun applyRefresh(state: SwipeState, incoming: List<PhotoAsset>): SwipeState {
        val classified = classifiedIds(state)
        val filtered = incoming.filterNot { it.id in classified }
        val incomingIds = incoming.mapTo(HashSet()) { it.id }
        val surviving = state.photos.filter { it.id in incomingIds }
        val survivingIds = surviving.mapTo(HashSet()) { it.id }
        val newcomers = filtered.filterNot { it.id in survivingIds }
        val photos = surviving + if (state.shuffleEnabled) newcomers.shuffled() else newcomers
        val topId = state.topPhoto?.id
        val activeIndex = topId?.let { id -> photos.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: state.activeIndex.coerceAtMost(photos.size)
        return withSimilarGroups(
            state.copy(
                loadedPhotos = filtered,
                photos = photos,
                activeIndex = activeIndex,
                history = state.history.filter { entry -> entry.photoIds.all { it in incomingIds } },
                pilePhotoCount = incoming.size,
                libraryLoaded = true
            )
        )
    }

    private fun forgetPhotos(state: SwipeState, photos: List<PhotoAsset>): SwipeState {
        val ids = photos.mapTo(HashSet()) { it.id }
        val trashPhotos = state.trashPhotos.filterNot { it.id in ids }
        return state.copy(
            keptPhotoIDs = state.keptPhotoIDs.filterNot { it in ids },
            deletedPhotoIDs = state.deletedPhotoIDs.filterNot { it in ids },
            trashPhotos = trashPhotos,
            trashPreviewIndex = clampedTrashPreview(state.trashPreviewIndex, trashPhotos.size)
        )
    }

    private fun clampedTrashPreview(index: Int?, trashCount: Int): Int? =
        if (index == null || trashCount == 0) null else index.coerceAtMost(trashCount - 1)

    private fun restoreFromTrash(id: Long) {
        if (!_state.value.deletedPhotoIDs.contains(id)) return
        _state.update {
            val trashPhotos = it.trashPhotos.filterNot { p -> p.id == id }
            it.copy(
                deletedPhotoIDs = it.deletedPhotoIDs - id,
                trashPhotos = trashPhotos,
                trashPreviewIndex = clampedTrashPreview(it.trashPreviewIndex, trashPhotos.size),
                keptPhotoIDs = (it.keptPhotoIDs - id) + id,
                history = it.history.filterNot { h -> id in h.photoIds },
                sessionDeletedIds = it.sessionDeletedIds - id,
                sessionKeptIds = if (id in it.sessionDeletedIds) it.sessionKeptIds + id else it.sessionKeptIds
            )
        }
        persistProgress()
    }

    private fun toggleShuffle(state: SwipeState): SwipeState {
        val classified = classifiedIds(state)
        val remaining = state.loadedPhotos.filterNot { it.id in classified }
        val cleared = state.copy(
            history = emptyList(),
            dragX = 0f, dragY = 0f,
            exitingDirection = 0,
            exitingPhoto = null,
            exitingStartDragX = 0f,
            exitingStartDragY = 0f,
            isDragging = false
        )
        if (!state.shuffleEnabled) {
            val topId = state.topPhoto?.id
            val unshuffledIndex = topId?.let { id -> state.loadedPhotos.indexOfFirst { it.id == id } }
                ?.takeIf { it >= 0 }
                ?: state.loadedPhotos.size
            return cleared.copy(
                shuffleEnabled = true,
                unshuffledIndex = unshuffledIndex,
                photos = remaining.shuffled(),
                activeIndex = 0
            )
        }
        val resumeId = state.loadedPhotos
            .drop(state.unshuffledIndex.coerceAtLeast(0))
            .firstOrNull { it.id !in classified }
            ?.id
        val activeIndex = resumeId?.let { id -> remaining.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: remaining.size
        return cleared.copy(
            shuffleEnabled = false,
            photos = remaining,
            activeIndex = activeIndex
        )
    }

    private fun pruneTrashedPhotos(state: SwipeState): SwipeState {
        val deletedSet = state.deletedPhotoIDs.toSet()
        val topId = state.topPhoto?.id
        val newPhotos = state.photos.filterNot { it.id in deletedSet }
        val remapped = topId?.let { id -> newPhotos.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: state.activeIndex.coerceAtMost(newPhotos.size)
        return state.copy(
            loadedPhotos = state.loadedPhotos.filterNot { it.id in deletedSet },
            photos = newPhotos,
            activeIndex = remapped,
            pilePhotoCount = (state.pilePhotoCount - (state.photos.size - newPhotos.size)).coerceAtLeast(0),
            deletedPhotoIDs = emptyList(),
            trashPhotos = emptyList(),
            deletedSheetOpen = false,
            trashInFlight = false,
            trashPendingUris = null,
            deleteError = null,
            history = emptyList()
        )
    }

    private fun undo(state: SwipeState, last: SwipeHistoryEntry): SwipeState {
        val cleared = state.copy(
            history = state.history.dropLast(1),
            exitingPhoto = null,
            exitingDirection = 0,
            exitingStartDragX = 0f,
            exitingStartDragY = 0f
        )
        val indexes = last.photoIds
            .map { id -> state.photos.indexOfFirst { it.id == id } }
            .filter { it >= 0 }
        if (indexes.isEmpty()) return cleared
        val ids = last.photoIds.toSet()
        val keptIds = last.decisions.filter { it.kept }.map { it.photoId }.toSet()
        val deletedIds = ids - keptIds
        return cleared.copy(
            keptPhotoIDs = state.keptPhotoIDs.filterNot { it in keptIds },
            deletedPhotoIDs = state.deletedPhotoIDs.filterNot { it in deletedIds },
            trashPhotos = state.trashPhotos.filterNot { it.id in deletedIds },
            sessionKeptIds = state.sessionKeptIds - keptIds,
            sessionDeletedIds = state.sessionDeletedIds - deletedIds,
            sessionFinishedAt = null,
            activeIndex = indexes.min()
        )
    }

    /** Last decision wins: the id lives in exactly one list afterwards. */
    private fun classify(state: SwipeState, photo: PhotoAsset, kept: Boolean): SwipeState {
        val keptWithout = state.keptPhotoIDs - photo.id
        val deletedWithout = state.deletedPhotoIDs - photo.id
        val trashWithout = state.trashPhotos.filterNot { it.id == photo.id }
        return state.copy(
            keptPhotoIDs = if (kept) keptWithout + photo.id else keptWithout,
            deletedPhotoIDs = if (kept) deletedWithout else deletedWithout + photo.id,
            trashPhotos = if (kept) trashWithout else trashWithout + photo,
            sessionKeptIds = if (kept) state.sessionKeptIds + photo.id else state.sessionKeptIds - photo.id,
            sessionDeletedIds = if (kept) state.sessionDeletedIds - photo.id else state.sessionDeletedIds + photo.id
        )
    }

    private fun classifiedIds(state: SwipeState): Set<Long> =
        (state.deletedPhotoIDs.asSequence() + state.keptPhotoIDs.asSequence()).toHashSet()

    private fun startSwipe(direction: Int) {
        val s = _state.value
        if (s.activeIndex >= s.photos.size) return
        val committedIndex = s.activeIndex
        val committedPhoto = s.photos[committedIndex]
        _state.update {
            classify(it, committedPhoto, direction == 1).copy(
                history = it.history + SwipeHistoryEntry(committedPhoto.id, direction == 1),
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
        afterDecisions(count = 1)
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

    private fun clearSession(state: SwipeState): SwipeState = state.copy(
        sessionDeletedIds = emptySet(),
        sessionFinishedAt = null,
        sessionKeptIds = emptySet(),
        sessionStartedAt = null
    )

    private fun resetSession(state: SwipeState): SwipeState = clearSession(state).copy(
        activeIndex = 0,
        history = emptyList(),
        deleteError = null,
        dragX = 0f,
        dragY = 0f,
        exitingDirection = 0,
        exitingPhoto = null,
        exitingStartDragX = 0f,
        exitingStartDragY = 0f,
        isDragging = false,
        similarSheetOpen = false,
        unshuffledIndex = 0
    )

    class Factory(
        private val photoRepository: PhotoRepository,
        private val settingsRepository: SettingsRepository,
        private val progressRepository: SwipeProgressRepository,
        private val statsRepository: StatsRepository,
        private val reminderScheduler: ReminderScheduler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SwipeViewModel(
                photoRepository,
                settingsRepository,
                progressRepository,
                statsRepository,
                reminderScheduler
            ) as T
    }

    companion object {
        /** Slightly longer than the overlay tween (260 ms) so the fly-off always finishes first. */
        const val EXIT_ANIMATION_DURATION_MS = 320L
        const val HASH_WINDOW = 30
        private const val LOG_TAG = "[photos]"
    }
}
