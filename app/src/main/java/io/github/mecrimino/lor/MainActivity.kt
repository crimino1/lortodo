package io.github.mecrimino.lor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mecrimino.lor.data.SettingsRepository
import io.github.mecrimino.lor.data.TaskRepository
import io.github.mecrimino.lor.data.ThemeMode
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Only what the app shell needs, so typing a name or logging water doesn't recompose every screen. */
data class AppShell(val theme: ThemeMode, val onboarded: Boolean)

@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository, repo: TaskRepository) : ViewModel() {
    val shell: StateFlow<AppShell?> = settings.settings
        .map { AppShell(it.theme, it.onboarded) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
        splash.setKeepOnScreenCondition { vm.shell.value == null }
        enableEdgeToEdge()
        if (savedInstanceState == null) readTaskExtra(intent) // not again on rotation or restore
        setContent {
            val shell by vm.shell.collectAsStateWithLifecycle()
            shell?.let { s ->
                LorTheme(s.theme) { LorNavHost(s.onboarded, openTask) }
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

/**
 * Acts only while [entry] is the screen on top. A double tap would otherwise run twice: two editors pushed,
 * or two pops that remove the home screen and leave a blank window. Works mid-transition too.
 */
private fun NavHostController.from(entry: NavBackStackEntry, action: NavHostController.() -> Unit) {
    if (currentBackStackEntry?.id == entry.id) action()
}

private const val ENTER_MS = 220
private const val EXIT_MS = 160

@Composable
private fun LorNavHost(onboarded: Boolean, openTask: MutableStateFlow<Long?>) {
    val nav = rememberNavController()
    val start by rememberSaveable { mutableStateOf(if (onboarded) Routes.MAIN else Routes.ONBOARDING) }
    val isOnboarded by rememberUpdatedState(onboarded)

    // "Delete all data" clears the onboarded flag: back to first launch.
    LaunchedEffect(onboarded) {
        if (!onboarded && nav.currentDestination?.route != Routes.ONBOARDING) {
            nav.navigate(Routes.ONBOARDING) { popUpTo(0) { inclusive = true } }
        }
    }
    LaunchedEffect(Unit) {
        openTask.filterNotNull().collect { id ->
            if (isOnboarded) nav.navigate(Routes.task(id))
            openTask.value = null
        }
    }

    NavHost(
        nav, start, Modifier.fillMaxSize().background(LocalLor.current.bg),
        // Short and light: the 700 ms default fade makes every tap feel slow.
        enterTransition = { fadeIn(tween(ENTER_MS)) + slideInHorizontally(tween(ENTER_MS)) { it / 10 } },
        exitTransition = { fadeOut(tween(EXIT_MS)) },
        popEnterTransition = { fadeIn(tween(ENTER_MS)) },
        popExitTransition = { fadeOut(tween(EXIT_MS)) + slideOutHorizontally(tween(ENTER_MS)) { it / 10 } },
    ) {
        composable(Routes.ONBOARDING) { entry ->
            OnboardingScreen(onFinished = { nav.from(entry) { navigate(Routes.MAIN) { popUpTo(0) { inclusive = true } } } })
        }
        composable(Routes.MAIN) { entry ->
            MainScreen(
                onOpenTask = { nav.from(entry) { navigate(Routes.task(it)) } },
                onNewTask = { date -> nav.from(entry) { navigate(Routes.task(date = date)) } },
                onSearch = { nav.from(entry) { navigate(Routes.SEARCH) } },
                onSettings = { nav.from(entry) { navigate(Routes.SETTINGS) } },
                onHistory = { nav.from(entry) { navigate(Routes.HISTORY) } },
                onStats = { nav.from(entry) { navigate(Routes.STATS) } },
            )
        }
        composable(
            Routes.TASK,
            listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                navArgument("date") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { entry -> TaskEditScreen(onBack = { nav.from(entry) { popBackStack() } }) }
        composable(Routes.SEARCH) { entry ->
            SearchScreen(
                onBack = { nav.from(entry) { popBackStack() } },
                onOpenTask = { nav.from(entry) { navigate(Routes.task(it)) } },
            )
        }
        composable(Routes.SETTINGS) { entry ->
            SettingsScreen(
                onBack = { nav.from(entry) { popBackStack() } },
                onHistory = { nav.from(entry) { navigate(Routes.HISTORY) } },
                onStats = { nav.from(entry) { navigate(Routes.STATS) } },
            )
        }
        composable(Routes.HISTORY) { entry -> HistoryScreen(onBack = { nav.from(entry) { popBackStack() } }) }
        composable(Routes.STATS) { entry -> StatsScreen(onBack = { nav.from(entry) { popBackStack() } }) }
    }
}
