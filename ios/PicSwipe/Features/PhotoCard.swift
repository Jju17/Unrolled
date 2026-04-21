//
//  PhotoCard.swift
//  PicSwipe
//

import SwiftUI

struct PhotoCard: View {
    let photo: Photo
    let counterText: String?
    let keepColor: Color
    let dropColor: Color
    let keepOpacity: Double
    let dropOpacity: Double
    let showLabels: Bool

    var body: some View {
        GeometryReader { geo in
            ZStack {
                AsyncImage(url: photo.url) { phase in
                    switch phase {
                    case .empty:
                        Color(white: 0.13)
                            .overlay(ProgressView().tint(.white))
                    case .success(let image):
                        image.resizable().scaledToFill()
                    case .failure:
                        Color(white: 0.13)
                            .overlay(
                                Image(systemName: "photo")
                                    .font(.system(size: 32))
                                    .foregroundStyle(.white.opacity(0.4))
                            )
                    @unknown default:
                        Color(white: 0.13)
                    }
                }
                .frame(width: geo.size.width, height: geo.size.height)
                .clipped()

                LinearGradient(
                    colors: [.clear, .black.opacity(0.55)],
                    startPoint: .top, endPoint: .bottom
                )
                .allowsHitTesting(false)

                // Counter (top right)
                if let counterText {
                    Text(counterText)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(.white)
                        .tracking(0.3)
                        .padding(.horizontal, 11)
                        .padding(.vertical, 5)
                        .background(Capsule().fill(Color.black.opacity(0.42)))
                        .padding(18)
                        .frame(width: geo.size.width, height: geo.size.height,
                               alignment: .topTrailing)
                }

                // Date + location (bottom left)
                VStack(alignment: .leading, spacing: 2) {
                    Text(photo.date)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(.white.opacity(0.85))
                        .tracking(0.2)
                    Text(photo.location)
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .padding(18)
                .frame(width: geo.size.width, height: geo.size.height,
                       alignment: .bottomLeading)

                // KEEP/DELETE stamps
                if showLabels {
                    stamp(text: "KEEP", color: keepColor, opacity: keepOpacity, rotation: -18)
                        .padding(22)
                        .frame(width: geo.size.width, height: geo.size.height,
                               alignment: .topLeading)
                    stamp(text: "DELETE", color: dropColor, opacity: dropOpacity, rotation: 18)
                        .padding(22)
                        .frame(width: geo.size.width, height: geo.size.height,
                               alignment: .topTrailing)
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
            .background(
                RoundedRectangle(cornerRadius: 24).fill(Color(white: 0.13))
            )
            .clipShape(RoundedRectangle(cornerRadius: 24))
            .shadow(color: .black.opacity(0.18), radius: 14, x: 0, y: 14)
            .shadow(color: .black.opacity(0.10), radius: 4, x: 0, y: 4)
        }
    }

    private func stamp(text: String, color: Color, opacity: Double, rotation: Double) -> some View {
        Text(text)
            .font(.system(size: 28, weight: .heavy))
            .tracking(2)
            .foregroundStyle(color)
            .padding(.horizontal, 18)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(Color.white.opacity(0.92))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(color, lineWidth: 3))
            )
            .rotationEffect(.degrees(rotation))
            .scaleEffect(0.6 + opacity * 0.4)
            .opacity(opacity)
            .allowsHitTesting(false)
    }
}
