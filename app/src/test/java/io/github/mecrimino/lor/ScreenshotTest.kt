package io.github.mecrimino.lor

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.SavedStateHandle
import io.github.mecrimino.lor.data.Priority
import io.github.mecrimino.lor.data.ReminderType
import io.github.mecrimino.lor.data.Subtask
import io.github.mecrimino.lor.data.Task
import io.github.mecrimino.lor.data.ThemeMode
import io.github.mecrimino.lor.ui.calendar.CalendarScreen
import io.github.mecrimino.lor.ui.calendar.CalendarViewModel
import io.github.mecrimino.lor.ui.MainFrame
import io.github.mecrimino.lor.ui.history.HistoryViewModel
import io.github.mecrimino.lor.ui.history.StatsScreen
import io.github.mecrimino.lor.ui.home.HomeScreen
import io.github.mecrimino.lor.ui.home.HomeViewModel
import io.github.mecrimino.lor.ui.lists.ListsScreen
import io.github.mecrimino.lor.ui.lists.ListsViewModel
import io.github.mecrimino.lor.ui.search.SearchScreen
import io.github.mecrimino.lor.ui.search.SearchViewModel
import io.github.mecrimino.lor.ui.settings.SettingsScreen
import io.github.mecrimino.lor.ui.settings.SettingsViewModel
import io.github.mecrimino.lor.ui.task.TaskEditScreen
import io.github.mecrimino.lor.ui.task.TaskEditViewModel
import io.github.mecrimino.lor.ui.theme.LocalLor
import io.github.mecrimino.lor.ui.theme.LorTheme
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders real screens on the JVM to app/build/screens/ for design review and store screenshots.
 * Run with: gradlew testDebugUnitTest --tests '*ScreenshotTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var env: TestEnv
    private val today = LocalDate.now()

    /** The design's sample day, with times shifted later than "now" so they read as today, not overdue. */
    @Before fun seed() = runBlocking {
        env = TestEnv()
        env.settings.setName("Sam")
        env.settings.setOnboarded()
        env.settings.setBatteryTipSeen()
        repeat(5) { env.settings.tapWater() }
        fun t(title: String, cat: Long, offset: Int, day: LocalDate = today, priority: Priority = Priority.LOW) = Task(
            title = title, categoryId = cat, dueEpochDay = day.toEpochDay(), priority = priority,
            dueMinute = minOf(LocalTime.now().hour * 60 + 30 + offset * 30, 1439),
        )
        val run = env.repo.save(t("Morning run, 5 km", 3, 0))
        val breakfast = env.repo.save(t("Breakfast: oats and banana", 4, 0))
        env.repo.complete(run)
        env.repo.complete(breakfast)
        env.repo.save(t("Linear algebra: eigenvalues", 2, 1, priority = Priority.HIGH))
        env.repo.save(t("Edit next video script", 1, 2, priority = Priority.MEDIUM))
        env.repo.save(t("Lunch", 4, 3))
        env.repo.save(t("Physics lab record", 2, 4))
        env.repo.save(t("Evening stretch, 15 min", 3, 5))
        env.repo.save(t("Dinner", 4, 6))
        env.repo.save(t("Call home", 6, 7))
        env.repo.save(t("Submit assignment", 2, 0, today.minusDays(1), Priority.HIGH))
        env.repo.save(t("Gym: legs", 3, 0, today.plusDays(1)))
        env.repo.save(t("Upload space video", 1, 2, today.plusDays(1)))
        env.repo.save(t("Grocery run", 4, 1, today.plusDays(4)))
        env.repo.save(t("Mock test: real analysis", 2, 0, today.plusDays(6)))
        env.repo.save(
            Task(title = "Plan the week", categoryId = 6, dueEpochDay = today.plusDays(2).toEpochDay(), reminder = ReminderType.NONE),
            listOf(Subtask(title = "Review goals"), Subtask(title = "Block study time", done = true)),
        )
        Unit
    }

    private fun shoot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            LorTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Box(Modifier.fillMaxSize().background(LocalLor.current.bg)) { content() }
            }
        }
        // Room and DataStore finish on real background threads the test clock can't see.
        repeat(20) {
            Thread.sleep(50)
            compose.waitForIdle()
        }
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Composable
    private fun TabScreen(tab: Int, content: @Composable () -> Unit) =
        MainFrame(tab, {}, SnackbarHostState(), {}, {}, {}, content)

    @Test fun home() = shoot("home") {
        TabScreen(0) { HomeScreen({}, SnackbarHostState(), HomeViewModel(env.repo, env.settings)) }
    }

    @Test fun homeDark() = shoot("home_dark", dark = true) {
        TabScreen(0) { HomeScreen({}, SnackbarHostState(), HomeViewModel(env.repo, env.settings)) }
    }

    @Test fun calendar() = shoot("calendar") {
        TabScreen(1) { CalendarScreen({}, CalendarViewModel(env.repo)) }
    }

    @Test fun lists() = shoot("lists") {
        TabScreen(2) { ListsScreen({}, {}, ListsViewModel(env.repo, env.settings)) }
    }

    @Test fun editor() = shoot("task_editor") {
        TaskEditScreen({}, TaskEditViewModel(SavedStateHandle(mapOf("id" to 0L, "date" to today.toEpochDay())), env.repo, env.settings))
    }

    @Test fun search() = shoot("search") {
        SearchScreen({}, {}, SearchViewModel(env.repo))
    }

    @Test fun stats() = shoot("stats") {
        StatsScreen({}, HistoryViewModel(env.repo))
    }

    @Test fun settings() = shoot("settings") {
        SettingsScreen({}, {}, {}, SettingsViewModel(env.settings, env.repo, env.backup, env.scheduler, env.notifier))
    }
}
