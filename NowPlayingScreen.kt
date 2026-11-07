package com.example.offlineplayer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.util.Locale

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun speedLabel(x: Float): String =
    String.format(Locale.US, "%.2f", x).trimEnd('0').trimEnd('.') + "×"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(vm: PlayerViewModel, onClose: () -> Unit) {
    val controller = vm.controller
    val isVideo = vm.currentId?.startsWith("v:") == true

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(controller) {
        while (true) {
            if (controller != null && !dragging) {
                position = controller.currentPosition
                duration = controller.duration.coerceAtLeast(0L)
            }
            delay(300)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.now_playing)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isVideo) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            keepScreenOn = true
                            player = controller
                        }
                    },
                    update = { it.player = controller },
                    onRelease = { it.player = null },
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.4f),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Text(
                vm.currentTitle,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 16.dp),
            )
            if (vm.currentArtist.isNotBlank()) {
                Text(
                    vm.currentArtist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // כפתורי הנגינה וציר הזמן נשארים משמאל-לימין גם בממשק עברי
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(
                    modifier = Modifier.padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val max = maxOf(duration, 1L).toFloat()
                    Slider(
                        value = if (dragging) dragValue else position.toFloat().coerceIn(0f, max),
                        valueRange = 0f..max,
                        onValueChange = {
                            dragging = true
                            dragValue = it
                        },
                        onValueChangeFinished = {
                            vm.seekTo(dragValue.toLong())
                            position = dragValue.toLong()
                            dragging = false
                        },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            formatTime(if (dragging) dragValue.toLong() else position),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(formatTime(duration), style = MaterialTheme.typography.bodySmall)
                    }

                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(onClick = vm::previous, modifier = Modifier.size(56.dp)) {
                            Icon(
                                Icons.Filled.SkipPrevious,
                                contentDescription = stringResource(R.string.previous),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        IconButton(onClick = { vm.seekBy(-10_000) }, modifier = Modifier.size(56.dp)) {
                            Icon(
                                Icons.Filled.Replay10,
                                contentDescription = stringResource(R.string.back10),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        FilledIconButton(onClick = vm::togglePlay, modifier = Modifier.size(72.dp)) {
                            Icon(
                                if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(
                                    if (vm.isPlaying) R.string.pause else R.string.play
                                ),
                                modifier = Modifier.size(40.dp),
                            )
                        }
                        IconButton(onClick = { vm.seekBy(10_000) }, modifier = Modifier.size(56.dp)) {
                            Icon(
                                Icons.Filled.Forward10,
                                contentDescription = stringResource(R.string.forward10),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        IconButton(onClick = vm::next, modifier = Modifier.size(56.dp)) {
                            Icon(
                                Icons.Filled.SkipNext,
                                contentDescription = stringResource(R.string.next),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Text(
                        stringResource(R.string.speed),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SPEEDS.forEach { s ->
                            FilterChip(
                                selected = vm.speed == s,
                                onClick = { vm.setSpeed(s) },
                                label = { Text(speedLabel(s)) },
                            )
                        }
                    }
                }
            }
        }
    }
}
