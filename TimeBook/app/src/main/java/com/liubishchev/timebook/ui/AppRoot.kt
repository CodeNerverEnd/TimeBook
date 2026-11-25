package com.liubishchev.timebook.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.liubishchev.timebook.ui.items.ItemScreen
import com.liubishchev.timebook.ui.plan.PlanScreen
import com.liubishchev.timebook.ui.records.RecordScreen
import com.liubishchev.timebook.ui.stats.StatsScreen
import com.liubishchev.timebook.ui.timer.TimerScreen

private val tabs: List<Pair<String, ImageVector>> = listOf(
    "计时" to Icons.Filled.Timer,
    "记录" to Icons.AutoMirrored.Filled.List,
    "统计" to Icons.Filled.BarChart,
    "计划" to Icons.Filled.TrackChanges,
    "事项" to Icons.Filled.Label
)

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val message by vm.message.collectAsState()

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        vm.consumeMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, entry ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(entry.second, contentDescription = entry.first) },
                        label = { Text(entry.first) }
                    )
                }
            }
        }
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = inner.calculateBottomPadding())
        ) {
            when (tab) {
                0 -> TimerScreen(vm)
                1 -> RecordScreen(vm)
                2 -> StatsScreen(vm)
                3 -> PlanScreen(vm)
                else -> ItemScreen(vm)
            }
        }
    }
}
