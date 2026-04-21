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
        var albumID = "recents"
        var deleted = 0
        var drag: CGSize = .zero
        var exitingDirection = 0  // -1 delete, +1 keep, 0 none
        var history: [SwipeHistoryEntry] = []
        var isDragging = false
        var kept = 0
        var menuOpen = false
        var pickerOpen = false
    }

    enum Action {
        case aboutDismissed
        case aboutMenuItemTapped
        case albumPicked(String)
        case albumPickerDismissed
        case albumTitleTapped
        case cardDragChanged(CGSize)
        case cardDragEnded
        case deleteButtonTapped
        case keepButtonTapped
        case menuButtonTapped
        case menuDismissed
        case resetButtonTapped
        case swipeAnimationCompleted(direction: Int)
        case undoButtonTapped
    }

    enum CancelID { case swipeAnimation }

    static let dragThreshold: CGFloat = 110
    static let animationDurationMs = 320

    @Dependency(\.continuousClock) var clock

    var body: some Reducer<State, Action> {
        Reduce { state, action in
            switch action {
            case .aboutDismissed:
                state.aboutOpen = false
                return .none

            case .aboutMenuItemTapped:
                state.menuOpen = false
                state.aboutOpen = true
                return .none

            case .albumPicked(let id):
                state.albumID = id
                state.pickerOpen = false
                resetProgress(&state)
                return .cancel(id: CancelID.swipeAnimation)

            case .albumPickerDismissed:
                state.pickerOpen = false
                return .none

            case .albumTitleTapped:
                state.pickerOpen = true
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

            case .menuButtonTapped:
                state.menuOpen.toggle()
                return .none

            case .menuDismissed:
                state.menuOpen = false
                return .none

            case .resetButtonTapped:
                resetProgress(&state)
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

            case .undoButtonTapped:
                guard let last = state.history.popLast() else { return .none }
                if last.kept {
                    state.kept = max(0, state.kept - 1)
                } else {
                    state.deleted = max(0, state.deleted - 1)
                }
                state.activeIndex = last.index
                return .none
            }
        }
    }

    // MARK: - Helpers

    private func startSwipe(_ state: inout State, direction: Int) -> Effect<Action> {
        guard state.activeIndex < PhotoLibrary.samples.count else { return .none }
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
    }
}

struct SwipeHistoryEntry: Equatable {
    let index: Int
    let kept: Bool
}
