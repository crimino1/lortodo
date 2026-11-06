package io.github.mecrimino.lor.ui.task

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mecrimino.lor.data.Priority
import io.github.mecrimino.lor.data.ReminderType
import io.github.mecrimino.lor.data.RepeatType
import io.github.mecrimino.lor.ui.components.BackHeader
import io.github.mecrimino.lor.ui.components.ConfirmDialog
import io.github.mecrimino.lor.ui.components.HeaderIconButton
import io.github.mecrimino.lor.ui.components.LorChip
import io.github.mecrimino.lor.ui.components.LorField
import io.github.mecrimino.lor.ui.components.LorIcon
import io.github.mecrimino.lor.ui.components.LorIcons
import io.github.mecrimino.lor.ui.components.PrimaryButton
import io.github.mecrimino.lor.ui.components.SectionLabel
import io.github.mecrimino.lor.ui.components.Segmented
import io.github.mecrimino.lor.ui.components.TaskCheck
import io.github.mecrimino.lor.ui.components.formatMinute
import io.github.mecrimino.lor.ui.theme.Accent
import io.github.mecrimino.lor.ui.theme.LocalLor
import io.github.mecrimino.lor.ui.theme.LorType
import io.github.mecrimino.lor.ui.theme.PriorityHigh
import io.github.mecrimino.lor.ui.theme.PriorityLow
import io.github.mecrimino.lor.ui.theme.PriorityMedium
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val WeekLetters = listOf("M", "T", "W", "T", "F", "S", "S")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskEditScreen(onBack: () -> Unit, vm: TaskEditViewModel = hiltViewModel()) {
    val c = LocalLor.current
    val context = LocalContext.current
    val s by vm.state.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var newSubtask by rememberSaveable { mutableStateOf("") }

    // Opened from a stale notification for a task that no longer exists.
    LaunchedEffect(s.loaded) { if (s.loaded && !vm.isNew && s.base == null) onBack() }

    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        BackHeader(if (vm.isNew) "New task" else "Edit task", onBack) {
            if (!vm.isNew) HeaderIconButton(LorIcons.trash, "Delete task") { confirmDelete = true }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Field("Title") {
                LorField(s.title, { t -> vm.edit { it.copy(title = t) } }, "What needs doing?")
            }
            Field("Notes") {
                LorField(s.notes, { t -> vm.edit { it.copy(notes = t) } }, "Optional", singleLine = false)
            }
            Field("Due") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickerButton(s.date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")), Modifier.weight(1.4f)) {
                        DatePickerDialog(
                            context,
                            { _, y, m, d -> vm.edit { it.copy(date = LocalDate.of(y, m + 1, d)) } },
                            s.date.year, s.date.monthValue - 1, s.date.dayOfMonth,
                        ).show()
                    }
                    PickerButton(s.minute?.let(::formatMinute) ?: "Any time", Modifier.weight(1f)) {
                        val start = s.minute ?: nextHourMinute()
                        TimePickerDialog(
                            context,
                            { _, h, m -> vm.edit { it.withMinute(h * 60 + m) } },
                            start / 60, start % 60, DateFormat.is24HourFormat(context),
                        ).show()
                    }
                    if (s.minute != null) HeaderIconButton(LorIcons.close, "Clear time") { vm.edit { it.withMinute(null) } }
                }
            }
            Field("Priority") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Priority.LOW to PriorityLow, Priority.MEDIUM to PriorityMedium, Priority.HIGH to PriorityHigh).forEach { (p, color) ->
                        PriorityOption(p.name.lowercase().replaceFirstChar(Char::uppercase), color, s.priority == p, Modifier.weight(1f)) {
                            vm.edit { it.copy(priority = p) }
                        }
                    }
                }
            }
            Field("List") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LorChip("None", s.categoryId == null, { vm.edit { it.copy(categoryId = null) } })
                    cats.forEach { cat ->
                        LorChip(
                            cat.name, s.categoryId == cat.id, { vm.edit { it.copy(categoryId = cat.id) } },
                            iconPath = LorIcons.category(cat.icon), iconColor = Color(cat.color),
                        )
                    }
                }
            }
            Field("Repeat") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RepeatType.entries.forEach { r ->
                        LorChip(
                            if (r == RepeatType.CUSTOM) "Custom days" else r.name.lowercase().replaceFirstChar(Char::uppercase),
                            s.repeat == r, { vm.edit { it.copy(repeat = r) } },
                        )
                    }
                }
                if (s.repeat == RepeatType.CUSTOM) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        WeekLetters.forEachIndexed { bit, letter ->
                            val on = s.repeatDays and (1 shl bit) != 0
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(if (on) c.ink else Color.Transparent)
                                    .border(1.dp, if (on) c.ink else c.line, CircleShape)
                                    .clickable(role = Role.Checkbox) { vm.edit { it.toggledDay(bit) } }
                                    .semantics { contentDescription = java.time.DayOfWeek.of(bit + 1).name.lowercase() },
                                contentAlignment = Alignment.Center,
                            ) { Text(letter, style = LorType.body(13, 500), color = if (on) c.inv else c.ink) }
                        }
                    }
                }
            }
            Field("Reminder") {
                Segmented(
                    listOf(ReminderType.NONE to "None", ReminderType.NOTIFICATION to "Notify", ReminderType.ALARM to "Alarm"),
                    s.reminder, { r -> vm.edit { it.withReminder(r) } }, Modifier.fillMaxWidth(),
                )
            }
            Field("Subtasks") {
                s.subtasks.forEachIndexed { i, sub ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TaskCheck(
                            sub.done, c.ink, (if (sub.done) "Mark not done: " else "Mark done: ") + sub.title,
                            { vm.edit { st -> st.copy(subtasks = st.subtasks.mapIndexed { j, x -> if (j == i) x.copy(done = !x.done) else x }) } },
                        )
                        Text(sub.title, style = LorType.body(15), color = if (sub.done) c.muted else c.ink, modifier = Modifier.weight(1f))
                        HeaderIconButton(LorIcons.close, "Remove ${sub.title}") {
                            vm.edit { st -> st.copy(subtasks = st.subtasks.filterIndexed { j, _ -> j != i }) }
                        }
                    }
                }
                fun commit() {
                    vm.addSubtask(newSubtask)
                    newSubtask = ""
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LorField(newSubtask, { newSubtask = it }, "Add a step", Modifier.weight(1f), onDone = ::commit)
                    HeaderIconButton(LorIcons.plus, "Add step", ::commit)
                }
            }
        }
        PrimaryButton(
            if (vm.isNew) "Add task" else "Save changes",
            { vm.save(onBack) },
            Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
            enabled = s.canSave,
        )
    }

    if (confirmDelete) {
        ConfirmDialog("Delete this task?", "This also removes its subtasks and reminders.", "Delete", { vm.delete(onBack) }, { confirmDelete = false })
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label)
        content()
    }
}

@Composable
private fun PickerButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalLor.current
    Box(
        modifier.height(46.dp).clip(RoundedCornerShape(12.dp)).background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) { Text(text, style = LorType.body(14, 500), color = c.ink, maxLines = 1) }
}

@Composable
private fun PriorityOption(text: String, color: Color, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalLor.current
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) c.tint(color) else Color.Transparent)
            .border(1.5.dp, if (selected) color else c.line, RoundedCornerShape(12.dp))
            .clickable(role = Role.RadioButton, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text, style = LorType.body(13, 500), color = c.ink, modifier = Modifier.padding(start = 6.dp))
    }
}
