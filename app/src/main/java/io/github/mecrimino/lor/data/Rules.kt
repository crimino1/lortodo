package io.github.mecrimino.lor.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** firedCount value for tasks whose due time had already passed when saved: no reminder, no nags. */
const val SILENCED = Int.MAX_VALUE / 2

/** Whether the next fire rings the alarm (first fire or end of a snooze) rather than a nag notification. */
val Task.isRingFire: Boolean get() = snoozedUntil != null || firedCount == 0

/** When this task's next reminder should fire, or null if none. Past times fire as soon as they are scheduled. */
fun nextTrigger(task: Task, nagIntervalMin: Int, nagMax: Int): Long? {
    if (task.isDone || task.reminder == ReminderType.NONE) return null
    val due = task.dueAtMillis() ?: return null
    task.snoozedUntil?.let { return it }
    if (task.firedCount == 0) return due
    if (task.firedCount > nagMax) return null
    return (task.lastFiredAt ?: due) + nagIntervalMin * 60_000L
}

/**
 * Next due date for a repeating task, strictly after [from] and not before [today], so finishing a daily
 * task a week late doesn't spawn a week of backlog. Null when it doesn't repeat.
 */
fun nextDueDate(repeat: RepeatType, repeatDays: Int, from: LocalDate, today: LocalDate): LocalDate? {
    val step: (LocalDate) -> LocalDate = when (repeat) {
        RepeatType.NONE -> return null
        RepeatType.DAILY -> { d -> d.plusDays(1) }
        RepeatType.WEEKLY -> { d -> d.plusWeeks(1) }
        // ponytail: plusMonths clips to month end and the clip sticks (31 Jan -> 28 Feb -> 28 Mar); keep an anchor day if anyone cares.
        RepeatType.MONTHLY -> { d -> d.plusMonths(1) }
        RepeatType.CUSTOM -> {
            if (repeatDays and 0x7F == 0) return null
            { d -> generateSequence(d.plusDays(1)) { it.plusDays(1) }.first { repeatDays and (1 shl (it.dayOfWeek.value - 1)) != 0 } }
        }
    }
    var next = step(from)
    while (next.isBefore(today)) next = step(next)
    return next
}

/** Consecutive days with a completion, ending today (or yesterday, so the streak survives until today ends). */
fun currentStreak(completedDays: Set<LocalDate>, today: LocalDate): Int {
    val start = when {
        today in completedDays -> today
        today.minusDays(1) in completedDays -> today.minusDays(1)
        else -> return 0
    }
    return generateSequence(start) { it.minusDays(1) }.takeWhile { it in completedDays }.count()
}

fun startOfWeek(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
