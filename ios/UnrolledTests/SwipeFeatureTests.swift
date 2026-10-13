//
//  SwipeFeatureTests.swift
//  UnrolledTests
//

import ComposableArchitecture
import XCTest

@MainActor
final class SwipeFeatureTests: XCTestCase {

    private let now = TestDates.now

    private func makeStore(
        _ state: SwipeFeature.State = SwipeFeature.State(),
        _ configure: (inout DependencyValues) -> Void = { _ in }
    ) -> TestStoreOf<SwipeFeature> {
        TestStore(initialState: state) {
            SwipeFeature()
        } withDependencies: {
            $0.date = .constant(TestDates.now)
            configure(&$0)
        }
    }

    // MARK: - Authorization / initial load

    func test_onAppear_whenNotDetermined_doesNothing() async {
        let store = makeStore { $0.photoLibrary = .stubNotDeterminedThenGrants }

        await store.send(.onAppear) {
            $0.stats = .fresh
        }
    }

    func test_onAppear_whenAuthorized_triggersLoadLibrary() async {
        let store = makeStore { $0.photoLibrary = .stubAuthorized() }

        await store.send(.onAppear) {
            $0.authorizationStatus = .authorized
            $0.isLoading = true
            $0.stats = .fresh
        }
        await store.receive(\.photosLoaded) {
            $0.pilePhotoCount = 5
            $0.loadedPhotos = PhotoAsset.five
            $0.photos = PhotoAsset.five
            $0.isLoading = false
            $0.libraryLoaded = true
        }
        await store.receive(\.pilesLoaded) {
            $0.piles = Pile.three
        }
        await store.receive(\.trashPhotosLoaded)
    }

    func test_onAppear_loadsPersistedProgressStatsFiltersDeckAndResolvesTrash() async {
        let progress = SwipeProgress(
            deletedIDs: [PhotoAsset.p3.id],
            keptIDs: [PhotoAsset.p1.id]
        )
        let stored = ActivityStats(bytesFreed: 42, photosReviewed: 7, todayCount: 3, todayKey: "2000-01-01")
        let store = makeStore {
            $0.photoLibrary = .stubAuthorized(fileSizes: [PhotoAsset.p3.id: 1_000])
            $0.swipeProgress = SwipeProgressClient(load: { progress }, save: { _ in })
            $0.stats = StatsClient(load: { stored }, save: { _ in })
        }

        await store.send(.onAppear) {
            $0.authorizationStatus = .authorized
            $0.deletedPhotoIDs = [PhotoAsset.p3.id]
            $0.keptPhotoIDs = [PhotoAsset.p1.id]
            $0.isLoading = true
            $0.stats = ActivityStats(bytesFreed: 42, photosReviewed: 7, todayCount: 0, todayKey: TestDates.today)
        }
        await store.receive(\.photosLoaded) {
            $0.pilePhotoCount = 5
            $0.loadedPhotos = [.p2, .p4, PhotoAsset.five[4]]
            $0.photos = [.p2, .p4, PhotoAsset.five[4]]
            $0.isLoading = false
            $0.libraryLoaded = true
        }
        await store.receive(\.pilesLoaded) {
            $0.piles = Pile.three
        }
        await store.receive(\.trashPhotosLoaded) {
            $0.trashPhotos = [.p3]
        }
        await store.receive(\.fileSizesLoaded) {
            $0.byteCounts = [PhotoAsset.p3.id: 1_000]
        }
    }

    func test_trashPhotosLoaded_prunesIDsWhoseAssetIsGone() async {
        let saved = LockIsolated<[SwipeProgress]>([])
        let store = makeStore(
            SwipeFeature.State(
                byteCounts: [PhotoAsset.p3.id: 5],
                deletedPhotoIDs: [PhotoAsset.p3.id, "gone"],
                keptPhotoIDs: ["k"]
            )
        ) {
            $0.swipeProgress = .recording(into: saved)
        }

        await store.send(.trashPhotosLoaded([.p3])) {
            $0.trashPhotos = [.p3]
            $0.deletedPhotoIDs = [PhotoAsset.p3.id]
        }
        await store.finish()
        XCTAssertEqual(saved.value, [SwipeProgress(deletedIDs: [PhotoAsset.p3.id], keptIDs: ["k"])])
    }

    func test_requestAuthorizationTapped_granted_fetchesLibrary() async {
        let store = makeStore { $0.photoLibrary = .stubNotDeterminedThenGrants }

        await store.send(.requestAuthorizationTapped)
        await store.receive(\.authorizationStatusReceived) {
            $0.authorizationStatus = .authorized
            $0.isLoading = true
        }
        await store.receive(\.photosLoaded) {
            $0.pilePhotoCount = 5
            $0.loadedPhotos = PhotoAsset.five
            $0.photos = PhotoAsset.five
            $0.isLoading = false
            $0.libraryLoaded = true
        }
        await store.receive(\.pilesLoaded) {
            $0.piles = Pile.three
        }
        await store.receive(\.trashPhotosLoaded)
    }

    func test_requestAuthorizationTapped_denied_doesNotFetch() async {
        let store = makeStore { $0.photoLibrary = .stubNotDeterminedThenDenies }

        await store.send(.requestAuthorizationTapped)
        await store.receive(\.authorizationStatusReceived) {
            $0.authorizationStatus = .denied
        }
    }

    func test_openSettingsTapped_opensSystemSettings() async {
        let openedURL = LockIsolated<URL?>(nil)
        let store = makeStore {
            $0.openURL = OpenURLEffect { url in
                openedURL.withValue { $0 = url }
                return true
            }
        }

        await store.send(.openSettingsTapped)
        await store.finish()
        XCTAssertEqual(openedURL.value, URL(string: "app-settings:"))
    }

    // MARK: - Refresh on activation

    func test_libraryRefreshRequested_beforeFirstLoad_loadsLibrary() async {
        let store = makeStore { $0.photoLibrary = .stubAuthorized() }

        await store.send(.libraryRefreshRequested) {
            $0.authorizationStatus = .authorized
            $0.isLoading = true
            $0.stats = .fresh
        }
        await store.receive(\.photosLoaded) {
            $0.pilePhotoCount = 5
            $0.loadedPhotos = PhotoAsset.five
            $0.photos = PhotoAsset.five
            $0.isLoading = false
            $0.libraryLoaded = true
        }
        await store.receive(\.pilesLoaded) {
            $0.piles = Pile.three
        }
        await store.receive(\.trashPhotosLoaded)
    }

    func test_libraryRefreshRequested_whileLoading_isNoOp() async {
        let store = makeStore(
            SwipeFeature.State(authorizationStatus: .authorized, isLoading: true, stats: .fresh)
        ) {
            $0.photoLibrary = .stubAuthorized()
        }

        await store.send(.libraryRefreshRequested)
    }

    func test_libraryRefreshRequested_rollsOverTheDailyCounter() async {
        let store = makeStore(
            SwipeFeature.State(
                authorizationStatus: .authorized,
                isLoading: true,
                stats: ActivityStats(currentStreak: 2, lastGoalDay: "2000-01-01", todayCount: 9, todayKey: "2000-01-01")
            )
        ) {
            $0.photoLibrary = .stubAuthorized()
        }

        await store.send(.libraryRefreshRequested) {
            $0.stats = ActivityStats(currentStreak: 0, lastGoalDay: "2000-01-01", todayCount: 0, todayKey: TestDates.today)
        }
    }

    func test_libraryRefreshRequested_afterLoad_refreshesSilentlyAndKeepsPlace() async {
        // p2 and p4 vanished from the library, p6 is new. The user is
        // on p3: they must stay on p3, the spinner must not show, and
        // the history entry for the vanished p2 is dropped. Session
        // tallies survive the refresh.
        let p6 = PhotoAsset(id: "a6")
        let store = makeStore(
            SwipeFeature.State(
                activeIndex: 2,
                authorizationStatus: .authorized,
                deletedPhotoIDs: [PhotoAsset.p2.id],
                history: [
                    SwipeHistoryEntry(photoID: PhotoAsset.p1.id, kept: true),
                    SwipeHistoryEntry(photoID: PhotoAsset.p2.id, kept: false),
                ],
                keptPhotoIDs: [PhotoAsset.p1.id],
                libraryLoaded: true,
                loadedPhotos: PhotoAsset.five,
                photos: PhotoAsset.five,
                pilePhotoCount: 5,
                piles: Pile.three,
                sessionDeletedIDs: [PhotoAsset.p2.id],
                sessionKeptIDs: [PhotoAsset.p1.id],
                sessionStartedAt: TestDates.now,
                stats: .fresh,
                trashPhotos: [.p2]
            )
        ) {
            $0.photoLibrary = .stubAuthorized(photos: [.p1, .p3, PhotoAsset.five[4], p6])
        }

        await store.send(.libraryRefreshRequested)
        await store.receive(\.photosLoaded) {
            $0.pilePhotoCount = 4
            $0.loadedPhotos = [.p3, PhotoAsset.five[4], p6]
            $0.photos = [.p1, .p3, PhotoAsset.five[4], p6]
            $0.activeIndex = 1
            $0.history = [SwipeHistoryEntry(photoID: PhotoAsset.p1.id, kept: true)]
        }
        await store.receive(\.pilesLoaded)
        await store.receive(\.trashPhotosLoaded) {
            $0.trashPhotos = []
            $0.deletedPhotoIDs = []
        }
        XCTAssertEqual(store.state.sessionReviewed, 2)
    }

    func test_changeSelectionTapped_showsPickerThenRefreshes() async {
        let presented = LockIsolated(false)
        let store = makeStore(
            SwipeFeature.State(authorizationStatus: .limited, isLoading: true, stats: .fresh)
        ) {
            var client = PhotoLibraryClient.stubAuthorized(status: .limited)
            client.presentLimitedLibraryPicker = { presented.setValue(true) }
            $0.photoLibrary = client
        }

        await store.send(.changeSelectionTapped)
        await store.receive(\.libraryRefreshRequested)
        XCTAssertTrue(presented.value)
    }

    // MARK: - Piles

    func test_pilesLoaded_storesPiles() async {
        let store = makeStore(SwipeFeature.State(selectedPile: .onThisDay))

        await store.send(.pilesLoaded(Pile.three)) {
            $0.piles = Pile.three
        }
        XCTAssertNil(store.state.currentPile)
    }

    func test_pilePicked_resetsSessionButKeepsPersistedProgress() async {
        let store = makeStore(
            SwipeFeature.State(
                activeIndex: 3,
                authorizationStatus: .authorized,
                deletedPhotoIDs: ["x"],
                history: [
                    SwipeHistoryEntry(photoID: "y", kept: true),
                    SwipeHistoryEntry(photoID: "x", kept: false),
                ],
                keptPhotoIDs: ["y", "z"],
                libraryLoaded: true,
                loadedPhotos: PhotoAsset.five,
                photos: PhotoAsset.five,
                pilePhotoCount: 5,
                piles: Pile.three,
                sessionDeletedIDs: ["x"],
                sessionKeptIDs: ["y"],
                sessionStartedAt: TestDates.now
            )
        ) {
            $0.photoLibrary = .stubAuthorized(photosByPile: [Pile.trip.selector: [.p1, .p2]])
        }

        await store.send(.pilePicked(Pile.trip.selector)) {
            $0.selectedPile = Pile.trip.selector
            $0.pickerOpen = false
            $0.activeIndex = 0
            $0.history = []
            $0.isLoading = true
            $0.sessionDeletedIDs = []
            $0.sessionKeptIDs = []
            $0.sessionStartedAt = nil
        }
        await store.receive(\.photosLoaded) {
            $0.pilePhotoCount = 2
            $0.loadedPhotos = [.p1, .p2]
            $0.photos = [.p1, .p2]
            $0.isLoading = false
        }
        XCTAssertEqual(store.state.currentPile, .trip)
    }

    func test_pilePicked_twice_onlyLastPileLands() async {
        let clock = TestClock()
        let store = makeStore(
            SwipeFeature.State(authorizationStatus: .authorized, piles: Pile.three)
        ) {
            var client = PhotoLibraryClient.stubAuthorized()
            client.fetchPhotos = { selector, _ in
                if selector == Pile.thisMonth.selector {
                    try? await clock.sleep(for: .seconds(1))
                    return [.p1, .p2]
                }
                return [.p3]
            }
            $0.photoLibrary = client
            $0.continuousClock = clock
        }

        await store.send(.pilePicked(Pile.thisMonth.selector)) {
            $0.selectedPile = Pile.thisMonth.selector
            $0.isLoading = true
        }
        await store.send(.pilePicked(Pile.trip.selector)) {
            $0.selectedPile = Pile.trip.selector
        }
        await store.receive(\.photosLoaded) {
            $0.pilePhotoCount = 1
            $0.loadedPhotos = [.p3]
            $0.photos = [.p3]
            $0.isLoading = false
            $0.libraryLoaded = true
        }
        await clock.advance(by: .seconds(2))
        await store.finish()
    }

    func test_viewAllPhotosTapped_routesToAllPile() async {
        let store = makeStore(
            SwipeFeature.State(
                authorizationStatus: .authorized,
                piles: Pile.three,
                selectedPile: Pile.trip.selector
            )
        ) {
            $0.photoLibrary = .stubAuthorized()
        }

        await store.send(.viewAllPhotosTapped)
        await store.receive(\.pilePicked) {
            $0.selectedPile = .all
            $0.pickerOpen = false
            $0.isLoading = true
        }
        await store.receive(\.photosLoaded) {
            $0.pilePhotoCount = 5
            $0.loadedPhotos = PhotoAsset.five
            $0.photos = PhotoAsset.five
            $0.isLoading = false
            $0.libraryLoaded = true
        }
    }

    func test_pileTitleTapped_opensPicker() async {
        let store = makeStore()
        await store.send(.pileTitleTapped) { $0.pickerOpen = true }
    }

    func test_pileSelector_idsAreStable() {
        XCTAssertEqual(PileSelector.all.id, "all")
        XCTAssertEqual(PileSelector.album("x").id, "album:x")
        XCTAssertEqual(PileSelector.month(year: 2026, month: 3).id, "month:2026-03")
        XCTAssertEqual(PileSelector.videos.id, "videos")
        XCTAssertEqual(Pile.trip.progress, 0.9, accuracy: 0.0001)
        XCTAssertEqual(Pile(selector: .all, photoCount: 0, remainingCount: 0).progress, 0)
    }

    // MARK: - Drag mechanics

    func test_cardDragChanged_marksDragging() async {
        let store = makeStore()
        await store.send(.cardDragChanged(CGSize(width: 50, height: 10))) {
            $0.isDragging = true
            $0.drag = CGSize(width: 50, height: 10)
        }
    }

    func test_cardDragEnded_underThreshold_resetsDrag() async {
        let store = makeStore(
            SwipeFeature.State(drag: CGSize(width: 30, height: 0), isDragging: true)
        )

        await store.send(.cardDragEnded) {
            $0.isDragging = false
            $0.drag = .zero
        }
    }

    func test_cardDragEnded_pastRightThreshold_startsKeepSwipe() async {
        let clock = TestClock()
        let saved = LockIsolated<[SwipeProgress]>([])
        let savedStats = LockIsolated<[ActivityStats]>([])
        let store = makeStore(
            SwipeFeature.State(
                drag: CGSize(width: 150, height: 0),
                isDragging: true,
                photos: PhotoAsset.five,
                stats: .fresh
            )
        ) {
            $0.continuousClock = clock
            $0.swipeProgress = .recording(into: saved)
            $0.stats = .recording(into: savedStats)
        }

        await store.send(.cardDragEnded) {
            $0.isDragging = false
            $0.exitingDirection = 1
            $0.exitingPhoto = PhotoAsset.five[0]
            $0.exitingStartDrag = CGSize(width: 150, height: 0)
            $0.activeIndex = 1
            $0.keptPhotoIDs = [PhotoAsset.five[0].id]
            $0.history = [SwipeHistoryEntry(photoID: PhotoAsset.p1.id, kept: true)]
            $0.drag = .zero
            $0.sessionKeptIDs = [PhotoAsset.p1.id]
            $0.sessionStartedAt = TestDates.now
            $0.stats = .reviewed(1)
        }
        await clock.advance(by: .milliseconds(SwipeFeature.exitAnimationDurationMs))
        await store.receive(\.swipeExitAnimationCompleted) {
            $0.exitingDirection = 0
            $0.exitingPhoto = nil
            $0.exitingStartDrag = .zero
        }
        XCTAssertEqual(saved.value, [SwipeProgress(deletedIDs: [], keptIDs: [PhotoAsset.p1.id])])
        XCTAssertEqual(savedStats.value, [.reviewed(1)])
    }

    func test_cardDragEnded_pastLeftThreshold_startsDeleteSwipeAndFetchesSize() async {
        let clock = TestClock()
        let store = makeStore(
            SwipeFeature.State(
                drag: CGSize(width: -150, height: 0),
                isDragging: true,
                photos: PhotoAsset.five,
                stats: .fresh
            )
        ) {
            $0.continuousClock = clock
            $0.photoLibrary = .stubAuthorized(fileSizes: [PhotoAsset.p1.id: 2_500])
        }

        await store.send(.cardDragEnded) {
            $0.isDragging = false
            $0.exitingDirection = -1
            $0.exitingPhoto = PhotoAsset.five[0]
            $0.exitingStartDrag = CGSize(width: -150, height: 0)
            $0.activeIndex = 1
            $0.deletedPhotoIDs = [PhotoAsset.p1.id]
            $0.trashPhotos = [.p1]
            $0.history = [SwipeHistoryEntry(photoID: PhotoAsset.p1.id, kept: false)]
            $0.drag = .zero
            $0.sessionDeletedIDs = [PhotoAsset.p1.id]
            $0.sessionStartedAt = TestDates.now
            $0.stats = .reviewed(1)
        }
        await store.receive(\.fileSizesLoaded) {
            $0.byteCounts = [PhotoAsset.p1.id: 2_500]
        }
        await clock.advance(by: .milliseconds(SwipeFeature.exitAnimationDurationMs))
        await store.receive(\.swipeExitAnimationCompleted) {
            $0.exitingDirection = 0
            $0.exitingPhoto = nil
            $0.exitingStartDrag = .zero
        }
        XCTAssertEqual(store.state.sessionBytesToFree, 2_500)
    }

    func test_deleteButton_atEndOfStack_doesNotStartSwipe() async {
        let store = makeStore(SwipeFeature.State(activeIndex: 5, photos: PhotoAsset.five))
        await store.send(.deleteButtonTapped)
    }

    func test_emptyPhotos_deleteButtonTapped_isNoOp() async {
        let store = makeStore()
        await store.send(.deleteButtonTapped)
    }

    func test_reclassifyingAPhoto_neverDuplicatesIt() async {
        // p1 was kept earlier (still in the deck). Deleting it moves it
        // across lists; keeping it again moves it back. Counts stay 1.
        let clock = TestClock()
        let store = makeStore(
            SwipeFeature.State(keptPhotoIDs: [PhotoAsset.p1.id], photos: PhotoAsset.five, stats: .fresh)
        ) {
            $0.continuousClock = clock
        }

        await store.send(.deleteButtonTapped) {
            $0.keptPhotoIDs = []
            $0.deletedPhotoIDs = [PhotoAsset.p1.id]
            $0.trashPhotos = [.p1]
            $0.history = [SwipeHistoryEntry(photoID: PhotoAsset.p1.id, kept: false)]
            $0.activeIndex = 1
            $0.exitingDirection = -1
            $0.exitingPhoto = .p1
            $0.sessionDeletedIDs = [PhotoAsset.p1.id]
            $0.sessionStartedAt = TestDates.now
            $0.stats = .reviewed(1)
        }
        await store.receive(\.fileSizesLoaded)
        await store.send(.undoButtonTapped) {
            $0.deletedPhotoIDs = []
            $0.trashPhotos = []
            $0.history = []
            $0.activeIndex = 0
            $0.exitingDirection = 0
            $0.exitingPhoto = nil
            $0.sessionDeletedIDs = []
        }
        await store.send(.keepButtonTapped) {
            $0.keptPhotoIDs = [PhotoAsset.p1.id]
            $0.history = [SwipeHistoryEntry(photoID: PhotoAsset.p1.id, kept: true)]
            $0.activeIndex = 1
            $0.exitingDirection = 1
            $0.exitingPhoto = .p1
            $0.sessionKeptIDs = [PhotoAsset.p1.id]
            $0.stats = .reviewed(2)
        }
        await clock.advance(by: .milliseconds(SwipeFeature.exitAnimationDurationMs))
        await store.receive(\.swipeExitAnimationCompleted) {
            $0.exitingDirection = 0
            $0.exitingPhoto = nil
        }
        XCTAssertEqual(store.state.kept, 1)
        XCTAssertEqual(store.state.deleted, 0)
        XCTAssertEqual(store.state.sessionReviewed, 1)
    }

    // MARK: - Session summary

    func test_finishingTheDeck_stampsSessionEndAndComputesSpeed() async {
        let clock = TestClock()
        let started = TestDates.now.addingTimeInterval(-120)
        let store = makeStore(
            SwipeFeature.State(
                activeIndex: 1,
                byteCounts: ["a1": 700],
                photos: [.p1, .p2],
                pilePhotoCount: 2,
                sessionDeletedIDs: ["a1"],
                sessionStartedAt: started,
                stats: .reviewed(1)
            )
        ) {
            $0.continuousClock = clock
        }

        await store.send(.keepButtonTapped) {
            $0.activeIndex = 2
            $0.keptPhotoIDs = ["a2"]
            $0.history = [SwipeHistoryEntry(photoID: "a2", kept: true)]
            $0.exitingDirection = 1
            $0.exitingPhoto = .p2
            $0.sessionKeptIDs = ["a2"]
            $0.sessionFinishedAt = TestDates.now
            $0.stats = .reviewed(2)
        }
        XCTAssertTrue(store.state.isDone)
        XCTAssertEqual(store.state.sessionReviewed, 2)
        XCTAssertEqual(store.state.sessionBytesToFree, 700)
        XCTAssertEqual(store.state.sessionDuration, 120)
        XCTAssertEqual(store.state.sessionPhotosPerMinute, 1, accuracy: 0.0001)

        await store.send(.undoButtonTapped) {
            $0.activeIndex = 1
            $0.keptPhotoIDs = []
            $0.history = []
            $0.exitingDirection = 0
            $0.exitingPhoto = nil
            $0.sessionKeptIDs = []
            $0.sessionFinishedAt = nil
        }
        XCTAssertEqual(store.state.sessionDuration, 0)
        XCTAssertEqual(store.state.sessionPhotosPerMinute, 0)
    }

    // MARK: - Undo

    func test_undoButtonTapped_revertsLastKeep() async {
        let store = makeStore(
            SwipeFeature.State(
                activeIndex: 2,
                history: [
                    SwipeHistoryEntry(photoID: PhotoAsset.p1.id, kept: true),
                    SwipeHistoryEntry(photoID: PhotoAsset.p2.id, kept: true),
                ],
                keptPhotoIDs: [PhotoAsset.p1.id, PhotoAsset.p2.id],
                photos: PhotoAsset.five
            )
        )

        await store.send(.undoButtonTapped) {
            $0.history = [SwipeHistoryEntry(photoID: PhotoAsset.p1.id, kept: true)]
            $0.keptPhotoIDs = [PhotoAsset.p1.id]
            $0.activeIndex = 1
        }
    }

    func test_undoButtonTapped_revertsLastDelete() async {
        let store = makeStore(
            SwipeFeature.State(
                activeIndex: 1,
                deletedPhotoIDs: [PhotoAsset.p1.id],
                history: [SwipeHistoryEntry(photoID: PhotoAsset.p1.id, kept: false)],
                photos: PhotoAsset.five,
                trashPhotos: [.p1]
            )
        )

        await store.send(.undoButtonTapped) {
            $0.history = []
            $0.deletedPhotoIDs = []
            $0.trashPhotos = []
            $0.activeIndex = 0
        }
    }

    func test_undoButtonTapped_entryForVanishedPhoto_isDropped() async {
        let store = makeStore(
            SwipeFeature.State(
                activeIndex: 1,
                history: [SwipeHistoryEntry(photoID: "gone", kept: true)],
                keptPhotoIDs: ["gone"],
                photos: PhotoAsset.five
            )
        )

        await store.send(.undoButtonTapped) {
            $0.history = []
        }
    }

    func test_undoButtonTapped_emptyHistory_isNoOp() async {
        let store = makeStore()
        await store.send(.undoButtonTapped)
    }

    func test_undoButtonTapped_groupEntry_restoresEveryDecision() async {
        let store = makeStore(
            SwipeFeature.State(
                activeIndex: 3,
                deletedPhotoIDs: ["a2", "a3"],
                history: [
                    SwipeHistoryEntry(decisions: [
                        PhotoDecision(photoID: "a1", kept: true),
                        PhotoDecision(photoID: "a2", kept: false),
                        PhotoDecision(photoID: "a3", kept: false),
                    ]),
                ],
                keptPhotoIDs: ["a1"],
                photos: PhotoAsset.five,
                sessionDeletedIDs: ["a2", "a3"],
                sessionKeptIDs: ["a1"],
                trashPhotos: [.p2, .p3]
            )
        )

        await store.send(.undoButtonTapped) {
            $0.history = []
            $0.keptPhotoIDs = []
            $0.deletedPhotoIDs = []
            $0.trashPhotos = []
            $0.sessionDeletedIDs = []
            $0.sessionKeptIDs = []
            $0.activeIndex = 0
        }
    }

    // MARK: - Deleted review sheet

    func test_deletedSheetButtonTapped_opensSheet() async {
        let store = makeStore()
        await store.send(.deletedSheetButtonTapped) {
            $0.deletedSheetOpen = true
        }
    }

    func test_deletedPhotoRestore_movesFromDeletedToKeptAndForgetsHistory() async {
        let store = makeStore(
            SwipeFeature.State(
                activeIndex: 2,
                deletedPhotoIDs: ["a1", "a3"],
                history: [
                    SwipeHistoryEntry(photoID: "a1", kept: false),
                    SwipeHistoryEntry(photoID: "a3", kept: false),
                ],
                keptPhotoIDs: ["a4"],
                photos: PhotoAsset.five,
                sessionDeletedIDs: ["a1", "a3"],
                trashPhotos: [.p1, .p3]
            )
        )

        await store.send(.deletedPhotoRestoreTapped(id: "a1")) {
            $0.deletedPhotoIDs = ["a3"]
            $0.trashPhotos = [.p3]
            $0.keptPhotoIDs = ["a4", "a1"]
            $0.history = [SwipeHistoryEntry(photoID: "a3", kept: false)]
            $0.sessionDeletedIDs = ["a3"]
            $0.sessionKeptIDs = ["a1"]
        }
        // Undo now reverts a3, not the restored a1.
        await store.send(.undoButtonTapped) {
            $0.deletedPhotoIDs = []
            $0.trashPhotos = []
            $0.history = []
            $0.sessionDeletedIDs = []
        }
    }

    func test_deletedPhotoRestore_unknownID_isNoOp() async {
        let store = makeStore(SwipeFeature.State(deletedPhotoIDs: ["a1"], photos: PhotoAsset.five))
        await store.send(.deletedPhotoRestoreTapped(id: "nope"))
    }

    func test_trashPhotoTapped_opensPreviewAtThatPhoto() async {
        let store = makeStore(
            SwipeFeature.State(deletedPhotoIDs: ["a1", "a3"], photos: PhotoAsset.five, trashPhotos: [.p1, .p3])
        )
        await store.send(.trashPhotoTapped(id: "a3")) {
            $0.trashPreviewIndex = 1
        }
        await store.send(.trashPreviewDismissed) {
            $0.trashPreviewIndex = nil
        }
    }

    func test_trashPhotoTapped_unknownID_isNoOp() async {
        let store = makeStore(SwipeFeature.State(deletedPhotoIDs: ["a1"], photos: PhotoAsset.five, trashPhotos: [.p1]))
        await store.send(.trashPhotoTapped(id: "nope"))
    }

    func test_trashPreviewKeep_restoresThePhotoAndClosesThePreview() async {
        let store = makeStore(
            SwipeFeature.State(
                deletedPhotoIDs: ["a1", "a3"],
                history: [SwipeHistoryEntry(photoID: "a3", kept: false)],
                photos: PhotoAsset.five,
                sessionDeletedIDs: ["a3"],
                trashPhotos: [.p1, .p3],
                trashPreviewIndex: 0
            )
        )
        await store.send(.trashPreviewKeepTapped(localIdx: 1)) {
            $0.deletedPhotoIDs = ["a1"]
            $0.trashPhotos = [.p1]
            $0.trashPreviewIndex = nil
            $0.keptPhotoIDs = ["a3"]
            $0.history = []
            $0.sessionDeletedIDs = []
            $0.sessionKeptIDs = ["a3"]
        }
    }

    func test_trashPreviewKeep_outOfRange_isNoOp() async {
        let store = makeStore(
            SwipeFeature.State(deletedPhotoIDs: ["a1"], photos: PhotoAsset.five, trashPhotos: [.p1], trashPreviewIndex: 0)
        )
        await store.send(.trashPreviewKeepTapped(localIdx: 1))
        await store.send(.trashPreviewKeepTapped(localIdx: -1))
    }

    func test_deletedSheetClosed_dropsThePreview() async {
        let store = makeStore(
            SwipeFeature.State(deletedPhotoIDs: ["a1"], deletedSheetOpen: true, photos: PhotoAsset.five, trashPhotos: [.p1], trashPreviewIndex: 0)
        )
        await store.send(.binding(.set(\.deletedSheetOpen, false))) {
            $0.deletedSheetOpen = false
            $0.trashPreviewIndex = nil
        }
    }

    func test_emptyTrashButton_emptyList_isNoOp() async {
        let store = makeStore()
        await store.send(.emptyTrashButtonTapped)
    }

    func test_emptyTrashButton_whileInFlight_isNoOp() async {
        let store = makeStore(SwipeFeature.State(deletedPhotoIDs: ["a1"], trashInFlight: true))
        await store.send(.emptyTrashButtonTapped)
    }

    func test_emptyTrashButton_success_prunesDeckClearsTrashRecordsStats() async {
        let savedStats = LockIsolated<[ActivityStats]>([])
        let store = makeStore(
            SwipeFeature.State(
                activeIndex: 2,
                byteCounts: [PhotoAsset.p2.id: 1_000, "other-pile": 2_000],
                deletedPhotoIDs: [PhotoAsset.p2.id, "other-pile"],
                deletedSheetOpen: true,
                history: [
                    SwipeHistoryEntry(photoID: PhotoAsset.p1.id, kept: true),
                    SwipeHistoryEntry(photoID: PhotoAsset.p2.id, kept: false),
                ],
                keptPhotoIDs: [PhotoAsset.p1.id],
                loadedPhotos: PhotoAsset.five,
                photos: PhotoAsset.five,
                pilePhotoCount: 5,
                stats: .fresh,
                trashPhotos: [.p2, PhotoAsset(id: "other-pile")]
            )
        ) {
            $0.photoLibrary = .stubAuthorized()
            $0.stats = .recording(into: savedStats)
        }

        await store.send(.emptyTrashButtonTapped) {
            $0.trashInFlight = true
        }
        await store.receive(\.emptyTrashFinished) {
            $0.trashInFlight = false
            $0.pilePhotoCount = 4
            $0.loadedPhotos = [.p1, .p3, .p4, PhotoAsset.five[4]]
            $0.photos = [.p1, .p3, .p4, PhotoAsset.five[4]]
            $0.activeIndex = 1
            $0.deletedPhotoIDs = []
            $0.trashPhotos = []
            $0.deletedSheetOpen = false
            $0.history = []
            $0.stats = ActivityStats(bytesFreed: 3_000, photosDeleted: 2, todayKey: TestDates.today)
        }
        await store.finish()
        XCTAssertEqual(savedStats.value.last?.bytesFreed, 3_000)
    }

    func test_emptyTrashButton_success_surfacesFreedMilestone() async {
        let store = makeStore(
            SwipeFeature.State(
                byteCounts: ["a1": Milestone.gigabyte],
                deletedPhotoIDs: ["a1"],
                stats: .fresh,
                trashPhotos: [.p1]
            )
        ) {
            $0.photoLibrary = .stubAuthorized()
        }

        await store.send(.emptyTrashButtonTapped) {
            $0.trashInFlight = true
        }
        await store.receive(\.emptyTrashFinished) {
            $0.trashInFlight = false
            $0.deletedPhotoIDs = []
            $0.trashPhotos = []
            $0.milestoneReached = .freed1GB
            $0.stats = ActivityStats(
                bytesFreed: Milestone.gigabyte, photosDeleted: 1,
                reachedMilestones: ["freed1GB"], todayKey: TestDates.today
            )
        }
        await store.send(.milestoneDismissed) {
            $0.milestoneReached = nil
        }
    }

    func test_emptyTrashButton_userCancel_isSilent() async {
        let store = makeStore(
            SwipeFeature.State(deletedPhotoIDs: ["a1"], deletedSheetOpen: true)
        ) {
            var client = PhotoLibraryClient.stubAuthorized()
            client.deleteAssets = { _ in throw PhotoLibraryError.cancelled }
            $0.photoLibrary = client
        }

        await store.send(.emptyTrashButtonTapped) {
            $0.trashInFlight = true
        }
        await store.receive(\.emptyTrashFinished) {
            $0.trashInFlight = false
        }
    }

    func test_emptyTrashButton_failure_surfacesError() async {
        let store = makeStore(
            SwipeFeature.State(deletedPhotoIDs: ["a1"], deletedSheetOpen: true)
        ) {
            var client = PhotoLibraryClient.stubAuthorized()
            client.deleteAssets = { _ in
                throw NSError(domain: "T", code: 1, userInfo: [NSLocalizedDescriptionKey: "oops"])
            }
            $0.photoLibrary = client
        }

        await store.send(.emptyTrashButtonTapped) {
            $0.trashInFlight = true
        }
        await store.receive(\.emptyTrashFinished) {
            $0.trashInFlight = false
            $0.deleteError = "oops"
        }
    }

    // MARK: - Start over vs reset progress

    func test_resetButtonTapped_forgetsPileDecisionsAndRefillsDeck() async {
        let saved = LockIsolated<[SwipeProgress]>([])
        let store = makeStore(
            SwipeFeature.State(
                activeIndex: 5,
                deletedPhotoIDs: [PhotoAsset.p2.id, "other-pile"],
                history: [SwipeHistoryEntry(photoID: PhotoAsset.p1.id, kept: true)],
                keptPhotoIDs: [PhotoAsset.p1.id, PhotoAsset.p3.id],
                libraryLoaded: true,
                loadedPhotos: PhotoAsset.five,
                photos: PhotoAsset.five,
                pilePhotoCount: 5,
                sessionFinishedAt: TestDates.now,
                sessionKeptIDs: [PhotoAsset.p1.id],
                sessionStartedAt: TestDates.now,
                trashPhotos: [.p2, PhotoAsset(id: "other-pile")]
            )
        ) {
            $0.photoLibrary = .stubAuthorized()
            $0.swipeProgress = .recording(into: saved)
        }

        await store.send(.resetButtonTapped) {
            $0.activeIndex = 0
            $0.history = []
            $0.isLoading = true
            $0.sessionFinishedAt = nil
            $0.sessionKeptIDs = []
            $0.sessionStartedAt = nil
        }
        await store.receive(\.photosLoaded) {
            $0.keptPhotoIDs = []
            $0.deletedPhotoIDs = ["other-pile"]
            $0.trashPhotos = [PhotoAsset(id: "other-pile")]
            $0.loadedPhotos = PhotoAsset.five
            $0.photos = PhotoAsset.five
            $0.isLoading = false
        }
        await store.finish()
        XCTAssertEqual(saved.value.last, SwipeProgress(deletedIDs: ["other-pile"], keptIDs: []))
    }

    func test_resetProgressTapped_wipesPersistedListsAndRefetches() async {
        let store = makeStore(
            SwipeFeature.State(
                activeIndex: 2,
                deletedPhotoIDs: ["a1"],
                deletedSheetOpen: true,
                history: [SwipeHistoryEntry(photoID: "a2", kept: true)],
                keptPhotoIDs: ["a2", "a3"],
                photos: PhotoAsset.five,
                trashPhotos: [.p1]
            )
        ) {
            $0.photoLibrary = .stubAuthorized()
        }

        await store.send(.resetProgressTapped) {
            $0.deletedPhotoIDs = []
            $0.keptPhotoIDs = []
            $0.trashPhotos = []
            $0.deletedSheetOpen = false
            $0.activeIndex = 0
            $0.history = []
            $0.isLoading = true
        }
        await store.receive(\.photosLoaded) {
            $0.pilePhotoCount = 5
            $0.loadedPhotos = PhotoAsset.five
            $0.photos = PhotoAsset.five
            $0.isLoading = false
            $0.libraryLoaded = true
        }
    }

    // MARK: - Shuffle

    func test_shuffleButtonTapped_enable_savesPositionAndResets() async {
        let store = makeStore(
            SwipeFeature.State(
                activeIndex: 0,
                history: [SwipeHistoryEntry(photoID: "x", kept: true)],
                loadedPhotos: [.p1],
                photos: [.p1]
            )
        )

        await store.send(.shuffleButtonTapped) {
            $0.shuffleEnabled = true
            $0.unshuffledIndex = 0
            $0.photos = [.p1]
            $0.activeIndex = 0
            $0.history = []
        }
    }

    func test_shuffleButtonTapped_enable_dropsPhotosAlreadyClassified() async {
        let store = makeStore(
            SwipeFeature.State(
                activeIndex: 2,
                keptPhotoIDs: [PhotoAsset.p1.id, PhotoAsset.p2.id],
                loadedPhotos: [.p1, .p2, .p3],
                photos: [.p1, .p2, .p3]
            )
        )

        await store.send(.shuffleButtonTapped) {
            $0.shuffleEnabled = true
            $0.unshuffledIndex = 2
            $0.photos = [.p3]
            $0.activeIndex = 0
        }
    }

    func test_shuffleButtonTapped_disable_restoresPositionSkippingClassified() async {
        let store = makeStore(
            SwipeFeature.State(
                activeIndex: 1,
                keptPhotoIDs: [PhotoAsset.p4.id],
                loadedPhotos: PhotoAsset.five,
                photos: [.p4, .p2, .p1, .p3, PhotoAsset.five[4]],
                shuffleEnabled: true,
                unshuffledIndex: 3
            )
        )

        await store.send(.shuffleButtonTapped) {
            $0.shuffleEnabled = false
            $0.photos = [.p1, .p2, .p3, PhotoAsset.five[4]]
            $0.activeIndex = 3
        }
    }

    func test_shuffleDisable_whenUnshuffledIndexBeyondList_clampsToSize() async {
        let store = makeStore(
            SwipeFeature.State(
                activeIndex: 1,
                loadedPhotos: [.p1, .p2],
                photos: [.p2, .p1],
                shuffleEnabled: true,
                unshuffledIndex: 99
            )
        )

        await store.send(.shuffleButtonTapped) {
            $0.shuffleEnabled = false
            $0.photos = [.p1, .p2]
            $0.activeIndex = 2
        }
    }

    // MARK: - Fullscreen

    func test_fullscreenMarkAtRelativeIndex_classifiesTargetAndSkipsAhead() async {
        let store = makeStore(
            SwipeFeature.State(fullscreenOpen: true, photos: PhotoAsset.five, stats: .fresh)
        )

        await store.send(.fullscreenMarkAtRelativeIndex(localIdx: 2, kept: false)) {
            $0.deletedPhotoIDs = [PhotoAsset.p3.id]
            $0.trashPhotos = [.p3]
            $0.history = [SwipeHistoryEntry(photoID: PhotoAsset.p3.id, kept: false)]
            $0.activeIndex = 3
            $0.fullscreenOpen = false
            $0.sessionDeletedIDs = [PhotoAsset.p3.id]
            $0.sessionStartedAt = TestDates.now
            $0.stats = .reviewed(1)
        }
        await store.receive(\.fileSizesLoaded)
        await store.send(.undoButtonTapped) {
            $0.deletedPhotoIDs = []
            $0.trashPhotos = []
            $0.history = []
            $0.activeIndex = 2
            $0.sessionDeletedIDs = []
        }
    }

    func test_fullscreenMarkAtRelativeIndex_outOfRange_isNoOp() async {
        let store = makeStore(SwipeFeature.State(activeIndex: 4, photos: PhotoAsset.five))

        await store.send(.fullscreenMarkAtRelativeIndex(localIdx: 1, kept: true))
        await store.send(.fullscreenMarkAtRelativeIndex(localIdx: -1, kept: true))
    }

    // MARK: - Similar photos

    private var burst: [PhotoAsset] {
        [.shot("b1", at: 1000), .shot("b2", at: 1003), .shot("b3", at: 1006), .shot("lone", at: 5000)]
    }

    func test_photosLoaded_buildsRunsAndRequestsHashesForTheWindow() async {
        let store = makeStore(SwipeFeature.State()) {
            $0.photoLibrary = .stubAuthorized(hashes: ["b1": 0, "b2": 1, "b3": UInt64.max])
        }

        await store.send(.photosLoaded(burst, purpose: .initial)) {
            $0.pilePhotoCount = 4
            $0.loadedPhotos = self.burst
            $0.photos = self.burst
            $0.libraryLoaded = true
            $0.similarRuns = [["b1", "b2", "b3"]]
        }
        await store.receive(\.similarityHashesLoaded) {
            $0.similarityHashes = ["b1": 0, "b2": 1, "b3": UInt64.max]
            $0.similarGroups = [["b1", "b2"]]
        }
        XCTAssertEqual(store.state.topSimilarGroup.map(\.id), ["b1", "b2"])
    }

    func test_photosLoaded_withDetectSimilarOff_skipsHashing() async {
        let store = makeStore(
            SwipeFeature.State(settings: UserSettings(detectSimilar: false))
        )

        await store.send(.photosLoaded(burst, purpose: .initial)) {
            $0.pilePhotoCount = 4
            $0.loadedPhotos = self.burst
            $0.photos = self.burst
            $0.libraryLoaded = true
        }
    }

    func test_similarityHashesLoaded_confirmsGroupsFromRuns() async {
        let store = makeStore(
            SwipeFeature.State(
                loadedPhotos: burst,
                photos: burst,
                similarityHashes: ["b1": 0, "b2": 0, "b3": 0],
                similarRuns: [["b1", "b2", "b3"]]
            )
        )

        await store.send(.similarityHashesLoaded(["b3": 0])) {
            $0.similarGroups = [["b1", "b2", "b3"]]
        }
    }

    func test_similarBadgeTapped_opensSheetOnlyWithAGroup() async {
        let store = makeStore(
            SwipeFeature.State(loadedPhotos: burst, photos: burst, similarGroups: [["b1", "b2"]])
        )

        await store.send(.similarBadgeTapped) {
            $0.similarSheetOpen = true
        }
        await store.send(.binding(.set(\.similarSheetOpen, false))) {
            $0.similarSheetOpen = false
        }
        await store.send(.pilePicked(.all)) {
            $0.isLoading = true
        }
        await store.receive(\.photosLoaded) {
            $0.pilePhotoCount = 0
            $0.loadedPhotos = []
            $0.photos = []
            $0.isLoading = false
            $0.libraryLoaded = true
            $0.similarGroups = []
        }
        await store.send(.similarBadgeTapped)
    }

    func test_similarGroupResolved_classifiesEveryMemberAndSkipsPastThem() async {
        let clock = TestClock()
        let store = makeStore(
            SwipeFeature.State(
                loadedPhotos: burst,
                photos: burst,
                similarGroups: [["b1", "b2", "b3"]],
                similarSheetOpen: true,
                stats: .fresh
            )
        ) {
            $0.continuousClock = clock
            $0.photoLibrary = .stubAuthorized(fileSizes: ["b2": 10, "b3": 20])
        }

        await store.send(.similarGroupResolved(keptIDs: ["b1"], deletedIDs: ["b2", "b3"])) {
            $0.keptPhotoIDs = ["b1"]
            $0.deletedPhotoIDs = ["b2", "b3"]
            $0.trashPhotos = [.shot("b2", at: 1003), .shot("b3", at: 1006)]
            $0.history = [
                SwipeHistoryEntry(decisions: [
                    PhotoDecision(photoID: "b1", kept: true),
                    PhotoDecision(photoID: "b2", kept: false),
                    PhotoDecision(photoID: "b3", kept: false),
                ]),
            ]
            $0.activeIndex = 3
            $0.similarSheetOpen = false
            $0.sessionKeptIDs = ["b1"]
            $0.sessionDeletedIDs = ["b2", "b3"]
            $0.sessionStartedAt = TestDates.now
            $0.stats = .reviewed(3)
        }
        await store.receive(\.fileSizesLoaded) {
            $0.byteCounts = ["b2": 10, "b3": 20]
        }
        XCTAssertEqual(store.state.topPhoto?.id, "lone")
        XCTAssertEqual(store.state.sessionBytesToFree, 30)
        XCTAssertTrue(store.state.topSimilarGroup.isEmpty)
    }

    func test_similarGroupResolved_unknownIDs_isNoOp() async {
        let store = makeStore(SwipeFeature.State(photos: burst, similarSheetOpen: true))

        await store.send(.similarGroupResolved(keptIDs: ["nope"], deletedIDs: [])) {
            $0.similarSheetOpen = false
        }
    }

    // MARK: - Settings

    func test_settingsToggles_updateStateAndPersist() async {
        let saved = LockIsolated<[UserSettings]>([])
        let store = makeStore {
            $0.settings = SettingsClient(
                load: { .default },
                save: { value in saved.withValue { $0.append(value) } }
            )
        }

        await store.send(.settingsShowLocationToggled(false)) {
            $0.settings.showLocation = false
        }
        await store.send(.settingsPlayLivePhotosToggled(false)) {
            $0.settings.playLivePhotos = false
        }
        await store.send(.settingsGamificationToggled(false)) {
            $0.settings.gamificationEnabled = false
        }
        await store.send(.settingsDailyGoalChanged(100)) {
            $0.settings.dailyGoal = 100
        }
        await store.send(.settingsReminderHourChanged(8)) {
            $0.settings.reminderHour = 8
        }
        await store.finish()
        XCTAssertEqual(saved.value.count, 5)
        XCTAssertEqual(
            saved.value.last,
            UserSettings(
                dailyGoal: 100, gamificationEnabled: false, playLivePhotos: false,
                reminderHour: 8, showLocation: false
            )
        )
    }

    func test_settingsIncludeVideosToggled_reloadsPileAndPiles() async {
        let video = PhotoAsset(id: "v1", isVideo: true, durationSeconds: 12)
        let store = makeStore(
            SwipeFeature.State(
                activeIndex: 2, libraryLoaded: true, photos: PhotoAsset.five, pilePhotoCount: 5,
                settings: UserSettings(includeVideos: false)
            )
        ) {
            $0.photoLibrary = .stubAuthorized(photos: PhotoAsset.five + [video], piles: [.all])
        }

        await store.send(.settingsIncludeVideosToggled(true)) {
            $0.activeIndex = 0
            $0.isLoading = true
            $0.settings.includeVideos = true
        }
        await store.receive(\.photosLoaded) {
            $0.pilePhotoCount = 6
            $0.loadedPhotos = PhotoAsset.five + [video]
            $0.photos = PhotoAsset.five + [video]
            $0.isLoading = false
        }
        await store.receive(\.pilesLoaded) {
            $0.piles = [.all]
        }
    }

    func test_settingsDetectSimilarToggled_offClearsGroupsOnRebuildsThem() async {
        let store = makeStore(
            SwipeFeature.State(
                loadedPhotos: burst,
                photos: burst,
                similarGroups: [["b1", "b2"]],
                similarityHashes: ["b1": 0, "b2": 0, "b3": 0],
                similarRuns: [["b1", "b2", "b3"]]
            )
        )

        await store.send(.settingsDetectSimilarToggled(false)) {
            $0.settings.detectSimilar = false
            $0.similarGroups = []
            $0.similarRuns = []
        }
        await store.send(.settingsDetectSimilarToggled(true)) {
            $0.settings.detectSimilar = true
            $0.similarRuns = [["b1", "b2", "b3"]]
            $0.similarGroups = [["b1", "b2", "b3"]]
        }
    }

    func test_settingsReminderToggled_granted_enablesAndSchedules() async {
        let scheduled = LockIsolated<[Int]>([])
        let store = makeStore(SwipeFeature.State(settings: UserSettings(dailyGoal: 25, reminderHour: 20))) {
            $0.notifications = NotificationClient(
                cancelReminder: {},
                requestAuthorization: { true },
                scheduleDailyReminder: { hour, goal in scheduled.withValue { $0.append(hour * 1000 + goal) } }
            )
        }

        await store.send(.settingsReminderToggled(true))
        await store.receive(\.reminderAuthorizationResolved) {
            $0.settings.reminderEnabled = true
        }
        await store.finish()
        XCTAssertEqual(scheduled.value, [20_025])
    }

    func test_settingsReminderToggled_refused_keepsSettingOff() async {
        let store = makeStore {
            $0.notifications = NotificationClient(
                cancelReminder: {},
                requestAuthorization: { false },
                scheduleDailyReminder: { _, _ in XCTFail("must not schedule") }
            )
        }

        await store.send(.settingsReminderToggled(true))
        await store.receive(\.reminderAuthorizationResolved)
        XCTAssertFalse(store.state.settings.reminderEnabled)
    }

    func test_settingsReminderToggled_off_cancels() async {
        let cancelled = LockIsolated(false)
        let store = makeStore(SwipeFeature.State(settings: UserSettings(reminderEnabled: true))) {
            $0.notifications = NotificationClient(
                cancelReminder: { cancelled.setValue(true) },
                requestAuthorization: { true },
                scheduleDailyReminder: { _, _ in }
            )
        }

        await store.send(.settingsReminderToggled(false)) {
            $0.settings.reminderEnabled = false
        }
        await store.finish()
        XCTAssertTrue(cancelled.value)
    }

    func test_settingsDailyGoalChanged_withReminderOn_reschedules() async {
        let scheduled = LockIsolated<[Int]>([])
        let store = makeStore(SwipeFeature.State(settings: UserSettings(reminderEnabled: true, reminderHour: 9))) {
            $0.notifications = NotificationClient(
                cancelReminder: {},
                requestAuthorization: { true },
                scheduleDailyReminder: { _, goal in scheduled.withValue { $0.append(goal) } }
            )
        }

        await store.send(.settingsDailyGoalChanged(200)) {
            $0.settings.dailyGoal = 200
        }
        await store.finish()
        XCTAssertEqual(scheduled.value, [200])
    }

    // MARK: - Goals, streaks, milestones

    func test_reachingTheDailyGoal_celebratesOnce() async {
        let clock = TestClock()
        let store = makeStore(
            SwipeFeature.State(
                photos: PhotoAsset.five,
                settings: UserSettings(dailyGoal: 2),
                stats: ActivityStats(currentStreak: 1, lastGoalDay: "2000-01-01", longestStreak: 1, todayCount: 0, todayKey: TestDates.today)
            )
        ) {
            $0.continuousClock = clock
        }

        await store.send(.keepButtonTapped) {
            $0.activeIndex = 1
            $0.keptPhotoIDs = ["a1"]
            $0.history = [SwipeHistoryEntry(photoID: "a1", kept: true)]
            $0.exitingDirection = 1
            $0.exitingPhoto = .p1
            $0.sessionKeptIDs = ["a1"]
            $0.sessionStartedAt = TestDates.now
            $0.stats.photosReviewed = 1
            $0.stats.todayCount = 1
            $0.stats.currentStreak = 0
        }
        XCTAssertFalse(store.state.goalReachedToday)
        await store.send(.keepButtonTapped) {
            $0.activeIndex = 2
            $0.keptPhotoIDs = ["a1", "a2"]
            $0.history = [
                SwipeHistoryEntry(photoID: "a1", kept: true),
                SwipeHistoryEntry(photoID: "a2", kept: true),
            ]
            $0.exitingPhoto = .p2
            $0.sessionKeptIDs = ["a1", "a2"]
            $0.stats.photosReviewed = 2
            $0.stats.todayCount = 2
            $0.stats.currentStreak = 1
            $0.stats.lastGoalDay = TestDates.today
            $0.goalCelebration = true
        }
        XCTAssertTrue(store.state.goalReachedToday)
        await store.send(.goalCelebrationDismissed) {
            $0.goalCelebration = false
        }
        await store.send(.keepButtonTapped) {
            $0.activeIndex = 3
            $0.keptPhotoIDs = ["a1", "a2", "a3"]
            $0.history = [
                SwipeHistoryEntry(photoID: "a1", kept: true),
                SwipeHistoryEntry(photoID: "a2", kept: true),
                SwipeHistoryEntry(photoID: "a3", kept: true),
            ]
            $0.exitingPhoto = .p3
            $0.sessionKeptIDs = ["a1", "a2", "a3"]
            $0.stats.photosReviewed = 3
            $0.stats.todayCount = 3
        }
        await clock.advance(by: .seconds(1))
        await store.receive(\.swipeExitAnimationCompleted) {
            $0.exitingDirection = 0
            $0.exitingPhoto = nil
        }
    }

    func test_gamificationOff_countsButNeverCelebrates() async {
        let clock = TestClock()
        let store = makeStore(
            SwipeFeature.State(
                photos: PhotoAsset.five,
                settings: UserSettings(dailyGoal: 1, gamificationEnabled: false),
                stats: ActivityStats(photosReviewed: 99, todayKey: TestDates.today)
            )
        ) {
            $0.continuousClock = clock
        }

        await store.send(.keepButtonTapped) {
            $0.activeIndex = 1
            $0.keptPhotoIDs = ["a1"]
            $0.history = [SwipeHistoryEntry(photoID: "a1", kept: true)]
            $0.exitingDirection = 1
            $0.exitingPhoto = .p1
            $0.sessionKeptIDs = ["a1"]
            $0.sessionStartedAt = TestDates.now
            $0.stats.photosReviewed = 100
            $0.stats.todayCount = 1
        }
        XCTAssertFalse(store.state.goalReachedToday)
        XCTAssertNil(store.state.milestoneReached)
        await clock.advance(by: .seconds(1))
        await store.receive(\.swipeExitAnimationCompleted) {
            $0.exitingDirection = 0
            $0.exitingPhoto = nil
        }
    }

    func test_reviewedMilestone_surfacesOnceAndTurningGamificationOffHidesIt() async {
        let clock = TestClock()
        let store = makeStore(
            SwipeFeature.State(
                photos: PhotoAsset.five,
                settings: UserSettings(dailyGoal: 500),
                stats: ActivityStats(photosReviewed: 99, todayKey: TestDates.today)
            )
        ) {
            $0.continuousClock = clock
        }

        await store.send(.keepButtonTapped) {
            $0.activeIndex = 1
            $0.keptPhotoIDs = ["a1"]
            $0.history = [SwipeHistoryEntry(photoID: "a1", kept: true)]
            $0.exitingDirection = 1
            $0.exitingPhoto = .p1
            $0.sessionKeptIDs = ["a1"]
            $0.sessionStartedAt = TestDates.now
            $0.stats.photosReviewed = 100
            $0.stats.todayCount = 1
            $0.stats.reachedMilestones = ["reviewed100"]
            $0.milestoneReached = .reviewed100
        }
        await store.send(.settingsGamificationToggled(false)) {
            $0.settings.gamificationEnabled = false
            $0.milestoneReached = nil
        }
        await clock.advance(by: .seconds(1))
        await store.receive(\.swipeExitAnimationCompleted) {
            $0.exitingDirection = 0
            $0.exitingPhoto = nil
        }
    }

    func test_statsButtonAndReset() async {
        let savedStats = LockIsolated<[ActivityStats]>([])
        let store = makeStore(
            SwipeFeature.State(
                goalCelebration: true,
                milestoneReached: .streak7,
                stats: ActivityStats(bytesFreed: 9, currentStreak: 7, photosReviewed: 300, todayKey: TestDates.today)
            )
        ) {
            $0.stats = .recording(into: savedStats)
        }

        await store.send(.statsButtonTapped) {
            $0.statsOpen = true
        }
        await store.send(.statsResetTapped) {
            $0.stats = .fresh
            $0.goalCelebration = false
            $0.milestoneReached = nil
        }
        await store.finish()
        XCTAssertEqual(savedStats.value, [.fresh])
    }

    // MARK: - Presentation flags (BindableAction)

    func test_binding_setPickerOpenFalse_dismissesPicker() async {
        let store = makeStore(SwipeFeature.State(pickerOpen: true))

        await store.send(.binding(.set(\.pickerOpen, false))) {
            $0.pickerOpen = false
        }
    }

    func test_binding_closingDeletedSheet_clearsError() async {
        let store = makeStore(SwipeFeature.State(deleteError: "oops", deletedSheetOpen: true))

        await store.send(.binding(.set(\.deletedSheetOpen, false))) {
            $0.deletedSheetOpen = false
            $0.deleteError = nil
        }
    }

    func test_topCardTapped_clearsDragAndOpensFullscreen() async {
        let store = makeStore(
            SwipeFeature.State(drag: CGSize(width: 3, height: 2), isDragging: true, photos: PhotoAsset.five)
        )

        await store.send(.topCardTapped) {
            $0.drag = .zero
            $0.isDragging = false
            $0.fullscreenOpen = true
        }
    }

    func test_settingsButtonTapped_opensSettings() async {
        let store = makeStore()
        await store.send(.settingsButtonTapped) { $0.settingsOpen = true }
    }

    func test_aboutMenuItemTapped_opensAbout() async {
        let store = makeStore()
        await store.send(.aboutMenuItemTapped) { $0.aboutOpen = true }
    }

    // MARK: - viewInPhotos effect

    func test_viewInPhotosTapped_opensPhotosAppUrl() async {
        let openedURL = LockIsolated<URL?>(nil)
        let store = makeStore {
            $0.openURL = OpenURLEffect { url in
                openedURL.withValue { $0 = url }
                return true
            }
        }

        await store.send(.viewInPhotosTapped)
        await store.finish()
        XCTAssertEqual(openedURL.value, URL(string: "photos-redirect://"))
    }

    // MARK: - Photo loading + shuffle interaction

    func test_photosLoaded_whileShuffled_shufflesIncomingList() async {
        let store = makeStore(SwipeFeature.State(shuffleEnabled: true))

        await store.send(.photosLoaded([.p1], purpose: .initial)) {
            $0.pilePhotoCount = 1
            $0.loadedPhotos = [.p1]
            $0.photos = [.p1]
            $0.libraryLoaded = true
        }
    }

    func test_photosLoaded_withClassifiedIDs_filtersThemOut() async {
        let store = makeStore(
            SwipeFeature.State(deletedPhotoIDs: [PhotoAsset.p2.id], keptPhotoIDs: [PhotoAsset.p4.id])
        )

        await store.send(.photosLoaded(PhotoAsset.five, purpose: .initial)) {
            $0.pilePhotoCount = 5
            $0.loadedPhotos = [.p1, .p3, PhotoAsset.five[4]]
            $0.photos = [.p1, .p3, PhotoAsset.five[4]]
            $0.libraryLoaded = true
        }
    }

    // MARK: - Cancellation

    func test_resetButton_cancelsPendingSwipeAnimation() async {
        let clock = TestClock()
        let store = makeStore(
            SwipeFeature.State(drag: CGSize(width: 150, height: 0), photos: PhotoAsset.five, stats: .fresh)
        ) {
            $0.continuousClock = clock
            $0.photoLibrary = .stubAuthorized()
        }

        await store.send(.cardDragEnded) {
            $0.isDragging = false
            $0.exitingDirection = 1
            $0.exitingPhoto = PhotoAsset.five[0]
            $0.exitingStartDrag = CGSize(width: 150, height: 0)
            $0.activeIndex = 1
            $0.keptPhotoIDs = [PhotoAsset.five[0].id]
            $0.history = [SwipeHistoryEntry(photoID: PhotoAsset.p1.id, kept: true)]
            $0.drag = .zero
            $0.sessionKeptIDs = [PhotoAsset.p1.id]
            $0.sessionStartedAt = TestDates.now
            $0.stats = .reviewed(1)
        }

        await store.send(.resetButtonTapped) {
            $0.activeIndex = 0
            $0.history = []
            $0.exitingDirection = 0
            $0.exitingPhoto = nil
            $0.exitingStartDrag = .zero
            $0.isLoading = true
            $0.sessionKeptIDs = []
            $0.sessionStartedAt = nil
        }
        await store.receive(\.photosLoaded) {
            $0.keptPhotoIDs = []
            $0.pilePhotoCount = 5
            $0.loadedPhotos = PhotoAsset.five
            $0.photos = PhotoAsset.five
            $0.isLoading = false
            $0.libraryLoaded = true
        }

        await clock.advance(by: .milliseconds(SwipeFeature.exitAnimationDurationMs * 2))
        await store.finish()
    }

    // MARK: - Computed properties

    func test_currentPile_matchesSelector() {
        XCTAssertEqual(SwipeFeature.State(piles: Pile.three, selectedPile: Pile.trip.selector).currentPile, .trip)
        XCTAssertNil(SwipeFeature.State(piles: Pile.three, selectedPile: .screenshots).currentPile)
        XCTAssertNil(SwipeFeature.State().currentPile)
    }

    func test_topPhoto_returnsAtActiveIndex() {
        let state = SwipeFeature.State(activeIndex: 2, photos: PhotoAsset.five)
        XCTAssertEqual(state.topPhoto, .p3)
        XCTAssertEqual(state.remainingCount, 3)
    }

    func test_topPhoto_nilWhenPastEnd() {
        let state = SwipeFeature.State(activeIndex: 99, photos: PhotoAsset.five)
        XCTAssertNil(state.topPhoto)
        XCTAssertEqual(state.remainingCount, 0)
    }

    func test_topPhoto_nilWhenEmpty() {
        XCTAssertNil(SwipeFeature.State().topPhoto)
    }

    func test_keptAndDeleted_areDerivedFromLists() {
        let state = SwipeFeature.State(deletedPhotoIDs: ["a", "b", "c"], keptPhotoIDs: ["x", "y"])
        XCTAssertEqual(state.kept, 2)
        XCTAssertEqual(state.deleted, 3)
    }

    func test_topSimilarGroup_filtersClassifiedMembers() {
        let state = SwipeFeature.State(
            keptPhotoIDs: ["b2"],
            loadedPhotos: burst,
            photos: burst,
            similarGroups: [["b1", "b2", "b3"]]
        )
        XCTAssertEqual(state.topSimilarGroup.map(\.id), ["b1", "b3"])
        let single = SwipeFeature.State(
            keptPhotoIDs: ["b2", "b3"],
            loadedPhotos: burst,
            photos: burst,
            similarGroups: [["b1", "b2", "b3"]]
        )
        XCTAssertTrue(single.topSimilarGroup.isEmpty)
    }

    func test_doneAndEmpty_dependOnPilePhotoCount() {
        XCTAssertFalse(SwipeFeature.State().isDone)
        XCTAssertFalse(SwipeFeature.State().isPileEmpty)
        XCTAssertTrue(SwipeFeature.State(libraryLoaded: true, pilePhotoCount: 0).isPileEmpty)
        let allClassified = SwipeFeature.State(libraryLoaded: true, pilePhotoCount: 5)
        XCTAssertTrue(allClassified.isDone)
        XCTAssertFalse(allClassified.isPileEmpty)
        XCTAssertFalse(SwipeFeature.State(activeIndex: 2, photos: PhotoAsset.five, pilePhotoCount: 5).isDone)
        XCTAssertTrue(SwipeFeature.State(activeIndex: 5, photos: PhotoAsset.five, pilePhotoCount: 5).isDone)
    }

    func test_userSettings_decodesOlderBlobsWithDefaults() throws {
        let data = Data(#"{"playLivePhotos":false,"showLocation":false}"#.utf8)
        let decoded = try JSONDecoder().decode(UserSettings.self, from: data)
        XCTAssertEqual(decoded, UserSettings(playLivePhotos: false, showLocation: false))
        XCTAssertEqual(decoded.dailyGoal, 50)
    }
}
