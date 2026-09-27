package com.prabhupadaconnect.vedabase.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prabhupadaconnect.vedabase.data.corpus.DirectReferenceService

/**
 * The "@" direct-reference Quick Jump bar - type "@sb 10.14" and jump
 * straight to that verse, no drilling through Library chapters. Shared by
 * the Library and Search screens (see [QuickJumpViewModel]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickJumpBar(
    onOpenRecord: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuickJumpViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSuggestions by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { text ->
                viewModel.onQueryChanged(text)
                showSuggestions = DirectReferenceService.isReferenceQuery(text)
            },
            leadingIcon = { Icon(Icons.Filled.AlternateEmail, contentDescription = null) },
            placeholder = { Text("Jump to a reference, e.g. @SB 10.14") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (showSuggestions && state.suggestions.isNotEmpty()) {
            Card(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(state.suggestions, key = { it.queryToComplete }) { suggestion ->
                        ListItem(
                            headlineContent = { Text(suggestion.displayText) },
                            supportingContent = if (suggestion.subText.isNotBlank()) {
                                { Text(suggestion.subText) }
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (suggestion.recordKey != null) {
                                        onOpenRecord(suggestion.recordKey)
                                        viewModel.clear()
                                        showSuggestions = false
                                    } else {
                                        viewModel.onQueryChanged(suggestion.queryToComplete)
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}
