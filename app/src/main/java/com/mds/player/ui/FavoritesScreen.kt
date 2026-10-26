package com.mds.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mds.player.MainViewModel
import com.mds.player.data.CatalogItem

@Composable
fun FavoritesScreen(
    viewModel: MainViewModel,
    onOpen: (CatalogItem, List<CatalogItem>) -> Unit
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val preparingId by viewModel.preparingId.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Избранное",
            subtitle = if (favorites.isEmpty()) {
                "Записи, которые вы отметили сердечком"
            } else {
                "${formatCount(favorites.size)} ${recordsLabel(favorites.size)}"
            },
            eyebrow = "моя коллекция"
        )

        if (favorites.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.FavoriteBorder,
                title = "Пока пусто",
                message = "Нажмите на сердечко у любой записи — она сохранится здесь " +
                    "и будет доступна даже без интернета."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(favorites, key = { _, item -> item.id }) { index, item ->
                    val isCurrent = playback.mediaId == item.id
                    EntryCard(
                        item = item,
                        isFavorite = item.id in favoriteIds,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && playback.isPlaying,
                        isPreparing = preparingId == item.id,
                        onOpen = { onOpen(item, favorites) },
                        onPlay = {
                            if (isCurrent && playback.isPlaying) {
                                viewModel.togglePlayPause()
                            } else {
                                viewModel.playFromList(item, favorites, index)
                            }
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(item) }
                    )
                }
            }
        }
    }
}
