package io.github.mecrimino.lor.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.mecrimino.lor.MainActivity
import io.github.mecrimino.lor.data.Task
import io.github.mecrimino.lor.data.TaskRepository
import java.time.LocalDate

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDeps {
    fun repository(): TaskRepository
}

private fun repo(context: Context) = EntryPointAccessors.fromApplication(context, WidgetDeps::class.java).repository()

private val Bg = ColorProvider(day = Color(0xFFF7F7F4), night = Color(0xFF0E0E0F))
private val Ink = ColorProvider(day = Color(0xFF131313), night = Color(0xFFF2F2EF))
private val Muted = ColorProvider(day = Color(0xFF66665F), night = Color(0xFF9A9A96))
private val AccentColor = ColorProvider(day = Color(0xFFFF4F1A), night = Color(0xFFFF4F1A))

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val tasks = repo(context).tasksOn(LocalDate.now())
        provideContent {
            val today by tasks.collectAsState(emptyList())
            Content(today)
        }
    }

    @Composable
    private fun Content(tasks: List<Task>) {
        val (done, open) = tasks.partition { it.isDone }
        Column(GlanceModifier.fillMaxSize().background(Bg).cornerRadius(22.dp).padding(14.dp)) {
            Row(
                GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("lor", style = TextStyle(color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp))
                Text(".", style = TextStyle(color = AccentColor, fontWeight = FontWeight.Bold, fontSize = 18.sp))
                Spacer(GlanceModifier.defaultWeight())
                Text("${done.size} of ${tasks.size} done", style = TextStyle(color = Muted, fontSize = 12.sp))
            }
            Spacer(GlanceModifier.height(6.dp))
            if (tasks.isEmpty()) {
                Text("All clear.", style = TextStyle(color = Muted, fontSize = 14.sp))
            } else {
                LazyColumn {
                    items(open + done, itemId = { it.id }) { task ->
                        CheckBox(
                            checked = task.isDone,
                            onCheckedChange = actionRunCallback<ToggleTaskAction>(actionParametersOf(TaskIdKey to task.id)),
                            text = task.title,
                            style = TextStyle(color = if (task.isDone) Muted else Ink, fontSize = 14.sp),
                            colors = CheckboxDefaults.colors(checkedColor = AccentColor, uncheckedColor = Muted),
                            maxLines = 1,
                            modifier = GlanceModifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private val TaskIdKey = ActionParameters.Key<Long>("task_id")

class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskIdKey] ?: return
        val repo = repo(context)
        repo.task(id)?.let { repo.toggle(it) }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TodayWidget()
}
