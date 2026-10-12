//
//  SwipeFeature.swift
//  Unrolled
//

import ComposableArchitecture
import Foundation

@Reducer
struct SwipeFeature {
    @ObservableState
    struct State: Equatable {
        var aboutOpen = false
        var activeIndex = 0
        var authorizationStatus: PhotoAuthorizationStatus = .notDetermined
        /// Known file sizes by photo id. Filled lazily for trash and
        /// deleted photos only: the whole deck would be too slow.
        var byteCounts: [String: Int64] = [:]
        /// IDs of photos the user has swiped-delete, in deletion order.
        /// Staged until "Empty trash" hands them to PhotoKit. Persisted.
        var deletedPhotoIDs: [String] = []
        var deleteError: String?
        var deletedSheetOpen = false
        var drag: CGSize = .zero
        /// -1 delete, +1 keep, 0 none. Read by `ExitingCardView`.
        var exitingDirection = 0
        var exitingPhoto: PhotoAsset?
        var exitingStartDrag: CGSize = .zero
        var fullscreenOpen = false
        var goalCelebration = false
        var history: [SwipeHistoryEntry] = []
        var isDragging = false
        var isLoading = false
        /// IDs of photos the user has swiped-keep across all sessions.
        /// Persisted alongside `deletedPhotoIDs`; filters the deck.
        var keptPhotoIDs: [String] = []
        var libraryLoaded = false
        /// Pile photos minus the ones classified before the load
        /// (unshuffled source of truth for the deck).
        var loadedPhotos: [PhotoAsset] = []
        var milestoneReached: Milestone?
        /// The deck: `loadedPhotos`, possibly shuffled. Photos classified
        /// during the session stay in it so undo can bring them back.
        var photos: [PhotoAsset] = []
        var pickerOpen = false
        /// Unfiltered size of the last loaded pile. Distinguishes "the
        /// pile is empty" from "every photo is already classified".
        var pilePhotoCount = 0
        var piles: [Pile] = []
        var selectedPile: PileSelector = .all
        var sessionDeletedIDs: Set<String> = []
        var sessionFinishedAt: Date?
        var sessionKeptIDs: Set<String> = []
        var sessionStartedAt: Date?
        var settings: UserSettings = .default
        var settingsOpen = false
        var shuffleEnabled = false
        var similarGroups: [[String]] = []
        var similarityHashes: [String: UInt64] = [:]
        /// Time-adjacent runs of the current pile, awaiting hashes.
        var similarRuns: [[String]] = []
        var similarSheetOpen = false
        var stats: ActivityStats = .empty
        var statsOpen = false
        var trashInFlight = false
        /// Resolved assets for `deletedPhotoIDs`, same order.
        var trashPhotos: [PhotoAsset] = []
        /// Position in `loadedPhotos` when shuffle was enabled, so that
        /// disabling it resumes at the same place.
        var unshuffledIndex: Int = 0

        var deleted: Int { deletedPhotoIDs.count }
        var kept: Int { keptPhotoIDs.count }

        var isPileEmpty: Bool { libraryLoaded && pilePhotoCount == 0 }
        var isDone: Bool { pilePhotoCount > 0 && activeIndex >= photos.count }

        var remainingCount: Int { max(0, photos.count - activeIndex) }

        var topPhoto: PhotoAsset? {
            guard activeIndex < photos.count else { return nil }
            return photos[activeIndex]
        }

        var currentPile: Pile? {
            piles.first { $0.selector == selectedPile }
        }

        var classifiedIDs: Set<String> {
            Set(deletedPhotoIDs).union(keptPhotoIDs)
        }

        var goalReachedToday: Bool {
            settings.gamificationEnabled && stats.todayCount >= settings.dailyGoal
        }

        var sessionReviewed: Int { sessionKeptIDs.count + sessionDeletedIDs.count }

        var sessionBytesToFree: Int64 {
            sessionDeletedIDs.reduce(0) { $0 + (byteCounts[$1] ?? 0) }
        }

        var sessionDuration: TimeInterval {
            guard let sessionStartedAt, let sessionFinishedAt else { return 0 }
            return max(0, sessionFinishedAt.timeIntervalSince(sessionStartedAt))
        }

        var sessionPhotosPerMinute: Double {
            guard sessionDuration >= 1 else { return 0 }
            return Double(sessionReviewed) / (sessionDuration / 60)
        }

        /// Undecided members of the similar group the top card belongs
        /// to, in deck order. Empty unless at least two remain.
        var topSimilarGroup: [PhotoAsset] {
            guard let topID = topPhoto?.id,
                  let group = similarGroups.first(where: { $0.contains(topID) })
            else { return [] }
            let classified = classifiedIDs
            let members = Set(group).subtracting(classified)
            let ordered = loadedPhotos.filter { members.contains($0.id) }
            return ordered.count >= 2 ? ordered : []
        }
    }

    enum Action: BindableAction {
        case aboutMenuItemTapped
        case authorizationStatusReceived(PhotoAuthorizationStatus)
        case binding(BindingAction<State>)
        case cardDragChanged(CGSize)
        case cardDragEnded
        /// "Change selection" on the empty state of a `.limited` user.
        case changeSelectionTapped
        case deleteButtonTapped
        case deletedPhotoRestoreTapped(id: String)
        case deletedSheetButtonTapped
        case emptyTrashButtonTapped
        case emptyTrashFinished(EmptyTrashOutcome)
        case fileSizesLoaded([String: Int64])
        /// Keep/delete from the fullscreen pager. `localIdx` is the
        /// offset from `activeIndex`; skipped photos stay undecided.
        case fullscreenMarkAtRelativeIndex(localIdx: Int, kept: Bool)
        case goalCelebrationDismissed
        case keepButtonTapped
        /// Scene became active again or the limited picker closed:
        /// re-read the library without disturbing the deck.
        case libraryRefreshRequested
        case milestoneDismissed
        case onAppear
        case openSettingsTapped
        case photosLoaded([PhotoAsset], purpose: LoadPurpose)
        case pilePicked(PileSelector)
        case pilesLoaded([Pile])
        case pileTitleTapped
        case reminderAuthorizationResolved(Bool)
        case requestAuthorizationTapped
        /// "Start over" on the done view: forgets every decision taken
        /// on the current pile and reloads it.
        case resetButtonTapped
        /// Wipes the persisted `keptPhotoIDs` + `deletedPhotoIDs`.
        case resetProgressTapped
        case settingsButtonTapped
        case settingsDailyGoalChanged(Int)
        case settingsDetectSimilarToggled(Bool)
        case settingsGamificationToggled(Bool)
        case settingsIncludeVideosToggled(Bool)
        case settingsPlayLivePhotosToggled(Bool)
        case settingsReminderHourChanged(Int)
        case settingsReminderToggled(Bool)
        case settingsShowLocationToggled(Bool)
        case shuffleButtonTapped
        case similarBadgeTapped
        case similarGroupResolved(keptIDs: [String], deletedIDs: [String])
        case similarityHashesLoaded([String: UInt64])
        case statsButtonTapped
        case statsResetTapped
        case swipeExitAnimationCompleted
        case topCardTapped
        case trashPhotosLoaded([PhotoAsset])
        case undoButtonTapped
        case viewAllPhotosTapped
        case viewInPhotosTapped
    }

    enum CancelID {
        case emptyTrash, fetchHashes, fetchPhotos, fetchPiles, fetchSizes, fetchTrash, swipeAnimation
    }

    static let dragThreshold: CGFloat = 110
    /// The overlay animation is 260 ms; 60 ms of slack keeps the card
    /// from being cleared mid-flight.
    static let exitAnimationDurationMs = 320
    /// How far ahead of the top card similarity hashes are requested.
    static let hashLookahead = 30

    @Dependency(\.continuousClock) var clock
    @Dependency(\.date) var date
    @Dependency(\.notifications) var notifications
    @Dependency(\.openURL) var openURL
    @Dependency(\.photoLibrary) var photoLibrary
    @Dependency(\.settings) var settings
    @Dependency(\.stats) var statsClient
    @Dependency(\.swipeProgress) var swipeProgress

    var body: some Reducer<State, Action> {
        BindingReducer()
        Reduce { state, action in
            switch action {
            case .aboutMenuItemTapped:
                state.aboutOpen = true
                return .none

            case .authorizationStatusReceived(let status):
                state.authorizationStatus = status
                if status.grantsRead {
                    return loadLibrary(&state)
                }
                return .none

            case .binding(\.deletedSheetOpen):
                if !state.deletedSheetOpen {
                    state.deleteError = nil
                }
                return .none

            case .binding:
                return .none

            case .cardDragChanged(let translation):
                state.isDragging = true
                state.drag = translation
                return .none

            case .cardDragEnded:
                state.isDragging = false
                let x = state.drag.width
                if x > Self.dragThreshold {
                    return startSwipe(&state, direction: 1)
                } else if x < -Self.dragThreshold {
                    return startSwipe(&state, direction: -1)
                } else {
                    state.drag = .zero
                    return .none
                }

            case .changeSelectionTapped:
                return .run { send in
                    await photoLibrary.presentLimitedLibraryPicker()
                    await send(.libraryRefreshRequested)
                }

            case .deleteButtonTapped:
                return startSwipe(&state, direction: -1)

            case .deletedPhotoRestoreTapped(let id):
                guard state.deletedPhotoIDs.contains(id) else { return .none }
                state.deletedPhotoIDs.removeAll { $0 == id }
                state.trashPhotos.removeAll { $0.id == id }
                if !state.keptPhotoIDs.contains(id) {
                    state.keptPhotoIDs.append(id)
                }
                if state.sessionDeletedIDs.remove(id) != nil {
                    state.sessionKeptIDs.insert(id)
                }
                state.history = state.history.compactMap { entry in
                    let remaining = entry.decisions.filter { $0.photoID != id }
                    return remaining.isEmpty ? nil : SwipeHistoryEntry(decisions: remaining)
                }
                return persistProgress(state)

            case .deletedSheetButtonTapped:
                state.deletedSheetOpen = true
                return .none

            case .emptyTrashButtonTapped:
                // PhotoKit shows its own mandatory dialog, so there is
                // no in-app confirmation step.
                guard !state.deletedPhotoIDs.isEmpty,
                      !state.trashInFlight else { return .none }
                state.trashInFlight = true
                let ids = state.deletedPhotoIDs
                return .run { send in
                    do {
                        try await photoLibrary.deleteAssets(ids)
                        await send(.emptyTrashFinished(.success))
                    } catch PhotoLibraryError.cancelled {
                        await send(.emptyTrashFinished(.cancelled))
                    } catch {
                        await send(.emptyTrashFinished(.failure(error.localizedDescription)))
                    }
                }
                .cancellable(id: CancelID.emptyTrash)

            case .emptyTrashFinished(let outcome):
                state.trashInFlight = false
                switch outcome {
                case .success:
                    // Prune the trashed assets from the deck and keep
                    // the user on the same photo. History is wiped: a
                    // real delete cannot be undone.
                    let trashedIDs = state.deletedPhotoIDs
                    let deletedSet = Set(trashedIDs)
                    let topID = state.topPhoto?.id
                    let removedFromDeck = state.photos.filter { deletedSet.contains($0.id) }.count
                    state.loadedPhotos.removeAll { deletedSet.contains($0.id) }
                    state.photos.removeAll { deletedSet.contains($0.id) }
                    state.pilePhotoCount = max(0, state.pilePhotoCount - removedFromDeck)
                    remapActiveIndex(&state, topID: topID)
                    state.deletedPhotoIDs.removeAll()
                    state.trashPhotos.removeAll()
                    state.deletedSheetOpen = false
                    state.deleteError = nil
                    state.history.removeAll()
                    let bytes = trashedIDs.reduce(Int64(0)) { $0 + (state.byteCounts[$1] ?? 0) }
                    let outcome = ActivityTracker.recordTrash(
                        state.stats, count: trashedIDs.count, bytes: bytes,
                        trackGoals: state.settings.gamificationEnabled
                    )
                    state.stats = outcome.stats
                    if state.milestoneReached == nil {
                        state.milestoneReached = outcome.newMilestones.first
                    }
                    return .merge(persistProgress(state), persistStats(state.stats))
                case .cancelled:
                    return .none
                case .failure(let message):
                    state.deleteError = message
                    return .none
                }

            case .fileSizesLoaded(let sizes):
                state.byteCounts.merge(sizes) { _, new in new }
                return .none

            case .fullscreenMarkAtRelativeIndex(let localIdx, let kept):
                let targetIdx = state.activeIndex + localIdx
                guard localIdx >= 0, targetIdx < state.photos.count else { return .none }
                let photo = state.photos[targetIdx]
                applyDecision(&state, photo: photo, kept: kept)
                state.history.append(SwipeHistoryEntry(photoID: photo.id, kept: kept))
                state.activeIndex = targetIdx + 1
                state.fullscreenOpen = false
                markFinishedIfNeeded(&state)
                return .merge(
                    persistProgress(state),
                    recordDecisions(&state, count: 1),
                    fetchSizes(&state, ids: kept ? [] : [photo.id]),
                    requestHashes(&state)
                )

            case .goalCelebrationDismissed:
                state.goalCelebration = false
                return .none

            case .keepButtonTapped:
                return startSwipe(&state, direction: 1)

            case .libraryRefreshRequested:
                state.authorizationStatus = photoLibrary.authorizationStatus()
                state.stats = ActivityTracker.rollover(state.stats, now: date.now)
                guard state.authorizationStatus.grantsRead, !state.isLoading else { return .none }
                guard state.libraryLoaded else { return loadLibrary(&state) }
                return .concatenate(
                    fetchPhotos(&state, purpose: .refresh),
                    fetchPiles(state),
                    fetchTrash(state)
                )

            case .milestoneDismissed:
                state.milestoneReached = nil
                return .none

            case .onAppear:
                state.settings = settings.load()
                let progress = swipeProgress.load()
                state.deletedPhotoIDs = progress.deletedIDs
                state.keptPhotoIDs = progress.keptIDs
                state.stats = ActivityTracker.rollover(statsClient.load(), now: date.now)
                state.authorizationStatus = photoLibrary.authorizationStatus()
                if state.authorizationStatus.grantsRead {
                    return loadLibrary(&state)
                }
                return .none

            case .openSettingsTapped:
                return .run { _ in
                    if let url = URL(string: "app-settings:") {
                        await openURL(url)
                    }
                }

            case .photosLoaded(let incoming, let purpose):
                state.pilePhotoCount = incoming.count
                switch purpose {
                case .startOver:
                    let incomingIDs = Set(incoming.map(\.id))
                    state.keptPhotoIDs.removeAll { incomingIDs.contains($0) }
                    state.deletedPhotoIDs.removeAll { incomingIDs.contains($0) }
                    state.trashPhotos.removeAll { incomingIDs.contains($0.id) }
                    rebuildDeck(&state, from: incoming)
                    return .merge(persistProgress(state), requestHashes(&state))
                case .initial:
                    rebuildDeck(&state, from: incoming)
                    return requestHashes(&state)
                case .refresh:
                    refreshDeck(&state, from: incoming)
                    return requestHashes(&state)
                }

            case .pilePicked(let selector):
                state.selectedPile = selector
                state.pickerOpen = false
                resetSession(&state)
                return .merge(
                    .cancel(id: CancelID.swipeAnimation),
                    fetchPhotos(&state, purpose: .initial)
                )

            case .pilesLoaded(let piles):
                state.piles = piles
                return .none

            case .pileTitleTapped:
                state.pickerOpen = true
                return .none

            case .reminderAuthorizationResolved(let granted):
                guard granted else { return .none }
                state.settings.reminderEnabled = true
                return .merge(
                    persistSettings(state.settings),
                    scheduleReminder(state.settings)
                )

            case .requestAuthorizationTapped:
                return .run { send in
                    let status = await photoLibrary.requestAuthorization()
                    await send(.authorizationStatusReceived(status))
                }

            case .resetButtonTapped:
                resetSession(&state)
                return .merge(
                    .cancel(id: CancelID.swipeAnimation),
                    fetchPhotos(&state, purpose: .startOver)
                )

            case .resetProgressTapped:
                state.deletedPhotoIDs.removeAll()
                state.keptPhotoIDs.removeAll()
                state.trashPhotos.removeAll()
                state.deletedSheetOpen = false
                state.deleteError = nil
                resetSession(&state)
                return .merge(
                    .cancel(id: CancelID.swipeAnimation),
                    persistProgress(state),
                    fetchPhotos(&state, purpose: .initial)
                )

            case .settingsButtonTapped:
                state.settingsOpen = true
                return .none

            case .settingsDailyGoalChanged(let goal):
                state.settings.dailyGoal = goal
                return .merge(
                    persistSettings(state.settings),
                    state.settings.reminderEnabled ? scheduleReminder(state.settings) : .none
                )

            case .settingsDetectSimilarToggled(let value):
                state.settings.detectSimilar = value
                rebuildSimilarity(&state)
                return .merge(persistSettings(state.settings), requestHashes(&state))

            case .settingsGamificationToggled(let value):
                state.settings.gamificationEnabled = value
                if !value {
                    state.goalCelebration = false
                    state.milestoneReached = nil
                }
                return persistSettings(state.settings)

            case .settingsIncludeVideosToggled(let value):
                state.settings.includeVideos = value
                resetSession(&state)
                return .merge(
                    .cancel(id: CancelID.swipeAnimation),
                    persistSettings(state.settings),
                    .concatenate(fetchPhotos(&state, purpose: .initial), fetchPiles(state))
                )

            case .settingsPlayLivePhotosToggled(let value):
                state.settings.playLivePhotos = value
                return persistSettings(state.settings)

            case .settingsReminderHourChanged(let hour):
                state.settings.reminderHour = hour
                return .merge(
                    persistSettings(state.settings),
                    state.settings.reminderEnabled ? scheduleReminder(state.settings) : .none
                )

            case .settingsReminderToggled(let value):
                guard value else {
                    state.settings.reminderEnabled = false
                    return .merge(
                        persistSettings(state.settings),
                        .run { _ in notifications.cancelReminder() }
                    )
                }
                return .run { send in
                    let granted = await notifications.requestAuthorization()
                    await send(.reminderAuthorizationResolved(granted))
                }

            case .settingsShowLocationToggled(let value):
                state.settings.showLocation = value
                return persistSettings(state.settings)

            case .shuffleButtonTapped:
                state.shuffleEnabled.toggle()
                let classified = state.classifiedIDs
                let remaining = state.loadedPhotos.filter { !classified.contains($0.id) }
                if state.shuffleEnabled {
                    state.unshuffledIndex = state.topPhoto
                        .flatMap { top in state.loadedPhotos.firstIndex { $0.id == top.id } }
                        ?? state.loadedPhotos.count
                    state.photos = remaining.shuffled()
                    state.activeIndex = 0
                } else {
                    state.photos = remaining
                    let resumeID = state.loadedPhotos
                        .dropFirst(state.unshuffledIndex)
                        .first { !classified.contains($0.id) }?
                        .id
                    state.activeIndex = resumeID
                        .flatMap { id in remaining.firstIndex { $0.id == id } }
                        ?? remaining.count
                }
                state.history.removeAll()
                clearExit(&state)
                state.drag = .zero
                state.isDragging = false
                return .merge(.cancel(id: CancelID.swipeAnimation), requestHashes(&state))

            case .similarBadgeTapped:
                guard !state.topSimilarGroup.isEmpty else { return .none }
                state.similarSheetOpen = true
                return .none

            case .similarGroupResolved(let keptIDs, let deletedIDs):
                let byID = Dictionary(state.loadedPhotos.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
                var decisions: [PhotoDecision] = []
                for id in keptIDs {
                    guard let photo = byID[id] else { continue }
                    applyDecision(&state, photo: photo, kept: true)
                    decisions.append(PhotoDecision(photoID: id, kept: true))
                }
                for id in deletedIDs {
                    guard let photo = byID[id] else { continue }
                    applyDecision(&state, photo: photo, kept: false)
                    decisions.append(PhotoDecision(photoID: id, kept: false))
                }
                state.similarSheetOpen = false
                guard !decisions.isEmpty else { return .none }
                state.history.append(SwipeHistoryEntry(decisions: decisions))
                let classified = state.classifiedIDs
                let start = min(state.activeIndex, state.photos.count)
                state.activeIndex = (start..<state.photos.count)
                    .first { !classified.contains(state.photos[$0].id) } ?? state.photos.count
                markFinishedIfNeeded(&state)
                clearExit(&state)
                return .merge(
                    .cancel(id: CancelID.swipeAnimation),
                    persistProgress(state),
                    recordDecisions(&state, count: decisions.count),
                    fetchSizes(&state, ids: deletedIDs),
                    requestHashes(&state)
                )

            case .similarityHashesLoaded(let hashes):
                state.similarityHashes.merge(hashes) { _, new in new }
                state.similarGroups = state.similarRuns.flatMap {
                    SimilarityGrouper.confirmGroups(run: $0, hashes: state.similarityHashes)
                }
                return .none

            case .statsButtonTapped:
                state.statsOpen = true
                return .none

            case .statsResetTapped:
                state.stats = ActivityTracker.rollover(.empty, now: date.now)
                state.milestoneReached = nil
                state.goalCelebration = false
                return persistStats(state.stats)

            case .swipeExitAnimationCompleted:
                clearExit(&state)
                return .none

            case .topCardTapped:
                state.drag = .zero
                state.isDragging = false
                state.fullscreenOpen = true
                return .none

            case .trashPhotosLoaded(let photos):
                let byID = Dictionary(photos.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
                state.trashPhotos = state.deletedPhotoIDs.compactMap { byID[$0] }
                let resolvedIDs = state.trashPhotos.map(\.id)
                let sizes = fetchSizes(&state, ids: resolvedIDs)
                guard resolvedIDs != state.deletedPhotoIDs else { return sizes }
                state.deletedPhotoIDs = resolvedIDs
                return .merge(persistProgress(state), sizes)

            case .undoButtonTapped:
                guard let last = state.history.popLast() else { return .none }
                var lowestIndex: Int?
                for decision in last.decisions {
                    guard let index = state.photos.firstIndex(where: { $0.id == decision.photoID }) else {
                        continue
                    }
                    if decision.kept {
                        state.keptPhotoIDs.removeAll { $0 == decision.photoID }
                        state.sessionKeptIDs.remove(decision.photoID)
                    } else {
                        state.deletedPhotoIDs.removeAll { $0 == decision.photoID }
                        state.trashPhotos.removeAll { $0.id == decision.photoID }
                        state.sessionDeletedIDs.remove(decision.photoID)
                    }
                    lowestIndex = min(lowestIndex ?? index, index)
                }
                guard let lowestIndex else { return .none }
                state.activeIndex = lowestIndex
                state.sessionFinishedAt = nil
                clearExit(&state)
                return .merge(
                    .cancel(id: CancelID.swipeAnimation),
                    persistProgress(state)
                )

            case .viewAllPhotosTapped:
                return .send(.pilePicked(.all))

            case .viewInPhotosTapped:
                return .run { _ in
                    if let url = URL(string: "photos-redirect://") {
                        await openURL(url)
                    }
                }
            }
        }
    }

    // MARK: - Effects

    /// Sequential on purpose: the deck lands first, the trash sheet
    /// last, and the order is deterministic for tests.
    private func loadLibrary(_ state: inout State) -> Effect<Action> {
        .concatenate(
            fetchPhotos(&state, purpose: .initial),
            fetchPiles(state),
            fetchTrash(state)
        )
    }

    private func fetchPiles(_ state: State) -> Effect<Action> {
        let now = date.now
        let classified = state.classifiedIDs
        let includeVideos = state.settings.includeVideos
        return .run { send in
            let piles = await photoLibrary.fetchPiles(now, classified, includeVideos)
            await send(.pilesLoaded(piles))
        }
        .cancellable(id: CancelID.fetchPiles, cancelInFlight: true)
    }

    private func fetchPhotos(_ state: inout State, purpose: LoadPurpose) -> Effect<Action> {
        if purpose != .refresh {
            state.isLoading = true
        }
        let selector = state.selectedPile
        let includeVideos = state.settings.includeVideos
        return .run { send in
            let photos = await photoLibrary.fetchPhotos(selector, includeVideos)
            await send(.photosLoaded(photos, purpose: purpose))
        }
        .cancellable(id: CancelID.fetchPhotos, cancelInFlight: true)
    }

    private func fetchTrash(_ state: State) -> Effect<Action> {
        let ids = state.deletedPhotoIDs
        return .run { send in
            let photos = await photoLibrary.fetchPhotosByIDs(ids)
            await send(.trashPhotosLoaded(photos))
        }
        .cancellable(id: CancelID.fetchTrash, cancelInFlight: true)
    }

    private func fetchSizes(_ state: inout State, ids: [String]) -> Effect<Action> {
        let missing = ids.filter { state.byteCounts[$0] == nil }
        guard !missing.isEmpty else { return .none }
        return .run { send in
            let sizes = await photoLibrary.fetchFileSizes(missing)
            await send(.fileSizesLoaded(sizes))
        }
        .cancellable(id: CancelID.fetchSizes, cancelInFlight: false)
    }

    /// Requests hashes for the runs near the top of the deck whose
    /// members are not hashed yet.
    private func requestHashes(_ state: inout State) -> Effect<Action> {
        guard state.settings.detectSimilar, !state.similarRuns.isEmpty else { return .none }
        let end = min(state.activeIndex + Self.hashLookahead, state.photos.count)
        guard state.activeIndex < end else { return .none }
        let window = Set(state.photos[state.activeIndex..<end].map(\.id))
        var missing: [String] = []
        var seen: Set<String> = []
        for run in state.similarRuns where run.contains(where: { window.contains($0) }) {
            for id in run where state.similarityHashes[id] == nil && !seen.contains(id) {
                seen.insert(id)
                missing.append(id)
            }
        }
        guard !missing.isEmpty else { return .none }
        let ids = missing
        return .run { send in
            let hashes = await photoLibrary.fetchSimilarityHashes(ids)
            await send(.similarityHashesLoaded(hashes))
        }
        .cancellable(id: CancelID.fetchHashes, cancelInFlight: false)
    }

    private func persistSettings(_ value: UserSettings) -> Effect<Action> {
        .run { _ in
            settings.save(value)
        }
    }

    private func persistStats(_ value: ActivityStats) -> Effect<Action> {
        .run { _ in
            statsClient.save(value)
        }
    }

    private func persistProgress(_ state: State) -> Effect<Action> {
        let snapshot = SwipeProgress(
            deletedIDs: state.deletedPhotoIDs,
            keptIDs: state.keptPhotoIDs
        )
        return .run { _ in
            swipeProgress.save(snapshot)
        }
    }

    private func scheduleReminder(_ settings: UserSettings) -> Effect<Action> {
        let hour = settings.reminderHour
        let goal = settings.dailyGoal
        return .run { _ in
            notifications.scheduleDailyReminder(hour, goal)
        }
    }

    // MARK: - State helpers

    /// Commits the swipe synchronously and captures what the exit
    /// overlay needs, so the next card is immediately draggable.
    private func startSwipe(_ state: inout State, direction: Int) -> Effect<Action> {
        guard let committedPhoto = state.topPhoto else { return .none }
        let kept = direction == 1
        applyDecision(&state, photo: committedPhoto, kept: kept)
        state.history.append(SwipeHistoryEntry(photoID: committedPhoto.id, kept: kept))
        state.activeIndex += 1
        state.exitingPhoto = committedPhoto
        state.exitingDirection = direction
        state.exitingStartDrag = state.drag
        state.drag = .zero
        state.isDragging = false
        markFinishedIfNeeded(&state)
        let animation: Effect<Action> = .run { send in
            try await clock.sleep(for: .milliseconds(Self.exitAnimationDurationMs))
            await send(.swipeExitAnimationCompleted)
        }
        .cancellable(id: CancelID.swipeAnimation, cancelInFlight: true)
        return .merge(
            animation,
            persistProgress(state),
            recordDecisions(&state, count: 1),
            fetchSizes(&state, ids: kept ? [] : [committedPhoto.id]),
            requestHashes(&state)
        )
    }

    /// Last decision wins: a photo lives in exactly one of the two lists.
    private func applyDecision(_ state: inout State, photo: PhotoAsset, kept: Bool) {
        state.keptPhotoIDs.removeAll { $0 == photo.id }
        state.deletedPhotoIDs.removeAll { $0 == photo.id }
        state.trashPhotos.removeAll { $0.id == photo.id }
        state.sessionKeptIDs.remove(photo.id)
        state.sessionDeletedIDs.remove(photo.id)
        if kept {
            state.keptPhotoIDs.append(photo.id)
            state.sessionKeptIDs.insert(photo.id)
        } else {
            state.deletedPhotoIDs.append(photo.id)
            state.trashPhotos.append(photo)
            state.sessionDeletedIDs.insert(photo.id)
        }
        if state.sessionStartedAt == nil {
            state.sessionStartedAt = date.now
        }
    }

    private func markFinishedIfNeeded(_ state: inout State) {
        if state.activeIndex >= state.photos.count {
            state.sessionFinishedAt = date.now
        }
    }

    private func recordDecisions(_ state: inout State, count: Int) -> Effect<Action> {
        for _ in 0..<count {
            let outcome = ActivityTracker.recordDecision(
                state.stats,
                now: date.now,
                dailyGoal: state.settings.dailyGoal,
                trackGoals: state.settings.gamificationEnabled
            )
            state.stats = outcome.stats
            if outcome.goalJustReached {
                state.goalCelebration = true
            }
            if state.milestoneReached == nil {
                state.milestoneReached = outcome.newMilestones.first
            }
        }
        return persistStats(state.stats)
    }

    private func rebuildDeck(_ state: inout State, from incoming: [PhotoAsset]) {
        let classified = state.classifiedIDs
        let filtered = incoming.filter { !classified.contains($0.id) }
        state.loadedPhotos = filtered
        state.photos = state.shuffleEnabled ? filtered.shuffled() : filtered
        state.activeIndex = 0
        state.history.removeAll()
        state.isLoading = false
        state.libraryLoaded = true
        clearSessionTallies(&state)
        rebuildSimilarity(&state)
    }

    /// Applies a fresh library read without disturbing the user:
    /// vanished photos leave the deck, new ones join at the end, the
    /// top card stays the top card.
    private func refreshDeck(_ state: inout State, from incoming: [PhotoAsset]) {
        let classified = state.classifiedIDs
        let filtered = incoming.filter { !classified.contains($0.id) }
        state.loadedPhotos = filtered
        let incomingIDs = Set(incoming.map(\.id))
        let topID = state.topPhoto?.id
        var deck = state.photos.filter { incomingIDs.contains($0.id) }
        let present = Set(deck.map(\.id))
        var newcomers = filtered.filter { !present.contains($0.id) }
        if state.shuffleEnabled {
            newcomers.shuffle()
        }
        deck.append(contentsOf: newcomers)
        state.photos = deck
        remapActiveIndex(&state, topID: topID)
        let deckIDs = Set(deck.map(\.id))
        state.history = state.history.compactMap { entry in
            let remaining = entry.decisions.filter { deckIDs.contains($0.photoID) }
            return remaining.isEmpty ? nil : SwipeHistoryEntry(decisions: remaining)
        }
        state.libraryLoaded = true
        rebuildSimilarity(&state)
    }

    private func rebuildSimilarity(_ state: inout State) {
        guard state.settings.detectSimilar else {
            state.similarRuns = []
            state.similarGroups = []
            return
        }
        state.similarRuns = SimilarityGrouper.candidateRuns(photos: state.loadedPhotos)
        state.similarGroups = state.similarRuns.flatMap {
            SimilarityGrouper.confirmGroups(run: $0, hashes: state.similarityHashes)
        }
    }

    private func remapActiveIndex(_ state: inout State, topID: String?) {
        if let topID, let index = state.photos.firstIndex(where: { $0.id == topID }) {
            state.activeIndex = index
        } else {
            state.activeIndex = min(state.activeIndex, state.photos.count)
        }
    }

    private func clearExit(_ state: inout State) {
        state.exitingPhoto = nil
        state.exitingDirection = 0
        state.exitingStartDrag = .zero
    }

    private func clearSessionTallies(_ state: inout State) {
        state.sessionDeletedIDs = []
        state.sessionFinishedAt = nil
        state.sessionKeptIDs = []
        state.sessionStartedAt = nil
    }

    /// Resets the session (index, history, drag, exit overlay) but not
    /// the persisted lists.
    private func resetSession(_ state: inout State) {
        state.activeIndex = 0
        state.history.removeAll()
        state.deleteError = nil
        state.drag = .zero
        clearExit(&state)
        state.isDragging = false
        state.similarSheetOpen = false
        state.unshuffledIndex = 0
        clearSessionTallies(&state)
    }
}

enum LoadPurpose: Equatable {
    /// Fresh deck: pile pick, first load, reset progress.
    case initial
    /// Library re-read while the user is mid-deck.
    case refresh
    /// Forget every decision on the incoming photos, then fresh deck.
    case startOver
}

enum EmptyTrashOutcome: Equatable {
    case cancelled
    case failure(String)
    case success
}
