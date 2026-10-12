//
//  ActivityStats.swift
//  Unrolled
//

import Foundation

/// Lifetime counters and streak bookkeeping, persisted by `StatsClient`.
struct ActivityStats: Equatable, Codable {
    var bytesFreed: Int64 = 0
    var currentStreak: Int = 0
    var lastFreezeWeek: String?
    var lastGoalDay: String?
    var longestStreak: Int = 0
    var photosDeleted: Int = 0
    var photosReviewed: Int = 0
    var reachedMilestones: [String] = []
    var todayCount: Int = 0
    var todayKey: String = ""

    static let empty = ActivityStats()
}

enum Milestone: String, CaseIterable, Equatable, Codable {
    case reviewed100, reviewed500, reviewed1000, reviewed5000, reviewed10000, reviewed50000
    case freed1GB, freed5GB, freed10GB, freed50GB
    case streak7, streak30, streak100

    enum Kind: Equatable { case reviewed, freed, streak }

    static let gigabyte: Int64 = 1_000_000_000

    var kind: Kind {
        switch self {
        case .reviewed100, .reviewed500, .reviewed1000, .reviewed5000, .reviewed10000, .reviewed50000:
            .reviewed
        case .freed1GB, .freed5GB, .freed10GB, .freed50GB:
            .freed
        case .streak7, .streak30, .streak100:
            .streak
        }
    }

    var threshold: Int64 {
        switch self {
        case .reviewed100: 100
        case .reviewed500: 500
        case .reviewed1000: 1_000
        case .reviewed5000: 5_000
        case .reviewed10000: 10_000
        case .reviewed50000: 50_000
        case .freed1GB: Self.gigabyte
        case .freed5GB: 5 * Self.gigabyte
        case .freed10GB: 10 * Self.gigabyte
        case .freed50GB: 50 * Self.gigabyte
        case .streak7: 7
        case .streak30: 30
        case .streak100: 100
        }
    }

    func isReached(by stats: ActivityStats) -> Bool {
        switch kind {
        case .reviewed: Int64(stats.photosReviewed) >= threshold
        case .freed: stats.bytesFreed >= threshold
        case .streak: Int64(stats.currentStreak) >= threshold
        }
    }
}

enum ActivityTracker {
    struct DecisionOutcome: Equatable {
        var stats: ActivityStats
        var goalJustReached: Bool
        var newMilestones: [Milestone]
    }

    struct TrashOutcome: Equatable {
        var stats: ActivityStats
        var newMilestones: [Milestone]
    }

    static func dayKey(_ date: Date, calendar: Calendar = .current) -> String {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }

    static func weekKey(_ date: Date, calendar: Calendar = .current) -> String {
        var iso = Calendar(identifier: .iso8601)
        iso.timeZone = calendar.timeZone
        let parts = iso.dateComponents([.yearForWeekOfYear, .weekOfYear], from: date)
        return String(format: "%04d-W%02d", parts.yearForWeekOfYear ?? 0, parts.weekOfYear ?? 0)
    }

    /// Resets the daily counter when the day changed and drops a streak
    /// that can no longer be continued.
    static func rollover(
        _ stats: ActivityStats, now: Date, calendar: Calendar = .current
    ) -> ActivityStats {
        var updated = stats
        let today = dayKey(now, calendar: calendar)
        if updated.todayKey != today {
            updated.todayKey = today
            updated.todayCount = 0
        }
        if updated.currentStreak > 0, !streakAlive(updated, now: now, calendar: calendar) {
            updated.currentStreak = 0
        }
        return updated
    }

    static func recordDecision(
        _ stats: ActivityStats,
        now: Date,
        dailyGoal: Int,
        trackGoals: Bool,
        calendar: Calendar = .current
    ) -> DecisionOutcome {
        var updated = rollover(stats, now: now, calendar: calendar)
        let wasBelowGoal = updated.todayCount < dailyGoal
        updated.photosReviewed += 1
        updated.todayCount += 1
        guard trackGoals else {
            return DecisionOutcome(stats: updated, goalJustReached: false, newMilestones: [])
        }
        let today = dayKey(now, calendar: calendar)
        var goalJustReached = false
        if wasBelowGoal, updated.todayCount >= dailyGoal, updated.lastGoalDay != today {
            goalJustReached = true
            extendStreak(&updated, now: now, calendar: calendar)
        }
        let milestones = claimMilestones(&updated)
        return DecisionOutcome(stats: updated, goalJustReached: goalJustReached, newMilestones: milestones)
    }

    static func recordTrash(
        _ stats: ActivityStats, count: Int, bytes: Int64, trackGoals: Bool
    ) -> TrashOutcome {
        var updated = stats
        updated.photosDeleted += count
        updated.bytesFreed += bytes
        let milestones = trackGoals ? claimMilestones(&updated) : []
        return TrashOutcome(stats: updated, newMilestones: milestones)
    }

    private static func extendStreak(_ stats: inout ActivityStats, now: Date, calendar: Calendar) {
        let today = dayKey(now, calendar: calendar)
        if let last = stats.lastGoalDay, last == dayKey(daysAgo(1, now, calendar), calendar: calendar) {
            stats.currentStreak += 1
        } else if let last = stats.lastGoalDay,
                  last == dayKey(daysAgo(2, now, calendar), calendar: calendar),
                  stats.lastFreezeWeek != weekKey(now, calendar: calendar) {
            stats.lastFreezeWeek = weekKey(now, calendar: calendar)
            stats.currentStreak += 1
        } else {
            stats.currentStreak = 1
        }
        stats.lastGoalDay = today
        stats.longestStreak = max(stats.longestStreak, stats.currentStreak)
    }

    private static func streakAlive(_ stats: ActivityStats, now: Date, calendar: Calendar) -> Bool {
        guard let last = stats.lastGoalDay else { return false }
        if last == dayKey(now, calendar: calendar) { return true }
        if last == dayKey(daysAgo(1, now, calendar), calendar: calendar) { return true }
        if last == dayKey(daysAgo(2, now, calendar), calendar: calendar),
           stats.lastFreezeWeek != weekKey(now, calendar: calendar) {
            return true
        }
        return false
    }

    private static func claimMilestones(_ stats: inout ActivityStats) -> [Milestone] {
        let reached = Milestone.allCases.filter {
            !stats.reachedMilestones.contains($0.rawValue) && $0.isReached(by: stats)
        }
        stats.reachedMilestones.append(contentsOf: reached.map(\.rawValue))
        return reached
    }

    private static func daysAgo(_ days: Int, _ now: Date, _ calendar: Calendar) -> Date {
        calendar.date(byAdding: .day, value: -days, to: now) ?? now
    }
}
