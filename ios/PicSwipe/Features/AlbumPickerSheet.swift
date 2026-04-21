//
//  AlbumPickerSheet.swift
//  PicSwipe
//

import SwiftUI

struct AlbumPickerSheet: View {
    @Environment(\.colorScheme) private var colorScheme

    let albums: [Album]
    let currentID: String
    let onPick: (String) -> Void
    let onClose: () -> Void

    private let tint = Color(red: 0, green: 0.478, blue: 1.0)  // #007aff

    var body: some View {
        ZStack {
            Color.black.opacity(0.35)
                .ignoresSafeArea()
                .onTapGesture { onClose() }

            VStack(spacing: 8) {
                Spacer()
                listCard
                cancelCard
            }
            .padding(.horizontal, 8)
            .padding(.bottom, 8)
            .transition(.move(edge: .bottom))
        }
    }

    private var surface: Color {
        colorScheme == .dark ? Color(red: 0.173, green: 0.173, blue: 0.180).opacity(0.92)
                             : Color(red: 0.980, green: 0.980, blue: 0.988).opacity(0.92)
    }
    private var textPrim: Color { colorScheme == .dark ? .white : .black }
    private var textSec: Color {
        colorScheme == .dark ? Color(red: 0.922, green: 0.922, blue: 0.961).opacity(0.6)
                             : Color(red: 0.235, green: 0.235, blue: 0.263).opacity(0.6)
    }
    private var separator: Color {
        colorScheme == .dark ? Color(red: 0.329, green: 0.329, blue: 0.345).opacity(0.5)
                             : Color(red: 0.235, green: 0.235, blue: 0.263).opacity(0.14)
    }

    private var listCard: some View {
        VStack(spacing: 0) {
            Text("Choose an album")
                .font(.system(size: 13))
                .foregroundStyle(textSec)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .overlay(
                    Rectangle().fill(separator).frame(height: 0.5),
                    alignment: .bottom
                )

            ForEach(Array(albums.enumerated()), id: \.element.id) { idx, album in
                Button {
                    onPick(album.id)
                    onClose()
                } label: {
                    HStack(spacing: 14) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 8)
                                .fill(colorScheme == .dark ? Color.white.opacity(0.08) : Color.black.opacity(0.05))
                                .frame(width: 30, height: 30)
                            Text(album.icon)
                                .font(.system(size: 15))
                                .foregroundStyle(textPrim)
                        }
                        VStack(alignment: .leading, spacing: 1) {
                            Text(album.name)
                                .font(.system(size: 16))
                                .tracking(-0.3)
                                .foregroundStyle(textPrim)
                            Text("\(album.count.formatted()) photos")
                                .font(.system(size: 13))
                                .foregroundStyle(textSec)
                        }
                        Spacer()
                        if album.id == currentID {
                            Image(systemName: "checkmark")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(tint)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                }
                .buttonStyle(.plain)

                if idx < albums.count - 1 {
                    Rectangle().fill(separator).frame(height: 0.5)
                }
            }
        }
        .background(
            surface.background(.ultraThinMaterial)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private var cancelCard: some View {
        Button(action: onClose) {
            Text("Cancel")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(tint)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
        }
        .buttonStyle(.plain)
        .background(
            surface.background(.ultraThinMaterial)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}
