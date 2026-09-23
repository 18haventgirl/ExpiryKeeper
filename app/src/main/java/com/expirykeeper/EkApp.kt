package com.expirykeeper

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.expirykeeper.feature.addedit.AddEditScreen
import com.expirykeeper.feature.detail.DetailSheet
import com.expirykeeper.feature.list.ListScreen
import com.expirykeeper.feature.settings.SettingsScreen
import com.expirykeeper.feature.today.TodayScreen
import com.expirykeeper.ui.ItemsViewModel

@Composable
fun EkApp(vm: ItemsViewModel = viewModel()) {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Snackbar 事件总线：deleteWithUndo 等在此消费（撤销动作回调 action）
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.snackbar.collect { msg ->
            val result = snackbarHostState.showSnackbar(
                message = msg.text,
                actionLabel = msg.actionLabel,
                duration = if (msg.action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) msg.action?.invoke()
        }
    }

    // 底部导航与 FAB 只属于顶层页；添加/设置是整页，不带 chrome（修 A2）
    val chromeVisible = currentRoute == "today" || currentRoute == "list"
    // 详情浮层渲染在 Scaffold 之上：来路屏保持在场（含滚动位置），整屏连同底栏一起被
    // ModalBottomSheet 自己的 scrim 压暗。nav 的 dialog 路由会再叠一层窗口 dim，双重变暗（修 A1）
    var detailId by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (chromeVisible) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentRoute == "today",
                            onClick = { nav.navigate("today") { popUpTo(nav.graph.findStartDestination().id) { saveState = true } } },
                            icon = { Icon(Icons.Filled.CalendarMonth, "今日") }, label = { Text("今日") },
                        )
                        NavigationBarItem(
                            selected = currentRoute == "list",
                            onClick = { nav.navigate("list") { popUpTo(nav.graph.findStartDestination().id) { saveState = true } } },
                            icon = { Icon(Icons.Filled.FormatListBulleted, "清单") }, label = { Text("清单") },
                        )
                    }
                }
            },
            floatingActionButton = {
                if (chromeVisible) {
                    FloatingActionButton(onClick = { nav.navigate("add?itemId=null") }) {
                        Icon(Icons.Filled.Add, "添加")
                    }
                }
            },
        ) { padding ->
            NavHost(navController = nav, startDestination = "today", modifier = Modifier.padding(padding)) {
                composable("today") {
                    TodayScreen(vm,
                        onDetail = { detailId = it },
                        onSettings = { nav.navigate("settings") })
                }
                composable("list") {
                    ListScreen(vm, onDetail = { detailId = it })
                }
                composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
                composable("add?itemId={itemId}") { entry ->
                    val raw = entry.arguments?.getString("itemId")
                    AddEditScreen(vm, itemId = raw?.takeIf { it != "null" },
                        onDone = { if (!nav.popBackStack()) nav.navigate("today") })
                }
            }
        }

        detailId?.let { id ->
            DetailSheet(
                vm = vm,
                itemId = id,
                onEdit = {
                    detailId = null
                    nav.navigate("add?itemId=$it")
                },
                onDismiss = { detailId = null },
            )
        }
    }
}
