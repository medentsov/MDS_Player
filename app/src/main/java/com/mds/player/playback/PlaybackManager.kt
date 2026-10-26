package com.mds.player.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.mds.player.data.AudioSource
import com.mds.player.data.CatalogItem
import com.mds.player.data.ItemDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val mediaId: String? = null,
    val title: String = "",
    val author: String = "",
    val station: String = "",
    val date: String = "",
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val sourceHost: String? = null,
    val error: String? = null
) {
    val hasMedia: Boolean get() = mediaId != null
    val progress: Float
        get() = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

/**
 * Единая точка управления плеером. Живёт в Application, поэтому музыка
 * продолжает играть при переходах между экранами.
 */
class PlaybackManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var positionJob: Job? = null

    private var currentEntry: CatalogItem? = null
    private var sources: List<AudioSource> = emptyList()
    private var sourceIndex = 0

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateStateFromPlayer()
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlaybackError(error)
        }
    }

    init {
        connect()
    }

    private fun connect() {
        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { connected ->
                        controller = connected
                        connected.addListener(listener)
                        _state.update { it.copy(isConnected = true) }
                        updateStateFromPlayer()
                        startPositionUpdates()
                    }
                    .onFailure {
                        _state.update {
                            it.copy(error = "Не удалось подключиться к плееру")
                        }
                    }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    /**
     * Запускает запись. [preferred] позволяет выбрать конкретное зеркало,
     * остальные источники останутся запасными.
     */
    fun play(entry: CatalogItem, detail: ItemDetail, preferred: AudioSource? = null): Boolean {
        val available = detail.audio.filter { it.url.isNotBlank() }
        if (available.isEmpty()) {
            _state.update {
                it.copy(
                    mediaId = entry.id,
                    title = entry.title,
                    author = entry.author,
                    station = entry.station,
                    date = entry.date,
                    error = "Для этой записи не найдено доступных аудиофайлов"
                )
            }
            return false
        }

        val ordered = if (preferred != null) {
            listOf(preferred) + available.filterNot { it.url == preferred.url }
        } else {
            available
        }

        currentEntry = entry
        sources = ordered
        sourceIndex = 0
        return playSource(entry, ordered.first(), startPositionMs = 0L)
    }

    private fun playSource(
        entry: CatalogItem,
        source: AudioSource,
        startPositionMs: Long
    ): Boolean {
        val player = controller
        if (player == null) {
            _state.update { it.copy(error = "Плеер ещё не готов. Попробуйте через секунду.") }
            return false
        }

        val extras = Bundle().apply {
            putString(KEY_DATE, entry.date)
            putString(KEY_STATION, entry.station)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(entry.title)
            .setArtist(entry.author)
            .setAlbumTitle(entry.station.ifBlank { "Модель для сборки" })
            .setExtras(extras)
            .build()
        val item = MediaItem.Builder()
            .setUri(source.url)
            .setMediaId(entry.id)
            .setMediaMetadata(metadata)
            .build()

        _state.update {
            it.copy(
                mediaId = entry.id,
                title = entry.title,
                author = entry.author,
                date = entry.date,
                station = entry.station,
                sourceHost = source.host,
                error = null,
                isBuffering = true
            )
        }

        player.setMediaItem(item)
        player.prepare()
        if (startPositionMs > 0) player.seekTo(startPositionMs)
        player.play()
        return true
    }

    private fun handlePlaybackError(error: PlaybackException) {
        val entry = currentEntry
        if (entry != null && sourceIndex + 1 < sources.size) {
            sourceIndex++
            val position = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
            if (playSource(entry, sources[sourceIndex], position)) return
        }

        _state.update {
            it.copy(
                isPlaying = false,
                isBuffering = false,
                error = "Не удалось воспроизвести файл (${error.errorCodeName}). " +
                    "Попробуйте другое зеркало или повторите позже."
            )
        }
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) {
                player.prepare()
            }
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
        _state.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
    }

    fun seekBy(deltaMs: Long) {
        val player = controller ?: return
        val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: Long.MAX_VALUE
        val target = (player.currentPosition + deltaMs).coerceIn(0L, duration)
        player.seekTo(target)
        _state.update { it.copy(positionMs = target) }
    }

    /** Повторить текущую запись с первого зеркала. */
    fun retry() {
        val entry = currentEntry ?: return
        if (sources.isEmpty()) return
        sourceIndex = 0
        playSource(entry, sources.first(), startPositionMs = 0L)
    }

    fun stop() {
        controller?.stop()
        controller?.clearMediaItems()
        currentEntry = null
        sources = emptyList()
        sourceIndex = 0
        _state.update {
            PlayerUiState(isConnected = it.isConnected)
        }
    }

    fun release() {
        positionJob?.cancel()
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                updatePosition()
                delay(500)
            }
        }
    }

    private fun updateStateFromPlayer() {
        val player = controller ?: return
        val metadata = player.mediaMetadata
        val extras = metadata.extras
        val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
        val currentSource = sources.getOrNull(sourceIndex)

        _state.update { previous ->
            previous.copy(
                isConnected = true,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                mediaId = player.currentMediaItem?.mediaId ?: previous.mediaId,
                title = metadata.title?.toString().orEmpty().ifBlank { previous.title },
                author = metadata.artist?.toString().orEmpty().ifBlank { previous.author },
                station = metadata.albumTitle?.toString().orEmpty().ifBlank { previous.station },
                date = extras?.getString(KEY_DATE).orEmpty().ifBlank { previous.date },
                durationMs = duration,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                sourceHost = currentSource?.host ?: previous.sourceHost,
                error = if (player.playerError == null) previous.error else previous.error
            )
        }
    }

    private fun updatePosition() {
        val player = controller ?: return
        val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
        _state.update {
            it.copy(
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                durationMs = duration,
                positionMs = player.currentPosition.coerceAtLeast(0L)
            )
        }
    }

    companion object {
        private const val KEY_DATE = "date"
        private const val KEY_STATION = "station"
    }
}
