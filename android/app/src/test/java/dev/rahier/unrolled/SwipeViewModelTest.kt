package dev.rahier.unrolled

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SwipeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // MARK: - Fixtures

    private val p1 = PhotoAsset(id = 1, uri = "content://1", creationDate = null, sizeBytes = 100)
    private val p2 = PhotoAsset(id = 2, uri = "content://2", creationDate = null, sizeBytes = 200)
    private val p3 = PhotoAsset(id = 3, uri = "content://3", creationDate = null, sizeBytes = 300)
    private val p4 = PhotoAsset(id = 4, uri = "content://4", creationDate = null, sizeBytes = 400)
    private val p5 = PhotoAsset(id = 5, uri = "content://5", creationDate = null, sizeBytes = 500)
    private val fivePhotos = listOf(p1, p2, p3, p4, p5)

    private val tripSelector = PileSelector.Album("trip")
    private val allPile = Pile(selector = PileSelector.All, photoCount = 5, remainingCount = 5)
    private val tripPile = Pile(selector = tripSelector, name = "Trip", photoCount = 3, remainingCount = 3)
    private val piles = listOf(allPile, tripPile)

    private object NoopLogger : Logger {
        override fun warn(tag: String, message: String, error: Throwable?) {}
    }

    private class FakeClock(var now: Long = 1_000_000L) : () -> Long {
        override fun invoke(): Long = now
    }

    private class FakeReminderScheduler : ReminderScheduler {
        val scheduledHours = mutableListOf<Int>()
        var cancelCalls = 0
        override fun schedule(hour: Int) { scheduledHours += hour }
        override fun cancel() { cancelCalls++ }
    }

    private class InMemorySettingsRepository(
        private var current: UserSettings = UserSettings()
    ) : SettingsRepository {
        override fun load(): UserSettings = current
        override fun save(settings: UserSettings) { current = settings }
    }

    private class InMemorySwipeProgressRepository(
        var current: SwipeProgress = SwipeProgress()
    ) : SwipeProgressRepository {
        override fun load(): SwipeProgress = current
        override fun save(progress: SwipeProgress) { current = progress }
    }

    private class InMemoryStatsRepository(
        var current: ActivityStats = ActivityStats()
    ) : StatsRepository {
        override fun load(): ActivityStats = current
        override fun save(stats: ActivityStats) { current = stats }
    }

    private fun repo(photos: List<PhotoAsset> = fivePhotos, permission: Boolean = true) =
        FakePhotoRepository(initialHasPermission = permission, piles = piles, photos = photos)

    private fun vm(
        repo: PhotoRepository = repo(),
        settingsRepo: SettingsRepository = InMemorySettingsRepository(),
        progressRepo: SwipeProgressRepository = InMemorySwipeProgressRepository(),
        statsRepo: StatsRepository = InMemoryStatsRepository(),
        scheduler: ReminderScheduler = FakeReminderScheduler(),
        clock: () -> Long = FakeClock()
    ): SwipeViewModel = SwipeViewModel(repo, settingsRepo, progressRepo, statsRepo, scheduler, NoopLogger, clock)

    private fun loadedVm(
        repo: FakePhotoRepository = repo(),
        settingsRepo: SettingsRepository = InMemorySettingsRepository(),
        statsRepo: StatsRepository = InMemoryStatsRepository(),
        clock: () -> Long = FakeClock()
    ): SwipeViewModel {
        val viewModel = vm(repo, settingsRepo = settingsRepo, statsRepo = statsRepo, clock = clock)
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    private fun SwipeViewModel.swipe(intent: SwipeIntent) {
        onIntent(intent)
        dispatcher.scheduler.advanceTimeBy(SwipeViewModel.EXIT_ANIMATION_DURATION_MS + 50)
        dispatcher.scheduler.runCurrent()
    }

    // MARK: - Initial state

    @Test
    fun `initial state has sensible defaults`() = runTest(dispatcher) {
        val viewModel = vm(repo(permission = false))
        val state = viewModel.state.value
        assertEquals(0, state.activeIndex)
        assertTrue(state.photos.isEmpty())
        assertTrue(state.piles.isEmpty())
        assertEquals(PileSelector.All, state.selectedPile)
        assertEquals(PermissionStatus.NotDetermined, state.permissionStatus)
        assertFalse(state.shuffleEnabled)
        assertFalse(state.isLoading)
        assertFalse(state.loadFailed)
        assertFalse(state.libraryLoaded)
        assertNull(state.sessionStartedAt)
    }

    @Test
    fun `initial state restores persisted progress and stats`() = runTest(dispatcher) {
        val progressRepo = InMemorySwipeProgressRepository(
            SwipeProgress(deletedIDs = listOf(3), keptIDs = listOf(1))
        )
        val statsRepo = InMemoryStatsRepository(ActivityStats(bytesFreed = 42, photosReviewed = 9))
        val viewModel = vm(progressRepo = progressRepo, statsRepo = statsRepo)
        assertEquals(listOf(3L), viewModel.state.value.deletedPhotoIDs)
        assertEquals(listOf(1L), viewModel.state.value.keptPhotoIDs)
        assertEquals(42L, viewModel.state.value.stats.bytesFreed)
        assertEquals(9, viewModel.state.value.stats.photosReviewed)
    }

    @Test
    fun `initial state rolls the daily counter over to today`() = runTest(dispatcher) {
        val clock = FakeClock(now = 1_800_000_000_000L)
        val statsRepo = InMemoryStatsRepository(ActivityStats(todayCount = 12, todayKey = "2000-01-01"))
        val viewModel = vm(statsRepo = statsRepo, clock = clock)
        assertEquals(0, viewModel.state.value.stats.todayCount)
        assertEquals(ActivityTracker.dayKey(clock.now), viewModel.state.value.stats.todayKey)
    }

    // MARK: - Permission flow

    @Test
    fun `ScreenAppeared when permission granted triggers library load`() = runTest(dispatcher) {
        val repo = repo()
        val viewModel = vm(repo)

        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(PermissionStatus.Granted, state.permissionStatus)
        assertEquals(fivePhotos, state.photos)
        assertEquals(fivePhotos, state.loadedPhotos)
        assertEquals(piles, state.piles)
        assertEquals(allPile, state.currentPile)
        assertEquals(5, state.pilePhotoCount)
        assertTrue(state.libraryLoaded)
        assertFalse(state.isLoading)
        assertEquals(1, repo.loadPilesCalls)
        assertEquals(listOf<PileSelector>(PileSelector.All), repo.loadPhotosCalls)
        assertEquals(listOf(true), repo.loadPhotosIncludeVideos)
    }

    @Test
    fun `ScreenAppeared when permission not granted does not load`() = runTest(dispatcher) {
        val repo = repo(permission = false)
        val viewModel = vm(repo)

        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        assertEquals(PermissionStatus.NotDetermined, viewModel.state.value.permissionStatus)
        assertEquals(0, repo.loadPilesCalls)
        assertTrue(repo.loadPhotosCalls.isEmpty())
    }

    @Test
    fun `ScreenAppeared twice when denied does not downgrade to NotDetermined`() = runTest(dispatcher) {
        val viewModel = vm(repo(permission = false))

        viewModel.onIntent(SwipeIntent.PermissionResult(PermissionStatus.Denied))
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        assertEquals(PermissionStatus.Denied, viewModel.state.value.permissionStatus)
    }

    @Test
    fun `second ScreenAppeared refreshes silently and keeps the user on the same photo`() = runTest(dispatcher) {
        val repo = repo()
        val viewModel = loadedVm(repo)
        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)
        assertEquals(p3, viewModel.state.value.topPhoto)

        viewModel.state.test {
            skipItems(1)
            viewModel.onIntent(SwipeIntent.ScreenAppeared)
            advanceUntilIdle()
            cancelAndConsumeRemainingEvents().forEach { event ->
                val s = (event as app.cash.turbine.Event.Item).value
                assertFalse(s.isLoading)
            }
        }

        val s = viewModel.state.value
        assertEquals(2, repo.loadPhotosCalls.size)
        assertEquals(p3, s.topPhoto)
        assertEquals(2, s.activeIndex)
        assertEquals(fivePhotos, s.photos)
        assertEquals(listOf(p3, p4, p5), s.loadedPhotos)
        assertEquals(2, s.history.size)
        assertEquals(listOf(p2), s.trashPhotos)
        assertEquals(2, s.sessionReviewed)
    }

    @Test
    fun `silent refresh drops photos removed externally and appends newcomers`() = runTest(dispatcher) {
        val repo = repo()
        val viewModel = loadedVm(repo)
        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        assertEquals(p2, viewModel.state.value.topPhoto)

        val p6 = PhotoAsset(id = 6, uri = "content://6", creationDate = null)
        repo.photos = listOf(p6, p1, p3, p4, p5)
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        val s = viewModel.state.value
        assertEquals(listOf(p1, p3, p4, p5, p6), s.photos)
        assertEquals(p3, s.topPhoto)
        assertEquals(listOf(SwipeHistoryEntry(p1.id, kept = true)), s.history)
        assertEquals(5, s.pilePhotoCount)
    }

    @Test
    fun `PermissionResult Granted triggers library load`() = runTest(dispatcher) {
        val viewModel = vm(repo(permission = false))

        viewModel.onIntent(SwipeIntent.PermissionResult(PermissionStatus.Granted))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(PermissionStatus.Granted, state.permissionStatus)
        assertEquals(fivePhotos, state.photos)
        assertEquals(piles, state.piles)
    }

    @Test
    fun `PermissionResult PermanentlyDenied does not load`() = runTest(dispatcher) {
        val repo = repo(permission = false)
        val viewModel = vm(repo)

        viewModel.onIntent(SwipeIntent.PermissionResult(PermissionStatus.PermanentlyDenied))
        advanceUntilIdle()

        assertEquals(PermissionStatus.PermanentlyDenied, viewModel.state.value.permissionStatus)
        assertEquals(0, repo.loadPilesCalls)
    }

    // MARK: - Load errors

    @Test
    fun `photo load failure sets loadFailed and clears isLoading`() = runTest(dispatcher) {
        val repo = repo()
        repo.photosError = IllegalStateException("cursor closed")
        val viewModel = vm(repo)

        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.loadFailed)
        assertFalse(state.isLoading)
        assertTrue(state.photos.isEmpty())
    }

    @Test
    fun `RetryLoadTapped clears error and retries load`() = runTest(dispatcher) {
        val repo = repo()
        repo.photosError = RuntimeException("boom")
        val viewModel = vm(repo)

        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.loadFailed)

        viewModel.onIntent(SwipeIntent.RetryLoadTapped)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.loadFailed)
        assertEquals(fivePhotos, state.photos)
    }

    @Test
    fun `pile load failure surfaces as loadFailed`() = runTest(dispatcher) {
        val repo = repo()
        repo.pilesError = IllegalStateException("cursor bad")
        val viewModel = vm(repo)

        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.loadFailed)
    }

    // MARK: - Trash resolved from persisted ids

    @Test
    fun `cold start resolves trash photos from persisted ids in deletion order`() = runTest(dispatcher) {
        val repo = repo()
        val progressRepo = InMemorySwipeProgressRepository(
            SwipeProgress(deletedIDs = listOf(4, 2), keptIDs = listOf(1))
        )
        val viewModel = vm(repo, progressRepo = progressRepo)
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        val s = viewModel.state.value
        assertEquals(listOf(listOf(4L, 2L)), repo.loadPhotosByIdsCalls)
        assertEquals(listOf(p4, p2), s.trashPhotos)
        assertEquals(listOf(p3, p5), s.photos)
        assertEquals(2, s.deleted)
    }

    @Test
    fun `trash ids whose asset disappeared are pruned and persisted`() = runTest(dispatcher) {
        val progressRepo = InMemorySwipeProgressRepository(
            SwipeProgress(deletedIDs = listOf(99, 2), keptIDs = emptyList())
        )
        val viewModel = vm(repo(), progressRepo = progressRepo)
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        assertEquals(listOf(2L), viewModel.state.value.deletedPhotoIDs)
        assertEquals(listOf(p2), viewModel.state.value.trashPhotos)
        assertEquals(listOf(2L), progressRepo.current.deletedIDs)
    }

    // MARK: - Piles

    @Test
    fun `PilePicked resets session, swaps selector, keeps persisted progress`() = runTest(dispatcher) {
        val repo = repo()
        repo.setPhotosForPile(tripSelector, listOf(p1, p2))
        val viewModel = loadedVm(repo)

        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        val mid = viewModel.state.value
        assertEquals(1, mid.activeIndex)
        assertEquals(listOf(p1.id), mid.keptPhotoIDs)
        assertNotNull(mid.sessionStartedAt)

        viewModel.onIntent(SwipeIntent.PilePicked(tripSelector))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(tripSelector, state.selectedPile)
        assertEquals(tripPile, state.currentPile)
        assertEquals(0, state.activeIndex)
        assertTrue(state.history.isEmpty())
        assertFalse(state.pickerOpen)
        assertEquals(listOf(p1.id), state.keptPhotoIDs)
        assertEquals(listOf(p2), state.photos)
        assertEquals(2, state.pilePhotoCount)
        assertNull(state.sessionStartedAt)
        assertEquals(0, state.sessionReviewed)
    }

    @Test
    fun `PilesLoaded replaces the list and currentPile matches the selection`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.PilesLoaded(piles))
        assertEquals(piles, viewModel.state.value.piles)
        assertEquals(allPile, viewModel.state.value.currentPile)
        viewModel.onIntent(SwipeIntent.PilesLoaded(listOf(tripPile)))
        assertNull(viewModel.state.value.currentPile)
    }

    @Test
    fun `ViewAllPhotosTapped switches back to the whole library`() = runTest(dispatcher) {
        val repo = repo()
        repo.setPhotosForPile(tripSelector, listOf(p1, p2))
        val viewModel = loadedVm(repo)
        viewModel.onIntent(SwipeIntent.PilePicked(tripSelector))
        advanceUntilIdle()
        assertEquals(tripSelector, viewModel.state.value.selectedPile)

        viewModel.onIntent(SwipeIntent.ViewAllPhotosTapped)
        advanceUntilIdle()

        assertEquals(PileSelector.All, viewModel.state.value.selectedPile)
        assertEquals(fivePhotos, viewModel.state.value.photos)
    }

    @Test
    fun `pile ids and progress are derived from the selector`() {
        assertEquals("month:2026-09", PileSelector.Month(2026, 9).id)
        assertEquals("album:trip", tripSelector.id)
        assertEquals(0.4, Pile(PileSelector.All, photoCount = 10, remainingCount = 6).progress, 1e-9)
        assertEquals(0.0, Pile(PileSelector.All, photoCount = 0, remainingCount = 0).progress, 1e-9)
    }

    // MARK: - Drag + swipe

    @Test
    fun `drag below threshold resets`() = runTest(dispatcher) {
        val viewModel = loadedVm()

        viewModel.onIntent(SwipeIntent.CardDragChanged(x = 30f, y = 5f))
        viewModel.onIntent(SwipeIntent.CardDragEnded(thresholdPx = 100f))
        advanceUntilIdle()

        val s = viewModel.state.value
        assertEquals(0f, s.dragX)
        assertEquals(0f, s.dragY)
        assertFalse(s.isDragging)
        assertEquals(0, s.activeIndex)
    }

    @Test
    fun `drag past right threshold commits keep immediately`() = runTest(dispatcher) {
        val viewModel = loadedVm()

        viewModel.onIntent(SwipeIntent.CardDragChanged(x = 150f, y = 0f))
        viewModel.onIntent(SwipeIntent.CardDragEnded(thresholdPx = 100f))

        val mid = viewModel.state.value
        assertEquals(1, mid.exitingDirection)
        assertEquals(p1, mid.exitingPhoto)
        assertEquals(150f, mid.exitingStartDragX)
        assertEquals(1, mid.activeIndex)
        assertEquals(1, mid.kept)
        assertEquals(listOf(SwipeHistoryEntry(p1.id, kept = true)), mid.history)

        advanceTimeBy(SwipeViewModel.EXIT_ANIMATION_DURATION_MS + 50)
        runCurrent()

        val after = viewModel.state.value
        assertEquals(0, after.exitingDirection)
        assertNull(after.exitingPhoto)
        assertEquals(0f, after.exitingStartDragX)
        assertEquals(1, after.activeIndex)
        assertEquals(1, after.kept)
    }

    @Test
    fun `drag past left threshold commits delete and stages the asset`() = runTest(dispatcher) {
        val viewModel = loadedVm()

        viewModel.onIntent(SwipeIntent.CardDragChanged(x = -150f, y = 0f))
        viewModel.onIntent(SwipeIntent.CardDragEnded(thresholdPx = 100f))
        advanceUntilIdle()

        val s = viewModel.state.value
        assertEquals(1, s.activeIndex)
        assertEquals(1, s.deleted)
        assertEquals(0, s.kept)
        assertEquals(listOf(p1), s.trashPhotos)
        assertEquals(setOf(p1.id), s.sessionDeletedIds)
        assertEquals(100L, s.sessionBytesToFree)
    }

    @Test
    fun `button taps trigger the same swipe flow and persist the blob`() = runTest(dispatcher) {
        val progressRepo = InMemorySwipeProgressRepository()
        val viewModel = vm(progressRepo = progressRepo)
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        viewModel.swipe(SwipeIntent.DeleteButtonTapped)
        assertEquals(1, viewModel.state.value.deleted)
        assertEquals(SwipeProgress(deletedIDs = listOf(1), keptIDs = emptyList()), progressRepo.current)

        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        assertEquals(1, viewModel.state.value.kept)
        assertEquals(2, viewModel.state.value.activeIndex)
        assertEquals(SwipeProgress(deletedIDs = listOf(1), keptIDs = listOf(2)), progressRepo.current)
    }

    @Test
    fun `swipe past end of stack is a no-op`() = runTest(dispatcher) {
        val viewModel = loadedVm(repo(photos = listOf(p1)))

        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        assertEquals(1, viewModel.state.value.activeIndex)
        assertTrue(viewModel.state.value.done)

        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        assertEquals(1, viewModel.state.value.activeIndex)
        assertEquals(1, viewModel.state.value.kept)
    }

    @Test
    fun `re-classifying a photo never duplicates ids`() = runTest(dispatcher) {
        val viewModel = loadedVm()
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)
        viewModel.onIntent(SwipeIntent.UndoButtonTapped)
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)
        viewModel.onIntent(SwipeIntent.UndoButtonTapped)
        viewModel.swipe(SwipeIntent.KeepButtonTapped)

        val s = viewModel.state.value
        assertEquals(listOf(p1.id), s.keptPhotoIDs)
        assertTrue(s.deletedPhotoIDs.isEmpty())
        assertTrue(s.trashPhotos.isEmpty())
        assertEquals(setOf(p1.id), s.sessionKeptIds)
        assertTrue(s.sessionDeletedIds.isEmpty())
    }

    // MARK: - Session summary

    @Test
    fun `session counters track the deck on screen, not the lifetime lists`() = runTest(dispatcher) {
        val progressRepo = InMemorySwipeProgressRepository(
            SwipeProgress(deletedIDs = listOf(40, 41), keptIDs = listOf(42, 43, 44))
        )
        val viewModel = vm(repo(photos = listOf(p1, p2)), progressRepo = progressRepo)
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()
        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)

        val s = viewModel.state.value
        assertTrue(s.done)
        assertEquals(2, s.sessionReviewed)
        assertEquals(setOf(p1.id), s.sessionKeptIds)
        assertEquals(setOf(p2.id), s.sessionDeletedIds)
        assertEquals(200L, s.sessionBytesToFree)
        assertEquals(4, s.kept)
        assertEquals(1, s.deleted)
    }

    @Test
    fun `session timestamps come from the injected clock`() = runTest(dispatcher) {
        val clock = FakeClock(now = 10_000L)
        val viewModel = loadedVm(repo(photos = listOf(p1, p2, p3)), clock = clock)
        assertNull(viewModel.state.value.sessionStartedAt)

        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        assertEquals(10_000L, viewModel.state.value.sessionStartedAt)
        assertNull(viewModel.state.value.sessionFinishedAt)
        assertEquals(0L, viewModel.state.value.sessionDurationMs)

        clock.now = 40_000L
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)
        clock.now = 70_000L
        viewModel.swipe(SwipeIntent.KeepButtonTapped)

        val s = viewModel.state.value
        assertEquals(10_000L, s.sessionStartedAt)
        assertEquals(70_000L, s.sessionFinishedAt)
        assertEquals(60_000L, s.sessionDurationMs)
        assertEquals(3.0, s.sessionPhotosPerMinute, 1e-9)
    }

    @Test
    fun `undo after finishing reopens the session`() = runTest(dispatcher) {
        val clock = FakeClock(now = 10_000L)
        val viewModel = loadedVm(repo(photos = listOf(p1)), clock = clock)
        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        assertNotNull(viewModel.state.value.sessionFinishedAt)

        viewModel.onIntent(SwipeIntent.UndoButtonTapped)
        val s = viewModel.state.value
        assertNull(s.sessionFinishedAt)
        assertEquals(0, s.sessionReviewed)
        assertEquals(10_000L, s.sessionStartedAt)
    }

    @Test
    fun `speed is zero for sessions shorter than a second`() {
        val s = SwipeState(sessionStartedAt = 0L, sessionFinishedAt = 500L, sessionKeptIds = setOf(1L))
        assertEquals(0.0, s.sessionPhotosPerMinute, 1e-9)
    }

    @Test
    fun `pile with every photo already classified is done with an empty session`() = runTest(dispatcher) {
        val progressRepo = InMemorySwipeProgressRepository(
            SwipeProgress(deletedIDs = emptyList(), keptIDs = fivePhotos.map { it.id })
        )
        val viewModel = vm(progressRepo = progressRepo)
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        val s = viewModel.state.value
        assertTrue(s.done)
        assertFalse(s.isEmptyPile)
        assertEquals(0, s.sessionReviewed)
    }

    @Test
    fun `empty library shows the empty state`() = runTest(dispatcher) {
        val viewModel = loadedVm(repo(photos = emptyList()))
        val s = viewModel.state.value
        assertTrue(s.isEmptyPile)
        assertFalse(s.done)
    }

    // MARK: - Stats and goals

    @Test
    fun `every decision increments the lifetime and daily counters and persists`() = runTest(dispatcher) {
        val statsRepo = InMemoryStatsRepository()
        val viewModel = loadedVm(statsRepo = statsRepo)
        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)
        viewModel.onIntent(SwipeIntent.UndoButtonTapped)

        val stats = viewModel.state.value.stats
        assertEquals(2, stats.photosReviewed)
        assertEquals(2, stats.todayCount)
        assertEquals(stats, statsRepo.current)
    }

    @Test
    fun `reaching the daily goal celebrates once and starts the streak`() = runTest(dispatcher) {
        val settingsRepo = InMemorySettingsRepository(UserSettings(dailyGoal = 2))
        val viewModel = loadedVm(settingsRepo = settingsRepo)
        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        assertFalse(viewModel.state.value.goalCelebration)
        assertFalse(viewModel.state.value.goalReachedToday)

        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        val s = viewModel.state.value
        assertTrue(s.goalCelebration)
        assertTrue(s.goalReachedToday)
        assertEquals(1, s.stats.currentStreak)

        viewModel.onIntent(SwipeIntent.GoalCelebrationDismissed)
        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        assertFalse(viewModel.state.value.goalCelebration)
    }

    @Test
    fun `gamification off never celebrates but keeps counting`() = runTest(dispatcher) {
        val settingsRepo = InMemorySettingsRepository(UserSettings(dailyGoal = 1, gamificationEnabled = false))
        val viewModel = loadedVm(settingsRepo = settingsRepo)
        viewModel.swipe(SwipeIntent.KeepButtonTapped)

        val s = viewModel.state.value
        assertFalse(s.goalCelebration)
        assertFalse(s.goalReachedToday)
        assertEquals(0, s.stats.currentStreak)
        assertEquals(1, s.stats.photosReviewed)
    }

    @Test
    fun `turning gamification off clears pending celebrations`() = runTest(dispatcher) {
        val settingsRepo = InMemorySettingsRepository(UserSettings(dailyGoal = 1))
        val viewModel = loadedVm(settingsRepo = settingsRepo)
        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        assertTrue(viewModel.state.value.goalCelebration)

        viewModel.onIntent(SwipeIntent.SettingsGamificationToggled(false))
        assertFalse(viewModel.state.value.goalCelebration)
        assertNull(viewModel.state.value.milestoneReached)
        assertFalse(settingsRepo.load().gamificationEnabled)
    }

    @Test
    fun `milestone surfaces once and is dismissed`() = runTest(dispatcher) {
        val statsRepo = InMemoryStatsRepository(ActivityStats(photosReviewed = 99))
        val viewModel = loadedVm(statsRepo = statsRepo)
        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        assertEquals(Milestone.Reviewed100, viewModel.state.value.milestoneReached)
        assertEquals(listOf("reviewed:100"), statsRepo.current.reachedMilestones)

        viewModel.onIntent(SwipeIntent.MilestoneDismissed)
        assertNull(viewModel.state.value.milestoneReached)
        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        assertNull(viewModel.state.value.milestoneReached)
    }

    @Test
    fun `emptying the trash records bytes freed and reloads the piles`() = runTest(dispatcher) {
        val repo = repo()
        val statsRepo = InMemoryStatsRepository()
        val viewModel = loadedVm(repo, statsRepo = statsRepo)
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)
        viewModel.onIntent(SwipeIntent.EmptyTrashButtonTapped)
        viewModel.onIntent(SwipeIntent.TrashPendingConsumed)
        val pilesBefore = repo.loadPilesCalls

        viewModel.onIntent(SwipeIntent.TrashResult(success = true))
        advanceUntilIdle()

        val stats = viewModel.state.value.stats
        assertEquals(300L, stats.bytesFreed)
        assertEquals(2, stats.photosDeleted)
        assertEquals(stats, statsRepo.current)
        assertEquals(pilesBefore + 1, repo.loadPilesCalls)
    }

    @Test
    fun `StatsResetTapped wipes the counters`() = runTest(dispatcher) {
        val statsRepo = InMemoryStatsRepository(ActivityStats(bytesFreed = 10, photosReviewed = 5, currentStreak = 3))
        val viewModel = loadedVm(statsRepo = statsRepo)
        viewModel.onIntent(SwipeIntent.StatsButtonTapped)
        assertTrue(viewModel.state.value.statsOpen)

        viewModel.onIntent(SwipeIntent.StatsResetTapped)
        val stats = viewModel.state.value.stats
        assertEquals(0L, stats.bytesFreed)
        assertEquals(0, stats.photosReviewed)
        assertEquals(0, stats.currentStreak)
        assertEquals(stats, statsRepo.current)

        viewModel.onIntent(SwipeIntent.StatsDismissed)
        assertFalse(viewModel.state.value.statsOpen)
    }

    @Test
    fun `daily goal change persists`() = runTest(dispatcher) {
        val settingsRepo = InMemorySettingsRepository()
        val viewModel = vm(settingsRepo = settingsRepo)
        viewModel.onIntent(SwipeIntent.SettingsDailyGoalChanged(100))
        assertEquals(100, viewModel.state.value.settings.dailyGoal)
        assertEquals(100, settingsRepo.load().dailyGoal)
    }

    // MARK: - Reminder

    @Test
    fun `reminder toggle schedules and cancels through the scheduler`() = runTest(dispatcher) {
        val scheduler = FakeReminderScheduler()
        val settingsRepo = InMemorySettingsRepository()
        val viewModel = vm(settingsRepo = settingsRepo, scheduler = scheduler)

        viewModel.onIntent(SwipeIntent.SettingsReminderToggled(true))
        assertEquals(listOf(UserSettings.DEFAULT_REMINDER_HOUR), scheduler.scheduledHours)
        assertTrue(settingsRepo.load().reminderEnabled)

        viewModel.onIntent(SwipeIntent.SettingsReminderHourChanged(8))
        assertEquals(listOf(19, 8), scheduler.scheduledHours)
        assertEquals(8, settingsRepo.load().reminderHour)

        viewModel.onIntent(SwipeIntent.SettingsReminderToggled(false))
        assertEquals(1, scheduler.cancelCalls)
        assertFalse(settingsRepo.load().reminderEnabled)

        viewModel.onIntent(SwipeIntent.SettingsReminderHourChanged(9))
        assertEquals(listOf(19, 8), scheduler.scheduledHours)
    }

    // MARK: - Videos

    @Test
    fun `include videos toggle reloads the deck and the piles`() = runTest(dispatcher) {
        val video = PhotoAsset(id = 9, uri = "content://9", creationDate = null, isVideo = true, durationMs = 4000)
        val repo = repo(photos = fivePhotos + video)
        val settingsRepo = InMemorySettingsRepository()
        val viewModel = loadedVm(repo, settingsRepo = settingsRepo)
        assertEquals(6, viewModel.state.value.photos.size)
        viewModel.swipe(SwipeIntent.KeepButtonTapped)

        viewModel.onIntent(SwipeIntent.SettingsIncludeVideosToggled(false))
        advanceUntilIdle()

        val s = viewModel.state.value
        assertFalse(s.settings.includeVideos)
        assertFalse(settingsRepo.load().includeVideos)
        assertEquals(listOf(true, false), repo.loadPhotosIncludeVideos)
        assertEquals(listOf(p2, p3, p4, p5), s.photos)
        assertEquals(0, s.activeIndex)
        assertEquals(2, repo.loadPilesCalls)
    }

    // MARK: - Similar photos

    private fun timed(id: Long, seconds: Long, size: Long = 1_000) =
        PhotoAsset(id = id, uri = "content://$id", creationDate = Date(seconds * 1000), sizeBytes = size)

    private fun similarRepo(): FakePhotoRepository {
        val burst = listOf(timed(1, 100), timed(2, 102), timed(3, 104), timed(4, 300), timed(5, 301))
        return FakePhotoRepository(
            initialHasPermission = true,
            piles = piles,
            photos = burst,
            hashes = mapOf(1L to 0L, 2L to 1L, 3L to -1L, 4L to 8L, 5L to 8L)
        )
    }

    @Test
    fun `hashes are requested for the runs near the top and groups are confirmed`() = runTest(dispatcher) {
        val repo = similarRepo()
        val viewModel = loadedVm(repo)

        assertEquals(listOf(listOf(1L, 2L, 3L, 4L, 5L)), repo.loadHashesCalls)
        val s = viewModel.state.value
        assertEquals(listOf(listOf(1L, 2L), listOf(4L, 5L)), s.similarGroups)
        assertEquals(listOf(timed(1, 100), timed(2, 102)), s.topSimilarGroup)
    }

    @Test
    fun `detect similar off clears groups and skips hashing`() = runTest(dispatcher) {
        val repo = similarRepo()
        val viewModel = loadedVm(repo, settingsRepo = InMemorySettingsRepository(UserSettings(detectSimilar = false)))
        assertTrue(repo.loadHashesCalls.isEmpty())
        assertTrue(viewModel.state.value.similarGroups.isEmpty())
        assertTrue(viewModel.state.value.topSimilarGroup.isEmpty())

        viewModel.onIntent(SwipeIntent.SettingsDetectSimilarToggled(true))
        advanceUntilIdle()
        assertEquals(1, repo.loadHashesCalls.size)
        assertEquals(2, viewModel.state.value.similarGroups.size)
    }

    @Test
    fun `SimilarBadgeTapped only opens when the top photo has companions`() = runTest(dispatcher) {
        val viewModel = loadedVm(similarRepo())
        viewModel.onIntent(SwipeIntent.SimilarBadgeTapped)
        assertTrue(viewModel.state.value.similarSheetOpen)
        viewModel.onIntent(SwipeIntent.SimilarSheetDismissed)
        assertFalse(viewModel.state.value.similarSheetOpen)

        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        assertEquals(3L, viewModel.state.value.topPhoto?.id)
        viewModel.onIntent(SwipeIntent.SimilarBadgeTapped)
        assertFalse(viewModel.state.value.similarSheetOpen)
    }

    @Test
    fun `resolving a group classifies every member in one history entry and skips past them`() = runTest(dispatcher) {
        val statsRepo = InMemoryStatsRepository()
        val viewModel = loadedVm(similarRepo(), statsRepo = statsRepo)
        viewModel.onIntent(SwipeIntent.SimilarBadgeTapped)

        viewModel.onIntent(SwipeIntent.SimilarGroupResolved(keptIds = listOf(1L), deletedIds = listOf(2L)))

        val s = viewModel.state.value
        assertFalse(s.similarSheetOpen)
        assertEquals(2, s.activeIndex)
        assertEquals(3L, s.topPhoto?.id)
        assertEquals(listOf(1L), s.keptPhotoIDs)
        assertEquals(listOf(2L), s.deletedPhotoIDs)
        assertEquals(
            listOf(SwipeHistoryEntry(listOf(PhotoDecision(1L, true), PhotoDecision(2L, false)))),
            s.history
        )
        assertEquals(2, s.sessionReviewed)
        assertEquals(2, statsRepo.current.photosReviewed)
        assertNotNull(s.sessionStartedAt)
    }

    @Test
    fun `undo of a group entry restores every member and lands on the first one`() = runTest(dispatcher) {
        val viewModel = loadedVm(similarRepo())
        viewModel.onIntent(SwipeIntent.SimilarGroupResolved(keptIds = listOf(1L), deletedIds = listOf(2L)))
        assertEquals(2, viewModel.state.value.activeIndex)

        viewModel.onIntent(SwipeIntent.UndoButtonTapped)

        val s = viewModel.state.value
        assertEquals(0, s.activeIndex)
        assertTrue(s.keptPhotoIDs.isEmpty())
        assertTrue(s.deletedPhotoIDs.isEmpty())
        assertTrue(s.trashPhotos.isEmpty())
        assertTrue(s.history.isEmpty())
        assertEquals(0, s.sessionReviewed)
    }

    @Test
    fun `resolving a group with unknown ids only closes the sheet`() = runTest(dispatcher) {
        val viewModel = loadedVm(similarRepo())
        viewModel.onIntent(SwipeIntent.SimilarBadgeTapped)
        viewModel.onIntent(SwipeIntent.SimilarGroupResolved(keptIds = listOf(77L), deletedIds = emptyList()))
        val s = viewModel.state.value
        assertFalse(s.similarSheetOpen)
        assertEquals(0, s.activeIndex)
        assertTrue(s.history.isEmpty())
    }

    @Test
    fun `missing hashes keep a run unconfirmed`() = runTest(dispatcher) {
        val repo = similarRepo()
        repo.hashes = mapOf(1L to 0L)
        val viewModel = loadedVm(repo)
        assertTrue(viewModel.state.value.similarGroups.isEmpty())

        viewModel.onIntent(SwipeIntent.SimilarityHashesLoaded(mapOf(2L to 0L, 3L to -1L)))
        assertEquals(listOf(listOf(1L, 2L)), viewModel.state.value.similarGroups)
    }

    // MARK: - Fullscreen

    @Test
    fun `FullscreenMarkAtRelativeIndex classifies the chosen photo and skips the intermediate ones`() = runTest(dispatcher) {
        val viewModel = loadedVm()
        viewModel.onIntent(SwipeIntent.TopCardTapped)
        assertTrue(viewModel.state.value.fullscreenOpen)

        viewModel.onIntent(SwipeIntent.FullscreenMarkAtRelativeIndex(localIdx = 2, kept = false))

        val s = viewModel.state.value
        assertFalse(s.fullscreenOpen)
        assertEquals(3, s.activeIndex)
        assertEquals(listOf(p3.id), s.deletedPhotoIDs)
        assertEquals(listOf(p3), s.trashPhotos)
        assertEquals(listOf(SwipeHistoryEntry(p3.id, kept = false)), s.history)
        assertEquals(1, s.sessionReviewed)

        viewModel.onIntent(SwipeIntent.UndoButtonTapped)
        assertEquals(2, viewModel.state.value.activeIndex)
        assertTrue(viewModel.state.value.deletedPhotoIDs.isEmpty())
    }

    @Test
    fun `FullscreenMarkAtRelativeIndex out of range is a no-op`() = runTest(dispatcher) {
        val viewModel = loadedVm()
        val before = viewModel.state.value
        viewModel.onIntent(SwipeIntent.FullscreenMarkAtRelativeIndex(localIdx = 9, kept = true))
        viewModel.onIntent(SwipeIntent.FullscreenMarkAtRelativeIndex(localIdx = -1, kept = true))
        assertEquals(before, viewModel.state.value)
    }

    // MARK: - Deleted review sheet

    @Test
    fun `DeletedSheetButtonTapped opens sheet`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.DeletedSheetButtonTapped)
        assertTrue(viewModel.state.value.deletedSheetOpen)
    }

    @Test
    fun `DeletedPhotoRestoreTapped moves photo back to kept bucket and forgets its history`() = runTest(dispatcher) {
        val viewModel = loadedVm()
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)

        val mid = viewModel.state.value
        assertEquals(listOf(p1.id, p2.id), mid.deletedPhotoIDs)
        assertEquals(listOf(p1, p2), mid.trashPhotos)

        viewModel.onIntent(SwipeIntent.DeletedPhotoRestoreTapped(p1.id))

        val s = viewModel.state.value
        assertEquals(listOf(p2.id), s.deletedPhotoIDs)
        assertEquals(listOf(p2), s.trashPhotos)
        assertEquals(listOf(p1.id), s.keptPhotoIDs)
        assertEquals(listOf(SwipeHistoryEntry(p2.id, kept = false)), s.history)
        assertEquals(setOf(p1.id), s.sessionKeptIds)
        assertEquals(setOf(p2.id), s.sessionDeletedIds)
    }

    @Test
    fun `undo after restore does not corrupt the lists`() = runTest(dispatcher) {
        val viewModel = loadedVm()
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)
        viewModel.onIntent(SwipeIntent.DeletedPhotoRestoreTapped(p1.id))
        viewModel.onIntent(SwipeIntent.UndoButtonTapped)

        val s = viewModel.state.value
        assertEquals(listOf(p1.id), s.keptPhotoIDs)
        assertTrue(s.deletedPhotoIDs.isEmpty())
        assertEquals(1, s.activeIndex)
        assertTrue(s.history.isEmpty())
    }

    @Test
    fun `TrashPhotoTapped opens the preview at that photo and dismiss closes it`() = runTest(dispatcher) {
        val viewModel = loadedVm()
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)

        viewModel.onIntent(SwipeIntent.TrashPhotoTapped(p2.id))
        assertEquals(1, viewModel.state.value.trashPreviewIndex)

        viewModel.onIntent(SwipeIntent.TrashPreviewDismissed)
        assertNull(viewModel.state.value.trashPreviewIndex)
    }

    @Test
    fun `TrashPhotoTapped with an unknown id is a no-op`() = runTest(dispatcher) {
        val viewModel = loadedVm()
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)
        viewModel.onIntent(SwipeIntent.TrashPhotoTapped(999L))
        assertNull(viewModel.state.value.trashPreviewIndex)
    }

    @Test
    fun `TrashPreviewKeepTapped restores the photo and closes the preview`() = runTest(dispatcher) {
        val viewModel = loadedVm()
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)
        viewModel.onIntent(SwipeIntent.TrashPhotoTapped(p1.id))

        viewModel.onIntent(SwipeIntent.TrashPreviewKeepTapped(1))

        val s = viewModel.state.value
        assertNull(s.trashPreviewIndex)
        assertEquals(listOf(p1.id), s.deletedPhotoIDs)
        assertEquals(listOf(p1), s.trashPhotos)
        assertEquals(listOf(p2.id), s.keptPhotoIDs)
        assertEquals(setOf(p2.id), s.sessionKeptIds)
    }

    @Test
    fun `TrashPreviewKeepTapped out of range is a no-op`() = runTest(dispatcher) {
        val viewModel = loadedVm()
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)
        viewModel.onIntent(SwipeIntent.TrashPhotoTapped(p1.id))

        viewModel.onIntent(SwipeIntent.TrashPreviewKeepTapped(1))
        viewModel.onIntent(SwipeIntent.TrashPreviewKeepTapped(-1))

        val s = viewModel.state.value
        assertEquals(0, s.trashPreviewIndex)
        assertEquals(listOf(p1.id), s.deletedPhotoIDs)
    }

    @Test
    fun `DeletedSheetDismissed drops the preview`() = runTest(dispatcher) {
        val viewModel = loadedVm()
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)
        viewModel.onIntent(SwipeIntent.DeletedSheetButtonTapped)
        viewModel.onIntent(SwipeIntent.TrashPhotoTapped(p1.id))

        viewModel.onIntent(SwipeIntent.DeletedSheetDismissed)

        assertNull(viewModel.state.value.trashPreviewIndex)
    }

    @Test
    fun `EmptyTrashButtonTapped with non-empty list kicks off delete with the trash uris`() = runTest(dispatcher) {
        val viewModel = loadedVm()
        viewModel.onIntent(SwipeIntent.DeleteButtonTapped)

        viewModel.onIntent(SwipeIntent.EmptyTrashButtonTapped)

        val s = viewModel.state.value
        assertTrue(s.trashInFlight)
        assertEquals(listOf(p1.uri), s.trashPendingUris)
    }

    @Test
    fun `EmptyTrashButtonTapped is ignored while in flight`() = runTest(dispatcher) {
        val viewModel = loadedVm()
        viewModel.onIntent(SwipeIntent.DeleteButtonTapped)
        viewModel.onIntent(SwipeIntent.EmptyTrashButtonTapped)
        viewModel.onIntent(SwipeIntent.TrashPendingConsumed)

        viewModel.onIntent(SwipeIntent.EmptyTrashButtonTapped)

        assertNull(viewModel.state.value.trashPendingUris)
        assertTrue(viewModel.state.value.trashInFlight)
    }

    @Test
    fun `EmptyTrashButtonTapped with empty list is no-op`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.EmptyTrashButtonTapped)
        val s = viewModel.state.value
        assertFalse(s.trashInFlight)
        assertNull(s.trashPendingUris)
    }

    @Test
    fun `TrashResult success clears trash and history, keeps kept list`() = runTest(dispatcher) {
        val viewModel = loadedVm()
        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)

        viewModel.onIntent(SwipeIntent.DeletedSheetButtonTapped)
        viewModel.onIntent(SwipeIntent.EmptyTrashButtonTapped)
        viewModel.onIntent(SwipeIntent.TrashPendingConsumed)

        viewModel.onIntent(SwipeIntent.TrashResult(success = true))
        advanceUntilIdle()

        val s = viewModel.state.value
        assertTrue(s.deletedPhotoIDs.isEmpty())
        assertTrue(s.trashPhotos.isEmpty())
        assertFalse(s.deletedSheetOpen)
        assertFalse(s.trashInFlight)
        assertNull(s.trashPendingUris)
        assertNull(s.deleteError)
        assertEquals(listOf(p1.id), s.keptPhotoIDs)
        assertTrue(s.history.isEmpty())
        assertEquals(listOf(p1, p3, p4, p5), s.photos)
        assertEquals(listOf(p1, p3, p4, p5), s.loadedPhotos)
        assertEquals(1, s.activeIndex)
        assertEquals(4, s.pilePhotoCount)
    }

    @Test
    fun `TrashResult failure surfaces error and keeps list`() = runTest(dispatcher) {
        val viewModel = loadedVm()
        viewModel.onIntent(SwipeIntent.DeleteButtonTapped)
        viewModel.onIntent(SwipeIntent.DeletedSheetButtonTapped)
        viewModel.onIntent(SwipeIntent.EmptyTrashButtonTapped)

        viewModel.onIntent(SwipeIntent.TrashResult(success = false, message = "oops"))

        val s = viewModel.state.value
        assertEquals(listOf(p1.id), s.deletedPhotoIDs)
        assertTrue(s.deletedSheetOpen)
        assertFalse(s.trashInFlight)
        assertEquals("oops", s.deleteError)
    }

    @Test
    fun `TrashResult cancelled by the user leaves no error`() = runTest(dispatcher) {
        val viewModel = loadedVm()
        viewModel.onIntent(SwipeIntent.DeleteButtonTapped)
        viewModel.onIntent(SwipeIntent.EmptyTrashButtonTapped)

        viewModel.onIntent(SwipeIntent.TrashResult(success = false))

        val s = viewModel.state.value
        assertNull(s.deleteError)
        assertFalse(s.trashInFlight)
        assertEquals(listOf(p1.id), s.deletedPhotoIDs)
    }

    // MARK: - Undo

    @Test
    fun `undo after delete also removes id from deletedPhotoIDs`() = runTest(dispatcher) {
        val viewModel = loadedVm()
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)

        assertEquals(listOf(p1.id), viewModel.state.value.deletedPhotoIDs)
        viewModel.onIntent(SwipeIntent.UndoButtonTapped)

        val s = viewModel.state.value
        assertTrue(s.deletedPhotoIDs.isEmpty())
        assertTrue(s.trashPhotos.isEmpty())
        assertEquals(0, s.deleted)
        assertEquals(0, s.activeIndex)
    }

    @Test
    fun `undo reverts last swipe and restores index`() = runTest(dispatcher) {
        val viewModel = loadedVm()
        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)
        assertEquals(2, viewModel.state.value.activeIndex)

        viewModel.onIntent(SwipeIntent.UndoButtonTapped)

        val s = viewModel.state.value
        assertEquals(1, s.activeIndex)
        assertEquals(1, s.kept)
        assertEquals(0, s.deleted)
        assertEquals(listOf(SwipeHistoryEntry(p1.id, kept = true)), s.history)
    }

    @Test
    fun `undo with empty history is a no-op`() = runTest(dispatcher) {
        val viewModel = vm()
        val before = viewModel.state.value
        viewModel.onIntent(SwipeIntent.UndoButtonTapped)
        assertEquals(before, viewModel.state.value)
    }

    @Test
    fun `undo past empty history stays clamped at zero`() = runTest(dispatcher) {
        val viewModel = loadedVm()
        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        viewModel.onIntent(SwipeIntent.UndoButtonTapped)
        viewModel.onIntent(SwipeIntent.UndoButtonTapped)

        val s = viewModel.state.value
        assertEquals(0, s.kept)
        assertTrue(s.history.isEmpty())
    }

    // MARK: - Start over vs reset progress

    @Test
    fun `ResetButtonTapped clears the pile markers and refills the deck`() = runTest(dispatcher) {
        val elsewhere = PhotoAsset(id = 77, uri = "content://77", creationDate = null)
        val repo = repo()
        repo.setPhotosForPile(tripSelector, listOf(elsewhere))
        val progressRepo = InMemorySwipeProgressRepository(
            SwipeProgress(deletedIDs = listOf(elsewhere.id), keptIDs = listOf(p3.id))
        )
        val viewModel = vm(repo, progressRepo = progressRepo)
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()
        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)

        viewModel.onIntent(SwipeIntent.ResetButtonTapped)
        advanceUntilIdle()

        val s = viewModel.state.value
        assertEquals(0, s.activeIndex)
        assertTrue(s.history.isEmpty())
        assertEquals(fivePhotos, s.photos)
        assertTrue(s.keptPhotoIDs.isEmpty())
        assertEquals(listOf(elsewhere.id), s.deletedPhotoIDs)
        assertEquals(listOf(elsewhere), s.trashPhotos)
        assertEquals(0, s.sessionReviewed)
        assertEquals(SwipeProgress(deletedIDs = listOf(elsewhere.id), keptIDs = emptyList()), progressRepo.current)
    }

    @Test
    fun `ResetProgressTapped wipes persisted lists and refetches`() = runTest(dispatcher) {
        val progressRepo = InMemorySwipeProgressRepository()
        val viewModel = vm(progressRepo = progressRepo)
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()
        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)
        assertTrue(viewModel.state.value.kept > 0 || viewModel.state.value.deleted > 0)

        viewModel.onIntent(SwipeIntent.ResetProgressTapped)
        advanceUntilIdle()

        val s = viewModel.state.value
        assertTrue(s.keptPhotoIDs.isEmpty())
        assertTrue(s.deletedPhotoIDs.isEmpty())
        assertTrue(s.trashPhotos.isEmpty())
        assertFalse(s.deletedSheetOpen)
        assertEquals(0, s.activeIndex)
        assertTrue(s.history.isEmpty())
        assertEquals(SwipeProgress(), progressRepo.current)
        assertEquals(fivePhotos, s.photos)
    }

    // MARK: - Shuffle

    @Test
    fun `shuffle enable with single photo preserves list and saves index`() = runTest(dispatcher) {
        val viewModel = loadedVm(repo(photos = listOf(p1)))

        viewModel.onIntent(SwipeIntent.ShuffleButtonTapped)

        val s = viewModel.state.value
        assertTrue(s.shuffleEnabled)
        assertEquals(listOf(p1), s.photos)
        assertEquals(0, s.unshuffledIndex)
        assertEquals(0, s.activeIndex)
    }

    @Test
    fun `shuffle disable restores unshuffled order and resumes position`() = runTest(dispatcher) {
        val viewModel = loadedVm()
        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)
        assertEquals(2, viewModel.state.value.activeIndex)

        viewModel.onIntent(SwipeIntent.ShuffleButtonTapped)
        assertEquals(2, viewModel.state.value.unshuffledIndex)
        assertEquals(0, viewModel.state.value.activeIndex)
        assertEquals(setOf(p3, p4, p5), viewModel.state.value.photos.toSet())

        viewModel.onIntent(SwipeIntent.ShuffleButtonTapped)

        val s = viewModel.state.value
        assertFalse(s.shuffleEnabled)
        assertEquals(listOf(p3, p4, p5), s.photos)
        assertEquals(0, s.activeIndex)
        assertEquals(p3, s.topPhoto)
    }

    @Test
    fun `shuffle disable skips photos classified while shuffled`() = runTest(dispatcher) {
        val viewModel = loadedVm()
        viewModel.onIntent(SwipeIntent.ShuffleButtonTapped)
        val shuffledTop = viewModel.state.value.topPhoto!!
        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        viewModel.swipe(SwipeIntent.DeleteButtonTapped)
        val classified = viewModel.state.value.keptPhotoIDs + viewModel.state.value.deletedPhotoIDs
        assertTrue(shuffledTop.id in classified)

        viewModel.onIntent(SwipeIntent.ShuffleButtonTapped)

        val s = viewModel.state.value
        assertEquals(3, s.photos.size)
        assertTrue(s.photos.none { it.id in classified })
        assertEquals(fivePhotos.first { it.id !in classified }, s.topPhoto)
    }

    @Test
    fun `shuffle disable lands on the first unclassified photo after the saved position`() = runTest(dispatcher) {
        val viewModel = loadedVm()
        viewModel.swipe(SwipeIntent.KeepButtonTapped)
        viewModel.onIntent(SwipeIntent.ShuffleButtonTapped)
        viewModel.onIntent(SwipeIntent.ShuffleButtonTapped)
        assertEquals(p2, viewModel.state.value.topPhoto)
    }

    @Test
    fun `photosLoaded while shuffle enabled shuffles the incoming list`() = runTest(dispatcher) {
        val viewModel = loadedVm()
        viewModel.onIntent(SwipeIntent.ShuffleButtonTapped)
        assertTrue(viewModel.state.value.shuffleEnabled)

        viewModel.onIntent(SwipeIntent.PhotosLoaded(listOf(p1)))

        val s = viewModel.state.value
        assertEquals(listOf(p1), s.loadedPhotos)
        assertEquals(listOf(p1), s.photos)
    }

    // MARK: - Settings

    @Test
    fun `SettingsShowLocationToggled updates state and persists`() = runTest(dispatcher) {
        val settingsRepo = InMemorySettingsRepository()
        val viewModel = vm(settingsRepo = settingsRepo)

        viewModel.onIntent(SwipeIntent.SettingsShowLocationToggled(false))

        assertFalse(viewModel.state.value.settings.showLocation)
        assertFalse(settingsRepo.load().showLocation)
    }

    // MARK: - Cancellation

    @Test
    fun `ResetButtonTapped cancels pending swipe animation`() = runTest(dispatcher) {
        val viewModel = loadedVm()

        viewModel.onIntent(SwipeIntent.KeepButtonTapped)
        assertEquals(1, viewModel.state.value.exitingDirection)

        viewModel.onIntent(SwipeIntent.ResetButtonTapped)
        advanceTimeBy(SwipeViewModel.EXIT_ANIMATION_DURATION_MS * 5)
        runCurrent()

        val s = viewModel.state.value
        assertEquals(0, s.activeIndex)
        assertEquals(0, s.exitingDirection)
        assertNull(s.exitingPhoto)
    }

    @Test
    fun `PilePicked cancels pending swipe animation`() = runTest(dispatcher) {
        val viewModel = loadedVm()

        viewModel.onIntent(SwipeIntent.KeepButtonTapped)
        viewModel.onIntent(SwipeIntent.PilePicked(PileSelector.All))
        advanceUntilIdle()

        val s = viewModel.state.value
        assertEquals(0, s.activeIndex)
        assertEquals(listOf(p1.id), s.keptPhotoIDs)
    }

    @Test
    fun `concurrent pile picks cancel the earlier load`() = runTest(dispatcher) {
        val repo = repo()
        repo.setPhotosForPile(tripSelector, listOf(p1, p2))
        val viewModel = loadedVm(repo)

        viewModel.onIntent(SwipeIntent.PilePicked(tripSelector))
        viewModel.onIntent(SwipeIntent.PilePicked(PileSelector.All))
        advanceUntilIdle()

        val s = viewModel.state.value
        assertEquals(PileSelector.All, s.selectedPile)
        assertEquals(fivePhotos, s.photos)
    }

    // MARK: - Presentation flags

    @Test
    fun `PileTitleTapped opens picker and PilePickerDismissed closes it`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.PileTitleTapped)
        assertTrue(viewModel.state.value.pickerOpen)
        viewModel.onIntent(SwipeIntent.PilePickerDismissed)
        assertFalse(viewModel.state.value.pickerOpen)
    }

    @Test
    fun `SettingsButtonTapped opens settings and SettingsDismissed closes it`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.SettingsButtonTapped)
        assertTrue(viewModel.state.value.settingsOpen)
        viewModel.onIntent(SwipeIntent.SettingsDismissed)
        assertFalse(viewModel.state.value.settingsOpen)
    }

    @Test
    fun `AboutMenuItemTapped opens about and AboutDismissed closes it`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.AboutMenuItemTapped)
        assertTrue(viewModel.state.value.aboutOpen)
        viewModel.onIntent(SwipeIntent.AboutDismissed)
        assertFalse(viewModel.state.value.aboutOpen)
    }

    @Test
    fun `DeletedSheetDismissed closes the sheet and clears the error`() = runTest(dispatcher) {
        val viewModel = loadedVm()
        viewModel.onIntent(SwipeIntent.DeleteButtonTapped)
        viewModel.onIntent(SwipeIntent.DeletedSheetButtonTapped)
        viewModel.onIntent(SwipeIntent.EmptyTrashButtonTapped)
        viewModel.onIntent(SwipeIntent.TrashResult(success = false, message = "oops"))

        viewModel.onIntent(SwipeIntent.DeletedSheetDismissed)

        assertFalse(viewModel.state.value.deletedSheetOpen)
        assertNull(viewModel.state.value.deleteError)
    }

    @Test
    fun `TopCardTapped clears drag and opens fullscreen`() = runTest(dispatcher) {
        val viewModel = loadedVm()

        viewModel.onIntent(SwipeIntent.CardDragChanged(x = 3f, y = 2f))
        viewModel.onIntent(SwipeIntent.TopCardTapped)

        val s = viewModel.state.value
        assertEquals(0f, s.dragX)
        assertEquals(0f, s.dragY)
        assertFalse(s.isDragging)
        assertTrue(s.fullscreenOpen)

        viewModel.onIntent(SwipeIntent.FullscreenDismissed)
        assertFalse(viewModel.state.value.fullscreenOpen)
    }

    // MARK: - Computed properties

    @Test
    fun `currentPile is null while piles are empty`() {
        assertNull(SwipeState().currentPile)
    }

    @Test
    fun `topPhoto returns photo at activeIndex`() {
        val s = SwipeState(activeIndex = 2, photos = fivePhotos)
        assertEquals(p3, s.topPhoto)
    }

    @Test
    fun `topPhoto is null past end of stack`() {
        val s = SwipeState(activeIndex = 99, photos = fivePhotos)
        assertNull(s.topPhoto)
    }

    @Test
    fun `remainingInDeck never goes negative`() {
        assertEquals(3, SwipeState(activeIndex = 2, photos = fivePhotos).remainingInDeck)
        assertEquals(0, SwipeState(activeIndex = 9, photos = fivePhotos).remainingInDeck)
    }

    @Test
    fun `done flag depends on the pile photo count`() {
        assertFalse(SwipeState().done)
        assertTrue(SwipeState(activeIndex = 5, photos = fivePhotos, pilePhotoCount = 5).done)
        assertFalse(SwipeState(activeIndex = 2, photos = fivePhotos, pilePhotoCount = 5).done)
        assertTrue(SwipeState(activeIndex = 0, photos = emptyList(), pilePhotoCount = 5).done)
    }

    @Test
    fun `history entries expose their photo ids`() {
        val entry = SwipeHistoryEntry(listOf(PhotoDecision(4, true), PhotoDecision(9, false)))
        assertEquals(listOf(4L, 9L), entry.photoIds)
        assertEquals(entry.decisions.first(), SwipeHistoryEntry(4, true).decisions.single())
    }

    // MARK: - Turbine end-to-end

    @Test
    fun `full user flow emits expected state sequence`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.state.test {
            val initial = awaitItem()
            assertEquals(0, initial.activeIndex)
            assertTrue(initial.photos.isEmpty())

            viewModel.onIntent(SwipeIntent.ScreenAppeared)
            var s = awaitItem()
            while (s.photos.isEmpty() || s.piles.isEmpty()) s = awaitItem()
            assertEquals(PermissionStatus.Granted, s.permissionStatus)
            assertEquals(fivePhotos, s.photos)

            viewModel.onIntent(SwipeIntent.KeepButtonTapped)
            advanceTimeBy(SwipeViewModel.EXIT_ANIMATION_DURATION_MS + 50)
            runCurrent()
            while (awaitItem().activeIndex != 1) { /* keep draining */ }

            cancelAndIgnoreRemainingEvents()
        }
    }
}
