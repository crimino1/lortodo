package io.github.mecrimino.lor.ui.home

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mecrimino.lor.data.Category
import io.github.mecrimino.lor.data.LorDatabase
import io.github.mecrimino.lor.data.Settings
import io.github.mecrimino.lor.data.SettingsRepository
import io.github.mecrimino.lor.data.Task
import io.github.mecrimino.lor.data.TaskRepository
import io.github.mecrimino.lor.ui.TaskListViewModel
import io.github.mecrimino.lor.ui.clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Ring(val category: Category, val done: Int, val total: Int)

data class Chip(val category: Category?, val pending: Int)

data class HomeState(
    val greeting: String = "",
    val doneCount: Int = 0,
    val total: Int = 0,
    val water: Int = 0,
    val waterGoal: Int = 8,
    val waterCategory: Category = Category.Other,
    val rings: List<Ring> = emptyList(),
    val chips: List<Chip> = emptyList(),
    val filter: Long? = null,
    val addCategory: Category = Category.Other,
    val overdue: List<Task> = emptyList(),
    val today: List<Task> = emptyList(),
    val upcoming: List<Task> = emptyList(),
    val completed: List<Task> = emptyList(),
    val categories: Map<Long, Category> = emptyMap(),
    val showBatteryTip: Boolean = false,
    val loaded: Boolean = false,
)

private data class Selection(val filter: Long? = null, val addCategory: Long? = null)

fun greetingFor(hour: Int, name: String): String {
    val part = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    return if (name.isBlank()) part else "$part, $name"
}

fun isOverdue(task: Task, now: LocalDateTime): Boolean {
    if (task.isDone) return false
    val day = now.toLocalDate()
    return task.dueDate.isBefore(day) ||
        (task.dueDate == day && task.dueMinute != null && task.dueMinute < now.hour * 60 + now.minute)
}

private const val UPCOMING_DAYS = 7L

private fun buildState(tasks: List<Task>, cats: List<Category>, s: Settings, sel: Selection, now: LocalDateTime): HomeState {
    val today: LocalDate = now.toLocalDate()
    val byId = cats.associateBy { it.id }
    val todayAll = tasks.filter { it.dueDate == today }
    val scoped = if (sel.filter == null) tasks else tasks.filter { it.categoryId == sel.filter }

    // Rings follow the design (Exercise, Food), falling back to other lists if those were deleted.
    val preferred = listOf(LorDatabase.EXERCISE_ID, LorDatabase.FOOD_ID).mapNotNull { byId[it] }
    val ringCats = (preferred + cats.filter { it.id != LorDatabase.WATER_ID }).distinct().take(2)

    val overdue = scoped.filter { isOverdue(it, now) }
    return HomeState(
        greeting = greetingFor(now.hour, s.name),
        doneCount = todayAll.count { it.isDone },
        total = todayAll.size,
        water = s.waterToday(today),
        waterGoal = s.waterGoal,
        waterCategory = byId[LorDatabase.WATER_ID] ?: Category.Other.copy(color = 0xFF0EA5C6.toInt(), icon = "water"),
        rings = ringCats.map { c ->
            val list = todayAll.filter { it.categoryId == c.id }
            Ring(c, list.count { it.isDone }, list.size)
        },
        chips = listOf(Chip(null, todayAll.count { !it.isDone })) +
            cats.map { c -> Chip(c, todayAll.count { it.categoryId == c.id && !it.isDone }) },
        filter = sel.filter,
        addCategory = byId[sel.addCategory] ?: cats.firstOrNull() ?: Category.Other,
        overdue = overdue,
        today = scoped.filter { it.dueDate == today && !it.isDone && it !in overdue },
        upcoming = scoped.filter { !it.isDone && it.dueDate.isAfter(today) && !it.dueDate.isAfter(today.plusDays(UPCOMING_DAYS)) },
        completed = scoped.filter { it.dueDate == today && it.isDone },
        categories = byId,
        showBatteryTip = s.onboarded && !s.batteryTipSeen,
        loaded = true,
    )
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    repo: TaskRepository,
    private val settings: SettingsRepository,
) : TaskListViewModel(repo) {
    private val selection = MutableStateFlow(Selection())

    val state: StateFlow<HomeState> = combine(repo.tasks, repo.categories, settings.settings, selection, clock(), ::buildState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    /** Picking a list also targets the add bar at it, as in the design. */
    fun pickFilter(categoryId: Long?) = selection.update {
        Selection(filter = categoryId, addCategory = categoryId ?: it.addCategory)
    }

    fun cycleAddCategory() {
        val cats = state.value.chips.mapNotNull { it.category }
        if (cats.isEmpty()) return
        val i = cats.indexOfFirst { it.id == state.value.addCategory.id }
        selection.update { it.copy(addCategory = cats[(i + 1) % cats.size].id) }
    }

    fun add(title: String) = quickAdd(title, LocalDate.now(), state.value.addCategory.id.takeIf { it > 0 })

    fun tapWater() {
        viewModelScope.launch { settings.tapWater() }
    }

    fun dismissBatteryTip() {
        viewModelScope.launch { settings.setBatteryTipSeen() }
    }
}
