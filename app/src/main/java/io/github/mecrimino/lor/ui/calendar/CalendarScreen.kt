package io.github.mecrimino.lor.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mecrimino.lor.data.Category
import io.github.mecrimino.lor.data.Task
import io.github.mecrimino.lor.data.TaskRepository
import io.github.mecrimino.lor.ui.TaskListViewModel
import io.github.mecrimino.lor.ui.components.CircleIconButton
import io.github.mecrimino.lor.ui.components.EmptyState
import io.github.mecrimino.lor.ui.components.LorIcon
import io.github.mecrimino.lor.ui.components.LorIcons
import io.github.mecrimino.lor.ui.components.QuickAddBar
import io.github.mecrimino.lor.ui.components.TaskCheck
import io.github.mecrimino.lor.ui.components.formatTime
import io.github.mecrimino.lor.ui.of
import io.github.mecrimino.lor.ui.theme.Accent
import io.github.mecrimino.lor.ui.theme.LocalLor
import io.github.mecrimino.lor.ui.theme.LorType
import java.time.LocalDate
import java.time.YearMonth
import io.github.mecrimino.lor.ui.components.Fmt
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CalendarState(
    val month: YearMonth = YearMonth.now(),
    val selected: LocalDate = LocalDate.now(),
    val byDay: Map<LocalDate, List<Task>> = emptyMap(),
    val categories: Map<Long, Category> = emptyMap(),
    val addCategory: Category = Category.Other,
)

/** Monday-first grid cells for a month; null = blank cell outside the month. */
fun monthCells(month: YearMonth): List<LocalDate?> {
    val lead = month.atDay(1).dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val rows = (lead + days + 6) / 7
    return List(rows * 7) { i -> (i - lead + 1).takeIf { it in 1..days }?.let(month::atDay) }
}

@HiltViewModel
class CalendarViewModel @Inject constructor(repo: TaskRepository) : TaskListViewModel(repo) {
    private val month = MutableStateFlow(YearMonth.now())
    private val selected = MutableStateFlow(LocalDate.now())
    private val addCategoryId = MutableStateFlow<Long?>(null)

    val state: StateFlow<CalendarState> = combine(month, selected, repo.tasks, repo.categories, addCategoryId) { m, sel, tasks, cats, add ->
        val byId = cats.associateBy { it.id }
        CalendarState(m, sel, tasks.groupBy { it.dueDate }, byId, byId[add] ?: cats.firstOrNull() ?: Category.Other)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState())

    fun shiftMonth(by: Long) {
        val m = state.value.month.plusMonths(by)
        month.value = m
        selected.value = m.atDay(1)
    }

    fun select(day: LocalDate) {
        selected.value = day
    }

    fun cycleAddCategory() {
        val cats = state.value.categories.values.toList()
        if (cats.isEmpty()) return
        val i = cats.indexOfFirst { it.id == state.value.addCategory.id }
        addCategoryId.value = cats[(i + 1) % cats.size].id
    }

    fun add(title: String) = quickAdd(title, state.value.selected, state.value.addCategory.id.takeIf { it > 0 })
}

@Composable
fun CalendarScreen(onOpenTask: (Long) -> Unit, vm: CalendarViewModel = hiltViewModel()) {
    val c = LocalLor.current
    val s by vm.state.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    val today = LocalDate.now()
    val agenda = s.byDay[s.selected].orEmpty()
    val selShort = s.selected.format(Fmt.dayShort)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item("grid") {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.month.format(Fmt.month), style = LorType.month, color = c.ink, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CircleIconButton(LorIcons.prev, "Previous month", { vm.shiftMonth(-1) })
                        CircleIconButton(LorIcons.next, "Next month", { vm.shiftMonth(1) })
                    }
                }
                Row(Modifier.padding(top = 16.dp)) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                        Text(it, style = LorType.label, color = c.muted, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    monthCells(s.month).chunked(7).forEach { week ->
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            week.forEach { day ->
                                Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                                    if (day != null) DayCell(day, s, today, vm::select)
                                }
                            }
                        }
                    }
                }
            }
        }
        item("selected") {
            HorizontalDivider(Modifier.padding(top = 12.dp), color = c.line)
            Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    if (s.selected == today) "Today" else s.selected.format(Fmt.dayLong),
                    style = LorType.h2, color = c.ink, modifier = Modifier.weight(1f),
                )
                Text(if (agenda.size == 1) "1 task" else "${agenda.size} tasks", style = LorType.body(12, 500), color = c.muted)
            }
            QuickAddBar(
                category = s.addCategory,
                placeholder = "Add to ${s.addCategory.name} on $selShort…",
                draft = draft,
                onDraft = { draft = it },
                onCycle = vm::cycleAddCategory,
                onAdd = {
                    vm.add(draft)
                    draft = ""
                },
                height = 48.dp,
                radius = 14.dp,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 4.dp),
            )
        }
        items(agenda, key = { it.id }) { t ->
            AgendaRow(t, s.categories.of(t), { vm.toggle(t) }, { onOpenTask(t.id) })
        }
        if (agenda.isEmpty()) {
            item("empty") { EmptyState("A free day.", "Add something above, or keep it open.") }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, s: CalendarState, today: LocalDate, onPick: (LocalDate) -> Unit) {
    val c = LocalLor.current
    val tasks = s.byDay[day].orEmpty()
    val isSel = day == s.selected
    val isToday = day == today
    val dots = tasks.map { s.categories.of(it).color }.distinct().take(3)
    Column(
        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (isSel) c.ink else Color.Transparent)
            .border(1.5.dp, if (isToday && !isSel) Accent else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button) { onPick(day) }
            .semantics(mergeDescendants = true) {
                contentDescription = day.format(Fmt.dayFull) + if (tasks.isEmpty()) "" else ", ${tasks.size} tasks"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Text(
            "${day.dayOfMonth}",
            style = LorType.body(14).copy(fontWeight = if (isSel || isToday) FontWeight(600) else FontWeight(500)),
            color = if (isSel) c.inv else if (day.isBefore(today)) c.muted else c.ink,
        )
        Row(Modifier.height(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            dots.forEach { Box(Modifier.size(4.dp).clip(CircleShape).background(if (isSel) c.inv else Color(it))) }
        }
    }
}

@Composable
private fun AgendaRow(task: Task, category: Category, onToggle: () -> Unit, onOpen: () -> Unit) {
    val c = LocalLor.current
    val color = Color(category.color)
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(formatTime(task).ifEmpty { "Any time" }, style = LorType.body(11, 500), color = c.muted, modifier = Modifier.width(54.dp))
        Row(
            Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).background(c.tint(color))
                .clickable(onClick = onOpen).padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(c.surface), contentAlignment = Alignment.Center) {
                LorIcon(LorIcons.category(category.icon), color, 16.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    task.title,
                    style = LorType.body(14, 500).copy(textDecoration = if (task.isDone) TextDecoration.LineThrough else null),
                    color = if (task.isDone) c.muted else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(category.name, style = LorType.body(11), color = c.muted)
            }
            TaskCheck(task.isDone, color, (if (task.isDone) "Mark not done: " else "Mark done: ") + task.title, onToggle)
        }
    }
    Spacer(Modifier.height(0.dp))
}
