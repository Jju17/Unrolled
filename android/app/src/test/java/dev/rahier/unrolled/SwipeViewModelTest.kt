package dev.rahier.unrolled

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

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

    private val p1 = PhotoAsset(id = 1, uri = "content://1", creationDate = null)
    private val p2 = PhotoAsset(id = 2, uri = "content://2", creationDate = null)
    private val p3 = PhotoAsset(id = 3, uri = "content://3", creationDate = null)
    private val p4 = PhotoAsset(id = 4, uri = "content://4", creationDate = null)
    private val p5 = PhotoAsset(id = 5, uri = "content://5", creationDate = null)
    private val fivePhotos = listOf(p1, p2, p3, p4, p5)

    private val allAlbum = Album(id = Album.ALL_PHOTOS_ID, name = "All Photos", count = 5, icon = "🕐")
    private val tripAlbum = Album(id = "trip", name = "Trip", count = 3, icon = "📁")
    private val albums = listOf(allAlbum, tripAlbum)

    private fun vm(
        repo: PhotoRepository = FakePhotoRepository(
            initialHasPermission = true,
            albums = albums,
            photos = fivePhotos
        ),
        settingsRepo: SettingsRepository = InMemorySettingsRepository()
    ): SwipeViewModel = SwipeViewModel(repo, settingsRepo)

    private class InMemorySettingsRepository(
        private var current: UserSettings = UserSettings()
    ) : SettingsRepository {
        override fun load(): UserSettings = current
        override fun save(settings: UserSettings) { current = settings }
    }

    // MARK: - Initial state

    @Test
    fun `initial state has sensible defaults`() = runTest(dispatcher) {
        val viewModel = vm(FakePhotoRepository(initialHasPermission = false))
        val state = viewModel.state.value
        assertEquals(0, state.activeIndex)
        assertTrue(state.photos.isEmpty())
        assertTrue(state.albums.isEmpty())
        assertEquals(PermissionStatus.NotDetermined, state.permissionStatus)
        assertFalse(state.shuffleEnabled)
        assertFalse(state.isLoading)
        assertNull(state.loadError)
    }

    // MARK: - Permission flow

    @Test
    fun `ScreenAppeared when permission granted triggers library load`() = runTest(dispatcher) {
        val repo = FakePhotoRepository(initialHasPermission = true, albums = albums, photos = fivePhotos)
        val viewModel = vm(repo)

        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(PermissionStatus.Granted, state.permissionStatus)
        assertEquals(fivePhotos, state.photos)
        assertEquals(fivePhotos, state.loadedPhotos)
        assertEquals(albums, state.albums)
        assertEquals(Album.ALL_PHOTOS_ID, state.selectedAlbumID)
        assertFalse(state.isLoading)
        assertEquals(1, repo.loadAlbumsCalls)
        assertEquals(listOf<String?>(null), repo.loadPhotosCalls)
    }

    @Test
    fun `ScreenAppeared when permission not granted does not load`() = runTest(dispatcher) {
        val repo = FakePhotoRepository(initialHasPermission = false)
        val viewModel = vm(repo)

        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        assertEquals(PermissionStatus.NotDetermined, viewModel.state.value.permissionStatus)
        assertEquals(0, repo.loadAlbumsCalls)
        assertTrue(repo.loadPhotosCalls.isEmpty())
    }

    @Test
    fun `ScreenAppeared twice when denied does not downgrade to NotDetermined`() = runTest(dispatcher) {
        val repo = FakePhotoRepository(initialHasPermission = false)
        val viewModel = vm(repo)

        // User taps "Allow", system dialog dismisses with Denied.
        viewModel.onIntent(SwipeIntent.PermissionResult(PermissionStatus.Denied))
        // App backgrounds + resumes → ScreenAppeared fires again.
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        // Status should stay Denied, not regress to NotDetermined.
        assertEquals(PermissionStatus.Denied, viewModel.state.value.permissionStatus)
    }

    @Test
    fun `PermissionResult Granted triggers library load`() = runTest(dispatcher) {
        val repo = FakePhotoRepository(initialHasPermission = false, albums = albums, photos = fivePhotos)
        val viewModel = vm(repo)

        viewModel.onIntent(SwipeIntent.PermissionResult(PermissionStatus.Granted))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(PermissionStatus.Granted, state.permissionStatus)
        assertEquals(fivePhotos, state.photos)
        assertEquals(albums, state.albums)
    }

    @Test
    fun `PermissionResult PermanentlyDenied does not load`() = runTest(dispatcher) {
        val repo = FakePhotoRepository(initialHasPermission = false)
        val viewModel = vm(repo)

        viewModel.onIntent(SwipeIntent.PermissionResult(PermissionStatus.PermanentlyDenied))
        advanceUntilIdle()

        assertEquals(PermissionStatus.PermanentlyDenied, viewModel.state.value.permissionStatus)
        assertEquals(0, repo.loadAlbumsCalls)
    }

    // MARK: - Load errors

    @Test
    fun `photo load failure sets loadError and clears isLoading`() = runTest(dispatcher) {
        val repo = FakePhotoRepository(initialHasPermission = true, albums = albums, photos = fivePhotos)
        repo.photosError = IllegalStateException("cursor closed")
        val viewModel = vm(repo)

        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("cursor closed", state.loadError)
        assertFalse(state.isLoading)
        assertTrue(state.photos.isEmpty())
    }

    @Test
    fun `RetryLoadTapped clears error and retries load`() = runTest(dispatcher) {
        val repo = FakePhotoRepository(initialHasPermission = true, albums = albums, photos = fivePhotos)
        repo.photosError = RuntimeException("boom")
        val viewModel = vm(repo)

        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.loadError)

        // Second attempt succeeds (error is single-shot).
        viewModel.onIntent(SwipeIntent.RetryLoadTapped)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.loadError)
        assertEquals(fivePhotos, state.photos)
    }

    @Test
    fun `album load failure surfaces as loadError`() = runTest(dispatcher) {
        val repo = FakePhotoRepository(initialHasPermission = true, albums = albums, photos = fivePhotos)
        repo.albumsError = IllegalStateException("albums cursor bad")
        val viewModel = vm(repo)

        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        // Even though photos load succeeded, the albums failure is recorded.
        val state = viewModel.state.value
        assertEquals("albums cursor bad", state.loadError)
    }

    // MARK: - Album picker

    @Test
    fun `AlbumPicked resets progress, swaps albumId, reloads photos`() = runTest(dispatcher) {
        val repo = FakePhotoRepository(initialHasPermission = true, albums = albums, photos = fivePhotos)
        repo.setPhotosForAlbum("trip", listOf(p1, p2))
        val viewModel = vm(repo)

        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        // Simulate some progress before picking a different album.
        viewModel.onIntent(SwipeIntent.KeepButtonTapped)
        advanceTimeBy(400)   // past animation
        runCurrent()
        val mid = viewModel.state.value
        assertEquals(1, mid.activeIndex)
        assertEquals(1, mid.kept)

        viewModel.onIntent(SwipeIntent.AlbumPicked("trip"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("trip", state.selectedAlbumID)
        assertEquals(0, state.activeIndex)
        assertEquals(0, state.kept)
        assertEquals(0, state.deleted)
        assertTrue(state.history.isEmpty())
        assertEquals(listOf(p1, p2), state.photos)
        assertFalse(state.pickerOpen)
    }

    @Test
    fun `AlbumsLoaded adopts first album when none selected`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.AlbumsLoaded(albums))
        assertEquals(Album.ALL_PHOTOS_ID, viewModel.state.value.selectedAlbumID)
    }

    @Test
    fun `AlbumsLoaded preserves existing selection`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.PermissionResult(PermissionStatus.Granted))
        advanceUntilIdle()
        viewModel.onIntent(SwipeIntent.AlbumPicked("trip"))
        advanceUntilIdle()

        // Simulate a re-fetch of albums (e.g. user added a bucket externally).
        viewModel.onIntent(SwipeIntent.AlbumsLoaded(albums))
        assertEquals("trip", viewModel.state.value.selectedAlbumID)
    }

    @Test
    fun `ViewAllPhotosTapped switches to first album`() = runTest(dispatcher) {
        val repo = FakePhotoRepository(initialHasPermission = true, albums = albums, photos = fivePhotos)
        repo.setPhotosForAlbum("trip", listOf(p1, p2))
        val viewModel = vm(repo)

        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()
        viewModel.onIntent(SwipeIntent.AlbumPicked("trip"))
        advanceUntilIdle()
        assertEquals("trip", viewModel.state.value.selectedAlbumID)

        viewModel.onIntent(SwipeIntent.ViewAllPhotosTapped)
        advanceUntilIdle()

        assertEquals(Album.ALL_PHOTOS_ID, viewModel.state.value.selectedAlbumID)
        assertEquals(fivePhotos, viewModel.state.value.photos)
    }

    @Test
    fun `ViewAllPhotosTapped with empty albums is a no-op`() = runTest(dispatcher) {
        val viewModel = vm(FakePhotoRepository(initialHasPermission = true, albums = emptyList()))
        viewModel.onIntent(SwipeIntent.ViewAllPhotosTapped)
        advanceUntilIdle()
        // Nothing exploded, no photos loaded.
        assertTrue(viewModel.state.value.photos.isEmpty())
    }

    // MARK: - Drag + swipe

    @Test
    fun `drag below threshold resets`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        viewModel.onIntent(SwipeIntent.CardDragChanged(x = 30f, y = 5f))
        viewModel.onIntent(SwipeIntent.CardDragEnded(thresholdPx = 100f))
        advanceUntilIdle()

        val s = viewModel.state.value
        assertEquals(0f, s.dragX)
        assertEquals(0f, s.dragY)
        assertFalse(s.isDragging)
        assertEquals(0, s.activeIndex)  // no swipe committed
    }

    @Test
    fun `drag past right threshold commits keep immediately`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        viewModel.onIntent(SwipeIntent.CardDragChanged(x = 150f, y = 0f))
        viewModel.onIntent(SwipeIntent.CardDragEnded(thresholdPx = 100f))

        // Commit is synchronous now — index bump + tally + history all
        // happen in `startSwipe`. `exitingPhoto` tracks the card that
        // is animating off in the overlay.
        val mid = viewModel.state.value
        assertEquals(1, mid.exitingDirection)
        assertEquals(p1, mid.exitingPhoto)
        assertEquals(150f, mid.exitingStartDragX)
        assertEquals(1, mid.activeIndex)
        assertEquals(1, mid.kept)
        assertEquals(listOf(SwipeHistoryEntry(0, kept = true)), mid.history)

        // After the animation window the visual overlay clears.
        advanceTimeBy(SwipeViewModel.EXIT_ANIMATION_DURATION_MS + 50)
        runCurrent()

        val after = viewModel.state.value
        assertEquals(0, after.exitingDirection)
        assertNull(after.exitingPhoto)
        assertEquals(0f, after.exitingStartDragX)
        // Commit fields are unchanged by the completion handler.
        assertEquals(1, after.activeIndex)
        assertEquals(1, after.kept)
    }

    @Test
    fun `drag past left threshold commits delete`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        viewModel.onIntent(SwipeIntent.CardDragChanged(x = -150f, y = 0f))
        viewModel.onIntent(SwipeIntent.CardDragEnded(thresholdPx = 100f))
        advanceUntilIdle()

        val s = viewModel.state.value
        assertEquals(1, s.activeIndex)
        assertEquals(1, s.deleted)
        assertEquals(0, s.kept)
    }

    @Test
    fun `button taps trigger the same swipe flow`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        viewModel.onIntent(SwipeIntent.DeleteButtonTapped)
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.deleted)

        viewModel.onIntent(SwipeIntent.KeepButtonTapped)
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.kept)
        assertEquals(2, viewModel.state.value.activeIndex)
    }

    @Test
    fun `swipe past end of stack is a no-op`() = runTest(dispatcher) {
        val viewModel = vm(FakePhotoRepository(initialHasPermission = true, albums = emptyList(), photos = listOf(p1)))
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        // Swipe once — end of list.
        viewModel.onIntent(SwipeIntent.KeepButtonTapped)
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.activeIndex)

        // Second attempt should be guarded.
        viewModel.onIntent(SwipeIntent.KeepButtonTapped)
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.activeIndex)
        assertEquals(1, viewModel.state.value.kept)  // unchanged
    }

    // MARK: - Deleted review sheet

    @Test
    fun `DeletedSheetButtonTapped opens sheet`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.DeletedSheetButtonTapped)
        assertTrue(viewModel.state.value.deletedSheetOpen)
    }

    @Test
    fun `swipe left adds photo id to deletedPhotoIDs`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        viewModel.onIntent(SwipeIntent.DeleteButtonTapped)
        assertEquals(listOf(p1.id), viewModel.state.value.deletedPhotoIDs)
    }

    @Test
    fun `DeletedPhotoRestoreTapped moves photo back to kept bucket`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        viewModel.onIntent(SwipeIntent.DeleteButtonTapped)   // p1 → deleted
        advanceTimeBy(SwipeViewModel.EXIT_ANIMATION_DURATION_MS + 50); runCurrent()
        viewModel.onIntent(SwipeIntent.DeleteButtonTapped)   // p2 → deleted
        advanceTimeBy(SwipeViewModel.EXIT_ANIMATION_DURATION_MS + 50); runCurrent()

        val mid = viewModel.state.value
        assertEquals(listOf(p1.id, p2.id), mid.deletedPhotoIDs)
        assertEquals(2, mid.deleted)

        viewModel.onIntent(SwipeIntent.DeletedPhotoRestoreTapped(p1.id))

        val s = viewModel.state.value
        assertEquals(listOf(p2.id), s.deletedPhotoIDs)
        assertEquals(1, s.deleted)
        assertEquals(1, s.kept)
    }

    @Test
    fun `EmptyTrashButtonTapped with non-empty list opens alert`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()
        viewModel.onIntent(SwipeIntent.DeleteButtonTapped)

        viewModel.onIntent(SwipeIntent.EmptyTrashButtonTapped)
        assertTrue(viewModel.state.value.trashAlertOpen)
    }

    @Test
    fun `EmptyTrashButtonTapped with empty list is no-op`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.EmptyTrashButtonTapped)
        assertFalse(viewModel.state.value.trashAlertOpen)
    }

    @Test
    fun `TrashAlertConfirmed sets trashPendingIDs and trashInFlight`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()
        viewModel.onIntent(SwipeIntent.DeleteButtonTapped)
        viewModel.onIntent(SwipeIntent.EmptyTrashButtonTapped)

        viewModel.onIntent(SwipeIntent.TrashAlertConfirmed)

        val s = viewModel.state.value
        assertFalse(s.trashAlertOpen)
        assertTrue(s.trashInFlight)
        assertEquals(listOf(p1.id), s.trashPendingIDs)
    }

    @Test
    fun `TrashResult success resets counters, history and closes sheet`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()
        // 1 keep + 1 delete so we have non-zero tallies/history.
        viewModel.onIntent(SwipeIntent.KeepButtonTapped)
        advanceTimeBy(SwipeViewModel.EXIT_ANIMATION_DURATION_MS + 50); runCurrent()
        viewModel.onIntent(SwipeIntent.DeleteButtonTapped)
        advanceTimeBy(SwipeViewModel.EXIT_ANIMATION_DURATION_MS + 50); runCurrent()

        viewModel.onIntent(SwipeIntent.DeletedSheetButtonTapped)
        viewModel.onIntent(SwipeIntent.EmptyTrashButtonTapped)
        viewModel.onIntent(SwipeIntent.TrashAlertConfirmed)
        viewModel.onIntent(SwipeIntent.TrashPendingConsumed)

        viewModel.onIntent(SwipeIntent.TrashResult(success = true))

        val s = viewModel.state.value
        assertTrue(s.deletedPhotoIDs.isEmpty())
        assertFalse(s.deletedSheetOpen)
        assertFalse(s.trashInFlight)
        assertNull(s.trashPendingIDs)
        assertNull(s.deleteError)
        // Counters + history reset — session is "committed", user
        // continues on remaining photos with a clean slate.
        assertEquals(0, s.kept)
        assertEquals(0, s.deleted)
        assertTrue(s.history.isEmpty())
        // Deleted photos are pruned locally so we don't show assets that
        // are no longer in MediaStore. p2 was deleted so it disappears;
        // activeIndex remaps by ID to keep the user on the same photo
        // they were viewing (p3, now at index 1 instead of 2).
        assertEquals(listOf(p1, p3, p4, p5), s.photos)
        assertEquals(listOf(p1, p3, p4, p5), s.loadedPhotos)
        assertEquals(1, s.activeIndex)
    }

    @Test
    fun `TrashResult failure surfaces error and keeps list`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()
        viewModel.onIntent(SwipeIntent.DeleteButtonTapped)
        viewModel.onIntent(SwipeIntent.DeletedSheetButtonTapped)
        viewModel.onIntent(SwipeIntent.EmptyTrashButtonTapped)
        viewModel.onIntent(SwipeIntent.TrashAlertConfirmed)

        viewModel.onIntent(SwipeIntent.TrashResult(success = false, message = "oops"))

        val s = viewModel.state.value
        assertEquals(listOf(p1.id), s.deletedPhotoIDs)
        assertTrue(s.deletedSheetOpen)       // stays so user can retry
        assertFalse(s.trashInFlight)
        assertEquals("oops", s.deleteError)
    }

    @Test
    fun `undo after delete also removes id from deletedPhotoIDs`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()
        viewModel.onIntent(SwipeIntent.DeleteButtonTapped)
        advanceTimeBy(SwipeViewModel.EXIT_ANIMATION_DURATION_MS + 50); runCurrent()

        assertEquals(listOf(p1.id), viewModel.state.value.deletedPhotoIDs)
        viewModel.onIntent(SwipeIntent.UndoButtonTapped)

        val s = viewModel.state.value
        assertTrue(s.deletedPhotoIDs.isEmpty())
        assertEquals(0, s.deleted)
        assertEquals(0, s.activeIndex)
    }

    // MARK: - Undo

    @Test
    fun `undo reverts last swipe and restores index`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        viewModel.onIntent(SwipeIntent.KeepButtonTapped)
        advanceUntilIdle()
        viewModel.onIntent(SwipeIntent.DeleteButtonTapped)
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.activeIndex)

        viewModel.onIntent(SwipeIntent.UndoButtonTapped)

        val s = viewModel.state.value
        assertEquals(1, s.activeIndex)
        assertEquals(1, s.kept)
        assertEquals(0, s.deleted)
        assertEquals(listOf(SwipeHistoryEntry(0, kept = true)), s.history)
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
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        viewModel.onIntent(SwipeIntent.KeepButtonTapped)   // kept=1
        advanceTimeBy(SwipeViewModel.EXIT_ANIMATION_DURATION_MS + 50)
        runCurrent()
        viewModel.onIntent(SwipeIntent.UndoButtonTapped)   // kept=0
        viewModel.onIntent(SwipeIntent.UndoButtonTapped)   // history empty, no-op

        val s = viewModel.state.value
        assertEquals(0, s.kept)
        assertTrue(s.history.isEmpty())
    }

    // MARK: - Reset

    @Test
    fun `reset wipes progress`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()
        viewModel.onIntent(SwipeIntent.KeepButtonTapped)
        advanceUntilIdle()
        viewModel.onIntent(SwipeIntent.DeleteButtonTapped)
        advanceUntilIdle()

        viewModel.onIntent(SwipeIntent.ResetButtonTapped)

        val s = viewModel.state.value
        assertEquals(0, s.activeIndex)
        assertEquals(0, s.kept)
        assertEquals(0, s.deleted)
        assertTrue(s.history.isEmpty())
        assertEquals(0, s.unshuffledIndex)
    }

    // MARK: - Shuffle

    @Test
    fun `shuffle enable with single photo preserves list and saves index`() = runTest(dispatcher) {
        val viewModel = vm(FakePhotoRepository(initialHasPermission = true, albums = emptyList(), photos = listOf(p1)))
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        viewModel.onIntent(SwipeIntent.ShuffleButtonTapped)

        val s = viewModel.state.value
        assertTrue(s.shuffleEnabled)
        assertEquals(listOf(p1), s.photos)  // single-element shuffle == same list
        assertEquals(0, s.unshuffledIndex)  // was at 0 before enabling
        assertEquals(0, s.activeIndex)
    }

    @Test
    fun `shuffle disable restores unshuffled order and resumes position`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        // Swipe 2 photos in unshuffled mode.
        viewModel.onIntent(SwipeIntent.KeepButtonTapped)
        advanceUntilIdle()
        viewModel.onIntent(SwipeIntent.DeleteButtonTapped)
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.activeIndex)

        // Toggle shuffle ON — save position, reset index.
        viewModel.onIntent(SwipeIntent.ShuffleButtonTapped)
        assertEquals(2, viewModel.state.value.unshuffledIndex)
        assertEquals(0, viewModel.state.value.activeIndex)

        // Toggle OFF — restore position.
        viewModel.onIntent(SwipeIntent.ShuffleButtonTapped)

        val s = viewModel.state.value
        assertFalse(s.shuffleEnabled)
        assertEquals(fivePhotos, s.photos)  // unshuffled source restored
        assertEquals(2, s.activeIndex)
    }

    @Test
    fun `shuffle disable clamps unshuffledIndex when list shorter`() = runTest(dispatcher) {
        // Simulate the state where we entered shuffle with a larger list
        // that got replaced (e.g. album change mid-shuffle) — the saved
        // unshuffledIndex may exceed the new loadedPhotos.size.
        val repo = FakePhotoRepository(
            initialHasPermission = true,
            albums = emptyList(),
            photos = listOf(p1, p2)
        )
        val viewModel = vm(repo)
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        viewModel.onIntent(SwipeIntent.ShuffleButtonTapped)
        // Swipe through both photos while shuffled.
        viewModel.onIntent(SwipeIntent.KeepButtonTapped)
        advanceTimeBy(SwipeViewModel.EXIT_ANIMATION_DURATION_MS + 50)
        runCurrent()
        viewModel.onIntent(SwipeIntent.KeepButtonTapped)
        advanceTimeBy(SwipeViewModel.EXIT_ANIMATION_DURATION_MS + 50)
        runCurrent()

        viewModel.onIntent(SwipeIntent.ShuffleButtonTapped)  // back to unshuffled
        // activeIndex must never exceed loadedPhotos.size.
        val s = viewModel.state.value
        assertTrue(s.activeIndex <= s.loadedPhotos.size)
    }

    @Test
    fun `photosLoaded while shuffle enabled shuffles the incoming list`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()
        viewModel.onIntent(SwipeIntent.ShuffleButtonTapped)
        assertTrue(viewModel.state.value.shuffleEnabled)

        viewModel.onIntent(SwipeIntent.PhotosLoaded(listOf(p1)))

        val s = viewModel.state.value
        assertEquals(listOf(p1), s.loadedPhotos)
        // Single-element shuffle is identity; enough to verify plumbing.
        assertEquals(listOf(p1), s.photos)
    }

    // MARK: - Cancellation

    @Test
    fun `ResetButtonTapped cancels pending swipe animation`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        viewModel.onIntent(SwipeIntent.KeepButtonTapped)
        assertEquals(1, viewModel.state.value.exitingDirection)

        // Reset fires BEFORE animation delay completes.
        viewModel.onIntent(SwipeIntent.ResetButtonTapped)

        // Advance the clock past the animation window — if cancellation
        // worked, SwipeAnimationCompleted never fires, so kept stays 0.
        advanceTimeBy(SwipeViewModel.EXIT_ANIMATION_DURATION_MS * 5)
        runCurrent()

        assertEquals(0, viewModel.state.value.kept)
        assertEquals(0, viewModel.state.value.activeIndex)
    }

    @Test
    fun `AlbumPicked cancels pending swipe animation`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        viewModel.onIntent(SwipeIntent.KeepButtonTapped)
        viewModel.onIntent(SwipeIntent.AlbumPicked(Album.ALL_PHOTOS_ID))
        advanceUntilIdle()

        // Swipe animation should have been cancelled; album change resets.
        assertEquals(0, viewModel.state.value.kept)
        assertEquals(0, viewModel.state.value.activeIndex)
    }

    @Test
    fun `concurrent album picks cancels earlier load`() = runTest(dispatcher) {
        val repo = FakePhotoRepository(initialHasPermission = true, albums = albums, photos = fivePhotos)
        repo.setPhotosForAlbum("trip", listOf(p1, p2))
        val viewModel = vm(repo)
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        // Fire two album picks back-to-back — second should cancel the first.
        viewModel.onIntent(SwipeIntent.AlbumPicked("trip"))
        viewModel.onIntent(SwipeIntent.AlbumPicked(Album.ALL_PHOTOS_ID))
        advanceUntilIdle()

        // Only the last album's photos should be applied.
        val s = viewModel.state.value
        assertEquals(Album.ALL_PHOTOS_ID, s.selectedAlbumID)
        assertEquals(fivePhotos, s.photos)
    }

    // MARK: - Presentation flags

    @Test
    fun `AlbumTitleTapped opens picker`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.AlbumTitleTapped)
        assertTrue(viewModel.state.value.pickerOpen)
    }

    @Test
    fun `SettingsButtonTapped opens settings`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.SettingsButtonTapped)
        assertTrue(viewModel.state.value.settingsOpen)
    }

    @Test
    fun `SettingsDismissed closes settings`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.SettingsButtonTapped)
        viewModel.onIntent(SwipeIntent.SettingsDismissed)
        assertFalse(viewModel.state.value.settingsOpen)
    }

    @Test
    fun `AboutMenuItemTapped opens about`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.AboutMenuItemTapped)
        assertTrue(viewModel.state.value.aboutOpen)
    }

    @Test
    fun `TopCardTapped clears drag and opens fullscreen`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.onIntent(SwipeIntent.ScreenAppeared)
        advanceUntilIdle()

        viewModel.onIntent(SwipeIntent.CardDragChanged(x = 3f, y = 2f))
        viewModel.onIntent(SwipeIntent.TopCardTapped)

        val s = viewModel.state.value
        assertEquals(0f, s.dragX)
        assertEquals(0f, s.dragY)
        assertFalse(s.isDragging)
        assertTrue(s.fullscreenOpen)
    }

    // MARK: - Computed properties

    @Test
    fun `currentAlbum matches selectedAlbumID`() {
        val s = SwipeState(albums = albums, selectedAlbumID = "trip")
        assertEquals(tripAlbum, s.currentAlbum)
    }

    @Test
    fun `currentAlbum falls back to first album when selection missing`() {
        val s = SwipeState(albums = albums, selectedAlbumID = "nonexistent")
        assertEquals(allAlbum, s.currentAlbum)
    }

    @Test
    fun `currentAlbum is null while albums empty`() {
        assertNull(SwipeState().currentAlbum)
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
    fun `done flag becomes true only once photos loaded and exhausted`() {
        // Empty photos + activeIndex 0: NOT done (not started).
        assertFalse(SwipeState().done)
        // Past end of a non-empty list: done.
        assertTrue(SwipeState(activeIndex = 5, photos = fivePhotos).done)
        // Mid-stack: not done.
        assertFalse(SwipeState(activeIndex = 2, photos = fivePhotos).done)
    }

    // MARK: - Turbine end-to-end

    @Test
    fun `full user flow emits expected state sequence`() = runTest(dispatcher) {
        val viewModel = vm()
        viewModel.state.test {
            assertEquals(SwipeState(), awaitItem())  // initial

            viewModel.onIntent(SwipeIntent.ScreenAppeared)
            // Permission goes to Granted, loading flips true, photos + albums arrive.
            // Drain intermediate states until photos are populated.
            var s = awaitItem()
            while (s.photos.isEmpty() || s.albums.isEmpty()) s = awaitItem()
            assertEquals(PermissionStatus.Granted, s.permissionStatus)
            assertEquals(fivePhotos, s.photos)

            viewModel.onIntent(SwipeIntent.KeepButtonTapped)
            advanceTimeBy(SwipeViewModel.EXIT_ANIMATION_DURATION_MS + 50)
            runCurrent()
            // Drain to the post-swipe state.
            while (awaitItem().let { it.activeIndex != 1 }) { /* keep draining */ }

            cancelAndIgnoreRemainingEvents()
        }
    }
}

// Silence unused-parameter warnings from TestScope we don't use.
@Suppress("unused")
private val _testScopeDummy = TestScope::class
