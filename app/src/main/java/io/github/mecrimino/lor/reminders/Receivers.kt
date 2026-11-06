package io.github.mecrimino.lor.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.github.mecrimino.lor.data.SettingsRepository
import io.github.mecrimino.lor.data.TaskRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun BroadcastReceiver.launchAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** A task's alarm went off: ring or notify, then arm the next nag. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var repo: TaskRepository
    @Inject lateinit var notifier: Notifier

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1)
        launchAsync {
            val fired = repo.onReminderFired(id) ?: return@launchAsync
            if (!fired.ring || !AlarmService.start(context, id)) notifier.showReminder(fired.task)
        }
    }
}

/** Notification buttons and the alarm screen's buttons. */
@AndroidEntryPoint
class ActionReceiver : BroadcastReceiver() {
    @Inject lateinit var repo: TaskRepository

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1)
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 10)
        launchAsync {
            when (intent.action) {
                ACTION_DONE -> repo.complete(id)
                ACTION_SNOOZE -> repo.snooze(id, minutes)
            }
        }
    }

    companion object {
        const val ACTION_DONE = "io.github.mecrimino.lor.DONE"
        const val ACTION_SNOOZE = "io.github.mecrimino.lor.SNOOZE"
        const val EXTRA_MINUTES = "minutes"
    }
}

@AndroidEntryPoint
class SummaryReceiver : BroadcastReceiver() {
    @Inject lateinit var repo: TaskRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getIntExtra(EXTRA_KIND, MORNING)
        launchAsync {
            val s = settings.current()
            val count = repo.pendingCount(LocalDate.now())
            if (s.summaryOn && count > 0) {
                val tasks = if (count == 1) "1 task" else "$count tasks"
                if (kind == MORNING) notifier.showSummary(kind, "Good morning", "You have $tasks today, ${s.name}")
                else notifier.showSummary(kind, "Evening check-in", "$tasks still pending")
            }
            scheduler.syncSummaries()
        }
    }

    companion object {
        const val MORNING = 1
        const val EVENING = 2
        const val EXTRA_KIND = "kind"
    }
}

/** AlarmManager forgets everything on reboot and app update; time changes and permission changes also need a re-arm. */
@AndroidEntryPoint
class RescheduleReceiver : BroadcastReceiver() {
    @Inject lateinit var repo: TaskRepository

    override fun onReceive(context: Context, intent: Intent) = launchAsync { repo.rescheduleAll() }
}
