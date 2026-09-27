package com.prabhupadaconnect.vedabase.ui.saved

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.prabhupadaconnect.vedabase.ui.bookmarks.BookmarksScreen
import com.prabhupadaconnect.vedabase.ui.highlights.HighlightsScreen
import com.prabhupadaconnect.vedabase.ui.notes.NotesScreen

/**
 * Unified "Saved" hub replacing separate Bookmarks, Notes, and Highlights tabs
 * with a clean 3-tab sub-navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
    onOpenRecord: (String) -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Bookmarks", "Notes", "Highlights")

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Saved") }
                )
                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title, style = MaterialTheme.typography.titleSmall) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTabIndex) {
                0 -> BookmarksScreen(onOpenRecord = onOpenRecord, showTopBar = false)
                1 -> NotesScreen(showTopBar = false)
                2 -> HighlightsScreen(onOpenRecord = onOpenRecord, showTopBar = false)
            }
        }
    }
}
