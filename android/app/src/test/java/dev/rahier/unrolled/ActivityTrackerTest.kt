package dev.rahier.unrolled

import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActivityTrackerTest {

    private val zone: ZoneId = ZoneOffset.UTC

    private fun millis(date: String, hour: Int = 12): Long =
        LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun reachGoal(stats: ActivityStats, date: String, goal: Int = 2): DecisionOutcome =
        ActivityTracker.recordDecisions(stats, count = goal, nowMillis = millis(date), dailyGoal = goal, trackGoals = true, zone = zone)

    @Test
    fun `day and week keys follow the given zone`() {
        assertEquals("2026-10-12", ActivityTracker.dayKey(millis("2026-10-12"), zone))
        assertEquals("2026-W42", ActivityTracker.weekKey(millis("2026-10-12"), zone))
        assertEquals("2025-W01", ActivityTracker.weekKey(millis("2024-12-30"), zone))
    }

    @Test
    fun `rollover resets the daily counter on a new day only`() {
        val stats = ActivityStats(todayCount = 7, todayKey = "2026-10-11")
        assertEquals(stats, ActivityTracker.rollover(stats, millis("2026-10-11"), zone))
        val rolled = ActivityTracker.rollover(stats, millis("2026-10-12"), zone)
        assertEquals(0, rolled.todayCount)
        assertEquals("2026-10-12", rolled.todayKey)
    }

    @Test
    fun `rollover keeps a streak inside the grace window and drops it after`() {
        val stats = ActivityStats(currentStreak = 4, longestStreak = 4, lastGoalDay = "2026-10-10")
        assertEquals(4, ActivityTracker.rollover(stats, millis("2026-10-11"), zone).currentStreak)
        assertEquals(4, ActivityTracker.rollover(stats, millis("2026-10-12"), zone).currentStreak)
        val frozen = stats.copy(lastFreezeWeek = "2026-W42")
        assertEquals(0, ActivityTracker.rollover(frozen, millis("2026-10-12"), zone).currentStreak)
        val dropped = ActivityTracker.rollover(stats, millis("2026-10-13"), zone)
        assertEquals(0, dropped.currentStreak)
        assertEquals(4, dropped.longestStreak)
    }

    @Test
    fun `streak milestones follow the current streak`() {
        var stats = ActivityStats(currentStreak = 6, longestStreak = 6, lastGoalDay = "2026-10-11")
        val reached = reachGoal(stats, "2026-10-12")
        assertEquals(7, reached.stats.currentStreak)
        assertTrue(Milestone.Streak7 in reached.newMilestones)
        stats = ActivityStats(currentStreak = 0, longestStreak = 30, lastGoalDay = "2026-09-01", reachedMilestones = listOf(Milestone.Streak7.id))
        val restarted = reachGoal(stats, "2026-10-12")
        assertEquals(1, restarted.stats.currentStreak)
        assertTrue(restarted.newMilestones.isEmpty())
    }

    @Test
    fun `decisions count towards reviewed and today`() {
        val out = ActivityTracker.recordDecisions(ActivityStats(), count = 3, nowMillis = millis("2026-10-12"), dailyGoal = 50, trackGoals = true, zone = zone)
        assertEquals(3, out.stats.photosReviewed)
        assertEquals(3, out.stats.todayCount)
        assertEquals("2026-10-12", out.stats.todayKey)
        assertFalse(out.goalJustReached)
        assertTrue(out.newMilestones.isEmpty())
    }

    @Test
    fun `reaching the goal starts a streak and fires once per day`() {
        val first = reachGoal(ActivityStats(), "2026-10-12")
        assertTrue(first.goalJustReached)
        assertEquals(1, first.stats.currentStreak)
        assertEquals(1, first.stats.longestStreak)
        assertEquals("2026-10-12", first.stats.lastGoalDay)

        val again = reachGoal(first.stats, "2026-10-12")
        assertFalse(again.goalJustReached)
        assertEquals(1, again.stats.currentStreak)
    }

    @Test
    fun `consecutive days extend the streak`() {
        var stats = reachGoal(ActivityStats(), "2026-10-12").stats
        stats = reachGoal(stats, "2026-10-13").stats
        stats = reachGoal(stats, "2026-10-14").stats
        assertEquals(3, stats.currentStreak)
        assertEquals(3, stats.longestStreak)
    }

    @Test
    fun `one missed day is covered by the weekly freeze, a second one is not`() {
        var stats = reachGoal(ActivityStats(), "2026-10-12").stats
        stats = reachGoal(stats, "2026-10-14").stats
        assertEquals(2, stats.currentStreak)
        assertEquals("2026-W42", stats.lastFreezeWeek)

        stats = reachGoal(stats, "2026-10-16").stats
        assertEquals(1, stats.currentStreak)
        assertEquals(2, stats.longestStreak)
    }

    @Test
    fun `the freeze is available again the next week`() {
        var stats = reachGoal(ActivityStats(), "2026-10-12").stats
        stats = reachGoal(stats, "2026-10-14").stats
        stats = reachGoal(stats, "2026-10-15").stats
        stats = reachGoal(stats, "2026-10-16").stats
        stats = reachGoal(stats, "2026-10-17").stats
        stats = reachGoal(stats, "2026-10-18").stats
        stats = reachGoal(stats, "2026-10-20").stats
        assertEquals(7, stats.currentStreak)
        assertEquals("2026-W43", stats.lastFreezeWeek)
    }

    @Test
    fun `three missed days reset the streak to one`() {
        var stats = reachGoal(ActivityStats(), "2026-10-12").stats
        stats = reachGoal(stats, "2026-10-16").stats
        assertEquals(1, stats.currentStreak)
    }

    @Test
    fun `trackGoals false still counts but never touches streaks or milestones`() {
        val out = ActivityTracker.recordDecisions(
            ActivityStats(photosReviewed = 99), count = 5, nowMillis = millis("2026-10-12"), dailyGoal = 1, trackGoals = false, zone = zone
        )
        assertEquals(104, out.stats.photosReviewed)
        assertEquals(0, out.stats.currentStreak)
        assertFalse(out.goalJustReached)
        assertTrue(out.newMilestones.isEmpty())
        assertTrue(out.stats.reachedMilestones.isEmpty())
    }

    @Test
    fun `reviewed milestone fires when the threshold is crossed and is remembered`() {
        val out = ActivityTracker.recordDecisions(
            ActivityStats(photosReviewed = 99), count = 1, nowMillis = millis("2026-10-12"), dailyGoal = 50, trackGoals = true, zone = zone
        )
        assertEquals(listOf(Milestone.Reviewed100), out.newMilestones)
        assertEquals(listOf("reviewed:100"), out.stats.reachedMilestones)

        val again = ActivityTracker.recordDecisions(out.stats, count = 1, nowMillis = millis("2026-10-12"), dailyGoal = 50, trackGoals = true, zone = zone)
        assertTrue(again.newMilestones.isEmpty())
    }

    @Test
    fun `streak milestone fires on the seventh day`() {
        var stats = ActivityStats()
        var last: DecisionOutcome? = null
        for (day in 12..18) {
            last = reachGoal(stats, "2026-10-$day")
            stats = last.stats
        }
        assertEquals(listOf(Milestone.Streak7), last?.newMilestones)
    }

    @Test
    fun `trash records bytes and count and fires freed milestones`() {
        val out = ActivityTracker.recordTrash(ActivityStats(), count = 3, bytes = 1_200_000_000L, nowMillis = millis("2026-10-12"), trackGoals = true, zone = zone)
        assertEquals(3, out.stats.photosDeleted)
        assertEquals(1_200_000_000L, out.stats.bytesFreed)
        assertEquals(listOf(Milestone.Freed1Gb), out.newMilestones)
    }

    @Test
    fun `trash with gamification off accumulates silently`() {
        val out = ActivityTracker.recordTrash(ActivityStats(), count = 3, bytes = 2_000_000_000L, nowMillis = millis("2026-10-12"), trackGoals = false, zone = zone)
        assertEquals(2_000_000_000L, out.stats.bytesFreed)
        assertTrue(out.newMilestones.isEmpty())
    }

    @Test
    fun `negative byte sums are clamped`() {
        val out = ActivityTracker.recordTrash(ActivityStats(), count = 1, bytes = -5L, nowMillis = millis("2026-10-12"), trackGoals = true, zone = zone)
        assertEquals(0L, out.stats.bytesFreed)
    }

    @Test
    fun `newlyReached skips milestones already recorded`() {
        val before = ActivityStats(photosReviewed = 0)
        val after = ActivityStats(photosReviewed = 600, reachedMilestones = listOf("reviewed:100"))
        assertEquals(listOf(Milestone.Reviewed500), ActivityTracker.newlyReached(before, after))
    }
}
