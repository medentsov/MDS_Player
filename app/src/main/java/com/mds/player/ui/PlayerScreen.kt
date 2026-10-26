package com.mds.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mds.player.MainViewModel

@Composable
fun PlayerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.playbackState.collectAsStateWithLifecycle()
    val queue by viewModel.queueInfo.collectAsStateWithLifecycle()
    var dragPosition by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(state.mediaId) { dragPosition = null }

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
                text = "Плеер",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (state.hasMedia) {
                IconButton(
                    onClick = {
                        viewModel.stopPlayback()
                        onBack()
                    }
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Остановить и закрыть")
                }
            }
        }

        if (!state.hasMedia) {
            EmptyState(
                icon = Icons.Rounded.GraphicEq,
                title = "Ничего не играет",
                message = "Откройте каталог, найдите запись и нажмите «Слушать»."
            )
            return@Column
        }

        val colors = MaterialTheme.colorScheme
        val duration = state.durationMs.coerceAtLeast(0L)
        val sliderMax = duration.toFloat().coerceAtLeast(1f)
        val sliderValue = (dragPosition ?: state.positionMs.toFloat()).coerceIn(0f, sliderMax)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 18.dp)
                    .size(220.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                colors.primaryContainer,
                                colors.tertiaryContainer
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = colors.outlineVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(36.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                    modifier = Modifier.size(86.dp)
                )
            }

            Text(
                text = state.title.ifBlank { "Без названия" },
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 26.dp)
            )
            Text(
                text = state.author.ifBlank { "Автор неизвестен" },
                style = MaterialTheme.typography.titleMedium,
                color = colors.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                text = listOf(displayDate(state.date), displayStation(state.station))
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (queue.size > 1 && queue.index >= 0) {
                Text(
                    text = "В текущей подборке: ${queue.index + 1} из ${queue.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            Slider(
                value = sliderValue,
                onValueChange = { value ->
                    if (duration > 0) dragPosition = value
                },
                onValueChangeFinished = {
                    dragPosition?.let { viewModel.seekTo(it.toLong()) }
                    dragPosition = null
                },
                valueRange = 0f..sliderMax,
                enabled = duration > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = formatDuration((dragPosition ?: state.positionMs.toFloat()).toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = viewModel::playPrevious,
                    enabled = queue.hasPrevious
                ) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "Предыдущая запись",
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = { viewModel.seekBy(-10_000L) }) {
                    Icon(
                        Icons.Rounded.Replay10,
                        contentDescription = "Назад на 10 секунд",
                        modifier = Modifier.size(30.dp)
                    )
                }
                FilledIconButton(
                    onClick = viewModel::togglePlayPause,
                    shape = CircleShape,
                    modifier = Modifier.size(76.dp)
                ) {
                    if (state.isBuffering && !state.isPlaying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp,
                            color = colors.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = if (state.isPlaying) {
                                Icons.Rounded.Pause
                            } else {
                                Icons.Rounded.PlayArrow
                            },
                            contentDescription = if (state.isPlaying) "Пауза" else "Слушать",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                IconButton(onClick = { viewModel.seekBy(30_000L) }) {
                    Icon(
                        Icons.Rounded.Forward30,
                        contentDescription = "Вперёд на 30 секунд",
                        modifier = Modifier.size(30.dp)
                    )
                }
                IconButton(
                    onClick = viewModel::playNext,
                    enabled = queue.hasNext
                ) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Следующая запись",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            if (state.error != null) {
                Surface(
                    color = colors.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 22.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = state.error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onErrorContainer
                        )
                        TextButton(
                            onClick = viewModel::retryPlayback,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Повторить", color = colors.onErrorContainer)
                        }
                    }
                }
            }

            if (state.sourceHost != null) {
                Text(
                    text = "Источник: ${state.sourceHost}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 22.dp)
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}
