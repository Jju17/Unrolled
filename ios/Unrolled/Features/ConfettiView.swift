//
//  ConfettiView.swift
//  Unrolled
//

import SwiftUI

/// Short burst of falling confetti drawn on a `Canvas`. Callers gate it
/// on the reduce-motion setting.
struct ConfettiView: View {
    var duration: TimeInterval = 2

    private struct Piece {
        let x: Double
        let delay: Double
        let speed: Double
        let drift: Double
        let size: Double
        let hue: Double
        let spin: Double
    }

    private let pieces: [Piece] = (0..<70).map { index in
        var generator = SeededGenerator(seed: UInt64(index + 1))
        return Piece(
            x: Double.random(in: 0...1, using: &generator),
            delay: Double.random(in: 0...0.6, using: &generator),
            speed: Double.random(in: 0.5...1.0, using: &generator),
            drift: Double.random(in: -0.15...0.15, using: &generator),
            size: Double.random(in: 6...11, using: &generator),
            hue: Double.random(in: 0...1, using: &generator),
            spin: Double.random(in: 2...6, using: &generator)
        )
    }

    private let start = Date()

    var body: some View {
        TimelineView(.animation) { context in
            let elapsed = context.date.timeIntervalSince(start)
            Canvas { canvas, size in
                guard elapsed < duration + 1 else { return }
                for piece in pieces {
                    let t = elapsed - piece.delay
                    guard t > 0 else { continue }
                    let progress = t / duration * piece.speed
                    let y = progress * (size.height + 40) - 20
                    let x = (piece.x + piece.drift * sin(t * 3)) * size.width
                    let fade = max(0, 1 - progress)
                    let rect = CGRect(x: x, y: y, width: piece.size, height: piece.size * 0.6)
                    var transform = CGAffineTransform(translationX: rect.midX, y: rect.midY)
                    transform = transform.rotated(by: t * piece.spin)
                    transform = transform.translatedBy(x: -rect.midX, y: -rect.midY)
                    let path = Path(roundedRect: rect, cornerRadius: 1).applying(transform)
                    canvas.fill(path, with: .color(Color(hue: piece.hue, saturation: 0.75, brightness: 0.95).opacity(fade)))
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

private struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64

    init(seed: UInt64) {
        state = seed &* 0x9E37_79B9_7F4A_7C15
    }

    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }
}
