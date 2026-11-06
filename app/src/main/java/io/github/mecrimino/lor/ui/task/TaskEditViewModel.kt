package io.github.mecrimino.lor.ui.task

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

@HiltViewModel
class TaskEditViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: TaskRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val taskId: Long = handle["id"] ?: 0L
    private val dateArg: Long = handle["date"] ?: -1L

    val isNew get() = taskId == 0L
    val categories: StateFlow<List<Category>> = repo.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _state = MutableStateFlow(EditState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val task = if (isNew) null else repo.task(taskId)
            _state.value = if (task != null) {
                EditState(
                    base = task, title = task.title, notes = task.notes, date = task.dueDate, minute = task.dueMinute,
                    priority = task.priority, categoryId = task.categoryId, repeat = task.repeat, repeatDays = task.repeatDays,
                    reminder = task.reminder, subtasks = repo.subtasks(task.id), loaded = true,
                )
            } else {
                EditState(date = if (dateArg >= 0) LocalDate.ofEpochDay(dateArg) else LocalDate.now(), loaded = true)
                    .withReminder(settings.current().defaultReminder)
            }
        }
    }

    fun edit(change: (EditState) -> EditState) = _state.update(change)

    fun addSubtask(title: String) {
        if (title.isBlank()) return
        edit { it.copy(subtasks = it.subtasks + Subtask(title = title.trim())) }
    }

    fun save(then: () -> Unit) {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            repo.save(s.toTask(), s.subtasks)
            then()
        }
    }

    fun delete(then: () -> Unit) {
        val base = _state.value.base ?: return then()
        viewModelScope.launch {
            repo.delete(base)
            then()
        }
    }
}
