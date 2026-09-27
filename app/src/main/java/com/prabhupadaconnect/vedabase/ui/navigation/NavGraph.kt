package com.prabhupadaconnect.vedabase.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.prabhupadaconnect.vedabase.ui.bookmarks.BookmarksScreen
import com.prabhupadaconnect.vedabase.ui.highlights.HighlightsScreen
import com.prabhupadaconnect.vedabase.ui.history.RecentlyReadScreen
import com.prabhupadaconnect.vedabase.ui.library.LibraryScreen
import com.prabhupadaconnect.vedabase.ui.notes.NotesScreen
import com.prabhupadaconnect.vedabase.ui.reading.ReadingScreen
import com.prabhupadaconnect.vedabase.ui.search.SearchScreen
import com.prabhupadaconnect.vedabase.ui.settings.SettingsScreen

private object Routes {
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val BOOKMARKS = "bookmarks"
    const val NOTES = "notes"
    const val SETTINGS = "settings"
    const val READING = "reading/{recordKey}"
    const val HISTORY = "history"
    const val HIGHLIGHTS = "highlights"

    fun reading(recordKey: String) = "reading/$recordKey"
}

private data class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomTabs = listOf(
    BottomTab(Routes.LIBRARY, "Library", Icons.Filled.MenuBook),
    BottomTab(Routes.SEARCH, "Search", Icons.Filled.Search),
    BottomTab(Routes.BOOKMARKS, "Bookmarks", Icons.Filled.Bookmarks),
    BottomTab(Routes.NOTES, "Notes", Icons.Filled.Notes),
    BottomTab(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
)

@Composable
fun VedaBaseNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomTabs.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenRecord = { navController.navigate(Routes.reading(it)) },
                    onOpenHistory = { navController.navigate(Routes.HISTORY) }
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(onOpenRecord = { navController.navigate(Routes.reading(it)) })
            }
            composable(Routes.BOOKMARKS) {
                BookmarksScreen(
                    onOpenRecord = { navController.navigate(Routes.reading(it)) },
                    onOpenHighlights = { navController.navigate(Routes.HIGHLIGHTS) }
                )
            }
            composable(Routes.HISTORY) {
                RecentlyReadScreen(
                    onOpenRecord = { navController.navigate(Routes.reading(it)) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Routes.HIGHLIGHTS) {
                HighlightsScreen(
                    onOpenRecord = { navController.navigate(Routes.reading(it)) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Routes.NOTES) {
                NotesScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(
                route = Routes.READING,
                arguments = listOf(navArgument("recordKey") { defaultValue = "BG-1-1" })
            ) { entry ->
                val recordKey = entry.arguments?.getString("recordKey") ?: "BG-1-1"
                ReadingScreen(
                    recordKey = recordKey,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
