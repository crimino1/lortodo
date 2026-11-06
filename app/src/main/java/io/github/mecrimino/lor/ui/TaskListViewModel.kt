package io.github.mecrimino.lor.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mecrimino.lor.data.Category
import io.github.mecrimino.lor.data.Task
import io.github.mecrimino.lor.data.TaskRepository
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class UndoEvent(val message: String, val action: suspend () -> Unit)

/** Done/delete-with-undo shared by every screen that lists tasks. */
abstract class TaskListViewModel(protected val repo: TaskRepository) : ViewModel() {
    private val _undo = MutableSharedFlow<UndoEvent>(extraBufferCapacity = 1)
    val undo: SharedFlow<UndoEvent> = _undo

    fun toggle(task: Task) {
        viewModelScope.launch { repo.toggle(task) }
    }

    fun swipeDone(task: Task) {
        viewModelScope.launch {
            repo.complete(task.id)
            _undo.tryEmit(UndoEvent("Marked done") { repo.uncomplete(task.id) })
        }
    }

    fun swipeDelete(task: Task) {
        viewModelScope.launch {
            val (t, subtasks) = repo.delete(task)
            _undo.tryEmit(UndoEvent("Task deleted") { repo.restore(t, subtasks) })
        }
    }

    fun runUndo(event: UndoEvent) {
        viewModelScope.launch { event.action() }
    }

    protected fun quickAdd(title: String, date: java.time.LocalDate, categoryId: Long?) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repo.save(Task(title = trimmed, dueEpochDay = date.toEpochDay(), categoryId = categoryId))
        }
    }
}

@Composable
fun UndoSnackbars(vm: TaskListViewModel, host: SnackbarHostState) {
    LaunchedEffect(vm) {
        vm.undo.collectLatest { event ->
            if (host.showSnackbar(event.message, "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                vm.runUndo(event)
            }
        }
    }
}

/** Current time, re-emitted each minute so "overdue" and the greeting move on while the screen is open. */
fun clock(): Flow<LocalDateTime> = flow {
    while (true) {
        emit(LocalDateTime.now())
        delay(60_000)
    }
}

fun Map<Long, Category>.of(task: Task): Category = task.categoryId?.let { this[it] } ?: Category.Other
