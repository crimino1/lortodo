package io.github.mecrimino.lor.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mecrimino.lor.MainActivity
import io.github.mecrimino.lor.R
import io.github.mecrimino.lor.data.Task
import io.github.mecrimino.lor.ui.alarm.AlarmActivity
import io.github.mecrimino.lor.ui.components.formatDue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Notifier @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val manager get() = NotificationManagerCompat.from(context)

    fun createChannels() {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannels(
            listOf(
                NotificationChannel(CH_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Task reminders and repeat nudges"
                },
                NotificationChannel(CH_ALARMS, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Full-screen task alarms. The sound comes from the alarm itself."
                    setSound(null, null)
                    enableVibration(false)
                },
                NotificationChannel(CH_SUMMARY, "Daily summary", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Morning and evening overview of your tasks"
                },
            )
        )
    }

    /** True when the system will honour a full-screen intent. Always on before Android 14. */
    fun canFullScreen(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun showReminder(task: Task) {
        val nag = task.firedCount > 1
        post(
            notificationId(task.id),
            NotificationCompat.Builder(context, CH_REMINDERS)
                .setSmallIcon(R.drawable.ic_stat_lor)
                .setContentTitle(task.title)
                .setContentText(reminderText(task, nag))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(openTask(task.id))
                .setAutoCancel(true)
                .addAction(0, "Done", action(ActionReceiver.ACTION_DONE, task.id))
                .addAction(0, "Snooze 10 min", action(ActionReceiver.ACTION_SNOOZE, task.id, 10))
                .addAction(0, "Snooze 1 hr", action(ActionReceiver.ACTION_SNOOZE, task.id, 60))
                .build()
        )
    }

    /** The ringing alarm's foreground notification. Without full-screen access it is a plain heads-up. */
    fun alarmNotification(task: Task): Notification {
        val screen = PendingIntent.getActivity(
            context, task.id.toInt(), AlarmActivity.intent(context, task.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CH_ALARMS)
            .setSmallIcon(R.drawable.ic_stat_lor)
            .setContentTitle(task.title)
            .setContentText(reminderText(task, nag = false))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(screen)
            .apply { if (canFullScreen()) setFullScreenIntent(screen, true) }
            .addAction(0, "Done", action(ActionReceiver.ACTION_DONE, task.id))
            .addAction(0, "Snooze", action(ActionReceiver.ACTION_SNOOZE, task.id, AlarmActivity.SNOOZE_MIN))
            .build()
    }

    fun showSummary(kind: Int, title: String, text: String) {
        post(
            SUMMARY_ID_BASE - kind,
            NotificationCompat.Builder(context, CH_SUMMARY)
                .setSmallIcon(R.drawable.ic_stat_lor)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(
                    PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
                )
                .setAutoCancel(true)
                .build()
        )
    }

    fun cancel(taskId: Long) = manager.cancel(notificationId(taskId))

    private fun reminderText(task: Task, nag: Boolean): String {
        val due = formatDue(task)
        return when {
            nag -> "Still not done · due $due"
            task.notes.isNotBlank() -> task.notes.lineSequence().first()
            else -> "Due $due"
        }
    }

    @SuppressLint("MissingPermission") // checked just below; without it the reminder silently can't show
    private fun post(id: Int, notification: Notification) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) manager.notify(id, notification)
    }

    private fun openTask(id: Long) = PendingIntent.getActivity(
        context, id.toInt(),
        Intent(context, MainActivity::class.java).putExtra(ReminderScheduler.EXTRA_TASK_ID, id).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(action: String, id: Long, minutes: Int = 0) = PendingIntent.getBroadcast(
        context, (id * 100 + minutes).toInt() + action.hashCode(),
        Intent(context, ActionReceiver::class.java).setAction(action)
            .putExtra(ReminderScheduler.EXTRA_TASK_ID, id)
            .putExtra(ActionReceiver.EXTRA_MINUTES, minutes),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CH_REMINDERS = "reminders"
        const val CH_ALARMS = "alarms"
        const val CH_SUMMARY = "daily_summary"
        private const val SUMMARY_ID_BASE = -1
        fun notificationId(taskId: Long) = taskId.toInt()
    }
}
