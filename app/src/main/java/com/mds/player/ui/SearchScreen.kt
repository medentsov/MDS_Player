package com.mds.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mds.player.MainViewModel
import com.mds.player.data.CatalogItem

private val SEARCH_SUGGESTIONS = listOf(
    "Стругацкий", "Кинг", "Пелевин", "Лукьяненко", "1996", "Желязны"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onOpen: (CatalogItem, List<CatalogItem>) -> Unit
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val sync by viewModel.syncState.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val preparingId by viewModel.preparingId.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    val subtitle = if (sync.isSyncing) {
        "Загружаем каталог · ${formatCount(sync.loaded)} из ${formatCount(sync.total)}"
    } else {
        "По автору, названию, дате, радиостанции или номеру записи"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Поиск",
            subtitle = subtitle,
            eyebrow = "весь каталог"
        )

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setSearchQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Например: Стругацкий, 1996, Солярис…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Очистить")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { focusManager.clearFocus() }
            )
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (sync.isSyncing) {
                item(key = "sync") { SyncProgress(sync) }
            }
            if (sync.error != null && results.isEmpty()) {
                item(key = "error") {
                    ErrorBanner(
                        message = sync.error.orEmpty(),
                        onRetry = viewModel::refresh
                    )
                }
            }

            if (query.isBlank()) {
                item(key = "hint") {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            text = "Ищите по всему архиву",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "В приложении сохранён весь каталог — поиск работает без задержек " +
                                "и находит записи по автору, названию, году, радиостанции или номеру.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Text(
                            text = "Попробуйте один из запросов:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SEARCH_SUGGESTIONS.forEach { suggestion ->
                                SuggestionChip(
                                    onClick = { viewModel.setSearchQuery(suggestion) },
                                    label = { Text(suggestion) }
                                )
                            }
                        }
                    }
                }
            } else {
                item(key = "count") {
                    Text(
                        text = "Найдено: ${formatCount(results.size)} ${recordsLabel(results.size)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                if (results.isEmpty() && !sync.isSyncing) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Rounded.SearchOff,
                            title = "Ничего не найдено",
                            message = "Попробуйте другое имя автора, часть названия или год выхода в эфир."
                        )
                    }
                }

                itemsIndexed(results, key = { _, item -> item.id }) { index, item ->
                    val isCurrent = playback.mediaId == item.id
                    EntryCard(
                        item = item,
                        isFavorite = item.id in favoriteIds,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && playback.isPlaying,
                        isPreparing = preparingId == item.id,
                        onOpen = { onOpen(item, results) },
                        onPlay = {
                            if (isCurrent && playback.isPlaying) {
                                viewModel.togglePlayPause()
                            } else {
                                viewModel.playFromList(item, results, index)
                            }
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(item) }
                    )
                }
            }
        }
    }
}
