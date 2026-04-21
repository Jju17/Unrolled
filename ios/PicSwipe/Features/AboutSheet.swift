//
//  AboutSheet.swift
//  PicSwipe
//

import SwiftUI

struct AboutSheet: View {
    @Environment(\.colorScheme) private var colorScheme

    let onClose: () -> Void

    private let tint = Color(red: 0, green: 0.478, blue: 1.0)

    var body: some View {
        ZStack {
            Color.black.opacity(0.35)
                .ignoresSafeArea()
                .onTapGesture { onClose() }

            VStack(spacing: 0) {
                VStack(spacing: 0) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 16)
                            .fill(
                                LinearGradient(
                                    colors: [
                                        Color(red: 0.937, green: 0.243, blue: 0.357),
                                        Color(red: 0.976, green: 0.451, blue: 0.086)
                                    ],
                                    startPoint: .topLeading, endPoint: .bottomTrailing
                                )
                            )
                            .frame(width: 64, height: 64)
                            .shadow(color: Color(red: 0.937, green: 0.243, blue: 0.357).opacity(0.4),
                                    radius: 10, x: 0, y: 8)
                        Image(systemName: "heart.fill")
                            .font(.system(size: 32))
                            .foregroundStyle(.white)
                    }
                    .padding(.top, 28)

                    Text("PicSwipe")
                        .font(.system(size: 20, weight: .bold))
                        .tracking(-0.4)
                        .foregroundStyle(textPrim)
                        .padding(.top, 14)

                    Text("Swipe your camera roll clean.")
                        .font(.system(size: 13))
                        .foregroundStyle(textSec)
                        .padding(.top, 4)

                    infoRow(label: "Version", value: "1.0.2")
                        .padding(.top, 20)
                    infoRow(label: "Build", value: "2026.04.128")
                        .padding(.top, 6)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 20)

                Divider()
                    .background(separator)

                Button(action: onClose) {
                    Text("Close")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(tint)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                }
                .buttonStyle(.plain)
            }
            .frame(width: 280)
            .background(
                surface.background(.ultraThinMaterial)
            )
            .clipShape(RoundedRectangle(cornerRadius: 20))
        }
    }

    private func infoRow(label: String, value: String) -> some View {
        HStack {
            Text(label).foregroundStyle(textSec)
            Spacer()
            Text(value)
                .foregroundStyle(textPrim)
                .monospacedDigit()
                .fontWeight(.medium)
        }
        .font(.system(size: 14))
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(colorScheme == .dark ? Color.white.opacity(0.05) : Color.black.opacity(0.04))
        )
    }

    private var surface: Color {
        colorScheme == .dark ? Color(red: 0.110, green: 0.110, blue: 0.118).opacity(0.95)
                             : Color(red: 0.980, green: 0.980, blue: 0.988).opacity(0.96)
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
}
