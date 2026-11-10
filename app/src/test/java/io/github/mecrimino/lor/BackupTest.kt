package io.github.mecrimino.lor

import io.github.mecrimino.lor.data.BackupData
import io.github.mecrimino.lor.data.BackupManager
import io.github.mecrimino.lor.data.Category
import io.github.mecrimino.lor.data.Priority
import io.github.mecrimino.lor.data.ReminderType
import io.github.mecrimino.lor.data.SILENCED
import io.github.mecrimino.lor.data.Settings
import io.github.mecrimino.lor.data.Subtask
import io.github.mecrimino.lor.data.Task
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class BackupTest {
    private val tomorrow = LocalDate.now().plusDays(1).toEpochDay()
    private val sample = BackupData(
        Settings(name = "Sam", nagMax = 3, waterGoal = 6),
        listOf(Category(10, "Reading", 123, "star")),
        listOf(
            Task(id = 7, title = "Read", notes = "ch 4", dueEpochDay = tomorrow, dueMinute = 60, priority = Priority.HIGH, categoryId = 10, reminder = ReminderType.ALARM, createdAt = 5),
            Task(id = 8, title = "Any time", dueEpochDay = tomorrow, createdAt = 6),
        ),
        listOf(Subtask(taskId = 7, title = "step", done = true, position = 0)),
    )

    @Test fun roundTrip() {
        val back = BackupManager.decode(BackupManager.encode(sample))
        assertEquals(sample.categories, back.categories)
        assertEquals(sample.tasks, back.tasks)
        assertEquals(sample.subtasks, back.subtasks)
        assertEquals("Sam", back.settings.name)
        assertEquals(3, back.settings.nagMax)
    }

    @Test fun rejectsBadFiles() {
        fun bad(json: String) = assertThrows(json.take(60), Exception::class.java) { BackupManager.decode(json) }
        val ok = BackupManager.encode(sample)
        bad("not json")
        bad("{}")
        bad("""{"app":"other","version":1}""")
        bad(ok.replaceFirst("\"version\": 1", "\"version\": 99"))
        bad(ok.replace("\"title\": \"Read\"", "\"title\": \"  \""))
        bad(ok.replace("\"priority\": \"HIGH\"", "\"priority\": \"URGENT\""))
        bad(ok.replace("\"dueMinute\": 60", "\"dueMinute\": 5000"))
        bad(ok.replace("\"id\": 8", "\"id\": 7"))
    }

    @Test fun danglingListBecomesNoList() {
        val json = BackupManager.encode(sample).replace("\"categoryId\": 10", "\"categoryId\": 999")
        assertNull(BackupManager.decode(json).tasks.first().categoryId)
    }

    @Test fun restoreReplacesEverything() = runTest {
        val env = TestEnv()
        env.repo.save(Task(title = "Old", dueEpochDay = tomorrow))
        env.backup.restore(sample)
        assertEquals(listOf("Read", "Any time"), env.dao.allTasks().map { it.title })
        assertEquals(listOf("Reading"), env.repo.categories.first().map { it.name })
        assertEquals(listOf("step"), env.repo.subtasks(7).map { it.title })
        assertEquals("Sam", env.settings.current().name)
    }

    @Test fun restoreDoesNotFirePastReminders() = runTest {
        val env = TestEnv()
        val old = sample.tasks[0].copy(dueEpochDay = LocalDate.now().minusDays(3).toEpochDay())
        env.backup.restore(sample.copy(tasks = listOf(old)))
        assertEquals(SILENCED, env.repo.task(7)!!.firedCount)
    }

    @Test fun restoreSilencesTasksThatWereMidNag() = runTest {
        val env = TestEnv()
        val past = LocalDate.now().minusDays(3).toEpochDay()
        val nagging = sample.tasks[0].copy(dueEpochDay = past, firedCount = 2, lastFiredAt = 1L)
        env.backup.restore(sample.copy(tasks = listOf(nagging)))
        assertEquals(SILENCED, env.repo.task(7)!!.firedCount)
    }

    @Test fun failedDatabaseSwapLeavesDataAlone() = runTest {
        val env = TestEnv()
        env.repo.save(Task(title = "Keep me", dueEpochDay = tomorrow))
        val orphan = sample.copy(subtasks = listOf(Subtask(taskId = 999, title = "orphan")))
        runCatching { env.backup.restore(orphan) }
        assertEquals(listOf("Keep me"), env.dao.allTasks().map { it.title })
    }
}
