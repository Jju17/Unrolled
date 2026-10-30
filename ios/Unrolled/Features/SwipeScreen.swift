//
//  SwipeScreen.swift
//  Unrolled
//

import ComposableArchitecture
import SwiftUI
import UIKit

struct SwipeScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.displayScale) private var displayScale
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @Bindable var store: StoreOf<SwipeFeature>

    private let stackDepth = 4
    /// Drives both the stack rise on commit (depth 1 to 0, 2 to 1, …)
    /// AND the spring-back when a drag ends under the threshold. Same
    /// spring in both cases so the whole thing feels coherent.
    private static let stackAnimation: Animation =
        .spring(response: 0.32, dampingFraction: 0.82)

    private let keepColor = DeckPalette.keep
    private let dropColor = DeckPalette.drop
    private let undoColor = DeckPalette.undo

    private var bgPage: Color { DeckPalette.page(colorScheme) }
    private var textPrim: Color { DeckPalette.textPrimary(colorScheme) }
    private var textSec: Color { DeckPalette.textSecondary(colorScheme) }
    private var surface: Color { DeckPalette.surface(colorScheme) }
    private var border: Color { DeckPalette.border(colorScheme) }

    var body: some View {
        ZStack {
            bgPage.ignoresSafeArea()

            GeometryReader { proxy in
                let bounds = CGRect(origin: .zero, size: proxy.size)
                if let fold = Self.activeFold(in: proxy),
                   let panes = DeckLayout.foldedPanes(in: bounds, fold: fold) {
                    foldedLayout(panes)
                } else {
                    VStack(spacing: 0) {
                        header
                        stackArea
                        actionBar
                    }
                    .frame(maxWidth: DeckLayout.maxColumnWidth)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }

            if store.goalCelebration {
                goalToast
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .zIndex(300)
            }

            if let milestone = store.milestoneReached {
                MilestoneOverlay(milestone: milestone) {
                    store.send(.milestoneDismissed)
                }
                .zIndex(400)
            }
        }
        .animation(.spring(response: 0.4, dampingFraction: 0.8), value: store.goalCelebration)
        .sensoryFeedback(.success, trigger: store.goalCelebration) { _, new in new }
        .sensoryFeedback(.success, trigger: store.milestoneReached) { _, new in new != nil }
        .sensoryFeedback(.success, trigger: store.isDone) { _, new in new && store.sessionReviewed > 0 }
        .sheet(isPresented: $store.aboutOpen) { AboutSheet() }
        .sheet(isPresented: $store.pickerOpen) {
            PilePickerView(
                piles: store.piles,
                current: store.selectedPile,
                onPick: { store.send(.pilePicked($0)) }
            )
        }
        .sheet(isPresented: $store.settingsOpen) {
            SettingsSheet(
                settings: store.settings,
                onDailyGoalChanged: { store.send(.settingsDailyGoalChanged($0)) },
                onDetectSimilarToggled: { store.send(.settingsDetectSimilarToggled($0)) },
                onGamificationToggled: { store.send(.settingsGamificationToggled($0)) },
                onIncludeVideosToggled: { store.send(.settingsIncludeVideosToggled($0)) },
                onPlayLivePhotosToggled: { store.send(.settingsPlayLivePhotosToggled($0)) },
                onReminderHourChanged: { store.send(.settingsReminderHourChanged($0)) },
                onReminderToggled: { store.send(.settingsReminderToggled($0)) },
                onShowLocationToggled: { store.send(.settingsShowLocationToggled($0)) }
            )
        }
        .sheet(isPresented: $store.statsOpen) {
            StatsSheet(
                stats: store.stats,
                dailyGoal: store.settings.dailyGoal,
                gamificationEnabled: store.settings.gamificationEnabled,
                onReset: { store.send(.statsResetTapped) }
            )
        }
        .sheet(isPresented: $store.similarSheetOpen) {
            SimilarPhotosSheet(
                photos: store.topSimilarGroup,
                onResolve: { kept, deleted in
                    store.send(.similarGroupResolved(keptIDs: kept, deletedIDs: deleted))
                }
            )
        }
        .fullScreenCover(isPresented: $store.fullscreenOpen) {
            FullscreenPhotoView(
                photos: store.photos,
                startIndex: store.activeIndex,
                settings: store.settings,
                onKeepAtRelativeIndex: { localIdx in
                    store.send(.fullscreenMarkAtRelativeIndex(localIdx: localIdx, kept: true))
                },
                onDeleteAtRelativeIndex: { localIdx in
                    store.send(.fullscreenMarkAtRelativeIndex(localIdx: localIdx, kept: false))
                }
            )
        }
        .sheet(isPresented: $store.deletedSheetOpen) {
            DeletedPhotosSheet(store: store)
        }
        .onAppear { store.send(.onAppear) }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                store.send(.libraryRefreshRequested)
            }
        }
        // Light haptic every time a commit fires. `activeIndex` advancing
        // is the authoritative signal (covers gestures, buttons).
        .onChange(of: store.activeIndex) { old, new in
            if new > old {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            }
        }
    }

    // MARK: - Header

    private var pileTitle: String {
        PileTitles.title(for: store.selectedPile, name: store.currentPile?.name)
    }

    private var headerSubtitle: String {
        guard store.libraryLoaded, !store.isLoading else { return "" }
        return String(localized: "\(store.remainingCount) left")
    }

    private var header: some View {
        HStack(alignment: .bottom) {
            pileTitleBlock
            Spacer()
            headerControls
        }
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 4)
    }

    private var pileTitleBlock: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("PILE")
                .font(.system(size: 11, weight: .semibold))
                .tracking(2)
                .foregroundStyle(textSec)
            Button {
                store.send(.pileTitleTapped)
            } label: {
                HStack(spacing: 6) {
                    Text(verbatim: pileTitle)
                        .font(.system(size: 24, weight: .bold))
                        .tracking(-0.5)
                        .foregroundStyle(textPrim)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                    Image(systemName: "chevron.down")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(textPrim)
                }
            }
            .buttonStyle(.plain)
            .accessibilityHint(Text("Open pile picker"))
            HStack(spacing: 8) {
                if !headerSubtitle.isEmpty {
                    Text(verbatim: headerSubtitle)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(textSec)
                        .lineLimit(1)
                }
                statsPill
            }
            .frame(minHeight: 32)
        }
    }

    private var headerControls: some View {
        HStack(spacing: 10) {
            if store.settings.gamificationEnabled {
                Button {
                    store.send(.statsButtonTapped)
                } label: {
                    GoalRing(
                        count: store.stats.todayCount,
                        goal: store.settings.dailyGoal,
                        streak: store.stats.currentStreak,
                        color: store.goalReachedToday ? keepColor : dropColor,
                        track: colorScheme == .dark ? Color.white.opacity(0.12) : Color.black.opacity(0.08)
                    )
                    .foregroundStyle(textPrim)
                }
                .buttonStyle(.plain)
                .accessibilityHint(Text("Opens your stats"))
            }
            iconButton(
                systemImage: "shuffle",
                active: store.shuffleEnabled
            ) {
                store.send(.shuffleButtonTapped)
            }
            .accessibilityLabel(Text("Shuffle photos"))
            Menu {
                Button {
                    store.send(.statsButtonTapped)
                } label: {
                    Label(String(localized: "Your stats"), systemImage: "chart.bar")
                }
                Button {
                    store.send(.settingsButtonTapped)
                } label: {
                    Label(String(localized: "Settings"), systemImage: "gearshape")
                }
                Button {
                    store.send(.aboutMenuItemTapped)
                } label: {
                    Label(String(localized: "About Unrolled"), systemImage: "info.circle")
                }
            } label: {
                iconButton(dots: true)
            }
            .accessibilityLabel(Text("More options"))
        }
    }

    // MARK: - Folded iPhone Duo

    // Reserved regions need the iOS 27.1 SDK (Xcode 27, Swift 6.4); older toolchains build the flat layout only.
    private static func activeFold(in proxy: GeometryProxy) -> CGRect? {
        #if compiler(>=6.4)
        guard #available(iOS 27.1, *) else { return nil }
        guard let region = proxy.reservedRegions(kind: .division).first(where: \.isActive) else { return nil }
        let margins = region.margins
        return CGRect(
            x: region.frame.minX - margins.leading,
            y: region.frame.minY - margins.top,
            width: region.frame.width + margins.leading + margins.trailing,
            height: region.frame.height + margins.top + margins.bottom
        )
        #else
        _ = proxy
        return nil
        #endif
    }

    private func foldedLayout(_ panes: DeckLayout.FoldedPanes) -> some View {
        ZStack(alignment: .topLeading) {
            stackArea
                .frame(width: panes.content.width, height: panes.content.height)
                .offset(x: panes.content.minX, y: panes.content.minY)
            FoldedControls(
                store: store,
                orientation: panes.orientation,
                title: { pileTitleBlock },
                controls: { headerControls },
                animation: Self.stackAnimation
            )
            .frame(width: panes.controls.width, height: panes.controls.height)
            .offset(x: panes.controls.minX, y: panes.controls.minY)
        }
    }

    private var goalToast: some View {
        VStack {
            HStack(spacing: 8) {
                Image(systemName: "flame.fill")
                    .foregroundStyle(keepColor)
                if store.stats.currentStreak > 1 {
                    Text("Daily goal reached, \(store.stats.currentStreak) day streak")
                        .font(.system(size: 14, weight: .semibold))
                } else {
                    Text("Daily goal reached")
                        .font(.system(size: 14, weight: .semibold))
                }
            }
            .foregroundStyle(textPrim)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Capsule().fill(surface).shadow(color: .black.opacity(0.15), radius: 10, y: 4))
            .padding(.top, 72)
            Spacer()
        }
        .task {
            try? await Task.sleep(for: .seconds(2))
            store.send(.goalCelebrationDismissed)
        }
    }

    /// Tally pill: kept / deleted. Tapping opens the review sheet, which
    /// also hosts the progress reset. Hidden while there's nothing to
    /// show so it doesn't crowd a fresh pile.
    @ViewBuilder
    private var statsPill: some View {
        if store.kept > 0 || store.deleted > 0 {
            Button {
                store.send(.deletedSheetButtonTapped)
            } label: {
                HStack(spacing: 6) {
                    HStack(spacing: 3) {
                        Image(systemName: "heart.fill")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(keepColor)
                        // `.fixedSize` + `.lineLimit(1)` so SwiftUI never
                        // wraps a two-digit count into "3\n3" when the
                        // header's right group gets squeezed by a long
                        // album title on the left.
                        Text("\(store.kept)")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(textPrim)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                    }
                    Rectangle()
                        .fill(textSec.opacity(0.4))
                        .frame(width: 1, height: 12)
                    HStack(spacing: 3) {
                        Image(systemName: "xmark")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(dropColor)
                        Text("\(store.deleted)")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(textPrim)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(
                    Capsule().fill(
                        colorScheme == .dark
                            ? Color.white.opacity(0.08)
                            : Color.black.opacity(0.05)
                    )
                )
                .frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(
                Text("\(store.kept) kept, \(store.deleted) deleted")
            )
            .accessibilityHint(Text("Opens the trash list"))
        }
    }

    @ViewBuilder
    private func iconButton(
        systemImage: String = "",
        dots: Bool = false,
        active: Bool = false,
        action: @escaping () -> Void = {}
    ) -> some View {
        let backgroundColor: Color = active
            ? dropColor
            : (colorScheme == .dark ? Color.white.opacity(0.08) : Color.black.opacity(0.05))
        let foregroundColor: Color = active ? .white : textPrim

        let shape = Circle()
            .fill(backgroundColor)
            .frame(width: 32, height: 32)
            .overlay {
                if dots {
                    HStack(spacing: 3) {
                        ForEach(0..<3) { _ in Circle().fill(foregroundColor).frame(width: 4, height: 4) }
                    }
                } else {
                    Image(systemName: systemImage)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(foregroundColor)
                }
            }
            .frame(width: 44, height: 44)
            .contentShape(Circle())
        if dots {
            shape
        } else {
            Button(action: action) { shape }
                .buttonStyle(.plain)
        }
    }

    // MARK: - Stack

    /// Up to `stackDepth + 1` cards from `activeIndex` forward. The
    /// exiting card is rendered SEPARATELY as an overlay with its own
    /// `@State` animation, so the stack-rise and the fly-off are fully
    /// decoupled and neither can interrupt the other.
    private var visibleSlice: [(idx: Int, photo: PhotoAsset)] {
        let start = store.activeIndex
        let end = min(start + stackDepth + 1, store.photos.count)
        guard start < end else { return [] }
        return (start..<end).map { (idx: $0, photo: store.photos[$0]) }
    }

    private var stackArea: some View {
        GeometryReader { geo in
            let cardSize = DeckLayout.cardSize(fitting: geo.size)
            ZStack {
                switch contentState {
                case .permissionGate:
                    permissionGate.frame(maxWidth: 420)
                case .loading:
                    ProgressView().tint(textPrim)
                case .empty:
                    emptyState.frame(maxWidth: 420)
                case .done:
                    doneView.frame(maxWidth: 420)
                case .cards:
                    ForEach(visibleSlice, id: \.photo.id) { item in
                        let depth = item.idx - store.activeIndex
                        CardSlot(
                            photo: item.photo,
                            depth: depth,
                            drag: depth == 0 ? store.drag : .zero,
                            size: cardSize,
                            keepColor: keepColor,
                            dropColor: dropColor,
                            showLocation: store.settings.showLocation,
                            playLivePhotos: store.settings.playLivePhotos,
                            similarCount: depth == 0 ? store.topSimilarGroup.count : 0,
                            onSimilarTapped: { store.send(.similarBadgeTapped) },
                            onDragChanged: { store.send(.cardDragChanged($0)) },
                            onDragEnded: {
                                store.send(.cardDragEnded, animation: Self.stackAnimation)
                            },
                            onTap: { store.send(.topCardTapped) }
                        )
                        // zIndex must derive from a STABLE identifier,
                        // not from `depth`. `zIndex` isn't `Animatable`,
                        // so SwiftUI applies new values non-atomically
                        // during a transition: deriving it from depth
                        // (which changes on commit) causes cards to
                        // briefly flash in the wrong order. The photo's
                        // absolute index in `store.photos` is stable
                        // for a given card's lifetime, so no re-diff.
                        // Negative because smaller idx = closer to top.
                        .zIndex(-Double(item.idx))
                        // When a fresh photo enters the stack window
                        // (activeIndex bumps, idx = activeIndex + stackDepth
                        // now fits), SwiftUI adds it here. The asymmetric
                        // transition fades + scales it in so there's no
                        // pop-in at the bottom. Removal is `.identity`
                        // because the exit overlay handles the fly-off.
                        .transition(
                            .asymmetric(
                                insertion: .opacity.combined(with: .scale(scale: 0.85)),
                                removal: .identity
                            )
                        )
                    }
                    .onAppear { prefetchUpcoming(size: cardSize) }
                    .onChange(of: store.activeIndex) { _, _ in prefetchUpcoming(size: cardSize) }
                    .onChange(of: cardSize) { _, new in prefetchUpcoming(size: new) }

                    // Fly-off overlay: `id(...)` forces a fresh view
                    // instance per commit so `ExitingCardView.onAppear`
                    // re-runs and drives its `@State` animation.
                    if let exiting = store.exitingPhoto {
                        ExitingCardView(
                            photo: exiting,
                            direction: store.exitingDirection,
                            startDrag: store.exitingStartDrag,
                            size: cardSize,
                            keepColor: keepColor,
                            dropColor: dropColor,
                            showLocation: store.settings.showLocation
                        )
                        .id(exiting.id)
                        .zIndex(200)
                        .allowsHitTesting(false)
                    }
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 6)
    }

    private enum ContentState { case permissionGate, loading, empty, done, cards }

    private var contentState: ContentState {
        if !store.authorizationStatus.grantsRead { return .permissionGate }
        if store.isLoading { return .loading }
        if store.isPileEmpty { return .empty }
        if store.isDone { return .done }
        if store.photos.isEmpty { return .loading }
        return .cards
    }

    /// Number of upcoming photos to warm in the cache. Chosen for the
    /// iCloud case: photos may need a network round-trip to download,
    /// and the user can swipe through the visible stack faster than a
    /// single fetch completes. A 12-photo lookahead gives PhotoKit
    /// enough headroom to keep the *next* top card already decoded
    /// even on slower connections.
    private static let prefetchAhead = 12

    private func prefetchUpcoming(size: CGSize) {
        let start = store.activeIndex
        let end = min(start + Self.prefetchAhead, store.photos.count)
        guard end > start else { return }
        let ids = store.photos[start..<end].map(\.id)
        let px = CGSize(
            width: size.width * displayScale,
            height: size.height * displayScale
        )
        Task.detached(priority: .utility) {
            PhotoImageCache.shared.prefetch(ids: ids, size: px)
        }
    }

    // MARK: - Non-card states

    private var permissionGate: some View {
        VStack(spacing: 12) {
            Image(systemName: "photo.on.rectangle.angled")
                .font(.system(size: 48))
                .foregroundStyle(textSec)
            Text("Photo library access")
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(textPrim)
            Text("Unrolled needs permission to read your photos so you can swipe through them.")
                .font(.system(size: 14))
                .foregroundStyle(textSec)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
            if store.authorizationStatus == .denied {
                Button("Open Settings") {
                    store.send(.openSettingsTapped)
                }
                .buttonStyle(PrimaryButtonStyle(color: dropColor))
                .padding(.top, 8)
            } else {
                Button("Allow access") {
                    store.send(.requestAuthorizationTapped)
                }
                .buttonStyle(PrimaryButtonStyle(color: dropColor))
                .padding(.top, 8)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    @ViewBuilder
    private var emptyState: some View {
        if store.authorizationStatus == .limited {
            VStack(spacing: 10) {
                Image(systemName: "photo.on.rectangle.angled")
                    .font(.system(size: 42))
                    .foregroundStyle(textSec)
                Text("No selected photos")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(textPrim)
                Text("You only granted Unrolled access to a selection of photos. Pick more to continue.")
                    .font(.system(size: 14))
                    .foregroundStyle(textSec)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
                Button("Change selection") {
                    store.send(.changeSelectionTapped)
                }
                .buttonStyle(PrimaryButtonStyle(color: dropColor))
                .padding(.top, 10)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            VStack(spacing: 8) {
                Text("No photos found")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(textPrim)
                if store.selectedPile == .all {
                    Text("Your library is empty.")
                        .font(.system(size: 14))
                        .foregroundStyle(textSec)
                } else {
                    Text("This pile is empty.")
                        .font(.system(size: 14))
                        .foregroundStyle(textSec)
                }
                if store.selectedPile != .all {
                    Button("Change pile") {
                        store.send(.pileTitleTapped)
                    }
                    .buttonStyle(PrimaryButtonStyle(color: dropColor))
                    .padding(.top, 10)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private var doneView: some View {
        SessionSummaryView(
            reviewed: store.sessionReviewed,
            kept: store.sessionKeptIDs.count,
            deleted: store.sessionDeletedIDs.count,
            bytesToFree: store.sessionBytesToFree,
            duration: store.sessionDuration,
            photosPerMinute: store.sessionPhotosPerMinute,
            isOnAllPhotos: store.selectedPile == .all,
            keepColor: keepColor,
            dropColor: dropColor,
            textPrim: textPrim,
            textSec: textSec,
            border: border,
            onChangePile: { store.send(.pileTitleTapped) },
            onViewAll: { store.send(.viewAllPhotosTapped) },
            onStartOver: { store.send(.resetButtonTapped) }
        )
    }

    // MARK: - Action bar

    private var actionBar: some View {
        ZStack {
            HStack(spacing: 14) {
                Spacer()
                actionButton(size: 58, color: dropColor, action: {
                    store.send(.deleteButtonTapped, animation: Self.stackAnimation)
                }) {
                    Image(systemName: "xmark")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundStyle(dropColor)
                }
                .accessibilityLabel("Delete this photo")
                .accessibilityHint("The photo goes to the trash list")
                actionButton(size: 46, color: undoColor,
                             action: {
                                 store.send(.undoButtonTapped, animation: Self.stackAnimation)
                             },
                             disabled: store.history.isEmpty) {
                    Image(systemName: "arrow.uturn.backward")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(undoColor)
                }
                .accessibilityLabel("Undo last choice")
                actionButton(size: 58, color: keepColor, action: {
                    store.send(.keepButtonTapped, animation: Self.stackAnimation)
                }) {
                    Image(systemName: "heart.fill")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(keepColor)
                }
                .accessibilityLabel("Keep this photo")
                Spacer()
            }

            // Side ancillary action: "view in Photos.app". Pinned to the
            // leading edge as a ZStack overlay so the centered keep/undo/
            // delete trio above doesn't shift when this is shown/hidden.
            if store.topPhoto != nil {
                HStack {
                    actionButton(size: 40, color: textSec, action: {
                        store.send(.viewInPhotosTapped)
                    }) {
                        Image(systemName: "photo.on.rectangle")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(textSec)
                    }
                    .accessibilityLabel(Text("Open Photos app"))
                    Spacer()
                }
                .padding(.leading, 20)
            }
        }
        .padding(.top, 14)
        .padding(.bottom, 10)
        .background(
            LinearGradient(
                colors: [bgPage.opacity(0), bgPage.opacity(0.85), bgPage],
                startPoint: .top, endPoint: .bottom
            )
            .allowsHitTesting(false)
        )
        .opacity(contentState == .cards ? 1 : 0)
        .allowsHitTesting(contentState == .cards)
        .accessibilityHidden(contentState != .cards)
    }

    private func actionButton<Content: View>(
        size: CGFloat, color: Color, action: @escaping () -> Void,
        disabled: Bool = false, @ViewBuilder content: @escaping () -> Content
    ) -> some View {
        DeckActionButton(size: size, color: color, disabled: disabled, action: action, content: content)
    }
}

// MARK: - CardSlot

/// One stack card. Pure presentation: transforms follow the drag if
/// `depth == 0`, otherwise they settle to their stack position. The
/// ambient animation installed by `store.send(_:animation:)` drives any
/// state-change-induced transforms (stack rise, spring-back from
/// under-threshold drag). Live drag is un-animated (no ambient wrap on
/// `cardDragChanged`), so the card tracks the finger 1:1.
private struct CardSlot: View {
    let photo: PhotoAsset
    let depth: Int
    let drag: CGSize
    let size: CGSize
    let keepColor: Color
    let dropColor: Color
    let showLocation: Bool
    let playLivePhotos: Bool
    let similarCount: Int
    let onSimilarTapped: () -> Void
    let onDragChanged: (CGSize) -> Void
    let onDragEnded: () -> Void
    let onTap: () -> Void

    private static let stackRotations: [Double] = [0, -1.2, 0.8, -0.6, 0.4]

    private var isTop: Bool { depth == 0 }
    private var clampedDepth: Int { max(0, min(depth, Self.stackRotations.count - 1)) }

    private var offsetX: CGFloat { isTop ? drag.width : 0 }
    private var offsetY: CGFloat {
        isTop ? drag.height : CGFloat(clampedDepth) * 10
    }
    private var rotation: Double {
        isTop ? Double(drag.width / 15) : Self.stackRotations[clampedDepth]
    }
    private var scale: Double {
        isTop ? 1 : 1 - Double(clampedDepth) * 0.045
    }

    private var keepOpacity: Double {
        isTop ? max(0, min(1, Double(drag.width) / 80)) : 0
    }
    private var dropOpacity: Double {
        isTop ? max(0, min(1, Double(-drag.width) / 80)) : 0
    }

    var body: some View {
        PhotoCard(
            photo: photo,
            keepColor: keepColor,
            dropColor: dropColor,
            keepOpacity: keepOpacity,
            dropOpacity: dropOpacity,
            showLabels: isTop,
            showLocation: showLocation,
            playLivePhotos: playLivePhotos,
            similarCount: similarCount,
            onSimilarTapped: onSimilarTapped
        )
        .frame(width: size.width, height: size.height)
        // Always pinchable: non-top cards are behind in z-order so
        // they never receive touches anyway. Critically, applying the
        // modifier unconditionally keeps a stable view-tree shape: a
        // previous `if isTop { content.pinchable() } else { content }`
        // wrapper changed branches when a card moved from depth 1 to 0,
        // and the branch swap blew away the inner `PhotoAssetImage`'s
        // `@State image`, causing the half-second black flash on the
        // newly-promoted card while it reloaded.
        .pinchable()
        .clipShape(RoundedRectangle(cornerRadius: 24))
        .contentShape(RoundedRectangle(cornerRadius: 24))
        .offset(x: offsetX, y: offsetY)
        .rotationEffect(.degrees(rotation), anchor: .bottom)
        .scaleEffect(scale, anchor: .bottom)
        .onTapGesture { if isTop { onTap() } }
        // Always attach the drag gesture (its modifier becomes part of
        // the view tree). Conditioning the gesture on `isTop` previously
        // produced a "modifier present / modifier absent" tree swap on
        // every commit, which is one of the things that wiped state
        // from descendants. The gesture handlers themselves still no-op
        // when `isTop` is false, so non-top cards stay inert.
        .simultaneousGesture(dragGesture)
    }

    private var dragGesture: some Gesture {
        DragGesture()
            .onChanged { value in
                guard isTop else { return }
                onDragChanged(value.translation)
            }
            .onEnded { _ in
                guard isTop else { return }
                onDragEnded()
            }
    }
}

// MARK: - ExitingCardView

/// Overlay that flies a just-committed photo off-screen. Uses the
/// declarative `.animation(_:value:)` pattern rather than `withAnimation`
/// inside `.onAppear`: the latter is unreliable when SwiftUI happens to
/// be mid-transaction at the moment the overlay first appears, which
/// manifested as the card "stopping dead" the instant the finger lifted.
private struct ExitingCardView: View {
    let photo: PhotoAsset
    let direction: Int
    let startDrag: CGSize
    let size: CGSize
    let keepColor: Color
    let dropColor: Color
    let showLocation: Bool

    /// Shorter than the reducer's `exitAnimationDurationMs` so the
    /// overlay always finishes animating before it's cleared from state.
    private static let duration: Double = 0.26

    /// `false` = card sits at the finger's last position. `true` = card
    /// is fully off-screen. Flipped in `.onAppear`, animated by the
    /// `.animation(_:value:)` modifier below.
    @State private var offScreen = false

    private var endX: CGFloat {
        // `size.width + 300` ensures the card is fully past the edge no
        // matter how far the finger pulled it before lifting.
        startDrag.width + CGFloat(direction) * (size.width + 300)
    }

    var body: some View {
        PhotoCard(
            photo: photo,
            keepColor: keepColor,
            dropColor: dropColor,
            keepOpacity: direction > 0 ? 1 : 0,
            dropOpacity: direction < 0 ? 1 : 0,
            showLabels: true,
            showLocation: showLocation
        )
        .frame(width: size.width, height: size.height)
        .clipShape(RoundedRectangle(cornerRadius: 24))
        .offset(
            x: offScreen ? endX : startDrag.width,
            y: startDrag.height
        )
        .rotationEffect(
            .degrees(offScreen ? Double(direction) * 30
                               : Double(startDrag.width / 15)),
            anchor: .bottom
        )
        .animation(.easeOut(duration: Self.duration), value: offScreen)
        .onAppear { offScreen = true }
    }
}

private struct PrimaryButtonStyle: ButtonStyle {
    let color: Color
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, 22)
            .padding(.vertical, 12)
            .background(Capsule().fill(color))
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

#Preview {
    SwipeScreen(
        store: Store(initialState: SwipeFeature.State()) {
            SwipeFeature()
        } withDependencies: {
            $0.photoLibrary = .previewValue
        }
    )
}
