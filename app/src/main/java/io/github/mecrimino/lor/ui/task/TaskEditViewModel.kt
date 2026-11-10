package io.github.mecrimino.lor.ui.task

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mecrimino.lor.data.Category
import io.github.mecrimino.lor.data.Priority
import io.github.mecrimino.lor.data.ReminderType
import io.github.mecrimino.lor.data.RepeatType
import io.github.mecrimino.lor.data.SettingsRepository
import io.github.mecrimino.lor.data.Subtask
import io.github.mecrimino.lor.data.Task
import io.github.mecrimino.lor.data.TaskRepository
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EditState(
    val base: Task? = null,
    val title: String = "",
    val notes: String = "",
    val date: LocalDate = LocalDate.now(),
    val minute: Int? = null,
    val priority: Priority = Priority.LOW,
    val categoryId: Long? = null,
    val repeat: RepeatType = RepeatType.NONE,
    val repeatDays: Int = 0,
    val reminder: ReminderType = ReminderType.NONE,
    val subtasks: List<Subtask> = emptyList(),
    val loaded: Boolean = false,
) {
    val canSave get() = title.isNotBlank() && !(repeat == RepeatType.CUSTOM && repeatDays == 0)

    /** A reminder needs a time to fire at: picking one sets a sensible time, clearing the time drops the reminder. */
    fun withReminder(type: ReminderType) = copy(reminder = type, minute = if (type != ReminderType.NONE) minute ?: nextHourMinute() else minute)
    fun withMinute(value: Int?) = copy(minute = value, reminder = if (value == null) ReminderType.NONE else reminder)
    fun toggledDay(bit: Int) = copy(repeatDays = repeatDays xor (1 shl bit))

    fun toTask(): Task = (base ?: Task(title = title, dueEpochDay = date.toEpochDay())).copy(
        title = title.trim(), notes = notes.trim(), dueEpochDay = date.toEpochDay(), dueMinute = minute,
        priority = priority, categoryId = categoryId, repeat = repeat, repeatDays = repeatDays, reminder = reminder,
    )
}

/** Next full hour, clamped so a task created at 23:30 doesn't land on 00:00 of the same day. */
fun nextHourMinute(now: LocalTime = LocalTime.now()): Int = minOf((now.hour + 1) * 60, 23 * 60 + 59)

/** The editable fields as JSON, kept in SavedStateHandle so a half-written task survives Android killing the app. */
private fun EditState.toDraft(): String = JSONObject()
    .put("title", title).put("notes", notes).put("date", date.toEpochDay()).put("minute", minute ?: -1)
    .put("priority", priority.name).put("categoryId", categoryId ?: -1).put("repeat", repeat.name)
    .put("repeatDays", repeatDays).put("reminder", reminder.name)
    .put("subtasks", JSONArray(subtasks.map { JSONObject().put("title", it.title).put("done", it.done) }))
    .toString()

private fun EditState.withDraft(json: String): EditState {
    val o = JSONObject(json)
    val subs = o.getJSONArray("subtasks")
    return copy(
        title = o.getString("title"), notes = o.getString("notes"), date = LocalDate.ofEpochDay(o.getLong("date")),
        minute = o.getInt("minute").takeIf { it >= 0 }, priority = enumValueOf(o.getString("priority")),
        categoryId = o.getLong("categoryId").takeIf { it > 0 }, repeat = enumValueOf(o.getString("repeat")),
        repeatDays = o.getInt("repeatDays"), reminder = enumValueOf(o.getString("reminder")),
        subtasks = List(subs.length()) { i -> subs.getJSONObject(i).let { Subtask(title = it.getString("title"), done = it.getBoolean("done")) } },
    )
}

private const val DRAFT = "draft"

@HiltViewModel
class TaskEditViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val repo: TaskRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val taskId: Long = handle["id"] ?: 0L
    private val dateArg: Long = handle["date"] ?: -1L

    val isNew get() = taskId == 0L
    val categories: StateFlow<List<Category>> = repo.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Compose state, not a StateFlow: text fields need their value back in the same frame or typing lags and drops letters.
    var state by mutableStateOf(EditState())
        private set
    private var busy = false

    init {
        viewModelScope.launch {
            val task = if (isNew) null else repo.task(taskId)
            val loaded = if (task != null) {
                EditState(
                    base = task, title = task.title, notes = task.notes, date = task.dueDate, minute = task.dueMinute,
                    priority = task.priority, categoryId = task.categoryId, repeat = task.repeat, repeatDays = task.repeatDays,
                    reminder = task.reminder, subtasks = repo.subtasks(task.id), loaded = true,
                )
            } else {
                EditState(date = if (dateArg >= 0) LocalDate.ofEpochDay(dateArg) else LocalDate.now(), loaded = true)
                    .withReminder(settings.current().defaultReminder)
            }
            val draft = handle.get<String>(DRAFT)?.takeIf { isNew || task != null }
            state = draft?.let { runCatching { loaded.withDraft(it) }.getOrNull() } ?: loaded
        }
    }

    fun edit(change: (EditState) -> EditState) {
        state = change(state)
        handle[DRAFT] = state.toDraft()
    }

    fun addSubtask(title: String) {
        if (title.isBlank()) return
        edit { it.copy(subtasks = it.subtasks + Subtask(title = title.trim())) }
    }

    fun save(then: () -> Unit) {
        val s = state
        if (!s.canSave || busy) return // a double tap must not save a new task twice
        busy = true
        viewModelScope.launch {
            repo.save(s.toTask(), s.subtasks)
            then()
        }
    }

    fun delete(then: () -> Unit) {
        val base = state.base ?: return then()
        if (busy) return
        busy = true
        viewModelScope.launch {
            repo.delete(base)
            then()
        }
    }
}
