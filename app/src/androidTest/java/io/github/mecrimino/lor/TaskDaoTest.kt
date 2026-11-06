package io.github.mecrimino.lor

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mecrimino.lor.data.Category
import io.github.mecrimino.lor.data.LorDatabase
import io.github.mecrimino.lor.data.ReminderType
import io.github.mecrimino.lor.data.Subtask
import io.github.mecrimino.lor.data.Task
import io.github.mecrimino.lor.data.TaskDao
import io.github.mecrimino.lor.data.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskDaoTest {
    private lateinit var db: LorDatabase
    private lateinit var dao: TaskDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LorDatabase::class.java)
            .addCallback(LorDatabase.SEED)
            .build()
        dao = db.taskDao()
    }

    @After fun tearDown() = db.close()

    private fun task(title: String, day: Long = 100, minute: Int? = null) = Task(title = title, dueEpochDay = day, dueMinute = minute)

    @Test fun seedsDefaultLists() = runTest {
        assertEquals(listOf("Work", "Education", "Exercise", "Food", "Water", "Personal"), dao.observeCategories().first().map { it.name })
    }

    @Test fun ordersByDueThenTimeWithAnyTimeLast() = runTest {
        dao.insertTask(task("any", 100))
        dao.insertTask(task("late", 100, 600))
        dao.insertTask(task("early", 100, 60))
        dao.insertTask(task("yesterday", 99))
        assertEquals(listOf("yesterday", "early", "late", "any"), dao.observeTasks().first().map { it.title })
    }

    @Test fun observeDayOnlyReturnsThatDay() = runTest {
        dao.insertTask(task("a", 100))
        dao.insertTask(task("b", 101))
        assertEquals(listOf("a"), dao.observeDay(100).first().map { it.title })
    }

    @Test fun pendingWithReminderSkipsDoneAndNoReminder() = runTest {
        dao.insertTask(task("yes").copy(reminder = ReminderType.ALARM))
        dao.insertTask(task("none"))
        dao.insertTask(task("done").copy(reminder = ReminderType.ALARM, status = TaskStatus.DONE))
        assertEquals(listOf("yes"), dao.pendingWithReminder().map { it.title })
    }

    @Test fun deletingTaskCascadesToSubtasks() = runTest {
        val id = dao.insertTask(task("t"))
        dao.insertSubtasks(listOf(Subtask(taskId = id, title = "s")))
        dao.deleteTaskById(id)
        assertTrue(dao.allSubtasks().isEmpty())
    }

    @Test fun deletingCategorySetsTasksNull() = runTest {
        val id = dao.insertTask(task("t").copy(categoryId = 1))
        dao.deleteCategory(1)
        assertNull(dao.getTask(id)!!.categoryId)
    }

    @Test fun updatingCategoryKeepsTasks() = runTest {
        val id = dao.insertTask(task("t").copy(categoryId = 1))
        dao.updateCategory(Category(1, "Job", 0, "work"))
        assertEquals(1L, dao.getTask(id)!!.categoryId)
    }

    @Test fun replaceSubtasksRenumbers() = runTest {
        val id = dao.insertTask(task("t"))
        dao.insertSubtasks(listOf(Subtask(taskId = id, title = "old")))
        dao.replaceSubtasks(id, listOf(Subtask(title = "b"), Subtask(title = "a")))
        val subs = dao.subtasks(id)
        assertEquals(listOf("b", "a"), subs.map { it.title })
        assertEquals(listOf(0, 1), subs.map { it.position })
    }

    @Test fun clearDoneKeepsPending() = runTest {
        dao.insertTask(task("done").copy(status = TaskStatus.DONE))
        dao.insertTask(task("open"))
        dao.clearDone()
        assertEquals(listOf("open"), dao.allTasks().map { it.title })
    }

    @Test fun pendingCountCountsOnlyThatDayPending() = runTest {
        dao.insertTask(task("a", 100))
        dao.insertTask(task("b", 100).copy(status = TaskStatus.DONE))
        dao.insertTask(task("c", 101))
        assertEquals(1, dao.pendingCount(100))
    }

    @Test fun failedReplaceAllRollsBack() = runTest {
        dao.insertTask(task("keep"))
        runCatching {
            dao.replaceAll(emptyList(), listOf(task("new").copy(id = 5)), listOf(Subtask(taskId = 999, title = "orphan")))
        }
        assertEquals(listOf("keep"), dao.allTasks().map { it.title })
    }
}
