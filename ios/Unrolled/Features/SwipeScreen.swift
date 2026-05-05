//
//  SwipeScreen.swift
//  Unrolled
//

import ComposableArchitecture
import SwiftUI
import UIKit

struct SwipeScreen: View {
    @Environment(\.colorScheme) private var colorScheme

    @Bindable var store: StoreOf<SwipeFeature>

    private let stackDepth = 4
    /// Drives both the stack rise on commit (depth 1 → 0, 2 → 1, …)
    /// AND the spring-back when a drag ends under the threshold. Same
    /// spring in both cases so the whole thing feels coherent.
    private static let stackAnimation: Animation =
        .spring(response: 0.32, dampingFraction: 0.82)

    private var done: Bool { store.activeIndex >= store.photos.count && !store.photos.isEmpty }

    private let keepColor = Color(red: 0.188, green: 0.824, blue: 0.541)
    private let dropColor = Color(red: 0.937, green: 0.243, blue: 0.357)
    private let undoColor = Color(red: 0.961, green: 0.725, blue: 0.271)

    private var bgPage: Color {
        colorScheme == .dark
            ? Color(red: 0.055, green: 0.055, blue: 0.071)
            : Color(red: 0.965, green: 0.961, blue: 0.945)
    }
    private var textPrim: Color {
        colorScheme == .dark ? .white : Color(red: 0.102, green: 0.102, blue: 0.102)
    }
    private var textSec: Color {
        (colorScheme == .dark ? Color.white : Color.black).opacity(0.55)
    }
    private var surface: Color {
        colorScheme == .dark ? Color(red: 0.102, green: 0.102, blue: 0.125) : .white
    }
    private var border: Color {
        (colorScheme == .dark ? Color.white : Color.black)
            .opacity(colorScheme == .dark ? 0.08 : 0.06)
    }

    var body: some View {
        ZStack {
            bgPage.ignoresSafeArea()

            VStack(spacing: 0) {
                header
                stackArea
                actionBar
            }

            if contentState == .cards {
                settingsGearButton
                    .frame(maxWidth: .infinity, maxHeight: .infinity,
                           alignment: .bottomLeading)
                    .padding(.leading, 20)
                    .padding(.bottom, 48)
                    .allowsHitTesting(true)
            }
        }
        .sheet(isPresented: $store.aboutOpen) { AboutSheet() }
        .sheet(isPresented: $store.pickerOpen) {
            AlbumPickerView(
                albums: store.albums,
                currentID: store.currentAlbum?.id ?? "",
                onPick: { store.send(.albumPicked($0)) }
            )
        }
        .sheet(isPresented: $store.settingsOpen) {
            SettingsSheet(
                hasTopPhoto: store.topPhoto != nil,
                onViewInPhotos: { store.send(.viewInPhotosTapped) }
            )
        }
        .fullScreenCover(isPresented: $store.fullscreenOpen) {
            if let photo = store.topPhoto {
                FullscreenPhotoView(photo: photo)
            }
        }
        .sheet(isPresented: $store.deletedSheetOpen) {
            DeletedPhotosSheet(store: store)
        }
        .alert(
            // String Catalog handles plural variations (one/other) → "Delete %lld photo?" / "Delete %lld photos?"
            String(
                localized: "Delete \(store.deletedPhotoIDs.count) photos?"
            ),
            isPresented: $store.trashAlertOpen
        ) {
            Button("Cancel", role: .cancel) { }
            Button("Delete", role: .destructive) {
                store.send(.emptyTrashConfirmed)
            }
        } message: {
            Text("Photos will be moved to the Photos app's trash. You can recover them from \"Recently Deleted\" for 30 days.")
        }
        .onAppear { store.send(.onAppear) }
        // Light haptic every time a commit fires. `activeIndex` advancing
        // is the authoritative signal (covers gestures, buttons).
        .onChange(of: store.activeIndex) { old, new in
            if new > old {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            }
        }
    }

    private var settingsGearButton: some View {
        Button {
            store.send(.settingsButtonTapped)
        } label: {
            Image(systemName: "gearshape")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(textSec)
                .frame(width: 36, height: 36)
                .background(
                    Circle().fill(
                        colorScheme == .dark
                            ? Color.white.opacity(0.06)
                            : Color.black.opacity(0.04)
                    )
                )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Header

    private var header: some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 2) {
                Text("ALBUM")
                    .font(.system(size: 11, weight: .semibold))
                    .tracking(2)
                    .foregroundStyle(textSec)
                Button {
                    store.send(.albumTitleTapped)
                } label: {
                    HStack(spacing: 6) {
                        // Album.name comes from PhotoKit which already returns
                        // the localized smart-album label ("Récents" in fr,
                        // "Recents" in en). Fallback is a plain key the
                        // catalog will translate.
                        Text(store.currentAlbum?.name ?? String(localized: "Photos"))
                            .font(.system(size: 24, weight: .bold))
                            .tracking(-0.5)
                            .foregroundStyle(textPrim)
                        Image(systemName: "chevron.down")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(textPrim)
                    }
                }
                .buttonStyle(.plain)
            }
            Spacer()
            HStack(spacing: 10) {
                statsPill
                iconButton(
                    systemImage: "shuffle",
                    active: store.shuffleEnabled
                ) {
                    store.send(.shuffleButtonTapped)
                }
                Menu {
                    Button {
                        store.send(.aboutMenuItemTapped)
                    } label: {
                        Label(String(localized: "About Unrolled"), systemImage: "info.circle")
                    }
                } label: {
                    iconButton(dots: true)
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 12)
    }

    /// Tally pill: ♡ kept   ✗ deleted. Tapping opens the review sheet
    /// for the trash-staged photos. Hidden while there's nothing to
    /// show so it doesn't crowd a fresh album.
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
                        Text("\(store.kept)")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(textPrim)
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
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(
                    Capsule().fill(
                        colorScheme == .dark
                            ? Color.white.opacity(0.08)
                            : Color.black.opacity(0.05)
                    )
                )
            }
            .buttonStyle(.plain)
            .disabled(store.deleted == 0)
            .opacity(store.deleted == 0 ? 0.6 : 1)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(
                Text("\(store.kept) kept, \(store.deleted) deleted")
            )
            .accessibilityHint(
                store.deleted > 0
                    ? Text("Opens the trash list")
                    : Text("No photos to delete yet")
            )
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
                            size: geo.size,
                            keepColor: keepColor,
                            dropColor: dropColor,
                            onDragChanged: { store.send(.cardDragChanged($0)) },
                            onDragEnded: {
                                store.send(.cardDragEnded, animation: Self.stackAnimation)
                            },
                            onTap: { store.send(.topCardTapped) }
                        )
                        // zIndex must derive from a STABLE identifier,
                        // not from `depth`. `zIndex` isn't `Animatable`,
                        // so SwiftUI applies new values non-atomically
                        // during a transition — deriving it from depth
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
                    .onAppear { prefetchUpcoming(size: geo.size) }
                    .onChange(of: store.activeIndex) { _, _ in prefetchUpcoming(size: geo.size) }

                    // Fly-off overlay — `id(...)` forces a fresh view
                    // instance per commit so `ExitingCardView.onAppear`
                    // re-runs and drives its `@State` animation.
                    if let exiting = store.exitingPhoto {
                        ExitingCardView(
                            photo: exiting,
                            direction: store.exitingDirection,
                            startDrag: store.exitingStartDrag,
                            size: geo.size,
                            keepColor: keepColor,
                            dropColor: dropColor
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
        if store.photos.isEmpty { return .empty }
        if done { return .done }
        return .cards
    }

    private func prefetchUpcoming(size: CGSize) {
        let start = store.activeIndex
        let end = min(start + stackDepth + 2, store.photos.count)
        guard end > start else { return }
        let ids = store.photos[start..<end].map(\.id)
        let px = CGSize(
            width: size.width * UIScreen.main.scale,
            height: size.height * UIScreen.main.scale
        )
        Task.detached(priority: .utility) {
            await PhotoImageCache.shared.prefetch(ids: ids, size: px)
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
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
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

    private var emptyState: some View {
        VStack(spacing: 8) {
            Text("No photos found")
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(textPrim)
            Text("Your library is empty.")
                .font(.system(size: 14))
                .foregroundStyle(textSec)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var doneView: some View {
        let isOnAllPhotos = store.currentAlbum?.id == store.albums.first?.id

        return VStack(spacing: 8) {
            Image(systemName: "checkmark.seal.fill")
                .font(.system(size: 44))
                .foregroundStyle(keepColor)
                .padding(.bottom, 4)
            Text("Album finished")
                .font(.system(size: 28, weight: .bold))
                .tracking(-0.8)
                .foregroundStyle(textPrim)
            Text("\(store.kept) kept · \(store.deleted) deleted")
                .font(.system(size: 14))
                .foregroundStyle(textSec)

            VStack(spacing: 10) {
                if !isOnAllPhotos {
                    Button("View all my photos") {
                        store.send(.viewAllPhotosTapped)
                    }
                    .buttonStyle(PrimaryButtonStyle(color: dropColor))
                }
                Button("Change album") {
                    store.send(.albumTitleTapped)
                }
                .buttonStyle(SecondaryButtonStyle(color: textPrim, border: border))
                Button("Start over") {
                    store.send(.resetButtonTapped)
                }
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(textSec)
                .padding(.top, 2)
            }
            .padding(.top, 16)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .overlay(
            RoundedRectangle(cornerRadius: 24)
                .strokeBorder(border, style: StrokeStyle(lineWidth: 2, dash: [8, 6]))
        )
    }

    // MARK: - Action bar

    private var actionBar: some View {
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
    }

    private func actionButton<Content: View>(
        size: CGFloat, color: Color, action: @escaping () -> Void,
        disabled: Bool = false, @ViewBuilder content: () -> Content
    ) -> some View {
        Button(action: action) {
            ZStack {
                Circle()
                    .fill(surface)
                    .overlay(Circle().stroke(border, lineWidth: 1))
                    .shadow(color: color.opacity(disabled ? 0 : 0.2), radius: 9, x: 0, y: 6)
                content()
            }
            .frame(width: size, height: size)
        }
        .buttonStyle(ActionButtonStyle())
        .disabled(disabled)
        .opacity(disabled ? 0.35 : 1)
    }
}

// MARK: - CardSlot

/// One stack card. Pure presentation — transforms follow the drag if
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
            showLabels: isTop
        )
        .frame(width: size.width, height: size.height)
        .modifier(TopCardPinchable(enabled: isTop))
        .clipShape(RoundedRectangle(cornerRadius: 24))
        .offset(x: offsetX, y: offsetY)
        .rotationEffect(.degrees(rotation), anchor: .bottom)
        .scaleEffect(scale, anchor: .bottom)
        .onTapGesture { if isTop { onTap() } }
        .simultaneousGesture(isTop ? dragGesture : nil)
    }

    private var dragGesture: some Gesture {
        DragGesture()
            .onChanged { onDragChanged($0.translation) }
            .onEnded { _ in onDragEnded() }
    }
}

// MARK: - ExitingCardView

/// Overlay that flies a just-committed photo off-screen. Uses the
/// declarative `.animation(_:value:)` pattern rather than `withAnimation`
/// inside `.onAppear` — the latter is unreliable when SwiftUI happens to
/// be mid-transaction at the moment the overlay first appears, which
/// manifested as the card "stopping dead" the instant the finger lifted.
private struct ExitingCardView: View {
    let photo: PhotoAsset
    let direction: Int
    let startDrag: CGSize
    let size: CGSize
    let keepColor: Color
    let dropColor: Color

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
            showLabels: true
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

private struct TopCardPinchable: ViewModifier {
    let enabled: Bool
    func body(content: Content) -> some View {
        if enabled {
            content.pinchable()
        } else {
            content
        }
    }
}

private struct ActionButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.92 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
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

private struct SecondaryButtonStyle: ButtonStyle {
    let color: Color
    let border: Color
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(color)
            .padding(.horizontal, 22)
            .padding(.vertical, 12)
            .background(Capsule().stroke(border, lineWidth: 1))
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
