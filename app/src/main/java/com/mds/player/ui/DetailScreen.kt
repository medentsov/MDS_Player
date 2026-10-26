package com.mds.player.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mds.player.MainViewModel
import com.mds.player.data.AudioSource
import com.mds.player.data.CatalogItem
import com.mds.player.data.MdsClient

@Composable
fun DetailScreen(
    id: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val state by viewModel.detailState.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val preparingId by viewModel.preparingId.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(id) { viewModel.loadDetail(id) }

    val item = state.item
    val detail = state.detail

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Назад"
                )
            }
            Text(
                text = "Запись",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (item != null) {
                IconButton(onClick = { viewModel.toggleFavorite(item) }) {
                    Icon(
                        imageVector = if (item.id in favoriteIds) {
                            Icons.Rounded.Favorite
                        } else {
                            Icons.Rounded.FavoriteBorder
                        },
                        contentDescription = if (item.id in favoriteIds) {
                            "Убрать из избранного"
                        } else {
                            "Добавить в избранное"
                        },
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (item.detailAvailable) {
                    IconButton(onClick = { openOnSite(context, item) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = "Открыть на сайте"
                        )
                    }
                }
            }
        }

        when {
            item == null && state.loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            item == null -> {
                Column(modifier = Modifier.padding(16.dp)) {
                    ErrorBanner(
                        message = state.error ?: "Запись не найдена",
                        title = "Не удалось открыть запись",
                        onRetry = viewModel::retryDetail
                    )
                }
            }

            else -> {
                DetailContent(
                    item = item,
                    detail = detail,
                    loading = state.loading,
                    error = state.error,
                    playback = playback,
                    preparing = preparingId == item.id,
                    onPlay = { viewModel.playCurrent() },
                    onPlaySource = { source -> viewModel.playCurrent(source) },
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onRetryDetail = viewModel::retryDetail,
                    onRetryPlayback = viewModel::retryPlayback,
                    onOpenPlayer = onOpenPlayer
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    item: CatalogItem,
    detail: com.mds.player.data.ItemDetail?,
    loading: Boolean,
    error: String?,
    playback: com.mds.player.playback.PlayerUiState,
    preparing: Boolean,
    onPlay: () -> Unit,
    onPlaySource: (AudioSource) -> Unit,
    onTogglePlayPause: () -> Unit,
    onRetryDetail: () -> Unit,
    onRetryPlayback: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val audio = detail?.audio.orEmpty()
    val isCurrent = playback.mediaId == item.id
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Badge("#${item.displayNumber}")
            if (item.order.isNotBlank()) Badge("№${item.order} в эфире")
            if (detail?.hasReview == true || item.hasReview) Badge("Рецензия")
            if (isCurrent) Badge("Играет", accent = true)
        }

        Text(
            text = item.title.ifBlank { detail?.title.orEmpty() },
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Serif),
            color = colors.onSurface,
            modifier = Modifier.padding(top = 14.dp)
        )
        Text(
            text = item.author.ifBlank { detail?.author.orEmpty() }.ifBlank { "Автор неизвестен" },
            style = MaterialTheme.typography.titleMedium,
            color = colors.primary,
            modifier = Modifier.padding(top = 6.dp)
        )

        Surface(
            color = colors.surfaceVariant.copy(alpha = 0.45f),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                InfoRow("Дата выхода", displayDate(item.date))
                if (detail?.orderLabel?.isNotBlank() == true) {
                    InfoRow("В эфире", detail.orderLabel)
                }
                InfoRow(
                    "Радиостанция",
                    displayStation(item.station.ifBlank { detail?.station.orEmpty() })
                )
                InfoRow("Номер в каталоге", "#${item.displayNumber}")
            }
        }

        Surface(
            color = colors.primaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            if (isCurrent) onTogglePlayPause() else onPlay()
                        },
                        enabled = audio.isNotEmpty() && detail != null,
                        shape = CircleShape
                    ) {
                        when {
                            preparing -> CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.onPrimary
                            )
                            isCurrent && playback.isPlaying -> Icon(
                                Icons.Rounded.Pause,
                                contentDescription = null
                            )
                            else -> Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                preparing -> "Загрузка…"
                                isCurrent && playback.isPlaying -> "Пауза"
                                isCurrent -> "Продолжить"
                                else -> "Слушать"
                            }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (isCurrent) {
                        TextButton(onClick = onOpenPlayer) {
                            Text("Плеер")
                        }
                    }
                }

                if (isCurrent) {
                    LinearProgressIndicator(
                        progress = { playback.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                            .height(4.dp),
                        color = colors.primary,
                        trackColor = colors.surfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        Text(
                            text = formatDuration(playback.positionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = formatDuration(playback.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    playback.sourceHost?.let { host ->
                        Text(
                            text = "Поток: $host",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (playback.error != null) {
                        Text(
                            text = playback.error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        TextButton(onClick = onRetryPlayback) {
                            Text("Повторить")
                        }
                    }
                }

                if (loading && detail == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Получаем список аудиофайлов…",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                if (detail != null && audio.isEmpty()) {
                    Text(
                        text = "Для этой записи не найдено доступных аудиофайлов на http-серверах.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }

        if (error != null && item.detailAvailable) {
            ErrorBanner(
                message = error,
                title = "Не удалось загрузить аудиофайлы",
                onRetry = onRetryDetail,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        if (!item.detailAvailable) {
            Surface(
                color = colors.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(
                    text = "У этой записи каталога нет отдельной страницы с аудиофайлами.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        if (audio.isNotEmpty()) {
            Text(
                text = "Аудиофайлы",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                modifier = Modifier.padding(top = 24.dp)
            )
            Text(
                text = "Зеркала: ${audio.size}. Если поток не открывается, выберите другой сервер.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                audio.forEach { source ->
                    val sourceCurrent = isCurrent && playback.sourceHost == source.host
                    SourceRow(
                        source = source,
                        isCurrent = sourceCurrent,
                        isPlaying = sourceCurrent && playback.isPlaying,
                        onClick = {
                            if (sourceCurrent) onTogglePlayPause() else onPlaySource(source)
                        }
                    )
                }
            }
        }

        val review = detail?.review
        if (!review.isNullOrBlank()) {
            Surface(
                color = colors.surface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "РЕЦЕНЗИЯ",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary
                    )
                    detail?.reviewAuthor?.let { author ->
                        Text(
                            text = "Автор рецензии: $author",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = colors.outlineVariant.copy(alpha = 0.5f)
                    )
                    SelectionContainer {
                        Text(
                            text = review,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface
                        )
                    }
                }
            }
        }

        Text(
            text = "Материалы предоставлены сайтом mds-club.ru. " +
                "Приложение не хранит аудиофайлы и воспроизводит их с серверов архива.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SourceRow(
    source: AudioSource,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isCurrent) {
            colors.primaryContainer.copy(alpha = 0.55f)
        } else {
            colors.surfaceVariant.copy(alpha = 0.35f)
        },
        border = BorderStroke(
            1.dp,
            if (isCurrent) colors.primary.copy(alpha = 0.35f) else colors.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .padding(end = 0.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isCurrent) colors.primary else colors.surfaceVariant,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isCurrent && isPlaying) {
                                Icons.Rounded.Pause
                            } else if (isCurrent) {
                                Icons.Rounded.GraphicEq
                            } else {
                                Icons.Rounded.PlayArrow
                            },
                            contentDescription = null,
                            tint = if (isCurrent) colors.onPrimary else colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(source.host)
                        if (!source.size.isNullOrBlank()) {
                            append(" · ")
                            append(source.size)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isCurrent) {
                Text(
                    text = "играет",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

private fun openOnSite(context: Context, item: CatalogItem) {
    if (!item.detailAvailable) return
    val url = MdsClient.detailUrl(item.id).toString()
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
