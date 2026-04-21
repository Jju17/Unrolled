//
//  SwipeFeatureTests.swift
//  PicSwipeTests
//

import ComposableArchitecture
import XCTest

@MainActor
final class SwipeFeatureTests: XCTestCase {

    // MARK: - Authorization / initial load

    func test_onAppear_whenNotDetermined_doesNothing() async {
        let store = TestStore(initialState: SwipeFeature.State()) {
            SwipeFeature()
        } withDependencies: {
            $0.photoLibrary = .stubNotDeterminedThenGrants
        }

        await store.send(.onAppear)
        // authorizationStatus defaults to .notDetermined, so sending
        // `.onAppear` with a stub that returns `.notDetermined` is a no-op.
    }

    func test_onAppear_whenAuthorized_triggersLoadLibrary() async {
        let store = TestStore(initialState: SwipeFeature.State()) {
            SwipeFeature()
        } withDependencies: {
            $0.photoLibrary = .stubAuthorized()
        }

        await store.send(.onAppear) {
            $0.authorizationStatus = .authorized
            $0.isLoading = true
        }
        await store.receive(\.photosLoaded) {
            $0.loadedPhotos = PhotoAsset.five
            $0.photos = PhotoAsset.five
            $0.isLoading = false
        }
        await store.receive(\.albumsLoaded) {
            $0.albums = Album.three
            $0.selectedAlbumID = Album.recents.id
        }
    }

    func test_requestAuthorizationTapped_granted_fetchesLibrary() async {
        let store = TestStore(initialState: SwipeFeature.State()) {
            SwipeFeature()
        } withDependencies: {
            $0.photoLibrary = .stubNotDeterminedThenGrants
        }

        await store.send(.requestAuthorizationTapped)
        await store.receive(\.authorizationStatusReceived) {
            $0.authorizationStatus = .authorized
            $0.isLoading = true
        }
        await store.receive(\.photosLoaded) {
            $0.loadedPhotos = PhotoAsset.five
            $0.photos = PhotoAsset.five
            $0.isLoading = false
        }
        await store.receive(\.albumsLoaded) {
            $0.albums = Album.three
            $0.selectedAlbumID = Album.recents.id
        }
    }

    func test_requestAuthorizationTapped_denied_doesNotFetch() async {
        let store = TestStore(initialState: SwipeFeature.State()) {
            SwipeFeature()
        } withDependencies: {
            $0.photoLibrary = .stubNotDeterminedThenDenies
        }

        await store.send(.requestAuthorizationTapped)
        await store.receive(\.authorizationStatusReceived) {
            $0.authorizationStatus = .denied
        }
        // No photos/albums fetched.
    }

    // MARK: - Album loading

    func test_albumsLoaded_keepsExistingSelection() async {
        let store = TestStore(
            initialState: SwipeFeature.State(
                selectedAlbumID: Album.favorites.id
            )
        ) { SwipeFeature() }

        await store.send(.albumsLoaded(Album.three)) {
            $0.albums = Album.three
            // selectedAlbumID NOT overwritten.
        }
    }

    func test_albumPicked_resetsProgressAndRefetches() async {
        let store = TestStore(
            initialState: SwipeFeature.State(
                activeIndex: 3,
                albums: Album.three,
                authorizationStatus: .authorized,
                deleted: 2,
                history: [
                    SwipeHistoryEntry(index: 0, kept: true),
                    SwipeHistoryEntry(index: 1, kept: false),
                    SwipeHistoryEntry(index: 2, kept: true),
                ],
                kept: 2,
                loadedPhotos: PhotoAsset.five,
                photos: PhotoAsset.five,
                selectedAlbumID: Album.recents.id
            )
        ) {
            SwipeFeature()
        } withDependencies: {
            $0.photoLibrary = .stubAuthorized(
                photosByAlbum: [Album.favorites.id: [.p1, .p2]]
            )
        }

        await store.send(.albumPicked(Album.favorites.id)) {
            $0.selectedAlbumID = Album.favorites.id
            $0.pickerOpen = false
            // resetProgress wipes everything:
            $0.activeIndex = 0
            $0.history = []
            $0.kept = 0
            $0.deleted = 0
            $0.drag = .zero
            $0.exitingDirection = 0
            $0.isDragging = false
            $0.unshuffledIndex = 0
            // fetchPhotos sets loading:
            $0.isLoading = true
        }
        await store.receive(\.photosLoaded) {
            $0.loadedPhotos = [.p1, .p2]
            $0.photos = [.p1, .p2]
            $0.isLoading = false
        }
    }

    func test_viewAllPhotosTapped_routesToFirstAlbum() async {
        let store = TestStore(
            initialState: SwipeFeature.State(
                albums: Album.three,
                authorizationStatus: .authorized,
                selectedAlbumID: Album.favorites.id
            )
        ) {
            SwipeFeature()
        } withDependencies: {
            $0.photoLibrary = .stubAuthorized()
        }

        await store.send(.viewAllPhotosTapped)
        await store.receive(\.albumPicked) {
            $0.selectedAlbumID = Album.recents.id
            $0.pickerOpen = false
            $0.isLoading = true
        }
        await store.receive(\.photosLoaded) {
            $0.loadedPhotos = PhotoAsset.five
            $0.photos = PhotoAsset.five
            $0.isLoading = false
        }
    }

    func test_viewAllPhotosTapped_noAlbums_noOp() async {
        let store = TestStore(initialState: SwipeFeature.State()) {
            SwipeFeature()
        }
        await store.send(.viewAllPhotosTapped)
        // No effect fired because albums is empty.
    }

    // MARK: - Drag mechanics

    func test_cardDragChanged_marksDragging() async {
        let store = TestStore(initialState: SwipeFeature.State()) { SwipeFeature() }
        await store.send(.cardDragChanged(CGSize(width: 50, height: 10))) {
            $0.isDragging = true
            $0.drag = CGSize(width: 50, height: 10)
        }
    }

    func test_cardDragEnded_underThreshold_resetsDrag() async {
        let store = TestStore(
            initialState: SwipeFeature.State(
                drag: CGSize(width: 30, height: 0),
                isDragging: true
            )
        ) { SwipeFeature() }

        await store.send(.cardDragEnded) {
            $0.isDragging = false
            $0.drag = .zero
        }
    }

    func test_cardDragEnded_pastRightThreshold_startsKeepSwipe() async {
        let clock = TestClock()
        let store = TestStore(
            initialState: SwipeFeature.State(
                drag: CGSize(width: 150, height: 0),
                isDragging: true,
                photos: PhotoAsset.five
            )
        ) {
            SwipeFeature()
        } withDependencies: {
            $0.continuousClock = clock
        }

        // Commit happens synchronously — the new top card is
        // immediately drag-able while the fly-off overlay renders
        // `exitingPhoto`.
        await store.send(.cardDragEnded) {
            $0.isDragging = false
            $0.exitingDirection = 1
            $0.exitingPhoto = PhotoAsset.five[0]
            $0.exitingStartDrag = CGSize(width: 150, height: 0)
            $0.activeIndex = 1
            $0.kept = 1
            $0.history = [SwipeHistoryEntry(index: 0, kept: true)]
            $0.drag = .zero
        }
        await clock.advance(by: .milliseconds(SwipeFeature.exitAnimationDurationMs))
        await store.receive(\.swipeExitAnimationCompleted) {
            $0.exitingDirection = 0
            $0.exitingPhoto = nil
            $0.exitingStartDrag = .zero
        }
    }

    func test_cardDragEnded_pastLeftThreshold_startsDeleteSwipe() async {
        let clock = TestClock()
        let store = TestStore(
            initialState: SwipeFeature.State(
                drag: CGSize(width: -150, height: 0),
                isDragging: true,
                photos: PhotoAsset.five
            )
        ) {
            SwipeFeature()
        } withDependencies: {
            $0.continuousClock = clock
        }

        await store.send(.cardDragEnded) {
            $0.isDragging = false
            $0.exitingDirection = -1
            $0.exitingPhoto = PhotoAsset.five[0]
            $0.exitingStartDrag = CGSize(width: -150, height: 0)
            $0.activeIndex = 1
            $0.deleted = 1
            $0.history = [SwipeHistoryEntry(index: 0, kept: false)]
            $0.drag = .zero
        }
        await clock.advance(by: .milliseconds(SwipeFeature.exitAnimationDurationMs))
        await store.receive(\.swipeExitAnimationCompleted) {
            $0.exitingDirection = 0
            $0.exitingPhoto = nil
            $0.exitingStartDrag = .zero
        }
    }

    func test_deleteButton_atEndOfStack_doesNotStartSwipe() async {
        // activeIndex == photos.count — guard in startSwipe returns .none.
        let store = TestStore(
            initialState: SwipeFeature.State(
                activeIndex: 5,
                photos: PhotoAsset.five
            )
        ) { SwipeFeature() }

        await store.send(.deleteButtonTapped)
        // No exiting direction set, no effect fired.
    }

    func test_emptyPhotos_deleteButtonTapped_isNoOp() async {
        let store = TestStore(initialState: SwipeFeature.State()) { SwipeFeature() }
        await store.send(.deleteButtonTapped)
        // Guard fires because activeIndex (0) >= photos.count (0).
    }

    // MARK: - Undo

    func test_undoButtonTapped_revertsLastKeep() async {
        let store = TestStore(
            initialState: SwipeFeature.State(
                activeIndex: 2,
                history: [
                    SwipeHistoryEntry(index: 0, kept: true),
                    SwipeHistoryEntry(index: 1, kept: true),
                ],
                kept: 2,
                photos: PhotoAsset.five
            )
        ) { SwipeFeature() }

        await store.send(.undoButtonTapped) {
            $0.history = [SwipeHistoryEntry(index: 0, kept: true)]
            $0.kept = 1
            $0.activeIndex = 1
        }
    }

    func test_undoButtonTapped_revertsLastDelete() async {
        let store = TestStore(
            initialState: SwipeFeature.State(
                activeIndex: 1,
                deleted: 1,
                history: [SwipeHistoryEntry(index: 0, kept: false)],
                photos: PhotoAsset.five
            )
        ) { SwipeFeature() }

        await store.send(.undoButtonTapped) {
            $0.history = []
            $0.deleted = 0
            $0.activeIndex = 0
        }
    }

    func test_undoButtonTapped_emptyHistory_isNoOp() async {
        let store = TestStore(initialState: SwipeFeature.State()) { SwipeFeature() }
        await store.send(.undoButtonTapped)
        // popLast() returns nil → early return, no state change.
    }

    func test_undoButtonTapped_neverGoesNegative() async {
        // State corruption scenario — kept counter out of sync with history.
        // Undo a "keep" while kept = 0 — max(0, kept - 1) keeps it at 0.
        let store = TestStore(
            initialState: SwipeFeature.State(
                activeIndex: 1,
                history: [SwipeHistoryEntry(index: 0, kept: true)],
                kept: 0  // intentionally wrong
            )
        ) { SwipeFeature() }

        await store.send(.undoButtonTapped) {
            $0.history = []
            $0.kept = 0  // stays clamped
            $0.activeIndex = 0
        }
    }

    // MARK: - Reset

    func test_resetButtonTapped_wipesProgress() async {
        let store = TestStore(
            initialState: SwipeFeature.State(
                activeIndex: 4,
                deleted: 2,
                drag: CGSize(width: 50, height: 0),
                history: [
                    SwipeHistoryEntry(index: 0, kept: true),
                    SwipeHistoryEntry(index: 1, kept: false),
                ],
                isDragging: true,
                kept: 2,
                photos: PhotoAsset.five,
                unshuffledIndex: 3
            )
        ) { SwipeFeature() }

        await store.send(.resetButtonTapped) {
            $0.activeIndex = 0
            $0.history = []
            $0.kept = 0
            $0.deleted = 0
            $0.drag = .zero
            $0.exitingDirection = 0
            $0.isDragging = false
            $0.unshuffledIndex = 0
        }
    }

    // MARK: - Shuffle

    func test_shuffleButtonTapped_enable_savesPositionAndResets() async {
        // One photo — shuffled result is deterministic (same single element).
        let store = TestStore(
            initialState: SwipeFeature.State(
                activeIndex: 2,
                history: [
                    SwipeHistoryEntry(index: 0, kept: true),
                    SwipeHistoryEntry(index: 1, kept: false),
                ],
                loadedPhotos: [.p1],
                photos: [.p1]
            )
        ) { SwipeFeature() }

        await store.send(.shuffleButtonTapped) {
            $0.shuffleEnabled = true
            $0.unshuffledIndex = 2
            $0.photos = [.p1]  // shuffled single-element = same list
            $0.activeIndex = 0
            $0.history = []
            $0.drag = .zero
            $0.exitingDirection = 0
            $0.isDragging = false
        }
    }

    func test_shuffleButtonTapped_disable_restoresUnshuffledPosition() async {
        let store = TestStore(
            initialState: SwipeFeature.State(
                activeIndex: 2,  // random position in shuffled list
                loadedPhotos: PhotoAsset.five,
                photos: [.p3, .p1, .p2, .p4, PhotoAsset(id: "a5", creationDate: nil)],
                shuffleEnabled: true,
                unshuffledIndex: 3
            )
        ) { SwipeFeature() }

        await store.send(.shuffleButtonTapped) {
            $0.shuffleEnabled = false
            $0.photos = PhotoAsset.five
            $0.activeIndex = 3  // resumed from unshuffledIndex
            $0.history = []
            $0.drag = .zero
            $0.exitingDirection = 0
            $0.isDragging = false
        }
    }

    func test_shuffleDisable_whenUnshuffledIndexBeyondList_clampsToSize() async {
        // Defensive path: if loadedPhotos shrank while shuffled (e.g. album
        // changed but state wasn't fully reset), disable should not overflow.
        let store = TestStore(
            initialState: SwipeFeature.State(
                activeIndex: 1,
                loadedPhotos: [.p1, .p2],  // only 2 photos
                photos: [.p2, .p1],
                shuffleEnabled: true,
                unshuffledIndex: 99  // wildly out of bounds
            )
        ) { SwipeFeature() }

        await store.send(.shuffleButtonTapped) {
            $0.shuffleEnabled = false
            $0.photos = [.p1, .p2]
            $0.activeIndex = 2  // clamped to loadedPhotos.count
            $0.history = []
            $0.drag = .zero
            $0.exitingDirection = 0
            $0.isDragging = false
        }
    }

    // MARK: - Presentation flags (BindableAction)

    func test_albumTitleTapped_opensPicker() async {
        let store = TestStore(initialState: SwipeFeature.State()) { SwipeFeature() }
        await store.send(.albumTitleTapped) { $0.pickerOpen = true }
    }

    func test_binding_setPickerOpenFalse_dismissesPicker() async {
        let store = TestStore(
            initialState: SwipeFeature.State(pickerOpen: true)
        ) { SwipeFeature() }

        await store.send(.binding(.set(\.pickerOpen, false))) {
            $0.pickerOpen = false
        }
    }

    func test_topCardTapped_clearsDragAndOpensFullscreen() async {
        let store = TestStore(
            initialState: SwipeFeature.State(
                drag: CGSize(width: 3, height: 2),  // residual from near-zero drag
                isDragging: true,
                photos: PhotoAsset.five
            )
        ) { SwipeFeature() }

        await store.send(.topCardTapped) {
            $0.drag = .zero
            $0.isDragging = false
            $0.fullscreenOpen = true
        }
    }

    func test_settingsButtonTapped_opensSettings() async {
        let store = TestStore(initialState: SwipeFeature.State()) { SwipeFeature() }
        await store.send(.settingsButtonTapped) { $0.settingsOpen = true }
    }

    func test_aboutMenuItemTapped_opensAbout() async {
        let store = TestStore(initialState: SwipeFeature.State()) { SwipeFeature() }
        await store.send(.aboutMenuItemTapped) { $0.aboutOpen = true }
    }

    // MARK: - viewInPhotos effect

    func test_viewInPhotosTapped_opensPhotosAppUrl() async {
        let openedURL = LockIsolated<URL?>(nil)
        let store = TestStore(
            initialState: SwipeFeature.State(settingsOpen: true)
        ) {
            SwipeFeature()
        } withDependencies: {
            $0.openURL = OpenURLEffect { url in
                openedURL.withValue { $0 = url }
                return true
            }
        }

        await store.send(.viewInPhotosTapped)
        // SettingsOpen is NOT mutated by the action itself in the new design —
        // SwiftUI's sheet dismiss handles it via the binding. State stays true
        // until the view calls `dismiss()` in production. In the unit test we
        // only verify the URL was opened.
        await store.finish()
        XCTAssertEqual(openedURL.value, URL(string: "photos-redirect://"))
    }

    // MARK: - Photo loading + shuffle interaction

    func test_photosLoaded_whileShuffled_shufflesIncomingList() async {
        // With a single-photo list the shuffle is the identity, so the state
        // transition is deterministic.
        let store = TestStore(
            initialState: SwipeFeature.State(shuffleEnabled: true)
        ) { SwipeFeature() }

        await store.send(.photosLoaded([.p1])) {
            $0.loadedPhotos = [.p1]
            $0.photos = [.p1]
            $0.isLoading = false
        }
    }

    func test_photosLoaded_whileUnshuffled_storesAsIs() async {
        let store = TestStore(initialState: SwipeFeature.State()) { SwipeFeature() }
        await store.send(.photosLoaded(PhotoAsset.five)) {
            $0.loadedPhotos = PhotoAsset.five
            $0.photos = PhotoAsset.five
            $0.isLoading = false
        }
    }

    // MARK: - Cancellation

    func test_resetButton_cancelsPendingSwipeAnimation() async {
        let clock = TestClock()
        let store = TestStore(
            initialState: SwipeFeature.State(
                drag: CGSize(width: 150, height: 0),
                photos: PhotoAsset.five
            )
        ) {
            SwipeFeature()
        } withDependencies: {
            $0.continuousClock = clock
        }

        // Start a swipe — commit happens immediately, exit animation
        // is in-flight.
        await store.send(.cardDragEnded) {
            $0.isDragging = false
            $0.exitingDirection = 1
            $0.exitingPhoto = PhotoAsset.five[0]
            $0.exitingStartDrag = CGSize(width: 150, height: 0)
            $0.activeIndex = 1
            $0.kept = 1
            $0.history = [SwipeHistoryEntry(index: 0, kept: true)]
            $0.drag = .zero
        }

        // Reset before the clock advances past the animation window.
        await store.send(.resetButtonTapped) {
            $0.activeIndex = 0
            $0.history = []
            $0.kept = 0
            $0.deleted = 0
            $0.drag = .zero
            $0.exitingDirection = 0
            $0.exitingPhoto = nil
            $0.exitingStartDrag = .zero
            $0.isDragging = false
            $0.unshuffledIndex = 0
        }

        // Advance the clock — the exit animation effect was cancelled,
        // so no `swipeExitAnimationCompleted` should be received.
        await clock.advance(by: .milliseconds(SwipeFeature.exitAnimationDurationMs * 2))
        await store.finish()
    }

    // MARK: - currentAlbum computed property

    func test_currentAlbum_selectsMatch() {
        let state = SwipeFeature.State(
            albums: Album.three,
            selectedAlbumID: Album.favorites.id
        )
        XCTAssertEqual(state.currentAlbum, .favorites)
    }

    func test_currentAlbum_fallsBackToFirstWhenSelectionMissing() {
        let state = SwipeFeature.State(
            albums: Album.three,
            selectedAlbumID: "nonexistent"
        )
        XCTAssertEqual(state.currentAlbum, .recents)
    }

    func test_currentAlbum_nilWhenAlbumsEmpty() {
        let state = SwipeFeature.State()
        XCTAssertNil(state.currentAlbum)
    }

    // MARK: - topPhoto computed property

    func test_topPhoto_returnsAtActiveIndex() {
        let state = SwipeFeature.State(activeIndex: 2, photos: PhotoAsset.five)
        XCTAssertEqual(state.topPhoto, .p3)
    }

    func test_topPhoto_nilWhenPastEnd() {
        let state = SwipeFeature.State(activeIndex: 99, photos: PhotoAsset.five)
        XCTAssertNil(state.topPhoto)
    }

    func test_topPhoto_nilWhenEmpty() {
        let state = SwipeFeature.State()
        XCTAssertNil(state.topPhoto)
    }
}
