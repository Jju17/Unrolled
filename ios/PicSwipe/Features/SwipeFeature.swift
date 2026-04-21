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
        /// Fires after `exitAnimationDurationMs`. Clears the overlay so
        /// the exiting card disappears once it has flown off-screen.
        case swipeExitAnimationCompleted
        case topCardTapped
        case undoButtonTapped
        case viewAllPhotosTapped
        case viewInPhotosTapped
    }

    enum CancelID { case fetchAlbums, fetchPhotos, swipeAnimation }

    static let dragThreshold: CGFloat = 110
    /// The overlay's fly-off animation itself is 260 ms; we wait 60 ms
    /// more before clearing `exitingPhoto` so the overlay is never
    /// yanked mid-flight (which manifested as the card "stopping" the
    /// instant the finger lifted).
    static let exitAnimationDurationMs = 320

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
                if last.kept {
                    state.kept = max(0, state.kept - 1)
                } else {
                    state.deleted = max(0, state.deleted - 1)
                }
                state.activeIndex = last.index
                state.exitingPhoto = nil
                state.exitingDirection = 0
                state.exitingStartDrag = .zero
                return .cancel(id: CancelID.swipeAnimation)

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
        if direction == 1 { state.kept += 1 } else { state.deleted += 1 }
        state.activeIndex += 1
        state.exitingPhoto = committedPhoto
        state.exitingDirection = direction
        state.exitingStartDrag = state.drag
        state.drag = .zero
        state.isDragging = false
        return .run { send in
            try await clock.sleep(for: .milliseconds(Self.exitAnimationDurationMs))
            await send(.swipeExitAnimationCompleted)
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
