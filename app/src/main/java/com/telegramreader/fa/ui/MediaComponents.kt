package com.telegramreader.fa.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.telegramreader.fa.data.TelegramRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

@Composable
fun PhotoMedia(
    url: String,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .setHeader("User-Agent", TelegramRepository.USER_AGENT)
            .setHeader("Referer", "https://t.me/")
            .build(),
        contentDescription = null,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen),
        contentScale = ContentScale.Crop,
    )
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
                .crossfade(true)
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
                    .padding(16.dp),
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
    val player = remember(url, httpClient) {
        val factory = OkHttpDataSource.Factory(httpClient)
            .setDefaultRequestProperties(
                mapOf(
                    "User-Agent" to TelegramRepository.USER_AGENT,
                    "Referer" to "https://t.me/",
                ),
            )
        val source = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(MediaItem.fromUri(url))
        ExoPlayer.Builder(context).build().apply {
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
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
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
fun PdfViewer(
    repository: TelegramRepository,
    url: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    val downloadState by produceState<Result<File>?>(initialValue = null, url, title) {
        value = runCatching {
            withContext(Dispatchers.IO) {
                repository.downloadDocument(url, title)
            }
        }
    }

    when (val result = downloadState) {
        null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("در حال دریافت PDF…")
            }
        }

        else -> result.fold(
            onSuccess = { file -> PdfPages(file = file, modifier = modifier) },
            onFailure = { error ->
                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error.message ?: "باز کردن PDF ناموفق بود.")
                }
            },
        )
    }
}

@Composable
private fun PdfPages(file: File, modifier: Modifier = Modifier) {
    val pageCount by produceState(initialValue = 0, file) {
        value = withContext(Dispatchers.IO) { pdfPageCount(file) }
    }

    if (pageCount <= 0) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items((0 until pageCount).toList(), key = { it }) { page ->
            PdfPage(file = file, pageIndex = page)
        }
    }
}

@Composable
private fun PdfPage(file: File, pageIndex: Int) {
    val bitmap by produceState<Bitmap?>(initialValue = null, file, pageIndex) {
        value = withContext(Dispatchers.IO) {
            renderPdfPage(file, pageIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image == null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "صفحه \${pageIndex + 1}",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
            )
        }
    }
}

private fun pdfPageCount(file: File): Int {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            return renderer.pageCount
        }
    }
}

private fun renderPdfPage(file: File, pageIndex: Int): Bitmap {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            renderer.openPage(pageIndex).use { page ->
                val width = 1080
                val height = (width.toFloat() * page.height.toFloat() / page.width.toFloat())
                    .toInt()
                    .coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap
            }
        }
    }
}
