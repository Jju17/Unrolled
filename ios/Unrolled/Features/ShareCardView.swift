//
//  ShareCardView.swift
//  Unrolled
//

import SwiftUI

/// 1080x1350 card rendered off-screen for the share sheet. Text only,
/// never a photo.
struct ShareCardView: View {
    let headline: String
    let detail: String
    let footer: String

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.11, green: 0.11, blue: 0.16), Color(red: 0.24, green: 0.16, blue: 0.33)],
                startPoint: .topLeading, endPoint: .bottomTrailing
            )
            VStack(alignment: .leading, spacing: 40) {
                Text(verbatim: "Unrolled")
                    .font(.system(size: 54, weight: .bold))
                    .foregroundStyle(.white.opacity(0.7))
                Spacer()
                Text(headline)
                    .font(.system(size: 132, weight: .heavy))
                    .foregroundStyle(.white)
                    .minimumScaleFactor(0.5)
                    .lineLimit(2)
                Text(detail)
                    .font(.system(size: 60, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.9))
                Spacer()
                Text(footer)
                    .font(.system(size: 40, weight: .medium))
                    .foregroundStyle(.white.opacity(0.6))
            }
            .padding(90)
        }
        .frame(width: 1080, height: 1350)
    }

    @MainActor
    static func render(headline: String, detail: String, footer: String) -> Image? {
        let renderer = ImageRenderer(content: ShareCardView(headline: headline, detail: detail, footer: footer))
        renderer.scale = 1
        guard let image = renderer.uiImage else { return nil }
        return Image(uiImage: image)
    }
}

struct ShareCardButton: View {
    let headline: String
    let detail: String
    let footer: String

    var body: some View {
        if let image = ShareCardView.render(headline: headline, detail: detail, footer: footer) {
            ShareLink(
                item: image,
                preview: SharePreview(headline, image: image)
            ) {
                Label("Share", systemImage: "square.and.arrow.up")
            }
        }
    }
}
