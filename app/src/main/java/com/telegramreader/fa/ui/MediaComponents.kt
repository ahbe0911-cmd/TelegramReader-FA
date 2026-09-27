package com.telegramreader.fa.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.github.barteksc.pdfviewer.PDFView
import com.telegramreader.fa.data.MediaAction
import com.telegramreader.fa.data.TelegramRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

@Composable
fun PhotoMedia(
    url: String,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(90)
            .setHeader("User-Agent", TelegramRepository.USER_AGENT)
            .setHeader("Referer", "https://t.me/")
            .build(),
        contentDescription = null,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen),
        contentScale = ContentScale.Crop,
    ) {
        when (painter.state) {
            is AsyncImagePainter.State.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            }

            else -> SubcomposeAsyncImageContent()
        }
    }
}

@Composable
fun StickerMedia(
    url: String,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .crossfade(80)
                .setHeader("User-Agent", TelegramRepository.USER_AGENT)
                .setHeader("Referer", "https://t.me/")
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(220.dp)
                .clickable(onClick = onOpen),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
fun FullScreenPhoto(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .setHeader("User-Agent", TelegramRepository.USER_AGENT)
                    .setHeader("Referer", "https://t.me/")
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            ) {
                Icon(Icons.Default.Close, contentDescription = "بستن", tint = Color.White)
            }
        }
    }
}

@Composable
fun VideoMedia(
    url: String,
    httpClient: OkHttpClient,
    modifier: Modifier = Modifier,
    autoplay: Boolean = false,
    loop: Boolean = false,
    muted: Boolean = false,
) {
    val context = LocalContext.current

    // Start playback sooner, then keep a larger forward buffer once playing.
    val loadControl = remember {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2_000,   // min buffer
                30_000,  // max buffer
                350,     // start playback
                700,     // resume after rebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    val player = remember(url, httpClient) {
        val dataSourceFactory = OkHttpDataSource.Factory(httpClient)
            .setDefaultRequestProperties(
                mapOf(
                    "User-Agent" to TelegramRepository.USER_AGENT,
                    "Referer" to "https://t.me/",
                    "Accept" to "*/*",
                ),
            )

        val source = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(url))

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                setMediaSource(source)
                repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                volume = if (muted) 0f else 1f
                playWhenReady = autoplay
                prepare()
            }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                controllerShowTimeoutMs = 2200
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                keepScreenOn = false
            }
        },
        update = { it.player = player },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black),
    )
}

@Composable
fun AudioDocumentPlayer(
    repository: TelegramRepository,
    url: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val actionState by produceState<Result<MediaAction>?>(initialValue = null, url, title) {
        value = runCatching {
            withContext(Dispatchers.IO) {
                repository.prepareAudio(url, title)
            }
        }
    }

    when (val actionResult = actionState) {
        null -> Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.size(10.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "در حال یافتن لینک واقعی فایل صوتی…",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        else -> actionResult.fold(
            onSuccess = { action ->
                ResolvedAudioPlayer(
                    repository = repository,
                    action = action,
                    title = title,
                    modifier = modifier,
                )
            },
            onFailure = { error ->
                Card(
                    modifier = modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        error.message ?: "فایل صوتی قابل پخش نیست.",
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            },
        )
    }
}

@Composable
private fun ResolvedAudioPlayer(
    repository: TelegramRepository,
    action: MediaAction,
    title: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val httpClient = remember { repository.client() }

    val sourceKey = when (action) {
        is MediaAction.PlayAudioStream -> action.resolved.finalUrl
        is MediaAction.PlayAudioFile -> action.file.absolutePath
        else -> "invalid"
    }

    var failedMessage by remember(sourceKey) { mutableStateOf<String?>(null) }

    val player = remember(sourceKey, httpClient) {
        ExoPlayer.Builder(context)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        2_000,
                        45_000,
                        350,
                        900,
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build(),
            )
            .build()
            .apply {
                when (action) {
                    is MediaAction.PlayAudioStream -> {
                        val dataSourceFactory = OkHttpDataSource.Factory(httpClient)
                            .setDefaultRequestProperties(action.resolved.requiredHeaders)
                        val source = ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(
                                MediaItem.fromUri(action.resolved.finalUrl),
                            )
                        setMediaSource(source)
                    }

                    is MediaAction.PlayAudioFile -> {
                        setMediaItem(
                            MediaItem.fromUri(Uri.fromFile(action.file)),
                        )
                    }

                    else -> Unit
                }
                prepare()
            }
    }

    var isPlaying by remember(player) { mutableStateOf(false) }
    var position by remember(player) { mutableLongStateOf(0L) }
    var duration by remember(player) { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = player.duration.coerceAtLeast(0L)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                failedMessage = error.localizedMessage ?: "پخش فایل صوتی ممکن نشد."
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, isPlaying) {
        while (isActive) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            delay(if (isPlaying) 350L else 800L)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (failedMessage == null) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        if (player.isPlaying) player.pause() else player.play()
                    },
                    enabled = failedMessage == null,
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape,
                        ),
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "توقف" else "پخش",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                Spacer(Modifier.size(10.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                    Text(
                        failedMessage ?: when (action) {
                            is MediaAction.PlayAudioStream -> "پخش مستقیم از لینک نهایی"
                            is MediaAction.PlayAudioFile -> "پخش از کش محلی"
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (failedMessage == null) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                    )
                }

                Text(
                    "${formatMediaTime(position)} / ${formatMediaTime(duration)}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Slider(
                value = if (duration > 0L) {
                    (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                },
                onValueChange = { fraction ->
                    if (duration > 0L) {
                        player.seekTo((duration * fraction).toLong())
                    }
                },
                enabled = failedMessage == null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun formatMediaTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

/**
 * Dedicated in-app PDF reader.
 *
 * The previous implementation rendered each page into Compose Bitmaps with PdfRenderer.
 * That was memory-heavy and could crash on large/complex documents.  This viewer uses
 * Pdfium, keeps rendering inside its own optimized view and supports pinch-to-zoom,
 * double-tap zoom and smooth vertical scrolling.
 */
@Composable
fun PdfViewer(
    repository: TelegramRepository,
    url: String,
    title: String,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fileState by produceState<Result<File>?>(initialValue = null, url, title) {
        value = runCatching {
            withContext(Dispatchers.IO) {
                repository.downloadPdf(url, title)
            }
        }
    }

    when (val result = fileState) {
        null -> PdfLoading(modifier)

        else -> result.fold(
            onSuccess = { file ->
                DedicatedPdfView(
                    file = file,
                    modifier = modifier,
                )
            },
            onFailure = { error ->
                PdfError(
                    message = error.message ?: "باز کردن PDF ناموفق بود.",
                    onOpenExternal = onOpenExternal,
                    modifier = modifier,
                )
            },
        )
    }
}

@Composable
private fun DedicatedPdfView(
    file: File,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        factory = { context ->
            PDFView(context, null).apply {
                setBackgroundColor(android.graphics.Color.rgb(245, 245, 245))
                fromFile(file)
                    .defaultPage(0)
                    .enableSwipe(true)
                    .swipeHorizontal(false)
                    .enableDoubletap(true)
                    .spacing(8)
                    .load()
            }
        },
        update = { view ->
            // AndroidView may be reused after recomposition. Reload only when the file changed.
            val tag = file.absolutePath
            if (view.tag != tag) {
                view.tag = tag
                view.fromFile(file)
                    .defaultPage(0)
                    .enableSwipe(true)
                    .swipeHorizontal(false)
                    .enableDoubletap(true)
                    .spacing(8)
                    .load()
            }
        },
    )
}

@Composable
private fun PdfLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("در حال آماده‌سازی PDF…")
            Spacer(Modifier.height(5.dp))
            Text(
                "پس از دریافت، فایل با نمایشگر اختصاصی PDF باز می‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PdfError(
    message: String,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "نمایش PDF ممکن نشد",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onOpenExternal) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("باز کردن با برنامه دیگر")
                }
            }
        }
    }
}
