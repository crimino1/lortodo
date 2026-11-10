package io.github.mecrimino.lor

import androidx.lifecycle.SavedStateHandle
import io.github.mecrimino.lor.data.Category
import io.github.mecrimino.lor.data.LorDatabase
import io.github.mecrimino.lor.data.Priority
import io.github.mecrimino.lor.data.ReminderType
import io.github.mecrimino.lor.data.RepeatType
import io.github.mecrimino.lor.data.Task
import io.github.mecrimino.lor.data.TaskStatus
import io.github.mecrimino.lor.ui.UndoEvent
import io.github.mecrimino.lor.ui.calendar.CalendarViewModel
import io.github.mecrimino.lor.ui.calendar.monthCells
import io.github.mecrimino.lor.ui.history.buildHistory
import io.github.mecrimino.lor.ui.home.HomeViewModel
import io.github.mecrimino.lor.ui.home.greetingFor
import io.github.mecrimino.lor.ui.home.isOverdue
import io.github.mecrimino.lor.ui.search.SearchQuery
import io.github.mecrimino.lor.ui.search.SortBy
import io.github.mecrimino.lor.ui.search.applyQuery
import io.github.mecrimino.lor.ui.task.EditState
import io.github.mecrimino.lor.ui.task.TaskEditViewModel
import io.github.mecrimino.lor.ui.task.nextHourMinute
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class ViewModelsTest {
    private lateinit var env: TestEnv
    private val today get() = LocalDate.now()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        env = TestEnv()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun task(title: String, day: LocalDate = today, minute: Int? = null, cat: Long? = null) =
        Task(title = title, dueEpochDay = day.toEpochDay(), dueMinute = minute, categoryId = cat)

    private suspend fun awaitLoaded(vm: TaskEditViewModel): EditState = withTimeout(5_000) {
        while (!vm.state.loaded) delay(10)
        vm.state
    }

    @Test fun editorDraftSurvivesProcessDeath() = runBlocking {
        val handle = SavedStateHandle(mapOf("id" to 0L))
        val first = TaskEditViewModel(handle, env.repo, env.settings)
        awaitLoaded(first)
        first.edit { it.copy(title = "Half written", priority = Priority.HIGH) }
        first.addSubtask("step")
        val restored = awaitLoaded(TaskEditViewModel(handle, env.repo, env.settings))
        assertEquals("Half written", restored.title)
        assertEquals(Priority.HIGH, restored.priority)
        assertEquals(listOf("step"), restored.subtasks.map { it.title })
    }

    @Test fun editorIgnoresDoubleSave() = runBlocking {
        val vm = TaskEditViewModel(SavedStateHandle(mapOf("id" to 0L)), env.repo, env.settings)
        awaitLoaded(vm)
        vm.edit { it.copy(title = "Once") }
        val saved = CompletableDeferred<Unit>()
        vm.save { saved.complete(Unit) }
        vm.save { }
        withTimeout(5_000) { saved.await() }
        delay(200)
        assertEquals(1, env.dao.allTasks().size)
    }

    @Test fun greetingChangesWithTime() {
        assertEquals("Good morning, Sam", greetingFor(8, "Sam"))
        assertEquals("Good afternoon, Sam", greetingFor(13, "Sam"))
        assertEquals("Good evening, Sam", greetingFor(19, "Sam"))
        assertEquals("Good evening, Sam", greetingFor(2, "Sam"))
        assertEquals("Good morning", greetingFor(8, ""))
    }

    @Test fun overdueRules() {
        val now = LocalDateTime.of(2026, 11, 6, 12, 0)
        val day = now.toLocalDate()
        assertTrue(isOverdue(task("y", day.minusDays(1)), now))
        assertTrue(isOverdue(task("earlier", day, 600), now))
        assertFalse(isOverdue(task("later", day, 800), now))
        assertFalse(isOverdue(task("any time today", day), now))
        assertFalse(isOverdue(task("y", day.minusDays(1)).copy(status = TaskStatus.DONE), now))
    }

    @Test fun homeSortsTasksIntoSections() = runBlocking {
        env.settings.setName("Sam")
        env.repo.save(task("Overdue", today.minusDays(1), cat = 1))
        env.repo.save(task("Today A", cat = 1))
        env.repo.save(task("Soon", today.plusDays(2), cat = 3))
        env.repo.save(task("Far away", today.plusDays(30)))
        val doneId = env.repo.save(task("Today B", cat = 3))
        env.repo.complete(doneId)

        val vm = HomeViewModel(env.repo, env.settings)
        val job = launch { vm.state.collect {} }
        val s = withTimeout(5_000) { vm.state.first { it.loaded && it.total == 2 && it.doneCount == 1 } }
        assertEquals(listOf("Overdue"), s.overdue.map { it.title })
        assertEquals(listOf("Today A"), s.today.map { it.title })
        assertEquals(listOf("Soon"), s.upcoming.map { it.title })
        assertEquals(listOf("Today B"), s.completed.map { it.title })
        assertTrue(s.greeting.endsWith(", Sam"))
        assertEquals(1, s.rings.first { it.category.id == LorDatabase.EXERCISE_ID }.done)
        job.cancel()
    }

    @Test fun homeFilterAndQuickAdd() = runBlocking {
        env.repo.save(task("Work thing", cat = 1))
        env.repo.save(task("Food thing", cat = 4))
        val vm = HomeViewModel(env.repo, env.settings)
        val job = launch { vm.state.collect {} }
        withTimeout(5_000) { vm.state.first { it.total == 2 } }

        vm.pickFilter(4)
        val filtered = withTimeout(5_000) { vm.state.first { it.filter == 4L } }
        assertEquals(listOf("Food thing"), filtered.today.map { it.title })
        assertEquals(4L, filtered.addCategory.id)

        vm.add("  Lunch  ")
        vm.add("   ")
        val added = withTimeout(5_000) { vm.state.first { it.total == 3 } }
        val lunch = added.today.single { it.title == "Lunch" }
        assertEquals(4L, lunch.categoryId)
        assertEquals(today, lunch.dueDate)
        job.cancel()
    }

    @Test fun swipeDeleteOffersWorkingUndo() = runBlocking {
        val id = env.repo.save(task("Oops"))
        val vm = HomeViewModel(env.repo, env.settings)
        val event = CompletableDeferred<UndoEvent>()
        val job = launch(Dispatchers.Unconfined) { event.complete(vm.undo.first()) }
        vm.swipeDelete(env.repo.task(id)!!)
        val undo = withTimeout(5_000) { event.await() }
        withTimeout(5_000) { while (env.repo.task(id) != null) delay(10) }
        vm.runUndo(undo)
        withTimeout(5_000) { while (env.repo.task(id) == null) delay(10) }
        job.cancel()
    }

    @Test fun waterTapsResetAfterGoal() = runBlocking {
        env.settings.setWaterGoal(2)
        val vm = HomeViewModel(env.repo, env.settings)
        val job = launch { vm.state.collect {} }
        withTimeout(5_000) { vm.state.first { it.waterGoal == 2 } }
        vm.tapWater()
        withTimeout(5_000) { vm.state.first { it.water == 1 } }
        vm.tapWater()
        withTimeout(5_000) { vm.state.first { it.water == 2 } }
        vm.tapWater()
        withTimeout(5_000) { vm.state.first { it.water == 0 } }
        job.cancel()
    }

    @Test fun waterDoesNotCarryOverToNextDay() = runBlocking {
        env.settings.tapWater(today.minusDays(1))
        assertEquals(0, env.settings.current().waterToday(today))
    }

    @Test fun editStateRules() {
        val base = EditState()
        assertFalse(base.canSave)
        assertTrue(base.copy(title = "x").canSave)
        assertFalse(base.copy(title = "x", repeat = RepeatType.CUSTOM).canSave)
        assertTrue(base.copy(title = "x", repeat = RepeatType.CUSTOM).toggledDay(0).canSave)

        val withAlarm = base.withReminder(ReminderType.ALARM)
        assertTrue(withAlarm.minute != null)
        assertEquals(ReminderType.NONE, withAlarm.withMinute(null).reminder)
        assertEquals(1439, nextHourMinute(LocalTime.of(23, 30)))
        assertEquals(10 * 60, nextHourMinute(LocalTime.of(9, 15)))
    }

    @Test fun editorCreatesWithDefaultsAndSaves() = runBlocking {
        env.settings.setDefaultReminder(ReminderType.ALARM)
        val day = today.plusDays(3)
        val vm = TaskEditViewModel(SavedStateHandle(mapOf("id" to 0L, "date" to day.toEpochDay())), env.repo, env.settings)
        val loaded = awaitLoaded(vm)
        assertEquals(day, loaded.date)
        assertEquals(ReminderType.ALARM, loaded.reminder)
        assertTrue(loaded.minute != null)

        vm.edit { it.copy(title = "Dentist", priority = Priority.HIGH) }
        vm.addSubtask("Bring card")
        vm.addSubtask("  ")
        val saved = CompletableDeferred<Unit>()
        vm.save { saved.complete(Unit) }
        withTimeout(5_000) { saved.await() }
        val t = env.dao.allTasks().single()
        assertEquals("Dentist", t.title)
        assertEquals(Priority.HIGH, t.priority)
        assertEquals(listOf("Bring card"), env.repo.subtasks(t.id).map { it.title })
    }

    @Test fun editorLoadsExisting() = runBlocking {
        val id = env.repo.save(task("Old", minute = 60))
        val vm = TaskEditViewModel(SavedStateHandle(mapOf("id" to id)), env.repo, env.settings)
        val s = awaitLoaded(vm)
        assertEquals("Old", s.title)
        assertEquals(60, s.minute)
    }

    @Test fun editorHandlesVanishedTask() = runBlocking {
        val vm = TaskEditViewModel(SavedStateHandle(mapOf("id" to 999L)), env.repo, env.settings)
        val s = awaitLoaded(vm)
        assertNull(s.base)
    }

    @Test fun searchFiltersAndSorts() {
        val a = task("Buy milk").copy(id = 1, priority = Priority.LOW, createdAt = 3, dueEpochDay = 300)
        val b = task("Call").copy(id = 2, notes = "about MILK", priority = Priority.HIGH, createdAt = 1, dueEpochDay = 200, categoryId = 5)
        val c = task("Run").copy(id = 3, priority = Priority.MEDIUM, createdAt = 2, dueEpochDay = 100, status = TaskStatus.DONE)
        val all = listOf(a, b, c)
        assertEquals(listOf(b, a), applyQuery(all, SearchQuery(text = "milk")))
        assertEquals(listOf(b), applyQuery(all, SearchQuery(categoryId = 5)))
        assertEquals(listOf(b), applyQuery(all, SearchQuery(priority = Priority.HIGH)))
        assertEquals(listOf(c), applyQuery(all, SearchQuery(status = TaskStatus.DONE)))
        assertEquals(listOf(c, b, a), applyQuery(all, SearchQuery(sort = SortBy.DUE)))
        assertEquals(listOf(b, c, a), applyQuery(all, SearchQuery(sort = SortBy.PRIORITY)))
        assertEquals(listOf(a, c, b), applyQuery(all, SearchQuery(sort = SortBy.CREATED)))
    }

    @Test fun historyCountsWeekAndStreak() {
        val day = LocalDate.of(2026, 11, 6) // Friday
        fun doneOn(d: LocalDate) = task("x").copy(
            status = TaskStatus.DONE,
            completedAt = d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        val tasks = listOf(doneOn(day), doneOn(day), doneOn(day.minusDays(1)), doneOn(day.minusDays(10)), task("open"))
        val h = buildHistory(tasks, listOf(Category(1, "W", 0, "work")), day)
        assertEquals(3, h.completedThisWeek)
        assertEquals(2, h.streak)
        assertEquals(4, h.total)
        assertEquals(3, h.byDay.size)
    }

    @Test fun monthGridIsMondayFirst() {
        val nov = monthCells(YearMonth.of(2026, 11)) // starts on a Sunday
        assertEquals(42, nov.size)
        assertEquals(6, nov.indexOfFirst { it != null })
        assertEquals(30, nov.count { it != null })
    }

    @Test fun calendarGroupsAndAddsToSelectedDay() = runBlocking {
        val vm = CalendarViewModel(env.repo)
        val job = launch { vm.state.collect {} }
        withTimeout(5_000) { vm.state.first { it.categories.isNotEmpty() } }
        val day = today.plusDays(4)
        vm.select(day)
        withTimeout(5_000) { vm.state.first { it.selected == day } }
        vm.add("Dentist")
        val s = withTimeout(5_000) { vm.state.first { it.byDay[day]?.isNotEmpty() == true } }
        assertEquals("Dentist", s.byDay.getValue(day).single().title)
        job.cancel()
    }
}
