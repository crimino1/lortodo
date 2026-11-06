package io.github.mecrimino.lor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mecrimino.lor.ui.calendar.CalendarScreen
import io.github.mecrimino.lor.ui.components.HeaderIconButton
import io.github.mecrimino.lor.ui.components.LorHeader
import io.github.mecrimino.lor.ui.components.LorIcon
import io.github.mecrimino.lor.ui.components.LorIcons
import io.github.mecrimino.lor.ui.home.HomeScreen
import io.github.mecrimino.lor.ui.lists.ListsScreen
import io.github.mecrimino.lor.ui.theme.Accent
import io.github.mecrimino.lor.ui.theme.Inter
import io.github.mecrimino.lor.ui.theme.LocalLor
import java.time.LocalDate

private data class Tab(val label: String, val path: String)

private val Tabs = listOf(
    Tab("Today", LorIcons.tabToday),
    Tab("Calendar", LorIcons.tabCalendar),
    Tab("Lists", LorIcons.tabLists),
)

@Composable
fun MainScreen(
    onOpenTask: (Long) -> Unit,
    onNewTask: (Long) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
    onStats: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbars = remember { SnackbarHostState() }
    MainFrame(tab, { tab = it }, snackbars, { onNewTask(LocalDate.now().toEpochDay()) }, onSearch, onSettings) {
        when (tab) {
            0 -> HomeScreen(onOpenTask, snackbars)
            1 -> CalendarScreen(onOpenTask)
            2 -> ListsScreen(onHistory, onStats)
        }
    }
}

/** Header, tab content, "+" button and the design's bottom navigation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainFrame(
    tab: Int,
    onTab: (Int) -> Unit,
    snackbars: SnackbarHostState,
    onNewTask: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = LocalLor.current
    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        LorHeader(actions = {
            HeaderIconButton(LorIcons.search, "Search", onSearch)
            HeaderIconButton(LorIcons.settings, "Settings", onSettings)
            Spacer(Modifier.width(4.dp))
        })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            content()
            if (tab == 0) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(24.dp).size(56.dp).clip(RoundedCornerShape(18.dp))
                        .background(c.ink).clickable(role = Role.Button, onClick = onNewTask)
                        .semantics { contentDescription = "New task" },
                    contentAlignment = Alignment.Center,
                ) { LorIcon(LorIcons.plus, c.inv, 22.dp) }
            }
            SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter)) { data ->
                Snackbar(data, containerColor = c.ink, contentColor = c.inv, actionColor = Accent, shape = RoundedCornerShape(14.dp))
            }
        }
        if (!WindowInsets.isImeVisible) {
            HorizontalDivider(color = c.line, thickness = 1.dp)
            Row(
                Modifier.fillMaxWidth().background(c.bg).navigationBarsPadding().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                Tabs.forEachIndexed { i, t ->
                    val on = i == tab
                    Column(
                        Modifier.width(76.dp).height(52.dp).clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Tab) { onTab(i) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    ) {
                        LorIcon(t.path, if (on) c.ink else c.muted, 22.dp, strokeWidth = 1.8f)
                        Text(
                            t.label, color = if (on) c.ink else c.muted, fontFamily = Inter, fontSize = 11.sp,
                            fontWeight = if (on) FontWeight(600) else FontWeight(500),
                        )
                    }
                }
            }
        }
    }
}
