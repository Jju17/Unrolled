//
//  ActivityTrackerTests.swift
//  UnrolledTests
//

import XCTest

final class ActivityTrackerTests: XCTestCase {

    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Brussels") ?? .current
        return calendar
    }

    private func date(_ year: Int, _ month: Int, _ day: Int, hour: Int = 12) -> Date {
        var parts = DateComponents()
        parts.year = year
        parts.month = month
        parts.day = day
        parts.hour = hour
        return calendar.date(from: parts) ?? Date()
    }

    func test_dayKey_andWeekKey_useTheCalendar() {
        XCTAssertEqual(ActivityTracker.dayKey(date(2026, 10, 12), calendar: calendar), "2026-10-12")
        XCTAssertEqual(ActivityTracker.weekKey(date(2026, 10, 12), calendar: calendar), "2026-W42")
        XCTAssertEqual(ActivityTracker.weekKey(date(2027, 1, 1), calendar: calendar), "2026-W53")
    }

    func test_rollover_resetsTodayCountWhenDayChanges() {
        let stats = ActivityStats(todayCount: 7, todayKey: "2026-10-11")
        let rolled = ActivityTracker.rollover(stats, now: date(2026, 10, 12), calendar: calendar)
        XCTAssertEqual(rolled.todayCount, 0)
        XCTAssertEqual(rolled.todayKey, "2026-10-12")
        let same = ActivityTracker.rollover(rolled, now: date(2026, 10, 12, hour: 23), calendar: calendar)
        XCTAssertEqual(same, rolled)
    }

    func test_rollover_dropsStreakOlderThanTheGrace() {
        let stats = ActivityStats(currentStreak: 4, lastGoalDay: "2026-10-08", longestStreak: 4)
        let rolled = ActivityTracker.rollover(stats, now: date(2026, 10, 12), calendar: calendar)
        XCTAssertEqual(rolled.currentStreak, 0)
        XCTAssertEqual(rolled.longestStreak, 4)
    }

    func test_rollover_keepsStreakWithinGraceOrFreeze() {
        let yesterday = ActivityStats(currentStreak: 4, lastGoalDay: "2026-10-11")
        XCTAssertEqual(ActivityTracker.rollover(yesterday, now: date(2026, 10, 12), calendar: calendar).currentStreak, 4)
        let twoDays = ActivityStats(currentStreak: 4, lastGoalDay: "2026-10-10")
        XCTAssertEqual(ActivityTracker.rollover(twoDays, now: date(2026, 10, 12), calendar: calendar).currentStreak, 4)
        let frozen = ActivityStats(currentStreak: 4, lastFreezeWeek: "2026-W42", lastGoalDay: "2026-10-10")
        XCTAssertEqual(ActivityTracker.rollover(frozen, now: date(2026, 10, 12), calendar: calendar).currentStreak, 0)
    }

    func test_recordDecision_countsAndReachesGoalOnce() {
        var stats = ActivityStats()
        let now = date(2026, 10, 12)
        var reachedCount = 0
        for _ in 0..<3 {
            let outcome = ActivityTracker.recordDecision(stats, now: now, dailyGoal: 2, trackGoals: true, calendar: calendar)
            stats = outcome.stats
            if outcome.goalJustReached { reachedCount += 1 }
        }
        XCTAssertEqual(reachedCount, 1)
        XCTAssertEqual(stats.photosReviewed, 3)
        XCTAssertEqual(stats.todayCount, 3)
        XCTAssertEqual(stats.currentStreak, 1)
        XCTAssertEqual(stats.longestStreak, 1)
        XCTAssertEqual(stats.lastGoalDay, "2026-10-12")
    }

    func test_recordDecision_withoutGoalTracking_onlyCounts() {
        let outcome = ActivityTracker.recordDecision(
            ActivityStats(), now: date(2026, 10, 12), dailyGoal: 1, trackGoals: false, calendar: calendar
        )
        XCTAssertFalse(outcome.goalJustReached)
        XCTAssertEqual(outcome.stats.currentStreak, 0)
        XCTAssertEqual(outcome.stats.photosReviewed, 1)
        XCTAssertEqual(outcome.newMilestones, [])
    }

    func test_streak_extendsOnConsecutiveDays() {
        let day1 = ActivityStats(currentStreak: 1, lastGoalDay: "2026-10-11", longestStreak: 1)
        let outcome = ActivityTracker.recordDecision(day1, now: date(2026, 10, 12), dailyGoal: 1, trackGoals: true, calendar: calendar)
        XCTAssertTrue(outcome.goalJustReached)
        XCTAssertEqual(outcome.stats.currentStreak, 2)
        XCTAssertEqual(outcome.stats.longestStreak, 2)
    }

    func test_streak_usesOneFreezePerWeek() {
        let stats = ActivityStats(currentStreak: 3, lastGoalDay: "2026-10-10", longestStreak: 3)
        let frozen = ActivityTracker.recordDecision(stats, now: date(2026, 10, 12), dailyGoal: 1, trackGoals: true, calendar: calendar).stats
        XCTAssertEqual(frozen.currentStreak, 4)
        XCTAssertEqual(frozen.lastFreezeWeek, "2026-W42")

        let again = ActivityStats(currentStreak: 4, lastFreezeWeek: "2026-W42", lastGoalDay: "2026-10-12", longestStreak: 4)
        let reset = ActivityTracker.recordDecision(again, now: date(2026, 10, 14), dailyGoal: 1, trackGoals: true, calendar: calendar).stats
        XCTAssertEqual(reset.currentStreak, 1)
        XCTAssertEqual(reset.longestStreak, 4)
    }

    func test_streak_resetsAfterLongerGap() {
        let stats = ActivityStats(currentStreak: 9, lastGoalDay: "2026-10-01", longestStreak: 9)
        let outcome = ActivityTracker.recordDecision(stats, now: date(2026, 10, 12), dailyGoal: 1, trackGoals: true, calendar: calendar)
        XCTAssertEqual(outcome.stats.currentStreak, 1)
        XCTAssertEqual(outcome.stats.longestStreak, 9)
    }

    func test_milestones_fireOnceAcrossReviewedFreedAndStreak() {
        let nearly = ActivityStats(photosReviewed: 99, reachedMilestones: [], todayCount: 0, todayKey: "2026-10-12")
        let outcome = ActivityTracker.recordDecision(nearly, now: date(2026, 10, 12), dailyGoal: 500, trackGoals: true, calendar: calendar)
        XCTAssertEqual(outcome.newMilestones, [.reviewed100])
        XCTAssertEqual(outcome.stats.reachedMilestones, ["reviewed100"])

        let repeated = ActivityTracker.recordDecision(outcome.stats, now: date(2026, 10, 12), dailyGoal: 500, trackGoals: true, calendar: calendar)
        XCTAssertEqual(repeated.newMilestones, [])

        let trash = ActivityTracker.recordTrash(repeated.stats, count: 3, bytes: Milestone.gigabyte, trackGoals: true)
        XCTAssertEqual(trash.newMilestones, [.freed1GB])
        XCTAssertEqual(trash.stats.photosDeleted, 3)
        XCTAssertEqual(trash.stats.bytesFreed, Milestone.gigabyte)

        let streak = ActivityStats(currentStreak: 6, lastGoalDay: "2026-10-11", reachedMilestones: ["reviewed100"], todayKey: "2026-10-12")
        let seventh = ActivityTracker.recordDecision(streak, now: date(2026, 10, 12), dailyGoal: 1, trackGoals: true, calendar: calendar)
        XCTAssertEqual(seventh.newMilestones, [.streak7])
    }

    func test_recordTrash_withoutGoalTracking_skipsMilestones() {
        let trash = ActivityTracker.recordTrash(ActivityStats(), count: 1, bytes: 5 * Milestone.gigabyte, trackGoals: false)
        XCTAssertEqual(trash.newMilestones, [])
        XCTAssertEqual(trash.stats.bytesFreed, 5 * Milestone.gigabyte)
    }

    func test_milestone_thresholdsAndKinds() {
        XCTAssertEqual(Milestone.reviewed50000.threshold, 50_000)
        XCTAssertEqual(Milestone.freed50GB.threshold, 50 * Milestone.gigabyte)
        XCTAssertEqual(Milestone.streak100.kind, .streak)
        XCTAssertEqual(Milestone.allCases.count, 13)
        XCTAssertTrue(Milestone.streak30.isReached(by: ActivityStats(currentStreak: 30)))
        XCTAssertFalse(Milestone.freed5GB.isReached(by: ActivityStats(bytesFreed: Milestone.gigabyte)))
    }
}
