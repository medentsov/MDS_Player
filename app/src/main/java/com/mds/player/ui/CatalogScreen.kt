package com.mds.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mds.player.MainViewModel
import com.mds.player.data.CatalogItem
import com.mds.player.data.CatalogSort

@Composable
fun CatalogScreen(
    viewModel: MainViewModel,
    onOpen: (CatalogItem, List<CatalogItem>) -> Unit
) {
    val items by viewModel.catalogItems.collectAsStateWithLifecycle()
    val sync by viewModel.syncState.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val preparingId by viewModel.preparingId.collectAsStateWithLifecycle()

    var sortMenuOpen by remember { mutableStateOf(false) }

    val total = if (sync.total > 0) sync.total else items.size
    val subtitle = when {
        sync.isSyncing && sync.total > 0 ->
            "Загружаем каталог · ${formatCount(sync.loaded)} из ${formatCount(sync.total)}"
        sync.lastUpdated > 0 ->
            "${formatCount(total)} ${recordsLabel(total)} · ${formatUpdatedAt(sync.lastUpdated)}"
        else ->
            "${formatCount(total)} ${recordsLabel(total)}"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Модель для сборки",
            subtitle = subtitle,
            eyebrow = "mds-club.ru · архив передач"
        ) {
            Box {
                IconButton(onClick = { sortMenuOpen = true }) {
                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Сортировка")
                }
                DropdownMenu(
                    expanded = sortMenuOpen,
                    onDismissRequest = { sortMenuOpen = false }
                ) {
                    CatalogSort.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            leadingIcon = {
                                if (mode == sortMode) {
                                    Icon(Icons.Rounded.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                viewModel.setSortMode(mode)
                                sortMenuOpen = false
                            }
                        )
                    }
                }
            }
            IconButton(
                onClick = viewModel::refresh,
                enabled = !sync.isSyncing
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Обновить каталог")
            }
        }

        when {
            items.isEmpty() && sync.isSyncing -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            text = if (sync.total > 0) {
                                "Загружаем ${formatCount(sync.loaded)} из ${formatCount(sync.total)}"
                            } else {
                                "Загружаем каталог…"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                    }
                }
            }

            items.isEmpty() -> {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (sync.error != null) {
                        ErrorBanner(
                            message = sync.error.orEmpty(),
                            onRetry = viewModel::refresh
                        )
                    } else {
                        EmptyState(
                            icon = Icons.Rounded.CloudOff,
                            title = "Каталог пуст",
                            message = "Проверьте подключение к интернету и обновите каталог."
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (sync.isSyncing) {
                        item(key = "sync-progress") {
                            SyncProgress(sync, modifier = Modifier.padding(bottom = 2.dp))
                        }
                    }
                    if (sync.error != null) {
                        item(key = "sync-error") {
                            ErrorBanner(
                                message = sync.error.orEmpty(),
                                title = "Каталог загружен не полностью",
                                onRetry = viewModel::refresh
                            )
                        }
                    }
                    itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                        val isCurrent = playback.mediaId == item.id
                        EntryCard(
                            item = item,
                            isFavorite = item.id in favoriteIds,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && playback.isPlaying,
                            isPreparing = preparingId == item.id,
                            onOpen = { onOpen(item, items) },
                            onPlay = {
                                if (isCurrent && playback.isPlaying) {
                                    viewModel.togglePlayPause()
                                } else {
                                    viewModel.playFromList(item, items, index)
                                }
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(item) }
                        )
                    }
                }
            }
        }
    }
}
