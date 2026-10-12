package dev.rahier.unrolled

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

data class ActivityStats(
    val bytesFreed: Long = 0L,
    val photosDeleted: Int = 0,
    val photosReviewed: Int = 0,
    val todayCount: Int = 0,
    val todayKey: String = "",
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastGoalDay: String? = null,
    val lastFreezeWeek: String? = null,
    val reachedMilestones: List<String> = emptyList()
)

enum class Milestone(val id: String, val threshold: Long) {
    Reviewed100("reviewed:100", 100),
    Reviewed500("reviewed:500", 500),
    Reviewed1000("reviewed:1000", 1_000),
    Reviewed5000("reviewed:5000", 5_000),
    Reviewed10000("reviewed:10000", 10_000),
    Reviewed50000("reviewed:50000", 50_000),
    Freed1Gb("freed:1gb", 1_000_000_000L),
    Freed5Gb("freed:5gb", 5_000_000_000L),
    Freed10Gb("freed:10gb", 10_000_000_000L),
    Freed50Gb("freed:50gb", 50_000_000_000L),
    Streak7("streak:7", 7),
    Streak30("streak:30", 30),
    Streak100("streak:100", 100);

    val kind: Kind
        get() = when {
            id.startsWith("reviewed") -> Kind.Reviewed
            id.startsWith("freed") -> Kind.Freed
            else -> Kind.Streak
        }

    enum class Kind { Reviewed, Freed, Streak }

    fun value(stats: ActivityStats): Long = when (kind) {
        Kind.Reviewed -> stats.photosReviewed.toLong()
        Kind.Freed -> stats.bytesFreed
        Kind.Streak -> stats.currentStreak.toLong()
    }
}

data class DecisionOutcome(
    val stats: ActivityStats,
    val goalJustReached: Boolean,
    val newMilestones: List<Milestone>
)

data class TrashOutcome(
    val stats: ActivityStats,
    val newMilestones: List<Milestone>
)

object ActivityTracker {

    fun dayKey(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        localDate(nowMillis, zone).toString()

    fun weekKey(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = localDate(nowMillis, zone)
        val week = date.get(WeekFields.ISO.weekOfWeekBasedYear())
        val year = date.get(WeekFields.ISO.weekBasedYear())
        return "%04d-W%02d".format(year, week)
    }

    /** Resets the daily counter when the calendar day changed and drops a
     *  streak that can no longer be continued. */
    fun rollover(stats: ActivityStats, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): ActivityStats {
        val today = dayKey(nowMillis, zone)
        val rolled = if (stats.todayKey == today) stats else stats.copy(todayCount = 0, todayKey = today)
        return if (rolled.currentStreak > 0 && !streakAlive(rolled, nowMillis, zone)) {
            rolled.copy(currentStreak = 0)
        } else {
            rolled
        }
    }

    private fun streakAlive(stats: ActivityStats, nowMillis: Long, zone: ZoneId): Boolean {
        val last = stats.lastGoalDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
        val gap = localDate(nowMillis, zone).toEpochDay() - last.toEpochDay()
        return gap <= 1L || (gap == 2L && stats.lastFreezeWeek != weekKey(nowMillis, zone))
    }

    fun recordDecisions(
        stats: ActivityStats,
        count: Int,
        nowMillis: Long,
        dailyGoal: Int,
        trackGoals: Boolean,
        zone: ZoneId = ZoneId.systemDefault()
    ): DecisionOutcome {
        val rolled = rollover(stats, nowMillis, zone)
        val before = rolled
        var next = rolled.copy(
            photosReviewed = rolled.photosReviewed + count,
            todayCount = rolled.todayCount + count
        )
        if (!trackGoals) return DecisionOutcome(next, goalJustReached = false, newMilestones = emptyList())

        val today = dayKey(nowMillis, zone)
        val reachedNow = before.todayCount < dailyGoal && next.todayCount >= dailyGoal
        if (reachedNow && next.lastGoalDay != today) {
            next = registerGoalDay(next, today, nowMillis, zone)
        }
        val milestones = newlyReached(before, next)
        next = next.copy(reachedMilestones = next.reachedMilestones + milestones.map { it.id })
        return DecisionOutcome(next, goalJustReached = reachedNow, newMilestones = milestones)
    }

    fun recordTrash(stats: ActivityStats, count: Int, bytes: Long, nowMillis: Long, trackGoals: Boolean, zone: ZoneId = ZoneId.systemDefault()): TrashOutcome {
        val rolled = rollover(stats, nowMillis, zone)
        var next = rolled.copy(
            photosDeleted = rolled.photosDeleted + count,
            bytesFreed = rolled.bytesFreed + bytes.coerceAtLeast(0L)
        )
        if (!trackGoals) return TrashOutcome(next, emptyList())
        val milestones = newlyReached(rolled, next)
        next = next.copy(reachedMilestones = next.reachedMilestones + milestones.map { it.id })
        return TrashOutcome(next, milestones)
    }

    fun newlyReached(before: ActivityStats, after: ActivityStats): List<Milestone> =
        Milestone.entries.filter { milestone ->
            milestone.id !in after.reachedMilestones &&
                milestone.value(before) < milestone.threshold &&
                milestone.value(after) >= milestone.threshold
        }

    private fun registerGoalDay(stats: ActivityStats, today: String, nowMillis: Long, zone: ZoneId): ActivityStats {
        val last = stats.lastGoalDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val todayDate = LocalDate.parse(today)
        val gap = last?.let { todayDate.toEpochDay() - it.toEpochDay() }
        val week = weekKey(nowMillis, zone)
        val (streak, freezeWeek) = when {
            gap == 1L -> stats.currentStreak + 1 to stats.lastFreezeWeek
            gap == 2L && stats.lastFreezeWeek != week -> stats.currentStreak + 1 to week
            else -> 1 to stats.lastFreezeWeek
        }
        return stats.copy(
            currentStreak = streak,
            longestStreak = maxOf(stats.longestStreak, streak),
            lastGoalDay = today,
            lastFreezeWeek = freezeWeek
        )
    }

    private fun localDate(nowMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
}
