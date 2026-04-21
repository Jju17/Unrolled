//
//  SwipeScreen.swift
//  PicSwipe
//

import ComposableArchitecture
import SwiftUI

struct SwipeScreen: View {
    @Environment(\.colorScheme) private var colorScheme

    @Bindable var store: StoreOf<SwipeFeature>

    private let stackDepth = 4

    private var done: Bool { store.activeIndex >= store.photos.count && !store.photos.isEmpty }

    // Accent (coral default)
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

            // Subtle gear in the bottom-left corner — hidden until
            // we're actually showing cards so it doesn't clutter
            // loading / permission / done views.
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
        .onAppear { store.send(.onAppear) }
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
                        Text(store.currentAlbum?.name ?? "Photos")
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
                        Label("About PicSwipe", systemImage: "info.circle")
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

    /// Small circular topbar button. Either shows an SF Symbol, or the
    /// three-dot pattern when `dots == true` (used as the Menu's label).
    /// When `active` is true, the button is filled with the accent colour
    /// to signal a toggled-on state (e.g. shuffle enabled).
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
            shape  // caller wraps in Menu
        } else {
            Button(action: action) { shape }
                .buttonStyle(.plain)
        }
    }

    // MARK: - Stack

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
                    ForEach(Array(store.photos.enumerated()), id: \.element.id) { idx, photo in
                        let depth = idx - store.activeIndex
                        if depth >= 0 && depth <= stackDepth {
                            cardView(photo: photo, depth: depth, size: geo.size)
                                .zIndex(Double(100 - depth))
                        }
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

    private func cardView(photo: PhotoAsset, depth: Int, size: CGSize) -> some View {
        let isTop = depth == 0
        let stackRotations: [Double] = [0, -1.2, 0.8, -0.6, 0.4]
        let stackRotation = stackRotations[min(depth, stackRotations.count - 1)]
        let stackScale = 1 - Double(depth) * 0.045
        let stackY = CGFloat(depth) * 10

        let dragX = isTop ? store.drag.width : 0
        let dragY = isTop ? store.drag.height : 0
        let rotation = isTop ? Double(dragX / 15) : stackRotation

        let keepOpacity = isTop ? max(0, min(1, Double(dragX) / 80)) : 0
        let dropOpacity = isTop ? max(0, min(1, Double(-dragX) / 80)) : 0

        let exiting = isTop && store.exitingDirection != 0
        let exitX: CGFloat = exiting ? CGFloat(store.exitingDirection) * 800 : 0
        let exitRot: Double = exiting ? Double(store.exitingDirection) * 30 : 0

        return PhotoCard(
            photo: photo,
            keepColor: keepColor,
            dropColor: dropColor,
            keepOpacity: keepOpacity,
            dropOpacity: dropOpacity,
            showLabels: isTop
        )
        .frame(width: size.width, height: size.height)
        .offset(
            x: exiting ? exitX : dragX,
            y: (exiting ? 0 : dragY) + stackY
        )
        .rotationEffect(.degrees(exiting ? exitRot : rotation), anchor: .bottom)
        .scaleEffect(isTop ? 1 : stackScale, anchor: .bottom)
        .animation(store.isDragging && isTop ? nil : .spring(response: 0.35, dampingFraction: 0.78), value: store.drag)
        .animation(.easeIn(duration: 0.32), value: store.exitingDirection)
        .gesture(isTop ? dragGesture : nil)
    }

    private var dragGesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                store.send(.cardDragChanged(value.translation))
            }
            .onEnded { value in
                // Hypot-distance < 5pt counts as a tap (finger down + up
                // without meaningful motion) — open the fullscreen view.
                let distance = hypot(value.translation.width, value.translation.height)
                if distance < 5 {
                    store.send(.topCardTapped)
                } else {
                    store.send(.cardDragEnded)
                }
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
            Text("PicSwipe needs permission to read your photos so you can swipe through them.")
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
        // Hide "View all photos" if we're already on the first (all-photos)
        // album so the CTA never fires a no-op album switch.
        let isOnAllPhotos = store.currentAlbum?.id == store.albums.first?.id

        return VStack(spacing: 8) {
            Image(systemName: "checkmark.seal.fill")
                .font(.system(size: 44))
                .foregroundStyle(keepColor)
                .padding(.bottom, 4)
            Text("Album terminé")
                .font(.system(size: 28, weight: .bold))
                .tracking(-0.8)
                .foregroundStyle(textPrim)
            Text("\(store.kept) gardée\(store.kept == 1 ? "" : "s") · \(store.deleted) supprimée\(store.deleted == 1 ? "" : "s")")
                .font(.system(size: 14))
                .foregroundStyle(textSec)

            VStack(spacing: 10) {
                if !isOnAllPhotos {
                    Button("Voir toutes mes photos") {
                        store.send(.viewAllPhotosTapped)
                    }
                    .buttonStyle(PrimaryButtonStyle(color: dropColor))
                }
                Button("Changer d'album") {
                    store.send(.albumTitleTapped)
                }
                .buttonStyle(SecondaryButtonStyle(color: textPrim, border: border))
                Button("Tout refaire") {
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
            actionButton(size: 58, color: dropColor, action: { store.send(.deleteButtonTapped) }) {
                Image(systemName: "xmark")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(dropColor)
            }
            actionButton(size: 46, color: undoColor,
                         action: { store.send(.undoButtonTapped) },
                         disabled: store.history.isEmpty) {
                Image(systemName: "arrow.uturn.backward")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(undoColor)
            }
            actionButton(size: 58, color: keepColor, action: { store.send(.keepButtonTapped) }) {
                Image(systemName: "heart.fill")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(keepColor)
            }
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
