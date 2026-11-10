package io.github.mecrimino.lor.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mecrimino.lor.data.Category
import io.github.mecrimino.lor.data.Priority
import io.github.mecrimino.lor.data.Task
import io.github.mecrimino.lor.data.TaskRepository
import io.github.mecrimino.lor.data.TaskStatus
import io.github.mecrimino.lor.ui.TaskListViewModel
import io.github.mecrimino.lor.ui.UndoSnackbars
import io.github.mecrimino.lor.ui.components.BackHeader
import io.github.mecrimino.lor.ui.components.DoneRow
import io.github.mecrimino.lor.ui.components.EmptyState
import io.github.mecrimino.lor.ui.components.LorChip
import io.github.mecrimino.lor.ui.components.LorField
import io.github.mecrimino.lor.ui.components.LorIcons
import io.github.mecrimino.lor.ui.components.SectionLabel
import io.github.mecrimino.lor.ui.components.Segmented
import io.github.mecrimino.lor.ui.components.SwipeableRow
import io.github.mecrimino.lor.ui.components.TaskRow
import io.github.mecrimino.lor.ui.of
import io.github.mecrimino.lor.ui.theme.Accent
import io.github.mecrimino.lor.ui.theme.LocalLor
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class SortBy { DUE, PRIORITY, CREATED }

data class SearchQuery(
    val text: String = "",
    val categoryId: Long? = null,
    val priority: Priority? = null,
    val status: TaskStatus? = null,
    val sort: SortBy = SortBy.DUE,
)

fun applyQuery(tasks: List<Task>, q: SearchQuery): List<Task> {
    val text = q.text.trim()
    val matches = tasks.filter {
        (text.isEmpty() || it.title.contains(text, ignoreCase = true) || it.notes.contains(text, ignoreCase = true)) &&
            (q.categoryId == null || it.categoryId == q.categoryId) &&
            (q.priority == null || it.priority == q.priority) &&
            (q.status == null || it.status == q.status)
    }
    val byDue = compareBy<Task>({ it.dueEpochDay }, { it.dueMinute ?: Int.MAX_VALUE })
    return when (q.sort) {
        SortBy.DUE -> matches.sortedWith(byDue)
        SortBy.PRIORITY -> matches.sortedWith(compareByDescending<Task> { it.priority }.then(byDue))
        SortBy.CREATED -> matches.sortedByDescending { it.createdAt }
    }
}

data class SearchState(
    val query: SearchQuery = SearchQuery(),
    val results: List<Task> = emptyList(),
    val categories: List<Category> = emptyList(),
) {
    val byId = categories.associateBy { it.id }
}

@HiltViewModel
class SearchViewModel @Inject constructor(repo: TaskRepository) : TaskListViewModel(repo) {
    /** Compose state so the search box types without lag; results follow a frame later. */
    var query by mutableStateOf(SearchQuery())
        private set

    val state: StateFlow<SearchState> = combine(repo.tasks, repo.categories, snapshotFlow { query }) { tasks, cats, q ->
        SearchState(q, applyQuery(tasks, q), cats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState())

    fun update(change: (SearchQuery) -> SearchQuery) {
        query = change(query)
    }
}

@Composable
fun SearchScreen(onBack: () -> Unit, onOpenTask: (Long) -> Unit, vm: SearchViewModel = hiltViewModel()) {
    val c = LocalLor.current
    val s by vm.state.collectAsStateWithLifecycle()
    val q = vm.query
    val snackbars = remember { SnackbarHostState() }
    UndoSnackbars(vm, snackbars)

    Box(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        Column(Modifier.fillMaxSize()) {
            BackHeader("Search", onBack)
            LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item("controls") {
                    Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LorField(q.text, { t -> vm.update { it.copy(text = t) } }, "Search title and notes", Modifier.padding(horizontal = 24.dp))
                        FilterRow {
                            items(s.categories, key = { it.id }) { cat ->
                                LorChip(
                                    cat.name, q.categoryId == cat.id,
                                    { vm.update { it.copy(categoryId = if (it.categoryId == cat.id) null else cat.id) } },
                                    iconPath = LorIcons.category(cat.icon), iconColor = Color(cat.color),
                                )
                            }
                        }
                        FilterRow {
                            items(Priority.entries.reversed()) { p ->
                                LorChip(
                                    p.name.lowercase().replaceFirstChar(Char::uppercase), q.priority == p,
                                    { vm.update { it.copy(priority = if (it.priority == p) null else p) } },
                                )
                            }
                            items(TaskStatus.entries) { st ->
                                LorChip(
                                    st.name.lowercase().replaceFirstChar(Char::uppercase), q.status == st,
                                    { vm.update { it.copy(status = if (it.status == st) null else st) } },
                                )
                            }
                        }
                        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SectionLabel("Sort by")
                            Segmented(
                                listOf(SortBy.DUE to "Due date", SortBy.PRIORITY to "Priority", SortBy.CREATED to "Created"),
                                q.sort, { sort -> vm.update { it.copy(sort = sort) } }, Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                items(s.results, key = { it.id }) { t ->
                    if (t.isDone) {
                        DoneRow(t, s.byId.of(t), onToggle = { vm.toggle(t) }, onOpen = { onOpenTask(t.id) })
                    } else {
                        SwipeableRow(onDone = { vm.swipeDone(t) }, onDelete = { vm.swipeDelete(t) }) {
                            TaskRow(t, s.byId.of(t), onToggle = { vm.toggle(t) }, onOpen = { onOpenTask(t.id) }, showDate = true)
                        }
                    }
                }
                if (s.results.isEmpty()) item("empty") { EmptyState("Nothing found.", "Try fewer filters or another word.") }
            }
        }
        SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) { data ->
            Snackbar(data, containerColor = c.ink, contentColor = c.inv, actionColor = Accent, shape = RoundedCornerShape(14.dp))
        }
    }
}

@Composable
private fun FilterRow(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}
