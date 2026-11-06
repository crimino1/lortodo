package io.github.mecrimino.lor

import io.github.mecrimino.lor.data.ReminderType
import io.github.mecrimino.lor.data.RepeatType
import io.github.mecrimino.lor.data.Task
import io.github.mecrimino.lor.data.TaskStatus
import io.github.mecrimino.lor.data.currentStreak
import io.github.mecrimino.lor.data.isRingFire
import io.github.mecrimino.lor.data.nextDueDate
import io.github.mecrimino.lor.data.nextTrigger
import io.github.mecrimino.lor.data.startOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    private val day = LocalDate.of(2026, 11, 6) // a Friday
    private val task = Task(title = "t", dueEpochDay = day.toEpochDay(), dueMinute = 600, reminder = ReminderType.NOTIFICATION)
    private val due = task.dueAtMillis()!!

    @Test fun firstTriggerIsDueTime() = assertEquals(due, nextTrigger(task, 30, 5))

    @Test fun nagsFollowLastFire() {
        val fired = task.copy(firedCount = 1, lastFiredAt = due + 1000)
        assertEquals(due + 1000 + 30 * 60_000L, nextTrigger(fired, 30, 5))
    }

    @Test fun nagsStopAtMax() {
        assertEquals(due + 30 * 60_000L, nextTrigger(task.copy(firedCount = 5, lastFiredAt = due), 30, 5))
        assertNull(nextTrigger(task.copy(firedCount = 6, lastFiredAt = due), 30, 5))
    }

    @Test fun noNagsWhenMaxIsZero() = assertNull(nextTrigger(task.copy(firedCount = 1, lastFiredAt = due), 30, 0))

    @Test fun snoozeWinsOverNags() = assertEquals(123L, nextTrigger(task.copy(firedCount = 3, lastFiredAt = due, snoozedUntil = 123L), 30, 5))

    @Test fun nothingWhenDoneOrNoReminderOrNoTime() {
        assertNull(nextTrigger(task.copy(status = TaskStatus.DONE), 30, 5))
        assertNull(nextTrigger(task.copy(reminder = ReminderType.NONE), 30, 5))
        assertNull(nextTrigger(task.copy(dueMinute = null), 30, 5))
    }

    @Test fun ringsOnFirstFireAndAfterSnoozeOnly() {
        assertTrue(task.isRingFire)
        assertTrue(task.copy(firedCount = 2, snoozedUntil = 1L).isRingFire)
        assertFalse(task.copy(firedCount = 1).isRingFire)
    }

    @Test fun repeats() {
        assertNull(nextDueDate(RepeatType.NONE, 0, day, day))
        assertEquals(day.plusDays(1), nextDueDate(RepeatType.DAILY, 0, day, day))
        assertEquals(day.plusWeeks(1), nextDueDate(RepeatType.WEEKLY, 0, day, day))
        assertEquals(LocalDate.of(2026, 12, 6), nextDueDate(RepeatType.MONTHLY, 0, day, day))
    }

    @Test fun customDaysFindNextSetDay() {
        val monWed = 0b0000101
        assertEquals(LocalDate.of(2026, 11, 9), nextDueDate(RepeatType.CUSTOM, monWed, day, day)) // Fri -> Mon
        assertEquals(LocalDate.of(2026, 11, 11), nextDueDate(RepeatType.CUSTOM, monWed, LocalDate.of(2026, 11, 9), day)) // Mon -> Wed
    }

    @Test fun customWithNoDaysDoesNotHang() = assertNull(nextDueDate(RepeatType.CUSTOM, 0, day, day))

    @Test fun lateCompletionSkipsBacklog() {
        assertEquals(day, nextDueDate(RepeatType.DAILY, 0, day.minusDays(7), day))
    }

    @Test fun streak() {
        val d = setOf(day, day.minusDays(1), day.minusDays(2), day.minusDays(5))
        assertEquals(3, currentStreak(d, day))
        assertEquals(3, currentStreak(d, day.plusDays(1))) // today not done yet: streak still alive
        assertEquals(0, currentStreak(d, day.plusDays(2)))
        assertEquals(0, currentStreak(emptySet(), day))
    }

    @Test fun weekStartsMonday() {
        assertEquals(LocalDate.of(2026, 11, 2), startOfWeek(day))
        assertEquals(LocalDate.of(2026, 11, 2), startOfWeek(LocalDate.of(2026, 11, 2)))
    }
}
