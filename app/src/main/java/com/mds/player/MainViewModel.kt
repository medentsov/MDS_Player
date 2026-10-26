package com.mds.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mds.player.data.AudioSource
import com.mds.player.data.CatalogItem
import com.mds.player.data.CatalogQuery
import com.mds.player.data.CatalogSort
import com.mds.player.data.ItemDetail
import com.mds.player.data.MdsRepository
import com.mds.player.data.SyncState
import com.mds.player.playback.PlaybackManager
import com.mds.player.playback.PlayerUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

data class DetailUiState(
    val item: CatalogItem? = null,
    val detail: ItemDetail? = null,
    val loading: Boolean = false,
    val error: String? = null
)

data class QueueInfo(
    val index: Int = -1,
    val size: Int = 0
) {
    val hasPrevious: Boolean get() = index > 0
    val hasNext: Boolean get() = index >= 0 && index < size - 1
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MdsApplication
    private val repository: MdsRepository = app.repository
    val playback: PlaybackManager = app.playback

    val items: StateFlow<List<CatalogItem>> = repository.items
    val syncState: StateFlow<SyncState> = repository.syncState
    val favorites: StateFlow<List<CatalogItem>> = repository.favorites
    val favoriteIds: StateFlow<Set<String>> = repository.favoriteIds
    val playbackState: StateFlow<PlayerUiState> = playback.state

    private val _sortMode = MutableStateFlow(CatalogSort.NEWEST)
    val sortMode: StateFlow<CatalogSort> = _sortMode.asStateFlow()

    val catalogItems: StateFlow<List<CatalogItem>> = combine(items, _sortMode) { list, mode ->
        CatalogQuery.sort(list, mode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<CatalogItem>> =
        combine(items, _searchQuery) { list, query ->
            CatalogQuery.search(list, query)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _detailState = MutableStateFlow(DetailUiState())
    val detailState: StateFlow<DetailUiState> = _detailState.asStateFlow()

    private val _playQueue = MutableStateFlow<List<CatalogItem>>(emptyList())
    private val _queueIndex = MutableStateFlow(-1)
    val queueInfo: StateFlow<QueueInfo> =
        combine(_playQueue, _queueIndex) { queue, index ->
            QueueInfo(index = index.coerceAtMost(queue.lastIndex), size = queue.size)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QueueInfo())

    private val _preparingId = MutableStateFlow<String?>(null)
    val preparingId: StateFlow<String?> = _preparingId.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private var detailJob: Job? = null
    private var detailId: String? = null

    fun refresh() = repository.refresh()

    fun setSortMode(mode: CatalogSort) {
        _sortMode.value = mode
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavorite(item: CatalogItem) = repository.toggleFavorite(item)

    // ------------------------------------------------------------- details

    fun openDetail(item: CatalogItem, queue: List<CatalogItem> = listOf(item)) {
        detailId = item.id
        _playQueue.value = queue
        _queueIndex.value = queue.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        _detailState.value = DetailUiState(item = item, loading = true)
        loadDetailInternal(item, force = false)
    }

    fun loadDetail(id: String) {
        val current = _detailState.value
        if (current.item?.id == id && (current.loading || current.detail != null)) return

        detailId = id
        val known = repository.findItem(id) ?: current.item?.takeIf { it.id == id }
        _detailState.value = DetailUiState(item = known, loading = true)
        loadDetailInternal(known, force = false, idOverride = id)
    }

    fun retryDetail() {
        val item = _detailState.value.item
        val id = item?.id ?: detailId ?: return
        detailId = id
        _detailState.value = _detailState.value.copy(loading = true, error = null)
        loadDetailInternal(item, force = true, idOverride = id)
    }

    private fun loadDetailInternal(
        item: CatalogItem?,
        force: Boolean,
        idOverride: String? = null
    ) {
        val id = item?.id ?: idOverride ?: return
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            repository.getDetail(id, force).fold(
                onSuccess = { detail ->
                    val resolved = item ?: itemFromDetail(id, detail)
                    _detailState.value = DetailUiState(
                        item = resolved,
                        detail = detail,
                        loading = false
                    )
                },
                onFailure = { error ->
                    _detailState.value = _detailState.value.copy(
                        loading = false,
                        error = describeDetailError(error)
                    )
                }
            )
        }
    }

    private fun itemFromDetail(id: String, detail: ItemDetail): CatalogItem = CatalogItem(
        id = id,
        number = "",
        order = "",
        author = detail.author,
        title = detail.title,
        date = detail.date,
        station = detail.station,
        hasReview = detail.hasReview
    )

    // ------------------------------------------------------------ playback

    fun playFromList(item: CatalogItem, queue: List<CatalogItem>, index: Int) {
        _playQueue.value = queue
        _queueIndex.value = index.coerceAtLeast(0)

        if (!item.detailAvailable) {
            _messages.tryEmit("У этой записи нет страницы с аудиофайлами")
            return
        }

        val loaded = _detailState.value
        if (loaded.item?.id == item.id && loaded.detail != null) {
            playLoaded(item, loaded.detail, null)
            return
        }

        viewModelScope.launch {
            _preparingId.value = item.id
            val result = repository.getDetail(item.id)
            _preparingId.value = null
            result.fold(
                onSuccess = { detail ->
                    _detailState.value = DetailUiState(item = item, detail = detail)
                    playLoaded(item, detail, null)
                },
                onFailure = { error ->
                    _messages.tryEmit(describePlaybackError(error))
                }
            )
        }
    }

    fun playCurrent(preferred: AudioSource? = null) {
        val state = _detailState.value
        val item = state.item ?: return
        val detail = state.detail
        if (detail == null) {
            val index = _playQueue.value.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
            playFromList(item, _playQueue.value.ifEmpty { listOf(item) }, index)
        } else {
            playLoaded(item, detail, preferred)
        }
    }

    private fun playLoaded(item: CatalogItem, detail: ItemDetail, preferred: AudioSource?) {
        val index = _playQueue.value.indexOfFirst { it.id == item.id }
        if (index >= 0) _queueIndex.value = index
        val started = playback.play(item, detail, preferred)
        if (!started) {
            _messages.tryEmit("Для этой записи нет доступных аудиофайлов")
        }
    }

    fun playNext() {
        val queue = _playQueue.value
        val next = _queueIndex.value + 1
        if (next in queue.indices) {
            playFromList(queue[next], queue, next)
        }
    }

    fun playPrevious() {
        val queue = _playQueue.value
        val previous = _queueIndex.value - 1
        if (previous in queue.indices) {
            playFromList(queue[previous], queue, previous)
        }
    }

    fun togglePlayPause() = playback.togglePlayPause()

    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)

    fun seekBy(deltaMs: Long) = playback.seekBy(deltaMs)

    fun retryPlayback() = playback.retry()

    fun stopPlayback() = playback.stop()

    private fun describeDetailError(error: Throwable): String = when (error) {
        is IOException -> error.message ?: "Не удалось загрузить страницу записи"
        else -> "Не удалось загрузить страницу записи: ${error.message ?: "ошибка"}"
    }

    private fun describePlaybackError(error: Throwable): String = when (error) {
        is IOException -> "Не удалось получить аудиофайлы записи. Проверьте подключение."
        else -> "Не удалось получить аудиофайлы: ${error.message ?: "ошибка"}"
    }
}
