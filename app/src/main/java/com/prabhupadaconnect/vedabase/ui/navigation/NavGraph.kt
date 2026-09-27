package com.prabhupadaconnect.vedabase.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
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
import com.prabhupadaconnect.vedabase.ui.library.ChaptersScreen
import com.prabhupadaconnect.vedabase.ui.library.LibraryScreen
import com.prabhupadaconnect.vedabase.ui.library.LibraryViewModel
import com.prabhupadaconnect.vedabase.ui.library.VersesScreen
import com.prabhupadaconnect.vedabase.ui.notes.NotesScreen
import com.prabhupadaconnect.vedabase.ui.reading.ReadingScreen
import com.prabhupadaconnect.vedabase.ui.saved.SavedScreen
import com.prabhupadaconnect.vedabase.ui.search.SearchScreen
import com.prabhupadaconnect.vedabase.ui.settings.SettingsScreen

private object Routes {
    const val LIBRARY = "library"
    const val SAVED = "saved"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val CHAPTERS = "chapters/{bookKey}"
    const val VERSES = "verses/{bookKey}/{chapterTitle}"
    const val READING = "reading/{recordKey}"
    const val HISTORY = "history"
    const val HIGHLIGHTS = "highlights"

    fun chapters(bookKey: String) = "chapters/$bookKey"
    fun verses(bookKey: String, chapterTitle: String) = "verses/$bookKey/${java.net.URLEncoder.encode(chapterTitle, "UTF-8")}"
    fun reading(recordKey: String) = "reading/$recordKey"
}

private data class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

// Clean modern 3-tab navigation matching Reference Image 1
private val bottomTabs = listOf(
    BottomTab(Routes.LIBRARY, "Library", Icons.Filled.MenuBook),
    BottomTab(Routes.SAVED, "Saved", Icons.Filled.Bookmark),
    BottomTab(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
)

@Composable
fun VedaBaseNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomTabs.any { it.route == currentRoute }

    val libraryViewModel: LibraryViewModel = androidx.hilt.navigation.compose.hiltViewModel()

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
                    onOpenBookChapters = { bookKey -> navController.navigate(Routes.chapters(bookKey)) },
                    onOpenRecord = { navController.navigate(Routes.reading(it)) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    viewModel = libraryViewModel
                )
            }
            composable(
                route = Routes.CHAPTERS,
                arguments = listOf(navArgument("bookKey") { defaultValue = "BG" })
            ) { entry ->
                val bookKey = entry.arguments?.getString("bookKey") ?: "BG"
                val book = libraryViewModel.getBook(bookKey)
                if (book != null) {
                    ChaptersScreen(
                        book = book,
                        onNavigateBack = { navController.popBackStack() },
                        onSelectChapter = { _, chapter ->
                            // If chapter is single-record, jump straight to reading!
                            if (chapter.records.size == 1) {
                                navController.navigate(Routes.reading(chapter.records.first().recordKey))
                            } else {
                                navController.navigate(Routes.verses(book.bookKey, chapter.title))
                            }
                        }
                    )
                }
            }
            composable(
                route = Routes.VERSES,
                arguments = listOf(
                    navArgument("bookKey") { defaultValue = "BG" },
                    navArgument("chapterTitle") { defaultValue = "" }
                )
            ) { entry ->
                val bookKey = entry.arguments?.getString("bookKey") ?: "BG"
                val rawChapterTitle = entry.arguments?.getString("chapterTitle") ?: ""
                val chapterTitle = java.net.URLDecoder.decode(rawChapterTitle, "UTF-8")
                val book = libraryViewModel.getBook(bookKey)

                val allChapters = book?.let { b ->
                    if (b.hasGroups) b.groups.flatMap { it.chapters } else b.chapters
                }.orEmpty()

                val chapterIndex = allChapters.indexOfFirst { it.title.equals(chapterTitle, true) }
                val chapter = if (chapterIndex >= 0) allChapters[chapterIndex] else null

                if (book != null && chapter != null) {
                    VersesScreen(
                        bookTitle = book.title,
                        groupTitle = null,
                        chapter = chapter,
                        onNavigateBack = { navController.popBackStack() },
                        onSelectRecord = { recordKey -> navController.navigate(Routes.reading(recordKey)) },
                        onPrevChapter = if (chapterIndex > 0) {
                            { navController.navigate(Routes.verses(bookKey, allChapters[chapterIndex - 1].title)) { popUpTo(Routes.CHAPTERS) } }
                        } else null,
                        onNextChapter = if (chapterIndex < allChapters.lastIndex) {
                            { navController.navigate(Routes.verses(bookKey, allChapters[chapterIndex + 1].title)) { popUpTo(Routes.CHAPTERS) } }
                        } else null
                    )
                }
            }
            composable(Routes.SEARCH) {
                SearchScreen(onOpenRecord = { navController.navigate(Routes.reading(it)) })
            }
            composable(Routes.SAVED) {
                SavedScreen(onOpenRecord = { navController.navigate(Routes.reading(it)) })
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
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToRecord = { navController.navigate(Routes.reading(it)) }
                )
            }
        }
    }
}

