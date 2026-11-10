package io.github.mecrimino.lor.reminders

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.mecrimino.lor.data.SettingsRepository
import io.github.mecrimino.lor.data.Task
import io.github.mecrimino.lor.data.TaskRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Rings and vibrates in the foreground until the task is done or snoozed, or [RING_LIMIT_MS] passes. */
@AndroidEntryPoint
class AlarmService : Service() {
    @Inject lateinit var repo: TaskRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var notifier: Notifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var ringJob: Job? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1) ?: -1
        val previous = ringing.value
        ringing.value = id
        stopRinging()
        // startForeground must run before anything suspends, or the system kills the app. The title follows once loaded.
        showForeground(Task(id = id.coerceAtLeast(1), title = "Alarm", dueEpochDay = LocalDate.now().toEpochDay()))
        ringJob = scope.launch {
            val task = repo.task(id)
            // Another alarm was still ringing: it moves to a normal notification instead of vanishing.
            if (previous != NOT_RINGING && previous != id) repo.task(previous)?.takeUnless { it.isDone }?.let(notifier::showReminder)
            if (task == null || task.isDone) {
                stopSelf()
                return@launch
            }
            showForeground(task)
            val s = settings.current()
            play(s.alarmSound)
            if (s.vibrate) vibrate()
            delay(RING_LIMIT_MS)
            // Nobody answered: swap to a normal notification; nags stay armed. Leaving the foreground
            // first, because the system removes whatever notification the service still owns when it stops.
            ServiceCompat.stopForeground(this@AlarmService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            notifier.showReminder(task)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun showForeground(task: Task) {
        ServiceCompat.startForeground(
            this, Notifier.notificationId(task.id), notifier.alarmNotification(task),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
    }

    private fun play(chosen: String?) {
        val candidates = listOfNotNull(
            chosen?.let(Uri::parse),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        // A missing or unreadable sound (deleted file, restored URI) must never silence an alarm: try the next.
        for (uri in candidates) {
            val p = MediaPlayer()
            try {
                p.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
                p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                p.setDataSource(this, uri)
                p.isLooping = true
                p.prepare()
                p.start()
                player = p
                return
            } catch (_: Exception) {
                p.release()
            }
        }
    }

    @Suppress("DEPRECATION") // VibratorManager/VibrationAttributes need API 31/33; this overload still works everywhere
    private fun vibrate() {
        getSystemService(Vibrator::class.java)?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
        )
    }

    private fun stopRinging() {
        ringJob?.cancel()
        player?.run {
            runCatching { stop() }
            release()
        }
        player = null
        getSystemService(Vibrator::class.java)?.cancel()
    }

    override fun onDestroy() {
        stopRinging()
        scope.cancel()
        ringing.value = NOT_RINGING
        super.onDestroy()
    }

    companion object {
        private const val RING_LIMIT_MS = 5 * 60_000L
        const val NOT_RINGING = -1L
        private val ringing = MutableStateFlow(NOT_RINGING)
        val ringingTask: StateFlow<Long> = ringing

        /** False if Android refused the background start; the caller falls back to a notification. */
        fun start(context: Context, id: Long): Boolean = try {
            ContextCompat.startForegroundService(
                context, Intent(context, AlarmService::class.java).putExtra(ReminderScheduler.EXTRA_TASK_ID, id)
            )
            true
        } catch (_: IllegalStateException) { // ForegroundServiceStartNotAllowedException
            false
        }

        /** Silences only if this is the task ringing; finishing another task mustn't stop someone's alarm. */
        fun stopIfRinging(context: Context, id: Long) {
            if (ringing.value == id) context.stopService(Intent(context, AlarmService::class.java))
        }
    }
}
