package io.github.mecrimino.lor.ui.alarm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import io.github.mecrimino.lor.data.Task
import io.github.mecrimino.lor.data.TaskRepository
import io.github.mecrimino.lor.data.ThemeMode
import io.github.mecrimino.lor.reminders.ActionReceiver
import io.github.mecrimino.lor.reminders.AlarmService
import io.github.mecrimino.lor.reminders.ReminderScheduler
import io.github.mecrimino.lor.ui.components.PrimaryButton
import io.github.mecrimino.lor.ui.components.SecondaryButton
import io.github.mecrimino.lor.ui.components.Wordmark
import io.github.mecrimino.lor.ui.components.formatDue
import io.github.mecrimino.lor.ui.theme.LocalLor
import io.github.mecrimino.lor.ui.theme.LorTheme
import io.github.mecrimino.lor.ui.theme.LorType
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Full-screen ringing screen, shown over the lock screen. Sound lives in [AlarmService]; this is only the UI. */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {
    @Inject lateinit var repo: TaskRepository

    private var taskId by mutableLongStateOf(-1L)
    private var task by mutableStateOf<Task?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) { // manifest showWhenLocked/turnScreenOn need API 27
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1)
        setContent {
            LorTheme(ThemeMode.DARK) {
                BackHandler { /* an alarm is answered with a button, not dismissed by accident */ }
                LaunchedEffect(taskId) {
                    task = repo.task(taskId)
                    AlarmService.ringingTask.first { it != taskId } // answered elsewhere, or timed out
                    finish()
                }
                val c = LocalLor.current
                Column(
                    Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Wordmark()
                    Spacer(Modifier.weight(1f))
                    task?.let {
                        Text(formatDue(it), style = LorType.body(15, 500), color = c.muted)
                        Text(it.title, style = LorType.h1, color = c.ink)
                        if (it.notes.isNotBlank()) Text(it.notes, style = LorType.body(15), color = c.muted)
                    }
                    Spacer(Modifier.weight(1f))
                    PrimaryButton("Done", { send(ActionReceiver.ACTION_DONE) })
                    SecondaryButton("Snooze $SNOOZE_MIN min", { send(ActionReceiver.ACTION_SNOOZE) })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1)
    }

    private fun send(action: String) {
        sendBroadcast(
            Intent(this, ActionReceiver::class.java).setAction(action)
                .putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId)
                .putExtra(ActionReceiver.EXTRA_MINUTES, SNOOZE_MIN)
        )
        finish()
    }

    companion object {
        const val SNOOZE_MIN = 10

        fun intent(context: Context, id: Long) = Intent(context, AlarmActivity::class.java)
            .putExtra(ReminderScheduler.EXTRA_TASK_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
