package io.github.mecrimino.lor.ui.settings

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mecrimino.lor.BuildConfig
import io.github.mecrimino.lor.data.BackupManager
import io.github.mecrimino.lor.data.ReminderType
import io.github.mecrimino.lor.data.Settings
import io.github.mecrimino.lor.data.SettingsRepository
import io.github.mecrimino.lor.data.TaskRepository
import io.github.mecrimino.lor.data.ThemeMode
import io.github.mecrimino.lor.reminders.Notifier
import io.github.mecrimino.lor.reminders.ReminderScheduler
import io.github.mecrimino.lor.ui.components.BackHeader
import io.github.mecrimino.lor.ui.components.ConfirmDialog
import io.github.mecrimino.lor.ui.components.LorCard
import io.github.mecrimino.lor.ui.components.LorField
import io.github.mecrimino.lor.ui.components.LorIcons
import io.github.mecrimino.lor.ui.components.SecondaryButton
import io.github.mecrimino.lor.ui.components.SectionLabel
import io.github.mecrimino.lor.ui.components.Segmented
import io.github.mecrimino.lor.ui.components.StepperRow
import io.github.mecrimino.lor.ui.components.formatMinute
import io.github.mecrimino.lor.ui.lists.LinkRow
import io.github.mecrimino.lor.ui.theme.Accent
import io.github.mecrimino.lor.ui.theme.LocalLor
import io.github.mecrimino.lor.ui.theme.LorType
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val repo: TaskRepository,
    private val backup: BackupManager,
    val scheduler: ReminderScheduler,
    val notifier: Notifier,
) : ViewModel() {
    val state: StateFlow<Settings> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun setName(v: String) = launch { settings.setName(v) }
    fun setTheme(v: ThemeMode) = launch { settings.setTheme(v) }
    fun setDefaultReminder(v: ReminderType) = launch { settings.setDefaultReminder(v) }
    fun setAlarmSound(v: String?) = launch { settings.setAlarmSound(v) }
    fun setVibrate(v: Boolean) = launch { settings.setVibrate(v) }

    /** Nag changes affect alarms already armed. */
    fun setNagInterval(v: Int) = launch {
        settings.setNagInterval(v)
        repo.rescheduleAll()
    }

    fun setNagMax(v: Int) = launch {
        settings.setNagMax(v)
        repo.rescheduleAll()
    }

    fun setSummaryOn(v: Boolean) = launch {
        settings.setSummaryOn(v)
        scheduler.syncSummaries()
    }

    fun setMorning(v: Int) = launch {
        settings.setMorning(v)
        scheduler.syncSummaries()
    }

    fun setEvening(v: Int) = launch {
        settings.setEvening(v)
        scheduler.syncSummaries()
    }

    fun export(uri: Uri) = launch {
        _messages.tryEmit(
            runCatching { backup.export(uri) }.fold({ "Backup saved" }, { "Backup failed: ${it.message}" })
        )
    }

    fun import(uri: Uri) = launch {
        _messages.tryEmit(
            runCatching { backup.import(uri) }.fold({ "Backup restored" }, { "Restore failed, nothing changed: ${it.message}" })
        )
    }

    fun deleteAll() = launch {
        repo.deleteAll()
        settings.clear()
    }
}

private fun Context.openSetting(action: String, withPackageUri: Boolean = false, extra: Pair<String, String>? = null) {
    val intent = Intent(action).apply {
        if (withPackageUri) data = Uri.parse("package:$packageName")
        extra?.let { putExtra(it.first, it.second) }
    }
    runCatching { startActivity(intent) }
}

private fun ringtoneTitle(context: Context, uri: String?): String =
    uri?.let { runCatching { RingtoneManager.getRingtone(context, it.toUri())?.getTitle(context) }.getOrNull() } ?: "Default alarm"

@Composable
fun SettingsScreen(onBack: () -> Unit, onHistory: () -> Unit, onStats: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val c = LocalLor.current
    val context = LocalContext.current
    val s by vm.state.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var resumes by remember { mutableIntStateOf(0) } // re-check system permissions when returning from Settings
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbars.showSnackbar(it) } }

    val pickRingtone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            vm.setAlarmSound(uri?.toString())
        }
    }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::export)
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingRestore = uri
    }

    Box(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        Column(Modifier.fillMaxSize()) {
            BackHeader("Settings", onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Keyed on resumes so these re-evaluate after a trip to system settings.
                val problems = remember(resumes) {
                    buildList {
                        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) add(
                            Problem("Notifications are off", "Reminders can't show until you allow them.") {
                                context.openSetting(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS, extra = AndroidSettings.EXTRA_APP_PACKAGE to context.packageName)
                            }
                        )
                        if (!vm.scheduler.canScheduleExact() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(
                            Problem("Exact alarms are off", "Reminders may arrive late. Turn on \"Alarms & reminders\".") {
                                context.openSetting(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, withPackageUri = true)
                            }
                        )
                        if (!vm.notifier.canFullScreen() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) add(
                            Problem("Full-screen alarms are off", "Alarms show as a banner instead of over the lock screen. Tap to allow them.") {
                                context.openSetting(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, withPackageUri = true)
                            }
                        )
                    }
                }
                problems.forEach { p ->
                    LorCard(Modifier.clickable(role = Role.Button, onClick = p.fix)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(p.title, style = LorType.body(14, 600), color = Accent)
                            Text(p.detail, style = LorType.body(12), color = c.muted)
                        }
                    }
                }

                Card("You") {
                    // Local draft: the stored value is trimmed, which would eat the space while typing "Ann Lee".
                    var nameDraft by rememberSaveable { mutableStateOf<String?>(null) }
                    LorField(
                        nameDraft ?: s.name,
                        {
                            nameDraft = it.take(40)
                            vm.setName(it.take(40))
                        },
                        "Your name",
                    )
                }
                Card("Theme") {
                    Segmented(
                        listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"),
                        s.theme, vm::setTheme, Modifier.fillMaxWidth(),
                    )
                }
                Card("Reminders") {
                    Text("Default reminder for new tasks", style = LorType.body(14, 600), color = c.ink)
                    Segmented(
                        listOf(ReminderType.NONE to "None", ReminderType.NOTIFICATION to "Notify", ReminderType.ALARM to "Alarm"),
                        s.defaultReminder, vm::setDefaultReminder, Modifier.fillMaxWidth(),
                    )
                    StepperRow(
                        "Repeat every", "${s.nagIntervalMin} minutes until done",
                        { vm.setNagInterval(s.nagIntervalMin - 5) }, { vm.setNagInterval(s.nagIntervalMin + 5) },
                    )
                    StepperRow(
                        "Repeat up to", if (s.nagMax == 0) "Off" else "${s.nagMax} times",
                        { vm.setNagMax(s.nagMax - 1) }, { vm.setNagMax(s.nagMax + 1) },
                    )
                }
                Card("Alarm") {
                    val title = remember(s.alarmSound) { ringtoneTitle(context, s.alarmSound) }
                    SettingRow("Alarm sound", title) {
                        pickRingtone.launch(
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, s.alarmSound?.toUri())
                        )
                    }
                    SwitchRow("Vibrate", s.vibrate, vm::setVibrate)
                }
                Card("Daily summary") {
                    SwitchRow("Morning and evening summary", s.summaryOn, vm::setSummaryOn)
                    if (s.summaryOn) {
                        SettingRow("Morning", formatMinute(s.morningMin)) { pickTime(context, s.morningMin, vm::setMorning) }
                        SettingRow("Evening", formatMinute(s.eveningMin)) { pickTime(context, s.eveningMin, vm::setEvening) }
                    }
                }
                LorCard {
                    Column {
                        LinkRow(LorIcons.history, "Completed tasks", onHistory)
                        LinkRow(LorIcons.chart, "Stats", onStats)
                    }
                }
                Card("Your data") {
                    SecondaryButton("Export to a file", { createBackup.launch("lor-backup-${LocalDate.now()}.json") })
                    SecondaryButton("Restore from a file", { openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
                    SecondaryButton("Delete all data", { confirmDelete = true }, color = Accent)
                }
                Card("About") {
                    Text("LOR ${BuildConfig.VERSION_NAME}", style = LorType.body(14, 600), color = c.ink)
                    Text("No data ever leaves your device.", style = LorType.body(13), color = c.muted)
                    Text("Free software under the GNU GPL-3.0 licence.", style = LorType.body(13), color = c.muted)
                    Text(
                        BuildConfig.SOURCE_URL, style = LorType.body(13, 500), color = c.ink,
                        modifier = Modifier.clickable(role = Role.Button) {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, BuildConfig.SOURCE_URL.toUri())) }
                        },
                    )
                }
            }
        }
        SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) { data ->
            Snackbar(data, containerColor = c.ink, contentColor = c.inv, shape = RoundedCornerShape(14.dp))
        }
    }

    pendingRestore?.let { uri ->
        ConfirmDialog(
            "Restore this backup?", "Everything in LOR now will be replaced by what's in the file.", "Restore",
            { vm.import(uri) }, { pendingRestore = null },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete everything?", "All tasks, lists and settings will be gone. This can't be undone.", "Delete",
            vm::deleteAll, { confirmDelete = false },
        )
    }
}

private class Problem(val title: String, val detail: String, val fix: () -> Unit)

private fun pickTime(context: Context, minute: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, minute / 60, minute % 60, DateFormat.is24HourFormat(context)).show()
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    LorCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel(title)
            content()
        }
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    val c = LocalLor.current
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = LorType.body(14, 600), color = c.ink, modifier = Modifier.weight(1f))
        Text(value, style = LorType.body(13), color = c.muted)
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalLor.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = LorType.body(14, 600), color = c.ink, modifier = Modifier.weight(1f))
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.inv, checkedTrackColor = c.ink,
                uncheckedThumbColor = c.muted, uncheckedTrackColor = c.bg, uncheckedBorderColor = c.line,
            ),
        )
    }
}
