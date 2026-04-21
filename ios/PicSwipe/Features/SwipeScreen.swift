//
//  SwipeScreen.swift
//  PicSwipe
//

import ComposableArchitecture
import SwiftUI

struct SwipeScreen: View {
    @Environment(\.colorScheme) private var colorScheme

    let store: StoreOf<SwipeFeature>

    private let photos = PhotoLibrary.samples
    private let albums = AlbumLibrary.all
    private let stackDepth = 4

    private var currentAlbum: Album {
        albums.first { $0.id == store.albumID } ?? albums[0]
    }
    private var total: Int { photos.count }
    private var done: Bool { store.activeIndex >= total }

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
                progressBar
                stackArea
                actionBar
            }

            if store.pickerOpen {
                AlbumPickerSheet(
                    albums: albums,
                    currentID: store.albumID,
                    onPick: { store.send(.albumPicked($0)) },
                    onClose: { store.send(.albumPickerDismissed) }
                )
                .transition(.opacity)
            }

            if store.menuOpen {
                MenuPopover(
                    onAbout: { store.send(.aboutMenuItemTapped) },
                    onClose: { store.send(.menuDismissed) }
                )
            }

            if store.aboutOpen {
                AboutSheet(onClose: { store.send(.aboutDismissed) })
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.22), value: store.pickerOpen)
        .animation(.easeInOut(duration: 0.22), value: store.aboutOpen)
        .animation(.easeOut(duration: 0.16), value: store.menuOpen)
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
                        Text(currentAlbum.name)
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
            HStack(spacing: 14) {
                counterPill(icon: "xmark", color: dropColor, value: store.deleted)
                counterPill(icon: "heart.fill", color: keepColor, value: store.kept)
                Button {
                    store.send(.menuButtonTapped)
                } label: {
                    Circle()
                        .fill(colorScheme == .dark ? Color.white.opacity(0.08) : Color.black.opacity(0.05))
                        .frame(width: 32, height: 32)
                        .overlay(
                            HStack(spacing: 3) {
                                ForEach(0..<3) { _ in Circle().fill(textPrim).frame(width: 4, height: 4) }
                            }
                        )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 12)
    }

    private func counterPill(icon: String, color: Color, value: Int) -> some View {
        HStack(spacing: 5) {
            Image(systemName: icon)
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(color)
            Text("\(value)")
                .font(.system(size: 15, weight: .semibold))
                .monospacedDigit()
                .foregroundStyle(textPrim)
        }
    }

    // MARK: - Progress

    private var progressBar: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(border)
                Capsule()
                    .fill(dropColor)
                    .frame(width: geo.size.width * CGFloat(store.activeIndex) / CGFloat(max(total, 1)))
                    .animation(.easeInOut(duration: 0.35), value: store.activeIndex)
            }
        }
        .frame(height: 3)
        .padding(.horizontal, 20)
        .padding(.bottom, 16)
    }

    // MARK: - Stack

    private var stackArea: some View {
        GeometryReader { geo in
            ZStack {
                if done {
                    doneView.frame(maxWidth: 420)
                } else {
                    ForEach(Array(photos.enumerated()), id: \.element.id) { idx, photo in
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

    private func cardView(photo: Photo, depth: Int, size: CGSize) -> some View {
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
            counterText: isTop ? "\(store.activeIndex + 1) / \(total)" : nil,
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
            .onEnded { _ in
                store.send(.cardDragEnded)
            }
    }

    private var doneView: some View {
        VStack(spacing: 8) {
            Text("All done!")
                .font(.system(size: 36, weight: .bold))
                .tracking(-1)
                .foregroundStyle(textPrim)
            Text("\(store.kept) kept · \(store.deleted) deleted")
                .font(.system(size: 14))
                .foregroundStyle(textSec)
            Button("Start over") {
                store.send(.resetButtonTapped)
            }
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, 22)
            .padding(.vertical, 12)
            .background(Capsule().fill(dropColor))
            .padding(.top, 12)
        }
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
        .opacity(done ? 0 : 1)
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

#Preview {
    SwipeScreen(
        store: Store(initialState: SwipeFeature.State()) {
            SwipeFeature()
        }
    )
}
