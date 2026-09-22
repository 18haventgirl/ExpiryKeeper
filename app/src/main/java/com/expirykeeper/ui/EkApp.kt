package com.expirykeeper.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun EkApp(vm: ItemsViewModel = viewModel()) {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
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
        },
        floatingActionButton = {
            if (currentRoute != "add?itemId={itemId}") {
                FloatingActionButton(onClick = { nav.navigate("add?itemId=null") }) {
                    Icon(Icons.Filled.Add, "添加")
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = "today", modifier = Modifier.padding(padding)) {
            composable("today") { TodayScreen(vm) }
            composable("list") { ListScreen(vm, onEdit = { id -> nav.navigate("add?itemId=$id") }) }
            composable("add?itemId={itemId}") { entry ->
                val raw = entry.arguments?.getString("itemId")
                AddEditScreen(vm, itemId = raw?.takeIf { it != "null" },
                    onDone = { if (!nav.popBackStack()) nav.navigate("today") })
            }
        }
    }
}
