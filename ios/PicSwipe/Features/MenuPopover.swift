//
//  MenuPopover.swift
//  PicSwipe
//

import SwiftUI

struct MenuPopover: View {
    @Environment(\.colorScheme) private var colorScheme

    let onAbout: () -> Void
    let onClose: () -> Void

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture { onClose() }

            VStack(spacing: 0) {
                Button(action: onAbout) {
                    HStack {
                        Text("About PicSwipe")
                            .font(.system(size: 15))
                            .tracking(-0.2)
                            .foregroundStyle(textPrim)
                        Spacer()
                        Image(systemName: "info.circle")
                            .font(.system(size: 16))
                            .foregroundStyle(textPrim.opacity(0.75))
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                }
                .buttonStyle(.plain)
            }
            .frame(width: 220)
            .background(
                surface.background(.ultraThinMaterial)
            )
            .clipShape(RoundedRectangle(cornerRadius: 13))
            .shadow(color: .black.opacity(0.25), radius: 22, x: 0, y: 18)
            .padding(.top, 110)
            .padding(.trailing, 16)
        }
    }

    private var surface: Color {
        colorScheme == .dark ? Color(red: 0.173, green: 0.173, blue: 0.180).opacity(0.95)
                             : Color(red: 0.980, green: 0.980, blue: 0.988).opacity(0.95)
    }
    private var textPrim: Color { colorScheme == .dark ? .white : .black }
}
