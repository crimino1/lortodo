package io.github.mecrimino.lor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.Image
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mecrimino.lor.data.Category
import io.github.mecrimino.lor.data.Priority
import io.github.mecrimino.lor.data.Task
import io.github.mecrimino.lor.ui.theme.Accent
import io.github.mecrimino.lor.ui.theme.LocalLor
import io.github.mecrimino.lor.ui.theme.LorType
import io.github.mecrimino.lor.ui.theme.PriorityHigh
import io.github.mecrimino.lor.ui.theme.PriorityLow
import io.github.mecrimino.lor.ui.theme.PriorityMedium
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// ---- Icons: the design's own 24x24 stroke paths ----

object LorIcons {
    val categories = linkedMapOf(
        "work" to "M4 8h16v11H4zM9 8V5h6v3M4 13h16",
        "study" to "M2 9l10-5 10 5-10 5zM6 11v5c0 1.5 3 3 6 3s6-1.5 6-3v-5",
        "fitness" to "M6 7v10M3 9.5v5M18 7v10M21 9.5v5M6 12h12",
        "food" to "M7 3v8M5 3v5a2 2 0 0 0 4 0V3M7 11v10M17 3c-2 0-3 3-3 6s1 3 3 3v9",
        "water" to "M12 3c3.5 4.5 6 7.5 6 11a6 6 0 0 1-12 0c0-3.5 2.5-6.5 6-11z",
        "person" to "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21c0-4 4-6 8-6s8 2 8 6",
        "moon" to "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z",
        "cart" to "M5 7h14l-1.5 12h-11zM9 7a3 3 0 0 1 6 0",
        "heart" to "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z",
        "star" to "M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z",
    )
    const val tabToday = "M9 6h11M9 12h11M9 18h11M3.5 6l1 1 2-2M3.5 12l1 1 2-2M3.5 18l1 1 2-2"
    const val tabCalendar = "M6.5 5h11a3 3 0 0 1 3 3v9a3 3 0 0 1-3 3h-11a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3zM3.5 10h17M8 3v4M16 3v4"
    const val tabLists = "M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z"
    const val plus = "M12 5v14M5 12h14"
    const val minus = "M5 12h14"
    const val check = "m5 12.5 4.5 4.5L19 7.5"
    const val prev = "m15 6-6 6 6 6"
    const val next = "m9 6 6 6-6 6"
    // Not in the mockup; drawn to the same 24-unit stroke grid.
    const val search = "M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM20 20l-4-4"
    const val settings = "M4 7h10M18 7h2M4 17h2M10 17h10M16 5v4M8 15v4"
    const val close = "M6 6l12 12M18 6L6 18"
    const val trash = "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"
    const val bell = "M6 16V11a6 6 0 0 1 12 0v5l2 2H4zM10 21h4"
    const val history = "M12 7v5l3 2M4 12a8 8 0 1 0 2.5-5.8L4 8.5M4 4v4.5h4.5"
    const val chart = "M5 20V11M12 20V4M19 20v-6"
    fun category(key: String) = categories[key] ?: categories.getValue("star")
}

@Composable
fun LorIcon(path: String, color: Color, size: Dp = 16.dp, strokeWidth: Float = 2f, modifier: Modifier = Modifier) {
    val vector = remember(path, color, strokeWidth) {
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = addPathNodes(path),
                stroke = SolidColor(color),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
    }
    Image(rememberVectorPainter(vector), contentDescription = null, modifier = modifier.size(size))
}

// ---- Formatting ----

/** Built once: parsing a pattern on every recomposition (42 calendar cells, every row) is wasted work while scrolling. */
object Fmt {
    val time: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val dateLong: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
    val headerDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
    val dayShort: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
    val dayLong: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMM")
    val dayFull: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")
    val month: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
}

val categoryColor: (Category) -> Color = { Color(it.color) }

fun formatMinute(minute: Int): String =
    java.time.LocalTime.of(minute / 60, minute % 60).format(Fmt.time)

fun formatTime(task: Task): String = task.dueMinute?.let(::formatMinute) ?: ""

/** "10:00 AM", "Today", or "Mon 2 Nov · 10:00 AM" for other days. */
fun formatDue(task: Task, today: LocalDate = LocalDate.now()): String {
    val day = if (task.dueDate == today) "" else task.dueDate.format(Fmt.dayShort)
    return listOf(day, formatTime(task)).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "Today" }
}

// ---- Layout pieces ----

@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text("lor", style = LorType.wordmark, color = LocalLor.current.ink)
        Text(".", style = LorType.wordmark, color = Accent)
    }
}

/** Top bar: wordmark, optional actions, date pill. */
@Composable
fun LorHeader(actions: @Composable RowScope.() -> Unit = {}) {
    val c = LocalLor.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Wordmark()
        Spacer(Modifier.weight(1f))
        actions()
        Box(
            Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(c.surface)
                .border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                LocalDate.now().format(Fmt.headerDate),
                style = LorType.body(12, 500), color = c.muted,
            )
        }
    }
}

/** Sub-screen top bar: circular back button and title, in the style of the calendar's month arrows. */
@Composable
fun BackHeader(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    val c = LocalLor.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(LorIcons.prev, "Back", onBack)
        Text(title, style = LorType.month, color = c.ink, modifier = Modifier.padding(start = 12.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun CircleIconButton(path: String, label: String, onClick: () -> Unit, bg: Color = LocalLor.current.surface) {
    val c = LocalLor.current
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(bg).border(1.dp, c.line, CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { LorIcon(path, c.ink, 16.dp) }
}

/** Borderless 44dp icon button for header actions. */
@Composable
fun HeaderIconButton(path: String, label: String, onClick: () -> Unit) {
    val c = LocalLor.current
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { LorIcon(path, c.muted, 20.dp) }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = LocalLor.current.muted) {
    Text(text.uppercase(), style = LorType.label, color = color, modifier = modifier)
}

/** The design's rounded surface card. */
@Composable
fun LorCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = LocalLor.current
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(22.dp)),
    ) { content() }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = LocalLor.current
    Box(
        modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
            .background(if (enabled) c.ink else c.line)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = LorType.body(15, 600), color = if (enabled) c.inv else c.muted) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = LocalLor.current.ink) {
    val c = LocalLor.current
    Box(
        modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
            .border(1.dp, c.line, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = LorType.body(15, 600), color = color) }
}

/** The design's "Name" input. */
@Composable
fun LorField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minHeight: Dp = 46.dp,
    onDone: (() -> Unit)? = null,
) {
    val c = LocalLor.current
    Box(
        modifier.fillMaxWidth().height(if (singleLine) minHeight else minHeight * 2).clip(RoundedCornerShape(12.dp))
            .background(c.bg).border(1.dp, c.line, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = if (singleLine) 0.dp else 12.dp),
        contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
    ) {
        BareField(value, onChange, placeholder, LorType.body(15), singleLine, onDone)
    }
}

@Composable
private fun BareField(
    value: String, onChange: (String) -> Unit, placeholder: String, style: TextStyle, singleLine: Boolean, onDone: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = LocalLor.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = style.copy(color = c.ink),
        cursorBrush = SolidColor(c.ink),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = placeholder },
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, style = style, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            inner()
        },
    )
}

/** The design's add-to-list bar: tap the tile to cycle lists, type, tap + or Enter. */
@Composable
fun QuickAddBar(
    category: Category,
    placeholder: String,
    draft: String,
    onDraft: (String) -> Unit,
    onCycle: () -> Unit,
    onAdd: () -> Unit,
    height: Dp = 52.dp,
    radius: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    val c = LocalLor.current
    val cat = categoryColor(category)
    Row(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(radius)).background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(radius)).padding(horizontal = (height - 40.dp) / 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.tint(cat))
                .clickable(role = Role.Button, onClick = onCycle)
                .semantics { contentDescription = "List: ${category.name}. Tap to switch list." },
            contentAlignment = Alignment.Center,
        ) { LorIcon(LorIcons.category(category.icon), cat, 18.dp) }
        BareField(draft, onDraft, placeholder, LorType.body(15), true, onAdd, Modifier.weight(1f))
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.ink)
                .clickable(role = Role.Button, onClick = onAdd)
                .semantics { contentDescription = "Add task" },
            contentAlignment = Alignment.Center,
        ) { LorIcon(LorIcons.plus, c.inv, 18.dp) }
    }
}

@Composable
fun LorChip(
    text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    count: Int? = null, iconPath: String? = null, iconColor: Color = LocalLor.current.ink,
) {
    val c = LocalLor.current
    Row(
        modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp)).background(if (selected) c.ink else Color.Transparent)
            .border(1.dp, if (selected) c.ink else c.line, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (iconPath != null) LorIcon(iconPath, iconColor, 14.dp)
        val fg = if (selected) c.inv else c.ink
        Text(text, style = LorType.body(13, 500), color = fg, maxLines = 1)
        if (count != null) Text("$count", style = LorType.body(13, 500), color = fg.copy(alpha = .55f))
    }
}

/** The design's Light/Dark switch, generalised to any options. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalLor.current
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(3.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.weight(1f, fill = true).height(38.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (on) c.ink else Color.Transparent)
                    .clickable(role = Role.RadioButton, onClick = { onSelect(value) }).padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = LorType.body(13, 500), color = if (on) c.inv else c.ink, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun StepperRow(title: String, subtitle: String, onDown: () -> Unit, onUp: () -> Unit, modifier: Modifier = Modifier, leading: @Composable (() -> Unit)? = null) {
    val c = LocalLor.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = LorType.body(14, 600), color = c.ink)
            Text(subtitle, style = LorType.body(12), color = c.muted)
        }
        CircleIconButton(LorIcons.minus, "Lower $title", onDown, c.bg)
        CircleIconButton(LorIcons.plus, "Raise $title", onUp, c.bg)
    }
}

// ---- Rings and checks ----

@Composable
fun ProgressRing(fraction: Float, color: Color, iconPath: String, modifier: Modifier = Modifier) {
    val c = LocalLor.current
    Box(modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // viewBox 40: r=16, stroke 4, scaled to this canvas
            val unit = size.minDimension / 40f
            val stroke = Stroke(width = 4 * unit, cap = StrokeCap.Round)
            val d = 32 * unit
            val topLeft = Offset(4 * unit, 4 * unit)
            drawArc(c.line, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(4 * unit))
            if (fraction > 0f) drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, topLeft, Size(d, d), style = stroke)
        }
        LorIcon(iconPath, color, 14.dp)
    }
}

/** 44dp target with the design's 22dp ring / filled check. */
@Composable
fun TaskCheck(done: Boolean, color: Color, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(44.dp).clickable(role = Role.Checkbox, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                LorIcon(LorIcons.check, Color.White, 12.dp, strokeWidth = 3f)
            }
        } else {
            Box(Modifier.size(22.dp).border(BorderStroke(2.dp, color), CircleShape))
        }
    }
}

// ---- Task rows ----

@Composable
fun PriorityBadge(priority: Priority) {
    val c = LocalLor.current
    when (priority) {
        Priority.LOW -> Box(Modifier.size(6.dp).clip(CircleShape).background(PriorityLow).semantics { contentDescription = "Low priority" })
        Priority.MEDIUM, Priority.HIGH -> {
            val color = if (priority == Priority.HIGH) PriorityHigh else PriorityMedium
            Box(
                Modifier.heightIn(min = 22.dp).clip(RoundedCornerShape(11.dp)).background(c.tint(color)).padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (priority == Priority.HIGH) "High" else "Medium",
                    style = LorType.body(11, 600), color = if (c.isDark) color else c.ink,
                )
            }
        }
    }
}

/** Full-bleed row: the 44dp check overhangs the 24dp page margin so the ring itself aligns with it, as in the design. */
@Composable
fun TaskRow(
    task: Task,
    category: Category,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    overdue: Boolean = false,
    showDate: Boolean = false,
) {
    val c = LocalLor.current
    val color = categoryColor(category)
    Row(
        modifier.fillMaxWidth().background(if (overdue) c.tint(Accent).compositeOver(c.bg) else c.bg)
            .clickable(onClick = onOpen).hairline(c.line).heightIn(min = 62.dp).padding(start = 13.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskCheck(false, if (overdue) Accent else color, "Mark done: ${task.title}", onToggle)
        Column(Modifier.weight(1f).padding(start = 4.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(task.title, style = LorType.body(15, 500), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val time = if (overdue || showDate) formatDue(task) else formatTime(task)
            Text(
                listOf(time, category.name).filter { it.isNotEmpty() }.joinToString(" · "),
                style = LorType.body(12), color = if (overdue) Accent else c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        PriorityBadge(task.priority)
        Spacer(Modifier.width(12.dp))
        CategoryTile(category, 32.dp, 16.dp, 10.dp)
    }
}

@Composable
fun CategoryTile(category: Category, size: Dp, icon: Dp, radius: Dp, modifier: Modifier = Modifier) {
    val c = LocalLor.current
    val color = categoryColor(category)
    Box(modifier.size(size).clip(RoundedCornerShape(radius)).background(c.tint(color)), contentAlignment = Alignment.Center) {
        LorIcon(LorIcons.category(category.icon), color, icon)
    }
}

@Composable
fun DoneRow(task: Task, category: Category, onToggle: () -> Unit, modifier: Modifier = Modifier, onOpen: (() -> Unit)? = null, trailing: String? = null) {
    val c = LocalLor.current
    Row(
        modifier.fillMaxWidth().background(c.bg).then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .hairline(c.line).heightIn(min = 52.dp).padding(start = 13.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskCheck(true, categoryColor(category), "Mark not done: ${task.title}", onToggle)
        Text(
            task.title, style = LorType.body(15).copy(textDecoration = TextDecoration.LineThrough), color = c.muted,
            modifier = Modifier.weight(1f).padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) Text(trailing, style = LorType.body(12), color = c.muted)
    }
}

/** The 1dp divider under list rows, inset to the page margin. */
private fun Modifier.hairline(color: Color) = drawBehind {
    val margin = 24.dp.toPx()
    drawLine(color, Offset(margin, size.height - 0.5f), Offset(size.width - margin, size.height - 0.5f), 1.dp.toPx())
}

/** Swipe right = done, swipe left = delete, per spec. */
@Composable
fun SwipeableRow(onDone: () -> Unit, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val c = LocalLor.current
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val toDone = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                Modifier.fillMaxSize().background(c.tint(if (toDone) Color(0xFF1FA971) else Accent)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toDone) Arrangement.Start else Arrangement.End,
            ) {
                LorIcon(if (toDone) LorIcons.check else LorIcons.trash, if (toDone) Color(0xFF1FA971) else Accent, 20.dp)
            }
        },
        onDismiss = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onDone()
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
    ) { content() }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalLor.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, style = LorType.h2, color = c.ink) },
        text = { Text(text, style = LorType.body(14), color = c.muted) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm, color = Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.ink) } },
    )
}

@Composable
fun EmptyState(title: String, text: String, modifier: Modifier = Modifier) {
    val c = LocalLor.current
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = LorType.h2, color = c.ink)
        Text(text, style = LorType.body(13), color = c.muted, modifier = Modifier.padding(top = 4.dp), textAlign = TextAlign.Center)
    }
}
