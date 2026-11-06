package io.github.mecrimino.lor.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mecrimino.lor.data.SettingsRepository
import io.github.mecrimino.lor.reminders.ReminderScheduler
import io.github.mecrimino.lor.ui.components.LorField
import io.github.mecrimino.lor.ui.components.PrimaryButton
import io.github.mecrimino.lor.ui.components.SecondaryButton
import io.github.mecrimino.lor.ui.components.Wordmark
import io.github.mecrimino.lor.ui.theme.LocalLor
import io.github.mecrimino.lor.ui.theme.LorType
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    val scheduler: ReminderScheduler,
) : ViewModel() {
    fun saveName(name: String) = viewModelScope.launch { settings.setName(name) }
    fun finish(then: () -> Unit) = viewModelScope.launch {
        settings.setOnboarded()
        then()
    }
}

private const val STEP_NAME = 0
private const val STEP_NOTIFICATIONS = 1
private const val STEP_EXACT_ALARM = 2

/** Name, then a friendly explanation before each system permission dialog. Each step skips itself if not needed. */
@Composable
fun OnboardingScreen(onFinished: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(STEP_NAME) }

    fun advance(from: Int) {
        val needsNotifications = from < STEP_NOTIFICATIONS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        val needsExact = from < STEP_EXACT_ALARM && !vm.scheduler.canScheduleExact()
        step = when {
            needsNotifications -> STEP_NOTIFICATIONS
            needsExact -> STEP_EXACT_ALARM
            else -> {
                vm.finish(onFinished)
                from
            }
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        advance(STEP_NOTIFICATIONS)
    }
    val exactAlarmSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        advance(STEP_EXACT_ALARM)
    }

    when (step) {
        STEP_NAME -> NameStep { name ->
            vm.saveName(name)
            advance(STEP_NAME)
        }
        STEP_NOTIFICATIONS -> PermissionStep(
            title = "Never miss a task",
            body = "LOR reminds you with a notification when something is due. Everything stays on this phone.",
            allow = "Allow notifications",
            onAllow = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
            onSkip = { advance(STEP_NOTIFICATIONS) },
        )
        STEP_EXACT_ALARM -> PermissionStep(
            title = "Reminders on the dot",
            body = "To ring at exactly 7:00 rather than sometime around it, Android needs you to switch on \"Alarms & reminders\" for LOR on the next screen.",
            allow = "Open setting",
            onAllow = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    exactAlarmSettings.launch(
                        Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    )
                }
            },
            onSkip = { advance(STEP_EXACT_ALARM) },
        )
    }
}

@Composable
private fun NameStep(onContinue: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Page {
        Text("What should we call you?", style = LorType.h1, color = LocalLor.current.ink)
        Spacer(Modifier.height(8.dp))
        LorField(
            value = name,
            onChange = { name = it.take(40) },
            placeholder = "Your name",
            onDone = { if (name.isNotBlank()) onContinue(name) },
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton("Continue", { onContinue(name) }, enabled = name.isNotBlank())
    }
}

@Composable
private fun PermissionStep(title: String, body: String, allow: String, onAllow: () -> Unit, onSkip: () -> Unit) {
    val c = LocalLor.current
    Page {
        Text(title, style = LorType.h1, color = c.ink)
        Text(body, style = LorType.body(15), color = c.muted)
        Spacer(Modifier.weight(1f))
        PrimaryButton(allow, onAllow)
        SecondaryButton("Not now", onSkip)
    }
}

@Composable
private fun Page(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(LocalLor.current.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Wordmark()
        Spacer(Modifier.height(36.dp))
        content()
    }
}
