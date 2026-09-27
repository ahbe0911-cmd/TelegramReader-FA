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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.telegramreader.fa.data.TelegramRepository
import java.io.File
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
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
            .crossfade(120)
            .setHeader("User-Agent", TelegramRepository.USER_AGENT)
            .setHeader("Referer", "https://t.me/")
            .build(),
        contentDescription = null,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
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
                    modifier = Modifier.size(26.dp),
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
                .crossfade(100)
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
                controllerShowTimeoutMs = 2500
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                keepScreenOn = false
            }
        },
        update = { it.player = player },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black),
    )
}

@Composable
fun PdfViewer(
    repository: TelegramRepository,
    url: String,
    title: String,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadState by produceState<Result<File>?>(initialValue = null, url, title) {
        value = runCatching {
            withContext(Dispatchers.IO) {
                repository.downloadPdf(url, title)
            }
        }
    }

    when (val result = downloadState) {
        null -> PdfLoading(modifier)

        else -> result.fold(
            onSuccess = { file ->
                PdfPages(
                    file = file,
                    onOpenExternal = onOpenExternal,
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
private fun PdfLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("در حال آماده‌سازی PDF…")
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

@Composable
private fun PdfPages(
    file: File,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageCountState by produceState<Result<Int>?>(initialValue = null, file) {
        value = runCatching {
            withContext(Dispatchers.IO) { pdfPageCount(file) }
        }
    }

    val result = pageCountState
    if (result == null) {
        PdfLoading(modifier)
        return
    }

    val pageCount = result.getOrElse {
        PdfError(
            message = it.message ?: "ساختار این PDF توسط اندروید پشتیبانی نمی‌شود.",
            onOpenExternal = onOpenExternal,
            modifier = modifier,
        )
        return
    }

    if (pageCount <= 0) {
        PdfError(
            message = "این PDF صفحه قابل نمایش ندارد.",
            onOpenExternal = onOpenExternal,
            modifier = modifier,
        )
        return
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val targetWidthPx = remember(configuration.screenWidthDp, density) {
        with(density) {
            configuration.screenWidthDp.dp.roundToPx()
        }.coerceIn(480, 960)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = (0 until pageCount).toList(),
            key = { it },
        ) { pageIndex ->
            PdfPage(
                file = file,
                pageIndex = pageIndex,
                targetWidthPx = targetWidthPx,
            )
        }
    }
}

@Composable
private fun PdfPage(
    file: File,
    pageIndex: Int,
    targetWidthPx: Int,
) {
    val renderState by produceState<Result<Bitmap>?>(initialValue = null, file, pageIndex, targetWidthPx) {
        value = runCatching {
            withContext(Dispatchers.IO) {
                renderPdfPage(
                    file = file,
                    pageIndex = pageIndex,
                    targetWidthPx = targetWidthPx,
                )
            }
        }
    }

    val result = renderState

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        when {
            result == null -> Box(
                Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                )
            }

            result.isFailure -> Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "صفحه \${pageIndex + 1} قابل نمایش نیست.",
                    color = MaterialTheme.colorScheme.error,
                )
            }

            else -> {
                val bitmap = result.getOrNull() ?: return@Card

                DisposableEffect(bitmap) {
                    onDispose {
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                }

                Column {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "صفحه \${pageIndex + 1}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White),
                        contentScale = ContentScale.FillWidth,
                    )
                    Text(
                        "صفحه \${pageIndex + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

private fun pdfPageCount(file: File): Int = synchronized(PdfRenderLock) {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            renderer.pageCount
        }
    }
}

private fun renderPdfPage(
    file: File,
    pageIndex: Int,
    targetWidthPx: Int,
): Bitmap = synchronized(PdfRenderLock) {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            require(pageIndex in 0 until renderer.pageCount) { "صفحه PDF نامعتبر است." }

            renderer.openPage(pageIndex).use { page ->
                val scale = min(
                    targetWidthPx.toFloat() / page.width.toFloat(),
                    MAX_RENDER_HEIGHT_PX.toFloat() / page.height.toFloat(),
                ).coerceAtMost(1.7f)

                val width = (page.width * scale).toInt().coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(
                    width,
                    height,
                    Bitmap.Config.ARGB_8888,
                )
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(
                    bitmap,
                    null,
                    null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                )
                bitmap
            }
        }
    }
}

private object PdfRenderLock
private const val MAX_RENDER_HEIGHT_PX = 2400
