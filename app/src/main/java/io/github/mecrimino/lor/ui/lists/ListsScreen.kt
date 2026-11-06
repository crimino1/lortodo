package io.github.mecrimino.lor.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mecrimino.lor.data.Category
import io.github.mecrimino.lor.data.LorDatabase
import io.github.mecrimino.lor.data.Settings
import io.github.mecrimino.lor.data.SettingsRepository
import io.github.mecrimino.lor.data.TaskRepository
import io.github.mecrimino.lor.data.ThemeMode
import io.github.mecrimino.lor.ui.components.CategoryTile
import io.github.mecrimino.lor.ui.components.ConfirmDialog
import io.github.mecrimino.lor.ui.components.LorCard
import io.github.mecrimino.lor.ui.components.LorField
import io.github.mecrimino.lor.ui.components.LorIcon
import io.github.mecrimino.lor.ui.components.LorIcons
import io.github.mecrimino.lor.ui.components.PrimaryButton
import io.github.mecrimino.lor.ui.components.SecondaryButton
import io.github.mecrimino.lor.ui.components.SectionLabel
import io.github.mecrimino.lor.ui.components.Segmented
import io.github.mecrimino.lor.ui.components.StepperRow
import io.github.mecrimino.lor.ui.theme.Accent
import io.github.mecrimino.lor.ui.theme.LocalLor
import io.github.mecrimino.lor.ui.theme.LorType
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The design's 8 list colours. */
val Palette = listOf(0xFF2F6BFF, 0xFF7C5CFF, 0xFFFF6A2B, 0xFF1FA971, 0xFF0EA5C6, 0xFFE2468F, 0xFFE8A317, 0xFF5B6472).map { it.toInt() }

/** id == 0 means creating a new list. */
data class ListForm(val id: Long = 0, val name: String = "", val color: Int = 0xFFE8A317.toInt(), val icon: String = "star") {
    val previewName get() = name.trim().ifEmpty { "New list" }
}

data class ListCard(val category: Category, val open: Int)

data class ListsState(
    val cards: List<ListCard> = emptyList(),
    val form: ListForm = ListForm(),
    val settings: Settings = Settings(),
)

@HiltViewModel
class ListsViewModel @Inject constructor(
    private val repo: TaskRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val form = MutableStateFlow<ListForm?>(null)

    val state: StateFlow<ListsState> = combine(repo.categories, repo.tasks, form, settings.settings) { cats, tasks, f, s ->
        val today = LocalDate.now()
        ListsState(
            cards = cats.map { c -> ListCard(c, tasks.count { it.categoryId == c.id && !it.isDone && !it.dueDate.isBefore(today) }) },
            // Opens on Exercise, like the design, until the user picks something.
            form = f ?: (cats.firstOrNull { it.id == LorDatabase.EXERCISE_ID } ?: cats.firstOrNull())?.let(::formOf) ?: ListForm(),
            settings = s,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListsState())

    private fun formOf(c: Category) = ListForm(c.id, c.name, c.color, c.icon)

    fun edit(c: Category) {
        form.value = formOf(c)
    }

    fun newList() {
        form.value = ListForm()
    }

    fun update(change: (ListForm) -> ListForm) {
        form.update { change(it ?: state.value.form) }
    }

    fun save() {
        val f = state.value.form
        val name = f.name.trim().ifEmpty { "Untitled" }
        viewModelScope.launch {
            val id = repo.saveCategory(Category(f.id, name, f.color, f.icon))
            form.value = f.copy(id = id, name = name)
        }
    }

    fun delete() {
        val id = state.value.form.id
        if (id == 0L) return
        viewModelScope.launch {
            repo.deleteCategory(id)
            form.value = ListForm()
        }
    }

    fun setWaterGoal(goal: Int) {
        viewModelScope.launch { settings.setWaterGoal(goal) }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settings.setTheme(mode) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListsScreen(onHistory: () -> Unit, onStats: () -> Unit, vm: ListsViewModel = hiltViewModel()) {
    val c = LocalLor.current
    val s by vm.state.collectAsStateWithLifecycle()
    val f = s.form
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Lists", style = LorType.h1, color = c.ink)
        Text("Shape Lor around your day. Tap a list to edit it.", style = LorType.body(13), color = c.muted, modifier = Modifier.padding(top = 8.dp))

        // Three-column grid; the last cell is "New list".
        Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val cells: List<ListCard?> = s.cards + null
            cells.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { card ->
                        if (card != null) CatCard(card, f.id == card.category.id, Modifier.weight(1f)) { vm.edit(card.category) }
                        else NewListCard(f.id == 0L, Modifier.weight(1f), vm::newList)
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        LorCard(Modifier.padding(top = 16.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color(f.color)), contentAlignment = Alignment.Center) {
                        LorIcon(LorIcons.category(f.icon), Color.White, 22.dp)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(f.previewName, style = LorType.body(16, 600), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (f.id != 0L) "Editing list · changes apply everywhere" else "Pick a name, colour and icon",
                            style = LorType.body(12), color = c.muted,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("Name")
                    LorField(f.name, { n -> vm.update { it.copy(name = n.take(30)) } }, "e.g. Reading")
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Colour")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Palette.forEach { color ->
                            val on = color == f.color
                            Box(
                                Modifier.size(28.dp)
                                    // The design's selection ring sits outside the swatch, with a 2dp surface gap.
                                    .drawBehind { if (on) drawCircle(Color(color), size.minDimension / 2 + 3.dp.toPx(), style = Stroke(2.dp.toPx())) }
                                    .clip(CircleShape).background(Color(color))
                                    .clickable(role = Role.RadioButton) { vm.update { it.copy(color = color) } }
                                    .semantics { contentDescription = "Colour ${"#%06X".format(color and 0xFFFFFF)}" },
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Icon")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LorIcons.categories.entries.chunked(5).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (key, path) ->
                                    val on = key == f.icon
                                    Box(
                                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (on) c.ink else c.bg)
                                            .border(1.dp, if (on) c.ink else c.line, RoundedCornerShape(12.dp))
                                            .clickable(role = Role.RadioButton) { vm.update { it.copy(icon = key) } }
                                            .semantics { contentDescription = "Icon $key" },
                                        contentAlignment = Alignment.Center,
                                    ) { LorIcon(path, if (on) c.inv else c.ink, 18.dp) }
                                }
                            }
                        }
                    }
                }
                PrimaryButton(if (f.id != 0L) "Save changes" else "Create list", vm::save)
                if (f.id != 0L) SecondaryButton("Delete list", { confirmDelete = true }, color = Accent)
            }
        }

        LorCard(Modifier.padding(top = 12.dp)) {
            val waterColor = Color(0xFF0EA5C6)
            StepperRow(
                "Water goal", "${s.settings.waterGoal} glasses a day",
                { vm.setWaterGoal(s.settings.waterGoal - 1) }, { vm.setWaterGoal(s.settings.waterGoal + 1) },
                Modifier.padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                leading = {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(c.tint(waterColor)), contentAlignment = Alignment.Center) {
                        LorIcon(LorIcons.category("water"), waterColor, 17.dp)
                    }
                },
            )
        }

        LorCard(Modifier.padding(top = 12.dp)) {
            Column(Modifier.padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Appearance", style = LorType.body(14, 600), color = c.ink)
                Segmented(
                    listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"),
                    s.settings.theme, vm::setTheme, Modifier.fillMaxWidth(),
                )
            }
        }

        LorCard(Modifier.padding(top = 12.dp)) {
            Column {
                LinkRow(LorIcons.history, "Completed tasks", onHistory)
                LinkRow(LorIcons.chart, "Stats", onStats)
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            "Delete ${f.previewName}?", "Its tasks stay, moved to Other.", "Delete",
            vm::delete, { confirmDelete = false },
        )
    }
}

@Composable
private fun CatCard(card: ListCard, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalLor.current
    Column(
        modifier.heightIn(min = 96.dp).clip(RoundedCornerShape(18.dp)).background(c.surface)
            .border(1.5.dp, if (selected) c.ink else c.line, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Edit ${card.category.name} list" }
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        CategoryTile(card.category, 34.dp, 17.dp, 11.dp)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(card.category.name, style = LorType.body(14, 600), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${card.open} open", style = LorType.body(11), color = c.muted)
        }
    }
}

@Composable
private fun NewListCard(selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalLor.current
    val border = if (selected) c.ink else c.line
    Column(
        modifier.heightIn(min = 96.dp).clip(RoundedCornerShape(18.dp))
            .drawBehind {
                val radius = 18.dp.toPx()
                drawRoundRect(
                    border, cornerRadius = CornerRadius(radius),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .clickable(role = Role.Button, onClickLabel = "Create a new list", onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).border(1.dp, c.line, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            LorIcon(LorIcons.plus, c.ink, 16.dp)
        }
        Text("New list", style = LorType.body(14, 600), color = c.ink)
    }
}

@Composable
fun LinkRow(icon: String, title: String, onClick: () -> Unit, subtitle: String? = null) {
    val c = LocalLor.current
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LorIcon(icon, c.muted, 18.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = LorType.body(14, 600), color = c.ink)
            if (subtitle != null) Text(subtitle, style = LorType.body(12), color = c.muted)
        }
        LorIcon(LorIcons.next, c.muted, 14.dp)
    }
}
