package io.github.mecrimino.lor

import android.app.AlarmManager
import android.content.Context
import io.github.mecrimino.lor.data.Category
import io.github.mecrimino.lor.data.ReminderType
import io.github.mecrimino.lor.data.RepeatType
import io.github.mecrimino.lor.data.SILENCED
import io.github.mecrimino.lor.data.Subtask
import io.github.mecrimino.lor.data.Task
import io.github.mecrimino.lor.data.TaskStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class TaskRepositoryTest {
    private lateinit var env: TestEnv
    private val tomorrow get() = LocalDate.now().plusDays(1).toEpochDay()
    private val alarmManager get() = env.context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private fun alarms() = shadowOf(alarmManager).scheduledAlarms

    @Before fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        env = TestEnv()
    }

    private fun reminderTask(reminder: ReminderType = ReminderType.NOTIFICATION) =
        Task(title = "Call home", dueEpochDay = tomorrow, dueMinute = 9 * 60, reminder = reminder)

    @Test fun savingWithReminderArmsAlarmAtDueTime() = runTest {
        val id = env.repo.save(reminderTask())
        assertEquals(env.repo.task(id)!!.dueAtMillis(), alarms().single().triggerAtMs)
    }

    @Test fun alarmTypeUsesAlarmClockSoItFiresInDoze() = runTest {
        env.repo.save(reminderTask(ReminderType.ALARM))
        assertNotNull(alarms().single().alarmClockInfo)
    }

    @Test fun notificationTypeUsesExactWhileIdle() = runTest {
        env.repo.save(reminderTask())
        assertNull(alarms().single().alarmClockInfo)
        assertTrue(alarms().single().allowWhileIdle)
    }

    @Test fun deniedExactPermissionFallsBackToInexactInsteadOfCrashing() = runTest {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        env.repo.save(reminderTask(ReminderType.ALARM))
        assertNull(alarms().single().alarmClockInfo)
    }

    @Test fun completingCancelsAlarm() = runTest {
        val id = env.repo.save(reminderTask())
        env.repo.complete(id)
        assertTrue(alarms().isEmpty())
        val done = env.repo.task(id)!!
        assertEquals(TaskStatus.DONE, done.status)
        assertNotNull(done.completedAt)
    }

    @Test fun completingRepeatSpawnsNextWithFreshSubtasks() = runTest {
        val id = env.repo.save(
            reminderTask().copy(repeat = RepeatType.DAILY),
            listOf(Subtask(title = "a", done = true), Subtask(title = "b")),
        )
        env.repo.complete(id)
        val spawnedId = env.repo.task(id)!!.nextOccurrenceId!!
        val spawned = env.repo.task(spawnedId)!!
        assertEquals(tomorrow + 1, spawned.dueEpochDay)
        assertEquals(TaskStatus.PENDING, spawned.status)
        assertEquals(listOf(false, false), env.repo.subtasks(spawnedId).map { it.done })
        assertEquals(spawned.dueAtMillis(), alarms().single().triggerAtMs)
    }

    @Test fun uncompletingRemovesSpawnedRepeat() = runTest {
        val id = env.repo.save(reminderTask().copy(repeat = RepeatType.DAILY))
        env.repo.complete(id)
        val spawnedId = env.repo.task(id)!!.nextOccurrenceId!!
        env.repo.uncomplete(id)
        assertNull(env.repo.task(spawnedId))
        val reopened = env.repo.task(id)!!
        assertEquals(TaskStatus.PENDING, reopened.status)
        assertEquals(reopened.dueAtMillis(), alarms().single().triggerAtMs)
    }

    @Test fun firingNagsUntilMaxThenStops() = runTest {
        env.settings.setNagInterval(30)
        env.settings.setNagMax(2)
        val id = env.repo.save(reminderTask())
        val first = env.repo.onReminderFired(id)!!
        assertFalse(first.ring)
        assertEquals(first.task.lastFiredAt!! + 30 * 60_000L, alarms().single().triggerAtMs)
        env.repo.onReminderFired(id) // first nag
        assertEquals(1, alarms().size)
        env.repo.onReminderFired(id) // second nag: allowance used up
        assertTrue(alarms().isEmpty())
    }

    @Test fun alarmRingsOnFirstFireThenNagsQuietly() = runTest {
        val id = env.repo.save(reminderTask(ReminderType.ALARM))
        assertTrue(env.repo.onReminderFired(id)!!.ring)
        assertFalse(env.repo.onReminderFired(id)!!.ring)
    }

    @Test fun snoozeMovesAlarmAndRingsAgainWithFreshNags() = runTest {
        val id = env.repo.save(reminderTask(ReminderType.ALARM))
        env.repo.onReminderFired(id)
        env.repo.onReminderFired(id)
        val before = System.currentTimeMillis()
        env.repo.snooze(id, 10)
        assertTrue(alarms().single().triggerAtMs >= before + 10 * 60_000L)
        val fired = env.repo.onReminderFired(id)!!
        assertTrue(fired.ring)
        assertEquals(1, fired.task.firedCount)
        assertNull(fired.task.snoozedUntil)
    }

    @Test fun firingDoneOrDeletedTaskIsIgnored() = runTest {
        val id = env.repo.save(reminderTask())
        env.repo.complete(id)
        assertNull(env.repo.onReminderFired(id))
        assertNull(env.repo.onReminderFired(9999))
    }

    @Test fun pastDueTaskIsSilenced() = runTest {
        val id = env.repo.save(reminderTask().copy(dueEpochDay = LocalDate.now().minusDays(1).toEpochDay()))
        assertEquals(SILENCED, env.repo.task(id)!!.firedCount)
        assertTrue(alarms().isEmpty())
    }

    @Test fun changingDueTimeRearms() = runTest {
        val id = env.repo.save(reminderTask())
        env.repo.onReminderFired(id)
        env.repo.save(env.repo.task(id)!!.copy(dueMinute = 10 * 60))
        val task = env.repo.task(id)!!
        assertEquals(0, task.firedCount)
        assertEquals(task.dueAtMillis(), alarms().single().triggerAtMs)
    }

    @Test fun editingTitleKeepsNagProgress() = runTest {
        val id = env.repo.save(reminderTask())
        env.repo.onReminderFired(id)
        env.repo.save(env.repo.task(id)!!.copy(title = "New"))
        assertEquals(1, env.repo.task(id)!!.firedCount)
    }

    @Test fun deleteThenRestorePutsEverythingBack() = runTest {
        val id = env.repo.save(reminderTask(), listOf(Subtask(title = "step")))
        val (task, subtasks) = env.repo.delete(env.repo.task(id)!!)
        assertNull(env.repo.task(id))
        assertTrue(alarms().isEmpty())
        env.repo.restore(task, subtasks)
        assertEquals("Call home", env.repo.task(id)!!.title)
        assertEquals(listOf("step"), env.repo.subtasks(id).map { it.title })
        assertEquals(1, alarms().size)
    }

    @Test fun editingListKeepsItsTasks() = runTest {
        val id = env.repo.save(reminderTask().copy(categoryId = 1))
        env.repo.saveCategory(Category(1, "Job", 0, "work"))
        assertEquals(1L, env.repo.task(id)!!.categoryId)
        assertEquals("Job", env.repo.categories.first().first { it.id == 1L }.name)
    }

    @Test fun deletingListMovesTasksToOther() = runTest {
        val id = env.repo.save(reminderTask().copy(categoryId = 1))
        env.repo.deleteCategory(1)
        assertNull(env.repo.task(id)!!.categoryId)
    }

    @Test fun clearHistoryOnlyRemovesDone() = runTest {
        val done = env.repo.save(reminderTask())
        val open = env.repo.save(reminderTask())
        env.repo.complete(done)
        env.repo.clearHistory()
        assertNull(env.repo.task(done))
        assertNotNull(env.repo.task(open))
    }

    @Test fun rescheduleAllRestoresAlarmsAfterReboot() = runTest {
        env.repo.save(reminderTask())
        env.repo.save(reminderTask(ReminderType.ALARM))
        alarms().toList().forEach { alarmManager.cancel(it.operation!!) } // what a reboot does
        assertTrue(alarms().isEmpty())
        env.repo.rescheduleAll()
        assertEquals(2, alarms().size)
    }

    @Test fun summariesFollowSetting() = runTest {
        env.settings.setSummaryOn(true)
        env.scheduler.syncSummaries()
        assertEquals(2, alarms().size)
        env.settings.setSummaryOn(false)
        env.scheduler.syncSummaries()
        assertTrue(alarms().isEmpty())
    }

    @Test fun deleteAllResetsToDefaultLists() = runTest {
        env.repo.saveCategory(Category(name = "Extra", color = 0, icon = "star"))
        env.repo.save(reminderTask())
        env.repo.deleteAll()
        assertTrue(alarms().isEmpty())
        assertTrue(env.dao.allTasks().isEmpty())
        assertEquals(6, env.repo.categories.first().size)
    }
}
