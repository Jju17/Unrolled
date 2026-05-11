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
        var albums: [Album] = []
        var authorizationStatus: PhotoAuthorizationStatus = .notDetermined
        /// IDs of photos the user has swiped-delete, in deletion order.
        /// These are staged — still in the library, but shown in the
        /// review sheet. Calling `photoLibrary.deleteAssets` with these
        /// IDs actually moves them to the system Recently Deleted bucket.
        ///
        /// Persisted via `SwipeProgressClient` so a relaunch resumes the
        /// trash exactly where the user left it.
        var deletedPhotoIDs: [String] = []
        /// Error surfaced from the Recently-Deleted batch op (permission
        /// denied, network, etc.). Shown as an in-sheet banner.
        var deleteError: String?
        var deletedSheetOpen = false
        var drag: CGSize = .zero
        /// -1 delete, +1 keep, 0 none. Read by `ExitingCardView` to pick
        /// the direction of the fly-off.
        var exitingDirection = 0
        /// The card currently flying off. While non-nil, an overlay
        /// renders it with its own `@State`-driven animation, independent
        /// of the stack (which has already advanced). Cleared by
        /// `swipeExitAnimationCompleted` after the known animation
        /// duration has elapsed.
        var exitingPhoto: PhotoAsset?
        /// Finger position captured at commit time. The exit overlay
        /// starts here so the fly-off continues the gesture arc instead
        /// of teleporting to screen centre.
        var exitingStartDrag: CGSize = .zero
        var fullscreenOpen = false
        var history: [SwipeHistoryEntry] = []
        var isDragging = false
        var isLoading = false
        /// IDs of photos the user has swiped-keep across all sessions.
        /// Persisted alongside `deletedPhotoIDs`; used to filter out
        /// already-decided photos from the freshly-fetched library so
        /// the deck never re-presents a photo the user has classified.
        /// Only the explicit `Reset progress` button wipes this list.
        var keptPhotoIDs: [String] = []
        /// Photos as returned from the library (unshuffled source of truth).
        var loadedPhotos: [PhotoAsset] = []
        /// What the view displays — either `loadedPhotos` or a freshly-
        /// shuffled copy depending on `shuffleEnabled`.
        var photos: [PhotoAsset] = []
        var pickerOpen = false
        var selectedAlbumID: String?  // nil = Recents / all photos (default)
        var settings: UserSettings = .default
        var settingsOpen = false
        var shuffleEnabled = false
        /// `true` while a `deleteAssets` request is awaiting the system
        /// confirmation dialog + PhotoKit commit. Keeps the button
        /// disabled so a double-tap can't fire two requests.
        var trashInFlight = false
        /// Position in the unshuffled list. Stored when shuffle is
        /// enabled so disabling restores the user to the photo they
        /// were reviewing beforehand.
        var unshuffledIndex: Int = 0

        /// Display counter for the stats pill — derived from the persistent
        /// `deletedPhotoIDs` so the number always matches what the trash
        /// sheet shows ("Empty trash (N)" and the pill agree).
        var deleted: Int { deletedPhotoIDs.count }
        /// Display counter for the stats pill — derived from `keptPhotoIDs`
        /// for the same reason as `deleted`.
        var kept: Int { keptPhotoIDs.count }

        /// Photo currently on top of the stack, if any.
        var topPhoto: PhotoAsset? {
            guard activeIndex < photos.count else { return nil }
            return photos[activeIndex]
        }

        /// Currently-selected album, resolved from `selectedAlbumID` against
        /// the loaded `albums`. Falls back to the first album. Returns `nil`
        /// while albums are still loading — the view renders a neutral
        /// placeholder instead of a fake album in that case.
        var currentAlbum: Album? {
            if let selectedAlbumID,
               let match = albums.first(where: { $0.id == selectedAlbumID }) {
                return match
            }
            return albums.first
        }
    }

    enum Action: BindableAction {
        case aboutMenuItemTapped
        case albumPicked(String)
        case albumTitleTapped
        case albumsLoaded([Album])
        case authorizationStatusReceived(PhotoAuthorizationStatus)
        case binding(BindingAction<State>)
        case cardDragChanged(CGSize)
        case cardDragEnded
        case deleteButtonTapped
        /// "Garder" button next to a photo row in the review sheet —
        /// restores that photo from the trash-staging list back to kept.
        case deletedPhotoRestoreTapped(id: String)
        /// Stats pill in the header tapped — opens the review sheet.
        case deletedSheetButtonTapped
        /// User tapped the big red "Mettre à la poubelle" button.
        case emptyTrashButtonTapped
        /// PhotoKit delete completed. `success` is true if at least some
        /// assets moved; false carries an error message for the banner.
        case emptyTrashResult(success: Bool, message: String?)
        /// User tapped keep/delete inside the fullscreen pager. `localIdx`
        /// is offset from `activeIndex` — i.e. how many pages forward they
        /// scrolled before deciding. Intermediate photos are skipped for
        /// this session (re-appear on the next library load) but the
        /// chosen photo lands in `history` like a normal commit so undo
        /// works exactly as on the main stack.
        case fullscreenMarkAtRelativeIndex(localIdx: Int, kept: Bool)
        case keepButtonTapped
        case onAppear
        case photosLoaded([PhotoAsset])
        case requestAuthorizationTapped
        case resetButtonTapped
        /// Tapped from the deleted sheet — wipes the persisted
        /// `keptPhotoIDs` + `deletedPhotoIDs` so the deck shows the
        /// full library again as if from scratch.
        case resetProgressTapped
        case settingsButtonTapped
        /// Toggle for "play live photos" in Settings. Persists via `SettingsClient`.
        case settingsPlayLivePhotosToggled(Bool)
        /// Toggle for "show location metadata" in Settings.
        case settingsShowLocationToggled(Bool)
        case shuffleButtonTapped
        /// Fires after `exitAnimationDurationMs`. Clears the overlay so
        /// the exiting card disappears once it has flown off-screen.
        case swipeExitAnimationCompleted
        case topCardTapped
        case undoButtonTapped
        case viewAllPhotosTapped
        case viewInPhotosTapped
    }

    enum CancelID { case emptyTrash, fetchAlbums, fetchPhotos, swipeAnimation }

    static let dragThreshold: CGFloat = 110
    /// The overlay's fly-off animation itself is 260 ms; we wait 60 ms
    /// more before clearing `exitingPhoto` so the overlay is never
    /// yanked mid-flight (which manifested as the card "stopping" the
    /// instant the finger lifted).
    static let exitAnimationDurationMs = 320

    @Dependency(\.continuousClock) var clock
    @Dependency(\.openURL) var openURL
    @Dependency(\.photoLibrary) var photoLibrary
    @Dependency(\.settings) var settings
    @Dependency(\.swipeProgress) var swipeProgress

    var body: some Reducer<State, Action> {
        BindingReducer()
        Reduce { state, action in
            switch action {
            case .aboutMenuItemTapped:
                state.aboutOpen = true
                return .none

            case .albumPicked(let id):
                state.selectedAlbumID = id
                state.pickerOpen = false
                resetSession(&state)
                return .merge(
                    .cancel(id: CancelID.swipeAnimation),
                    fetchPhotos(&state, albumID: id)
                )

            case .albumTitleTapped:
                state.pickerOpen = true
                return .none

            case .albumsLoaded(let albums):
                state.albums = albums
                if state.selectedAlbumID == nil {
                    state.selectedAlbumID = albums.first?.id
                }
                return .none

            case .authorizationStatusReceived(let status):
                state.authorizationStatus = status
                if status.grantsRead {
                    return loadLibrary(&state)
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

            case .deleteButtonTapped:
                return startSwipe(&state, direction: -1)

            case .deletedPhotoRestoreTapped(let id):
                // Unstage from the trash list. The photo counts as
                // "kept" now so the user doesn't see it again next
                // session — moving it from one persisted list to the
                // other rather than dropping it entirely.
                guard state.deletedPhotoIDs.contains(id) else { return .none }
                state.deletedPhotoIDs.removeAll { $0 == id }
                if !state.keptPhotoIDs.contains(id) {
                    state.keptPhotoIDs.append(id)
                }
                return persistProgress(state)

            case .deletedSheetButtonTapped:
                state.deletedSheetOpen = true
                return .none

            case .emptyTrashButtonTapped:
                // No in-app confirmation: PhotoKit shows its own
                // mandatory system dialog, so duplicating it in-app
                // was just an extra tap for the user.
                guard !state.deletedPhotoIDs.isEmpty,
                      !state.trashInFlight else { return .none }
                state.trashInFlight = true
                let ids = state.deletedPhotoIDs
                return .run { send in
                    do {
                        try await photoLibrary.deleteAssets(ids)
                        await send(.emptyTrashResult(success: true, message: nil))
                    } catch {
                        await send(.emptyTrashResult(
                            success: false,
                            message: error.localizedDescription
                        ))
                    }
                }
                .cancellable(id: CancelID.emptyTrash)

            case .emptyTrashResult(let success, let message):
                state.trashInFlight = false
                if success {
                    // Photos are now in the system Recently Deleted
                    // bucket. Prune them out of our local `photos` /
                    // `loadedPhotos` so the user doesn't land on an
                    // asset that no longer exists in the regular library,
                    // and remap `activeIndex` by ID so the user keeps
                    // their place visually.
                    let deletedSet = Set(state.deletedPhotoIDs)
                    let topID = state.topPhoto?.id
                    state.loadedPhotos.removeAll { deletedSet.contains($0.id) }
                    state.photos.removeAll { deletedSet.contains($0.id) }
                    if let topID,
                       let idx = state.photos.firstIndex(where: { $0.id == topID }) {
                        state.activeIndex = idx
                    } else {
                        state.activeIndex = min(state.activeIndex, state.photos.count)
                    }
                    state.deletedPhotoIDs.removeAll()
                    state.deletedSheetOpen = false
                    state.deleteError = nil
                    // History is wiped: undo can't bring back a photo
                    // that no longer exists in the system library, and
                    // mixing keep-undo with deleted-and-gone entries
                    // would leave the stack in an inconsistent place.
                    // `keptPhotoIDs` is preserved (those photos are
                    // still in the library, still classified).
                    state.history.removeAll()
                    return persistProgress(state)
                } else {
                    state.deleteError = message ?? String(localized: "Couldn't delete the photos.")
                    return .none
                }

            case .fullscreenMarkAtRelativeIndex(let localIdx, let kept):
                let targetIdx = state.activeIndex + localIdx
                guard localIdx >= 0, targetIdx < state.photos.count else { return .none }
                let photo = state.photos[targetIdx]
                state.history.append(SwipeHistoryEntry(index: targetIdx, kept: kept))
                if kept {
                    state.keptPhotoIDs.append(photo.id)
                } else {
                    state.deletedPhotoIDs.append(photo.id)
                }
                state.activeIndex = targetIdx + 1
                state.fullscreenOpen = false
                return persistProgress(state)

            case .keepButtonTapped:
                return startSwipe(&state, direction: 1)

            case .onAppear:
                // Load both blobs synchronously so the very first
                // `photosLoaded` already knows which IDs to filter out
                // (avoids a flash of "all photos" before the persisted
                // classification trims the deck).
                state.settings = settings.load()
                let progress = swipeProgress.load()
                state.deletedPhotoIDs = progress.deletedIDs
                state.keptPhotoIDs = progress.keptIDs
                let current = photoLibrary.authorizationStatus()
                state.authorizationStatus = current
                if current.grantsRead {
                    return loadLibrary(&state)
                }
                return .none

            case .photosLoaded(let photos):
                // Drop already-classified IDs from the deck. The deck
                // is "the work left to do" — kept and deleted photos
                // live in their persisted lists and don't need a card
                // any more.
                let classified = Set(state.deletedPhotoIDs).union(state.keptPhotoIDs)
                let filtered = classified.isEmpty
                    ? photos
                    : photos.filter { !classified.contains($0.id) }
                state.loadedPhotos = filtered
                state.photos = state.shuffleEnabled ? filtered.shuffled() : filtered
                state.isLoading = false
                return .none

            case .requestAuthorizationTapped:
                return .run { send in
                    let status = await photoLibrary.requestAuthorization()
                    await send(.authorizationStatusReceived(status))
                }

            case .resetButtonTapped:
                resetSession(&state)
                return .cancel(id: CancelID.swipeAnimation)

            case .resetProgressTapped:
                state.deletedPhotoIDs.removeAll()
                state.keptPhotoIDs.removeAll()
                state.deletedSheetOpen = false
                state.deleteError = nil
                resetSession(&state)
                // Re-fetch so the previously-filtered photos rejoin
                // the deck. Cancels any in-flight fetch first.
                let refetch = fetchPhotos(&state, albumID: state.selectedAlbumID)
                let persist = persistProgress(state)
                return .merge(
                    .cancel(id: CancelID.swipeAnimation),
                    persist,
                    refetch
                )

            case .settingsButtonTapped:
                state.settingsOpen = true
                return .none

            case .settingsPlayLivePhotosToggled(let value):
                state.settings.playLivePhotos = value
                return persistSettings(state.settings)

            case .settingsShowLocationToggled(let value):
                state.settings.showLocation = value
                return persistSettings(state.settings)

            case .shuffleButtonTapped:
                state.shuffleEnabled.toggle()
                if state.shuffleEnabled {
                    state.unshuffledIndex = state.activeIndex
                    state.photos = state.loadedPhotos.shuffled()
                    state.activeIndex = 0
                } else {
                    state.photos = state.loadedPhotos
                    state.activeIndex = min(state.unshuffledIndex, state.photos.count)
                }
                state.history.removeAll()
                state.drag = .zero
                state.exitingDirection = 0
                state.exitingPhoto = nil
                state.exitingStartDrag = .zero
                state.isDragging = false
                return .cancel(id: CancelID.swipeAnimation)

            case .swipeExitAnimationCompleted:
                state.exitingPhoto = nil
                state.exitingDirection = 0
                state.exitingStartDrag = .zero
                return .none

            case .topCardTapped:
                state.drag = .zero
                state.isDragging = false
                state.fullscreenOpen = true
                return .none

            case .undoButtonTapped:
                guard let last = state.history.popLast() else { return .none }
                let undoneID: String? =
                    last.index < state.photos.count ? state.photos[last.index].id : nil
                if last.kept {
                    if let undoneID {
                        state.keptPhotoIDs.removeAll { $0 == undoneID }
                    }
                } else {
                    if let undoneID {
                        state.deletedPhotoIDs.removeAll { $0 == undoneID }
                    }
                }
                state.activeIndex = last.index
                state.exitingPhoto = nil
                state.exitingDirection = 0
                state.exitingStartDrag = .zero
                return .merge(
                    .cancel(id: CancelID.swipeAnimation),
                    persistProgress(state)
                )

            case .viewAllPhotosTapped:
                guard let firstID = state.albums.first?.id else { return .none }
                return .send(.albumPicked(firstID))

            case .viewInPhotosTapped:
                return .run { _ in
                    if let url = URL(string: "photos-redirect://") {
                        await openURL(url)
                    }
                }
            }
        }
    }

    // MARK: - Helpers

    private func loadLibrary(_ state: inout State) -> Effect<Action> {
        let photosEffect = fetchPhotos(&state, albumID: state.selectedAlbumID)
        let albumsEffect: Effect<Action> = .run { send in
            let albums = await photoLibrary.fetchAlbums()
            await send(.albumsLoaded(albums))
        }
        .cancellable(id: CancelID.fetchAlbums)
        return .merge(photosEffect, albumsEffect)
    }

    private func fetchPhotos(_ state: inout State, albumID: String?) -> Effect<Action> {
        state.isLoading = true
        return .run { send in
            let photos = await photoLibrary.fetchPhotos(albumID)
            await send(.photosLoaded(photos))
        }
        .cancellable(id: CancelID.fetchPhotos)
    }

    /// Commits the swipe synchronously AND captures the data the exit
    /// overlay needs (photo, direction, drag-at-lift). The overlay
    /// renders this photo flying off while the stack has already
    /// advanced underneath, so the new top card is immediately drag-able.
    private func startSwipe(_ state: inout State, direction: Int) -> Effect<Action> {
        guard state.activeIndex < state.photos.count else { return .none }
        let committedPhoto = state.photos[state.activeIndex]
        state.history.append(
            SwipeHistoryEntry(index: state.activeIndex, kept: direction == 1)
        )
        if direction == 1 {
            state.keptPhotoIDs.append(committedPhoto.id)
        } else {
            state.deletedPhotoIDs.append(committedPhoto.id)
        }
        state.activeIndex += 1
        state.exitingPhoto = committedPhoto
        state.exitingDirection = direction
        state.exitingStartDrag = state.drag
        state.drag = .zero
        state.isDragging = false
        let animation: Effect<Action> = .run { send in
            try await clock.sleep(for: .milliseconds(Self.exitAnimationDurationMs))
            await send(.swipeExitAnimationCompleted)
        }
        .cancellable(id: CancelID.swipeAnimation)
        return .merge(animation, persistProgress(state))
    }

    /// Fire-and-forget save of the settings blob. Errors are swallowed —
    /// UserDefaults can't really fail at runtime and the UI already
    /// reflects the optimistic state.
    private func persistSettings(_ value: UserSettings) -> Effect<Action> {
        return .run { _ in
            settings.save(value)
        }
    }

    /// Fire-and-forget snapshot of the kept/deleted lists. Called after
    /// every mutation that touches them so a relaunch picks up the same
    /// state. Cheap (~hundreds of strings of JSON, written via UserDefaults).
    private func persistProgress(_ state: State) -> Effect<Action> {
        let snapshot = SwipeProgress(
            deletedIDs: state.deletedPhotoIDs,
            keptIDs: state.keptPhotoIDs
        )
        return .run { _ in
            swipeProgress.save(snapshot)
        }
    }

    /// Resets the *session* state — index, history, drag, exit overlay.
    /// Does **not** clear the persisted `keptPhotoIDs` /
    /// `deletedPhotoIDs`; that's the job of `.resetProgressTapped`.
    private func resetSession(_ state: inout State) {
        state.activeIndex = 0
        state.history.removeAll()
        state.deleteError = nil
        state.drag = .zero
        state.exitingDirection = 0
        state.exitingPhoto = nil
        state.exitingStartDrag = .zero
        state.isDragging = false
        state.unshuffledIndex = 0
    }
}

struct SwipeHistoryEntry: Equatable {
    let index: Int
    let kept: Bool
}
