package io.github.mecrimino.lor.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mecrimino.lor.MainActivity
import io.github.mecrimino.lor.data.ReminderType
import io.github.mecrimino.lor.data.SettingsRepository
import io.github.mecrimino.lor.data.Task
import io.github.mecrimino.lor.data.isRingFire
import io.github.mecrimino.lor.data.nextTrigger
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** All AlarmManager access. One armed alarm per task (keyed by id), so re-syncing just moves it. */
@Singleton
class ReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    suspend fun sync(task: Task) {
        val s = settings.current()
        val at = nextTrigger(task, s.nagIntervalMin, s.nagMax)
        if (at == null) cancel(task.id)
        else arm(at, taskIntent(task.id), alarmClock = task.reminder == ReminderType.ALARM && task.isRingFire)
    }

    fun cancel(id: Long) = alarms.cancel(taskIntent(id))

    suspend fun syncSummaries() {
        val s = settings.current()
        listOf(SummaryReceiver.MORNING to s.morningMin, SummaryReceiver.EVENING to s.eveningMin).forEach { (kind, minute) ->
            val intent = summaryIntent(kind)
            if (s.summaryOn) arm(nextDailyAt(minute, LocalDateTime.now()), intent, alarmClock = false)
            else alarms.cancel(intent)
        }
    }

    private fun arm(at: Long, intent: PendingIntent, alarmClock: Boolean) {
        try {
            when {
                !canScheduleExact() -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
                alarmClock -> alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, openAppIntent()), intent)
                else -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            }
        } catch (_: SecurityException) { // permission revoked between the check and the call
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    private fun taskIntent(id: Long) = PendingIntent.getBroadcast(
        context, id.toInt(),
        Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_TASK_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun summaryIntent(kind: Int) = PendingIntent.getBroadcast(
        context, kind,
        Intent(context, SummaryReceiver::class.java).putExtra(SummaryReceiver.EXTRA_KIND, kind),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openAppIntent() = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val EXTRA_TASK_ID = "task_id"

        /** Next occurrence of a minute-of-day strictly after [now], in epoch millis. */
        fun nextDailyAt(minute: Int, now: LocalDateTime): Long {
            val today = now.toLocalDate().atTime(minute / 60, minute % 60)
            val next = if (today.isAfter(now)) today else today.plusDays(1)
            return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }
}
