package io.github.mecrimino.lor.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mecrimino.lor.data.Category
import io.github.mecrimino.lor.data.Task
import io.github.mecrimino.lor.data.TaskRepository
import io.github.mecrimino.lor.data.currentStreak
import io.github.mecrimino.lor.data.startOfWeek
import io.github.mecrimino.lor.ui.TaskListViewModel
import io.github.mecrimino.lor.ui.components.BackHeader
import io.github.mecrimino.lor.ui.components.ConfirmDialog
import io.github.mecrimino.lor.ui.components.DoneRow
import io.github.mecrimino.lor.ui.components.EmptyState
import io.github.mecrimino.lor.ui.components.LorCard
import io.github.mecrimino.lor.ui.components.SectionLabel
import io.github.mecrimino.lor.ui.of
import io.github.mecrimino.lor.ui.theme.Accent
import io.github.mecrimino.lor.ui.theme.LocalLor
import io.github.mecrimino.lor.ui.theme.LorType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import io.github.mecrimino.lor.ui.components.Fmt
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private fun Task.completedDay(): LocalDate? =
    completedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }

data class HistoryState(
    val byDay: Map<LocalDate, List<Task>> = emptyMap(),
    val categories: Map<Long, Category> = emptyMap(),
    val completedThisWeek: Int = 0,
    val streak: Int = 0,
    val total: Int = 0,
)

fun buildHistory(tasks: List<Task>, cats: List<Category>, today: LocalDate): HistoryState {
    val done = tasks.filter { it.isDone && it.completedAt != null }.sortedByDescending { it.completedAt }
    val days = done.mapNotNull { it.completedDay() }
    val weekStart = startOfWeek(today)
    return HistoryState(
        byDay = done.groupBy { it.completedDay()!! },
        categories = cats.associateBy { it.id },
        completedThisWeek = days.count { !it.isBefore(weekStart) },
        streak = currentStreak(days.toSet(), today),
        total = done.size,
    )
}

@HiltViewModel
class HistoryViewModel @Inject constructor(repo: TaskRepository) : TaskListViewModel(repo) {
    val state: StateFlow<HistoryState> = combine(repo.tasks, repo.categories) { tasks, cats ->
        buildHistory(tasks, cats, LocalDate.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    fun clear() {
        viewModelScope.launch { repo.clearHistory() }
    }
}

@Composable
fun HistoryScreen(onBack: () -> Unit, vm: HistoryViewModel = hiltViewModel()) {
    val c = LocalLor.current
    val s by vm.state.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        BackHeader("Completed", onBack) {
            if (s.total > 0) {
                Text(
                    "Clear", style = LorType.body(14, 600), color = Accent,
                    modifier = Modifier.clickable(role = Role.Button) { confirm = true }.padding(12.dp),
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            s.byDay.forEach { (day, tasks) ->
                item("label-$day") {
                    SectionLabel(
                        "${day.format(Fmt.dayShort)} · ${tasks.size}",
                        Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 2.dp),
                    )
                }
                items(tasks, key = { it.id }) { t ->
                    DoneRow(t, s.categories.of(t), onToggle = { vm.toggle(t) })
                }
            }
            if (s.total == 0) item("empty") { EmptyState("Nothing here yet.", "Tasks you finish will show up here.") }
        }
    }

    if (confirm) {
        ConfirmDialog(
            "Clear completed tasks?", "They'll be deleted for good, and your stats and streak start over.", "Clear",
            vm::clear, { confirm = false },
        )
    }
}

@Composable
fun StatsScreen(onBack: () -> Unit, vm: HistoryViewModel = hiltViewModel()) {
    val c = LocalLor.current
    val s by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(c.bg)) {
        BackHeader("Stats", onBack)
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("${s.completedThisWeek}", "Done this week", Modifier.weight(1f))
                StatTile(if (s.streak == 1) "1 day" else "${s.streak} days", "Current streak", Modifier.weight(1f))
            }
            StatTile("${s.total}", "Done all time", Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    val c = LocalLor.current
    LorCard(modifier) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.Start) {
            Text(value, style = LorType.month, color = c.ink)
            Text(label, style = LorType.body(12, 500), color = c.muted)
        }
    }
}
