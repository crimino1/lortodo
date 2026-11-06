package io.github.mecrimino.lor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mecrimino.lor.data.Settings
import io.github.mecrimino.lor.data.SettingsRepository
import io.github.mecrimino.lor.data.TaskRepository
import io.github.mecrimino.lor.reminders.ReminderScheduler
import io.github.mecrimino.lor.ui.MainScreen
import io.github.mecrimino.lor.ui.history.HistoryScreen
import io.github.mecrimino.lor.ui.history.StatsScreen
import io.github.mecrimino.lor.ui.onboarding.OnboardingScreen
import io.github.mecrimino.lor.ui.search.SearchScreen
import io.github.mecrimino.lor.ui.settings.SettingsScreen
import io.github.mecrimino.lor.ui.task.TaskEditScreen
import io.github.mecrimino.lor.ui.theme.LocalLor
import io.github.mecrimino.lor.ui.theme.LorTheme
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository, repo: TaskRepository) : ViewModel() {
    val settings: StateFlow<Settings?> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Force-stop clears alarms with no broadcast; opening the app is the one chance to re-arm them.
        // Not in Application.onCreate: a process started by a firing alarm would re-arm it and fire twice.
        viewModelScope.launch { repo.rescheduleAll() }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val openTask = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { vm.settings.value == null }
        enableEdgeToEdge()
        readTaskExtra(intent)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            settings?.let { s ->
                LorTheme(s.theme) { LorNavHost(s, openTask) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readTaskExtra(intent)
    }

    private fun readTaskExtra(intent: Intent) {
        intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1).takeIf { it > 0 }?.let { openTask.value = it }
    }
}

object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val STATS = "stats"
    const val TASK = "task?id={id}&date={date}"
    fun task(id: Long = 0, date: Long = -1) = "task?id=$id&date=$date"
}

@Composable
private fun LorNavHost(settings: Settings, openTask: MutableStateFlow<Long?>) {
    val nav = rememberNavController()
    val start by rememberSaveable { mutableStateOf(if (settings.onboarded) Routes.MAIN else Routes.ONBOARDING) }

    // "Delete all data" clears the onboarded flag: back to first launch.
    LaunchedEffect(settings.onboarded) {
        if (!settings.onboarded && nav.currentDestination?.route != Routes.ONBOARDING) {
            nav.navigate(Routes.ONBOARDING) { popUpTo(0) { inclusive = true } }
        }
    }
    LaunchedEffect(Unit) {
        openTask.filterNotNull().collect { id ->
            if (settings.onboarded) nav.navigate(Routes.task(id))
            openTask.value = null
        }
    }

    val back: () -> Unit = { nav.popBackStack() }
    NavHost(nav, start, Modifier.fillMaxSize().background(LocalLor.current.bg)) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinished = { nav.navigate(Routes.MAIN) { popUpTo(0) { inclusive = true } } })
        }
        composable(Routes.MAIN) {
            MainScreen(
                onOpenTask = { nav.navigate(Routes.task(it)) },
                onNewTask = { date -> nav.navigate(Routes.task(date = date)) },
                onSearch = { nav.navigate(Routes.SEARCH) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onHistory = { nav.navigate(Routes.HISTORY) },
                onStats = { nav.navigate(Routes.STATS) },
            )
        }
        composable(
            Routes.TASK,
            listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                navArgument("date") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { TaskEditScreen(onBack = back) }
        composable(Routes.SEARCH) { SearchScreen(onBack = back, onOpenTask = { nav.navigate(Routes.task(it)) }) }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = back, onHistory = { nav.navigate(Routes.HISTORY) }, onStats = { nav.navigate(Routes.STATS) })
        }
        composable(Routes.HISTORY) { HistoryScreen(onBack = back) }
        composable(Routes.STATS) { StatsScreen(onBack = back) }
    }
}
