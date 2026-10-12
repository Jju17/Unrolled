//
//  GoalRing.swift
//  Unrolled
//

import SwiftUI

/// Today's reviewed count over the daily goal, as a ring.
struct GoalRing: View {
    let count: Int
    let goal: Int
    let streak: Int
    let color: Color
    let track: Color

    private var progress: Double {
        guard goal > 0 else { return 0 }
        return min(1, Double(count) / Double(goal))
    }

    var body: some View {
        ZStack {
            Circle()
                .stroke(track, lineWidth: 3.5)
            Circle()
                .trim(from: 0, to: progress)
                .stroke(color, style: StrokeStyle(lineWidth: 3.5, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.spring(response: 0.5, dampingFraction: 0.8), value: progress)
            if progress >= 1 {
                Image(systemName: "flame.fill")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(color)
            } else {
                Text("\(count)")
                    .font(.system(size: 10, weight: .bold))
                    .monospacedDigit()
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
            }
        }
        .frame(width: 32, height: 32)
        .frame(width: 44, height: 44)
        .contentShape(Circle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("\(count) of \(goal) photos today, streak \(streak) days"))
    }
}
