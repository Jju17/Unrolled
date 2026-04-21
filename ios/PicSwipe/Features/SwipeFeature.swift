//
//  SwipeFeature.swift
//  PicSwipe
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
        var deleted = 0
        var drag: CGSize = .zero
        var exitingDirection = 0  // -1 delete, +1 keep, 0 none
        var fullscreenOpen = false
        var history: [SwipeHistoryEntry] = []
        var isDragging = false
        var isLoading = false
        var kept = 0
        /// Photos as returned from the library (unshuffled source of truth).
        var loadedPhotos: [PhotoAsset] = []
        /// What the view displays — either `loadedPhotos` or a freshly-
        /// shuffled copy depending on `shuffleEnabled`.
        var photos: [PhotoAsset] = []
        var pickerOpen = false
        var selectedAlbumID: String?  // nil = Recents / all photos (default)
        var settingsOpen = false
        var shuffleEnabled = false
        /// Position in the unshuffled list. Stored when shuffle is
        /// enabled so disabling restores the user to the photo they
        /// were reviewing beforehand.
        var unshuffledIndex: Int = 0

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
        case keepButtonTapped
        case onAppear
        case photosLoaded([PhotoAsset])
        case requestAuthorizationTapped
        case resetButtonTapped
        case settingsButtonTapped
        case shuffleButtonTapped
        case swipeAnimationCompleted(direction: Int)
        case topCardTapped
        case undoButtonTapped
        case viewAllPhotosTapped
        case viewInPhotosTapped
    }

    enum CancelID { case fetchAlbums, fetchPhotos, swipeAnimation }

    static let dragThreshold: CGFloat = 110
    static let animationDurationMs = 320

    @Dependency(\.continuousClock) var clock
    @Dependency(\.openURL) var openURL
    @Dependency(\.photoLibrary) var photoLibrary

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
                resetProgress(&state)
                return .merge(
                    .cancel(id: CancelID.swipeAnimation),
                    fetchPhotos(&state, albumID: id)
                )

            case .albumTitleTapped:
                state.pickerOpen = true
                return .none

            case .albumsLoaded(let albums):
                state.albums = albums
                // Adopt the first album's ID as the default if nothing is
                // selected yet — makes subsequent re-picks compare by id.
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
                // Sheet dismissals & other binding-driven state writes
                // (e.g. swipe-down to close) land here via `BindingReducer`.
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

            case .keepButtonTapped:
                return startSwipe(&state, direction: 1)

            case .onAppear:
                let current = photoLibrary.authorizationStatus()
                state.authorizationStatus = current
                if current.grantsRead {
                    return loadLibrary(&state)
                }
                return .none

            case .photosLoaded(let photos):
                state.loadedPhotos = photos
                state.photos = state.shuffleEnabled ? photos.shuffled() : photos
                state.isLoading = false
                return .none

            case .requestAuthorizationTapped:
                return .run { send in
                    let status = await photoLibrary.requestAuthorization()
                    await send(.authorizationStatusReceived(status))
                }

            case .resetButtonTapped:
                resetProgress(&state)
                return .cancel(id: CancelID.swipeAnimation)

            case .settingsButtonTapped:
                state.settingsOpen = true
                return .none

            case .shuffleButtonTapped:
                state.shuffleEnabled.toggle()
                if state.shuffleEnabled {
                    // Entering shuffle: remember where we are in the
                    // unshuffled list so we can come back to it.
                    state.unshuffledIndex = state.activeIndex
                    state.photos = state.loadedPhotos.shuffled()
                    state.activeIndex = 0
                } else {
                    // Exiting shuffle: resume at the photo the user was
                    // reviewing before turning shuffle on.
                    state.photos = state.loadedPhotos
                    state.activeIndex = min(state.unshuffledIndex, state.photos.count)
                }
                state.history.removeAll()
                state.drag = .zero
                state.exitingDirection = 0
                state.isDragging = false
                return .cancel(id: CancelID.swipeAnimation)

            case .swipeAnimationCompleted(let direction):
                state.history.append(
                    SwipeHistoryEntry(index: state.activeIndex, kept: direction == 1)
                )
                if direction == 1 { state.kept += 1 } else { state.deleted += 1 }
                state.activeIndex += 1
                state.drag = .zero
                state.exitingDirection = 0
                return .none

            case .topCardTapped:
                // Clear any transient drag state from the tap gesture
                // (onChanged can fire with zero translation before onEnded).
                state.drag = .zero
                state.isDragging = false
                state.fullscreenOpen = true
                return .none

            case .undoButtonTapped:
                guard let last = state.history.popLast() else { return .none }
                if last.kept {
                    state.kept = max(0, state.kept - 1)
                } else {
                    state.deleted = max(0, state.deleted - 1)
                }
                state.activeIndex = last.index
                return .none

            case .viewAllPhotosTapped:
                // First album in the list is always the "all photos"
                // collection (smartAlbumUserLibrary on iOS).
                guard let firstID = state.albums.first?.id else { return .none }
                return .send(.albumPicked(firstID))

            case .viewInPhotosTapped:
                // Sheet dismissal is handled by SwiftUI (the settings view
                // calls `dismiss()` which flips `settingsOpen` via the
                // BindingReducer). This action is purely the side-effect.
                //
                // No officially-documented URL scheme opens a specific
                // PHAsset; this launches the Photos app at its root
                // (best-effort, honoured on iOS 14+).
                return .run { _ in
                    if let url = URL(string: "photos-redirect://") {
                        await openURL(url)
                    }
                }
            }
        }
    }

    // MARK: - Helpers

    /// Kick off the two independent fetches that we need once the user is
    /// authorized: the list of albums for the picker + the photos for the
    /// currently-selected album (default: Recents).
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

    private func startSwipe(_ state: inout State, direction: Int) -> Effect<Action> {
        guard state.activeIndex < state.photos.count else { return .none }
        state.exitingDirection = direction
        return .run { send in
            try await clock.sleep(for: .milliseconds(Self.animationDurationMs))
            await send(.swipeAnimationCompleted(direction: direction))
        }
        .cancellable(id: CancelID.swipeAnimation)
    }

    private func resetProgress(_ state: inout State) {
        state.activeIndex = 0
        state.history.removeAll()
        state.kept = 0
        state.deleted = 0
        state.drag = .zero
        state.exitingDirection = 0
        state.isDragging = false
        state.unshuffledIndex = 0
    }
}

struct SwipeHistoryEntry: Equatable {
    let index: Int
    let kept: Bool
}
