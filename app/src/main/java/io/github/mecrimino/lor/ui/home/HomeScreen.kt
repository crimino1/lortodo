package io.github.mecrimino.lor.ui.home

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mecrimino.lor.data.Task
import io.github.mecrimino.lor.ui.UndoSnackbars
import io.github.mecrimino.lor.ui.components.DoneRow
import io.github.mecrimino.lor.ui.components.EmptyState
import io.github.mecrimino.lor.ui.components.LorCard
import io.github.mecrimino.lor.ui.components.LorChip
import io.github.mecrimino.lor.ui.components.LorIcons
import io.github.mecrimino.lor.ui.components.PrimaryButton
import io.github.mecrimino.lor.ui.components.ProgressRing
import io.github.mecrimino.lor.ui.components.QuickAddBar
import io.github.mecrimino.lor.ui.components.SecondaryButton
import io.github.mecrimino.lor.ui.components.SectionLabel
import io.github.mecrimino.lor.ui.components.SwipeableRow
import io.github.mecrimino.lor.ui.components.TaskRow
import io.github.mecrimino.lor.ui.of
import io.github.mecrimino.lor.ui.theme.Accent
import io.github.mecrimino.lor.ui.theme.LocalLor
import io.github.mecrimino.lor.ui.theme.LorType

private fun needsBatteryTip(context: Context): Boolean =
    !context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

@Composable
fun HomeScreen(onOpenTask: (Long) -> Unit, snackbars: SnackbarHostState, vm: HomeViewModel = hiltViewModel()) {
    val c = LocalLor.current
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var draft by rememberSaveable { mutableStateOf("") }
    UndoSnackbars(vm, snackbars)

    fun LazyListScope.section(title: String, tasks: List<Task>, keyPrefix: String, overdue: Boolean = false, showDate: Boolean = false) {
        if (tasks.isEmpty()) return
        item("$keyPrefix-label") {
            SectionLabel(
                "$title · ${tasks.size}",
                Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 2.dp),
                if (overdue) Accent else c.muted,
            )
        }
        items(tasks, key = { "$keyPrefix-${it.id}" }) { t ->
            SwipeableRow(onDone = { vm.swipeDone(t) }, onDelete = { vm.swipeDelete(t) }) {
                TaskRow(
                    t, s.categories.of(t), onToggle = { vm.toggle(t) }, onOpen = { onOpenTask(t.id) },
                    overdue = overdue, showDate = showDate,
                )
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item("head") {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s.greeting, style = LorType.body(13, 500), color = c.muted)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Text("Today", style = LorType.h1, color = c.ink, modifier = Modifier.weight(1f))
                        Text("${s.doneCount} of ${s.total} done", style = LorType.body(13, 500), color = c.muted, modifier = Modifier.padding(bottom = 3.dp))
                    }
                }
                val progress by animateFloatAsState(if (s.total == 0) 0f else s.doneCount.toFloat() / s.total, tween(350))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.line)) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c.ink))
                }
            }
        }
        item("rings") {
            Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val goalHit = s.water >= s.waterGoal
                RingCard(
                    fraction = s.water.toFloat() / s.waterGoal,
                    color = Color(s.waterCategory.color),
                    icon = LorIcons.category(s.waterCategory.icon),
                    value = "${s.water}/${s.waterGoal}",
                    label = if (goalHit) "Goal hit" else "Glasses",
                    description = if (goalHit) "Water goal reached. Tap to reset." else "Log a glass of water. ${s.water} of ${s.waterGoal} so far.",
                    onClick = vm::tapWater,
                    modifier = Modifier.weight(1f),
                )
                s.rings.forEach { r ->
                    RingCard(
                        fraction = if (r.total == 0) 0f else r.done.toFloat() / r.total,
                        color = Color(r.category.color),
                        icon = LorIcons.category(r.category.icon),
                        value = "${r.done}/${r.total}",
                        label = r.category.name,
                        description = "${r.category.name}: ${r.done} of ${r.total} done. Show this list.",
                        onClick = { vm.pickFilter(r.category.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item("chips") {
            LazyRow(
                Modifier.padding(top = 18.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(s.chips, key = { it.category?.id ?: 0L }) { chip ->
                    val cat = chip.category
                    LorChip(
                        text = cat?.name ?: "All",
                        selected = s.filter == cat?.id,
                        onClick = { vm.pickFilter(cat?.id) },
                        count = chip.pending,
                        iconPath = cat?.let { LorIcons.category(it.icon) },
                        iconColor = cat?.let { Color(it.color) } ?: c.ink,
                    )
                }
            }
        }
        item("add") {
            QuickAddBar(
                category = s.addCategory,
                placeholder = "Add to ${s.addCategory.name}…",
                draft = draft,
                onDraft = { draft = it },
                onCycle = vm::cycleAddCategory,
                onAdd = {
                    vm.add(draft)
                    draft = ""
                },
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp),
            )
        }
        if (s.showBatteryTip && needsBatteryTip(context)) {
            item("battery-tip") { BatteryTip(vm::dismissBatteryTip) }
        }

        section("Overdue", s.overdue, "overdue", overdue = true)
        items(s.today, key = { "today-${it.id}" }) { t ->
            SwipeableRow(onDone = { vm.swipeDone(t) }, onDelete = { vm.swipeDelete(t) }) {
                TaskRow(t, s.categories.of(t), onToggle = { vm.toggle(t) }, onOpen = { onOpenTask(t.id) })
            }
        }
        if (s.loaded && s.overdue.isEmpty() && s.today.isEmpty()) {
            item("empty") { EmptyState("All clear.", "Nothing left in this list today.") }
        }
        if (s.completed.isNotEmpty()) {
            item("done-label") {
                SectionLabel("Completed · ${s.completed.size}", Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 2.dp))
            }
            items(s.completed, key = { "done-${it.id}" }) { t ->
                DoneRow(t, s.categories.of(t), onToggle = { vm.toggle(t) }, onOpen = { onOpenTask(t.id) })
            }
        }
        section("Upcoming", s.upcoming, "upcoming", showDate = true)
    }
}

@Composable
private fun RingCard(
    fraction: Float, color: Color, icon: String, value: String, label: String, description: String,
    onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    val c = LocalLor.current
    val animated by animateFloatAsState(fraction, tween(350))
    Column(
        modifier.heightIn(min = 92.dp).clip(RoundedCornerShape(18.dp)).background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) { contentDescription = description }
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        ProgressRing(animated, color, icon)
        Column {
            // Tight line heights: the design's 92dp card only just holds ring, value and label.
            Text(value, style = LorType.ringValue.copy(lineHeight = 19.sp), color = c.ink, maxLines = 1)
            Text(label, style = LorType.body(11, 500).copy(lineHeight = 13.sp), color = c.muted, maxLines = 1)
        }
    }
}

/** One-time tip: some vendors kill background alarms unless battery optimisation is off for the app. */
@Composable
private fun BatteryTip(onDismiss: () -> Unit) {
    val c = LocalLor.current
    val context = LocalContext.current
    LorCard(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Keep reminders on time", style = LorType.body(14, 600), color = c.ink)
            Text(
                "Phones from Xiaomi, Oppo, Vivo and Realme often stop background alarms. Turn off battery optimisation for LOR so they still ring.",
                style = LorType.body(13), color = c.muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    "Open settings",
                    {
                        onDismiss()
                        runCatching { context.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    },
                    Modifier.weight(1f),
                )
                SecondaryButton("Got it", onDismiss, Modifier.weight(1f))
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}
