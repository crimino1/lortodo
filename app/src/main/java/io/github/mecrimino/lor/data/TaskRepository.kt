package io.github.mecrimino.lor.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mecrimino.lor.reminders.AlarmService
import io.github.mecrimino.lor.reminders.Notifier
import io.github.mecrimino.lor.reminders.ReminderScheduler
import io.github.mecrimino.lor.widget.TodayWidget
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class TaskRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: TaskDao,
    private val scheduler: ReminderScheduler,
    private val notifier: Notifier,
) {
    /** One state change at a time: a double tap, the widget and a notification button can race otherwise. */
    private val lock = Mutex()

    val tasks: Flow<List<Task>> = dao.observeTasks()
    val categories: Flow<List<Category>> = dao.observeCategories()
    fun tasksOn(day: LocalDate): Flow<List<Task>> = dao.observeDay(day.toEpochDay())

    suspend fun task(id: Long): Task? = dao.getTask(id)
    suspend fun subtasks(taskId: Long): List<Subtask> = dao.subtasks(taskId)
    suspend fun pendingCount(day: LocalDate): Int = dao.pendingCount(day.toEpochDay())

    /** Inserts or updates. A changed due time or reminder type re-arms the reminder from scratch. */
    suspend fun save(task: Task, subtasks: List<Subtask>? = null): Long = lock.withLock {
        val old = if (task.id != 0L) dao.getTask(task.id) else null
        // Only the fields the editor owns go onto the current row: a reminder may have fired, or the task been
        // completed from its notification, while the editor was open.
        val merged = old?.copy(
            title = task.title, notes = task.notes, dueEpochDay = task.dueEpochDay, dueMinute = task.dueMinute,
            priority = task.priority, categoryId = task.categoryId, repeat = task.repeat, repeatDays = task.repeatDays,
            reminder = task.reminder,
        ) ?: task
        val rearm = old == null || old.dueEpochDay != merged.dueEpochDay || old.dueMinute != merged.dueMinute || old.reminder != merged.reminder
        // A notification still showing for the old time would otherwise snooze or nag against the new one.
        if (old != null && rearm) silence(old.id)
        val toSave = if (rearm) merged.rearmed() else merged
        val id = if (old == null) dao.insertTask(toSave.copy(id = 0)) else toSave.id.also { dao.updateTask(toSave) }
        subtasks?.let { dao.replaceSubtasks(id, it) }
        scheduler.sync(toSave.copy(id = id))
        changed()
        id
    }

    suspend fun complete(id: Long) = lock.withLock {
        val task = dao.getTask(id)?.takeUnless { it.isDone } ?: return@withLock
        val today = LocalDate.now()
        val next = nextDueDate(task.repeat, task.repeatDays, task.dueDate, today)?.let { date ->
            val spawned = task.copy(
                id = 0, dueEpochDay = date.toEpochDay(), createdAt = System.currentTimeMillis(),
                snoozedUntil = null, nextOccurrenceId = null,
            ).rearmed()
            val newId = dao.insertTask(spawned)
            dao.replaceSubtasks(newId, dao.subtasks(id).map { it.copy(done = false) })
            scheduler.sync(spawned.copy(id = newId))
            newId
        }
        dao.updateTask(
            task.copy(status = TaskStatus.DONE, completedAt = System.currentTimeMillis(), snoozedUntil = null, nextOccurrenceId = next)
        )
        silence(id)
        changed()
    }

    suspend fun uncomplete(id: Long) = lock.withLock {
        val task = dao.getTask(id)?.takeIf { it.isDone } ?: return@withLock
        task.nextOccurrenceId?.let { spawned ->
            // Take the repeat back only while it's untouched; once done it is history, not a duplicate.
            if (dao.getTask(spawned)?.status == TaskStatus.PENDING) {
                dao.deleteTaskById(spawned)
                silence(spawned)
            }
        }
        val reopened = task.copy(status = TaskStatus.PENDING, completedAt = null, nextOccurrenceId = null).rearmed()
        dao.updateTask(reopened)
        scheduler.sync(reopened)
        changed()
    }

    suspend fun toggle(task: Task) = if (task.isDone) uncomplete(task.id) else complete(task.id)

    /** Returns the removed task and its subtasks so an Undo can put them back. */
    suspend fun delete(task: Task): Pair<Task, List<Subtask>> {
        val subtasks = dao.subtasks(task.id)
        dao.deleteTask(task)
        silence(task.id)
        changed()
        return task to subtasks
    }

    suspend fun restore(task: Task, subtasks: List<Subtask>) {
        dao.insertTask(task)
        dao.insertSubtasks(subtasks)
        scheduler.sync(task)
        changed()
    }

    suspend fun snooze(id: Long, minutes: Int) = lock.withLock {
        val task = dao.getTask(id)?.takeUnless { it.isDone } ?: return@withLock
        val snoozed = task.copy(snoozedUntil = System.currentTimeMillis() + minutes * 60_000L)
        dao.updateTask(snoozed)
        notifier.cancel(id)
        AlarmService.stopIfRinging(context, id)
        scheduler.sync(snoozed)
    }

    /** Called when a task's alarm goes off. Records the fire, arms the next nag, returns what to show. */
    suspend fun onReminderFired(id: Long): FiredReminder? = lock.withLock {
        val task = dao.getTask(id) ?: return@withLock null
        if (task.isDone || task.reminder == ReminderType.NONE) return@withLock null
        val ring = task.reminder == ReminderType.ALARM && task.isRingFire
        // A snooze ending counts as a fresh first fire, so nags resume from there with the full allowance.
        val fired = task.copy(
            snoozedUntil = null,
            firedCount = if (task.snoozedUntil != null) 1 else task.firedCount + 1,
            lastFiredAt = System.currentTimeMillis(),
        )
        dao.updateTask(fired)
        scheduler.sync(fired)
        FiredReminder(fired, ring)
    }

    suspend fun saveCategory(category: Category): Long = dao.upsertCategoryKeepingTasks(category).also { changed() }

    suspend fun deleteCategory(id: Long) {
        dao.deleteCategory(id)
        changed()
    }

    suspend fun clearHistory() {
        dao.clearDone()
        changed()
    }

    suspend fun rescheduleAll() {
        dao.pendingWithReminder().forEach { scheduler.sync(it) }
        scheduler.syncSummaries()
    }

    /** Backup restore / delete-all: drops every armed reminder for the old data, swaps data atomically, re-arms. */
    suspend fun replaceAll(categories: List<Category>, tasks: List<Task>, subtasks: List<Subtask>) {
        val oldIds = dao.allTasks().map { it.id }
        dao.replaceAll(categories, tasks, subtasks)
        oldIds.forEach { silence(it) }
        rescheduleAll()
        changed()
    }

    suspend fun deleteAll() = replaceAll(LorDatabase.DEFAULT_CATEGORIES, emptyList(), emptyList())

    private fun silence(id: Long) {
        scheduler.cancel(id)
        notifier.cancel(id)
        AlarmService.stopIfRinging(context, id)
    }

    private fun Task.rearmed(): Task {
        val due = dueAtMillis()
        return copy(
            snoozedUntil = null,
            lastFiredAt = null,
            firedCount = if (due != null && due <= System.currentTimeMillis()) SILENCED else 0,
        )
    }

    private suspend fun changed() = TodayWidget().updateAll(context)
}

data class FiredReminder(val task: Task, val ring: Boolean)

/** REPLACE would delete the old row first and SET NULL every task in the list, so updates must not use it. */
private suspend fun TaskDao.upsertCategoryKeepingTasks(c: Category): Long {
    if (c.id == 0L) return upsertCategory(c)
    updateCategory(c)
    return c.id
}
