package com.telegramreader.fa

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.telegramreader.fa.data.ChannelInfo
import com.telegramreader.fa.data.MediaKind
import com.telegramreader.fa.data.PostsPage
import com.telegramreader.fa.data.TelegramDocument
import com.telegramreader.fa.data.TelegramPost
import com.telegramreader.fa.data.TelegramRepository
import com.telegramreader.fa.ui.FullScreenPhoto
import com.telegramreader.fa.ui.PdfViewer
import com.telegramreader.fa.ui.PhotoMedia
import com.telegramreader.fa.ui.StickerMedia
import com.telegramreader.fa.ui.VideoMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS = "telegram_reader_fa"
private const val CHANNELS = "channels"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TelegramReaderApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramReaderApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val repository = remember { TelegramRepository(context.applicationContext) }
    val channels = remember {
        mutableStateListOf<String>().apply {
            addAll(prefs.getStringSet(CHANNELS, emptySet()).orEmpty().sorted())
        }
    }

    var darkMode by rememberSaveable {
        mutableStateOf(prefs.getBoolean("dark_mode", false))
    }
    var serifFont by rememberSaveable {
        mutableStateOf(prefs.getBoolean("serif_font", false))
    }
    var page by rememberSaveable { mutableStateOf("home") }
    var selectedChannel by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPdf by remember { mutableStateOf<TelegramDocument?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var feedRefreshKey by remember { mutableIntStateOf(0) }

    val typography = remember(serifFont) { persianTypography(serifFont) }

    MaterialTheme(
        colorScheme = if (darkMode) darkColorScheme() else lightColorScheme(),
        typography = typography,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                when (page) {
                                    "feed" -> selectedChannel?.let { "@$it" } ?: "کانال"
                                    "pdf" -> selectedPdf?.title ?: "PDF"
                                    else -> "تلگرام‌خوان فارسی"
                                },
                            )
                        },
                        navigationIcon = {
                            if (page == "feed" || page == "pdf") {
                                IconButton(
                                    onClick = {
                                        if (page == "pdf") {
                                            page = "feed"
                                            selectedPdf = null
                                        } else {
                                            page = "channels"
                                        }
                                    },
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت")
                                }
                            }
                        },
                        actions = {
                            if (page == "feed") {
                                IconButton(onClick = { feedRefreshKey++ }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "تازه‌سازی")
                                }
                            }
                            if (page == "pdf" && selectedPdf != null) {
                                IconButton(
                                    onClick = { openExternal(context, selectedPdf!!.url) },
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = "باز کردن بیرونی")
                                }
                            }
                        },
                    )
                },
                bottomBar = {
                    if (page != "feed" && page != "pdf") {
                        NavigationBar {
                            NavigationBarItem(
                                selected = page == "home",
                                onClick = { page = "home" },
                                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                                label = { Text("خانه") },
                            )
                            NavigationBarItem(
                                selected = page == "channels",
                                onClick = { page = "channels" },
                                icon = { Icon(Icons.Default.RssFeed, contentDescription = null) },
                                label = { Text("کانال‌ها") },
                            )
                            NavigationBarItem(
                                selected = page == "settings",
                                onClick = { page = "settings" },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text("تنظیمات") },
                            )
                        }
                    }
                },
                floatingActionButton = {
                    if (page == "channels") {
                        FloatingActionButton(onClick = { showAdd = true }) {
                            Icon(Icons.Default.Add, contentDescription = "افزودن کانال")
                        }
                    }
                },
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    when (page) {
                        "home" -> HomeScreen(
                            channels = channels,
                            onOpen = {
                                selectedChannel = it
                                page = "feed"
                            },
                            onAdd = { showAdd = true },
                        )

                        "channels" -> ChannelsScreen(
                            channels = channels,
                            onOpen = {
                                selectedChannel = it
                                page = "feed"
                            },
                            onRemove = { channel ->
                                channels.remove(channel)
                                prefs.edit().putStringSet(CHANNELS, channels.toSet()).apply()
                            },
                        )

                        "settings" -> SettingsScreen(
                            darkMode = darkMode,
                            serifFont = serifFont,
                            onDarkModeChange = {
                                darkMode = it
                                prefs.edit().putBoolean("dark_mode", it).apply()
                            },
                            onSerifFontChange = {
                                serifFont = it
                                prefs.edit().putBoolean("serif_font", it).apply()
                            },
                        )

                        "feed" -> selectedChannel?.let { channel ->
                            FeedScreen(
                                repository = repository,
                                channel = channel,
                                refreshKey = feedRefreshKey,
                                serifFont = serifFont,
                                onOpenPdf = {
                                    selectedPdf = it
                                    page = "pdf"
                                },
                            )
                        }

                        "pdf" -> selectedPdf?.let { doc ->
                            PdfViewer(
                                repository = repository,
                                url = doc.url,
                                title = doc.title,
                            )
                        }
                    }
                }
            }

            if (showAdd) {
                AddChannelDialog(
                    onDismiss = { showAdd = false },
                    onAdd = { raw ->
                        val channel = normalizeChannel(raw)
                        if (channel.isNotBlank() && channel !in channels) {
                            channels.add(channel)
                            prefs.edit().putStringSet(CHANNELS, channels.toSet()).apply()
                        }
                        showAdd = false
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    channels: List<String>,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Text(
                "کانال‌های شما",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (channels.isEmpty()) {
                    "یک کانال عمومی تلگرام اضافه کنید؛ عکس، ویدئو و فایل‌های پست‌ها در خود برنامه نمایش داده می‌شوند."
                } else {
                    "برای مشاهده پست‌ها، رسانه‌ها و فایل‌ها روی یک کانال بزنید."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
        }

        if (channels.isEmpty()) {
            item {
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("افزودن اولین کانال")
                }
            }
        } else {
            items(channels) { channel ->
                ChannelCard(channel = channel, onClick = { onOpen(channel) })
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun ChannelsScreen(
    channels: List<String>,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    if (channels.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("هنوز کانالی اضافه نشده است.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        items(channels) { channel ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    ) {
                        Text(
                            "@$channel",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Right,
                        )
                        Text(
                            "کانال عمومی تلگرام",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = { onOpen(channel) }) { Text("باز کردن") }
                    IconButton(onClick = { onRemove(channel) }) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun ChannelCard(channel: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "@$channel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "مشاهده آخرین پست‌ها",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AddChannelDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن کانال") },
        text = {
            Column {
                Text("نام کاربری یا لینک کانال عمومی را وارد کنید.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("@username یا t.me/username") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(value) },
                enabled = normalizeChannel(value).isNotBlank(),
            ) { Text("افزودن") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        },
    )
}

@Composable
private fun FeedScreen(
    repository: TelegramRepository,
    channel: String,
    refreshKey: Int,
    serifFont: Boolean,
    onOpenPdf: (TelegramDocument) -> Unit,
) {
    var pageData by remember(channel) { mutableStateOf<PostsPage?>(null) }
    var loading by remember(channel) { mutableStateOf(true) }
    var loadingMore by remember(channel) { mutableStateOf(false) }
    var error by remember(channel) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(channel, refreshKey) {
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) { repository.fetchPosts(channel) }
        }.onSuccess { pageData = it }
            .onFailure { error = it.message ?: "خطا در دریافت اطلاعات" }
        loading = false
    }

    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        error != null && pageData == null -> Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(error ?: "خطا", textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                "از اتصال اینترنت یا تنظیمات پراکسی مطمئن شوید و دوباره تازه‌سازی کنید.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }

        else -> {
            val current = pageData
            if (current == null || current.posts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("پستی پیدا نشد.")
                }
                return
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                current.channel?.let { info ->
                    item { ChannelHeader(info) }
                }

                items(current.posts, key = { it.id }) { post ->
                    PostCard(
                        repository = repository,
                        post = post,
                        serifFont = serifFont,
                        onOpenPdf = onOpenPdf,
                    )
                }

                if (current.hasMore && current.nextBefore != null) {
                    item {
                        OutlinedButton(
                            onClick = {
                                if (loadingMore) return@OutlinedButton
                                loadingMore = true
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            repository.fetchPosts(channel, current.nextBefore)
                                        }
                                    }.onSuccess { next ->
                                        val merged = (current.posts + next.posts)
                                            .distinctBy { it.id }
                                            .sortedByDescending { it.id }
                                        pageData = current.copy(
                                            posts = merged,
                                            nextBefore = next.nextBefore,
                                            hasMore = next.hasMore,
                                        )
                                    }.onFailure {
                                        error = it.message
                                    }
                                    loadingMore = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (loadingMore) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.size(8.dp))
                            }
                            Text("نمایش پست‌های قدیمی‌تر")
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ChannelHeader(info: ChannelInfo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            info.photoUrl?.let { url ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape),
                )
                Spacer(Modifier.size(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    info.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "@\${info.username}",
                    style = MaterialTheme.typography.bodySmall,
                )
                info.subscriberCount?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium)
                }
                info.description?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun PostCard(
    repository: TelegramRepository,
    post: TelegramPost,
    serifFont: Boolean,
    onOpenPdf: (TelegramDocument) -> Unit,
) {
    val context = LocalContext.current
    var openPhoto by remember { mutableStateOf<String?>(null) }
    val httpClient = remember { repository.client() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            post.forwardedFrom?.takeIf { it.isNotBlank() }?.let {
                Text(
                    "فوروارد از $it",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
            }

            post.media.forEachIndexed { index, media ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                when (media.kind) {
                    MediaKind.PHOTO -> PhotoMedia(
                        url = media.url,
                        modifier = Modifier.height(260.dp),
                        onOpen = { openPhoto = media.url },
                    )

                    MediaKind.STICKER -> StickerMedia(
                        url = media.url,
                        onOpen = { openPhoto = media.url },
                    )

                    MediaKind.VIDEO -> VideoMedia(
                        url = media.url,
                        httpClient = httpClient,
                    )

                    MediaKind.GIF -> VideoMedia(
                        url = media.url,
                        httpClient = httpClient,
                        autoplay = true,
                        loop = true,
                        muted = true,
                    )
                }
            }

            if (post.media.isNotEmpty() && (post.text.isNotBlank() || post.html != null)) {
                Spacer(Modifier.height(12.dp))
            }

            if (!post.html.isNullOrBlank()) {
                RichPostText(post.html, serifFont)
            } else if (post.text.isNotBlank()) {
                Text(
                    post.text,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            post.documents.forEach { document ->
                Spacer(Modifier.height(10.dp))
                DocumentCard(
                    document = document,
                    onClick = {
                        if (document.isPdf) {
                            onOpenPdf(document)
                        } else {
                            openExternal(context, document.url)
                        }
                    },
                )
            }

            if (post.date.isNotBlank() || post.views != null) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        formatDate(post.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    post.views?.let {
                        Text(
                            "\${toPersianDigits(it)} بازدید",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            post.postUrl?.let { url ->
                TextButton(
                    onClick = { openExternal(context, url) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("باز کردن پست")
                }
            }
        }
    }

    openPhoto?.let { url ->
        FullScreenPhoto(url = url, onDismiss = { openPhoto = null })
    }
}

@Composable
private fun DocumentCard(
    document: TelegramDocument,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (document.isPdf) Icons.Default.PictureAsPdf else Icons.Default.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    document.title,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                )
                document.extra?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (document.isPdf) {
                    Text(
                        "نمایش PDF داخل برنامه",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RichPostText(html: String, serifFont: Boolean) {
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            TextView(context).apply {
                textDirection = View.TEXT_DIRECTION_RTL
                textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                gravity = Gravity.END
                movementMethod = LinkMovementMethod.getInstance()
                linksClickable = true
                setTextIsSelectable(true)
                setLineSpacing(0f, 1.18f)
                textSize = 16f
            }
        },
        update = { view ->
            view.text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
            view.setTextColor(color)
            view.typeface = Typeface.create(
                if (serifFont) "serif" else "sans-serif",
                Typeface.NORMAL,
            )
        },
    )
}

@Composable
private fun SettingsScreen(
    darkMode: Boolean,
    serifFont: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onSerifFontChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    var proxyEnabled by remember { mutableStateOf(prefs.getBoolean("proxy_enabled", false)) }
    var host by remember { mutableStateOf(prefs.getString("proxy_host", "") ?: "") }
    var port by remember { mutableStateOf(prefs.getInt("proxy_port", 8080).toString()) }
    var type by remember { mutableStateOf(prefs.getString("proxy_type", "HTTP") ?: "HTTP") }
    var saved by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("ظاهر و فارسی‌سازی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("حالت تیره")
                    Text("رابط کامل راست‌به‌چپ است.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
            }
        }
        item {
            Text("فونت", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onSerifFontChange(false) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (!serifFont) "✓ خوانا" else "خوانا")
                }
                OutlinedButton(
                    onClick = { onSerifFontChange(true) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (serifFont) "✓ نسخ" else "نسخ")
                }
            }
            Text(
                "هر دو حالت از شکل‌دهی و حروف فارسی اندروید استفاده می‌کنند.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("پراکسی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("استفاده از پراکسی", modifier = Modifier.weight(1f))
                Switch(
                    checked = proxyEnabled,
                    onCheckedChange = {
                        proxyEnabled = it
                        saved = false
                    },
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        type = "HTTP"
                        saved = false
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(if (type == "HTTP") "✓ HTTP" else "HTTP") }
                OutlinedButton(
                    onClick = {
                        type = "SOCKS"
                        saved = false
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(if (type == "SOCKS") "✓ SOCKS" else "SOCKS") }
            }
        }
        item {
            OutlinedTextField(
                value = host,
                onValueChange = {
                    host = it
                    saved = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("آدرس پراکسی") },
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = port,
                onValueChange = {
                    port = it.filter(Char::isDigit)
                    saved = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("پورت") },
                singleLine = true,
            )
        }
        item {
            Button(
                onClick = {
                    prefs.edit()
                        .putBoolean("proxy_enabled", proxyEnabled)
                        .putString("proxy_host", host.trim())
                        .putInt("proxy_port", port.toIntOrNull() ?: 8080)
                        .putString("proxy_type", type)
                        .apply()
                    saved = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saved) "ذخیره شد" else "ذخیره تنظیمات پراکسی")
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Text("رسانه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "عکس‌ها داخل برنامه باز می‌شوند، ویدئوها پخش‌کننده داخلی دارند و PDF پس از دریافت داخل برنامه صفحه‌به‌صفحه نمایش داده می‌شود.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun persianTypography(serif: Boolean): Typography {
    val base = Typography()
    val family = if (serif) FontFamily.Serif else FontFamily.SansSerif
    val locale = LocaleList(Locale("fa-IR"))
    return Typography(
        headlineSmall = base.headlineSmall.copy(fontFamily = family, localeList = locale),
        titleLarge = base.titleLarge.copy(fontFamily = family, localeList = locale),
        titleMedium = base.titleMedium.copy(fontFamily = family, localeList = locale),
        bodyLarge = base.bodyLarge.copy(fontFamily = family, localeList = locale),
        bodyMedium = base.bodyMedium.copy(fontFamily = family, localeList = locale),
        bodySmall = base.bodySmall.copy(fontFamily = family, localeList = locale),
        labelLarge = base.labelLarge.copy(fontFamily = family, localeList = locale),
        labelMedium = base.labelMedium.copy(fontFamily = family, localeList = locale),
        labelSmall = base.labelSmall.copy(fontFamily = family, localeList = locale),
    )
}

private fun normalizeChannel(raw: String): String =
    raw.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .removePrefix("t.me/")
        .removePrefix("telegram.me/")
        .removePrefix("@")
        .substringBefore("?")
        .substringBefore("/")
        .filter { it.isLetterOrDigit() || it == '_' }

private fun openExternal(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun formatDate(raw: String): String {
    if (raw.isBlank()) return ""
    val clean = raw.replace("T", " ").replace("Z", "").take(16)
    return toPersianDigits(clean)
}

private fun toPersianDigits(value: String): String {
    val latin = "0123456789"
    val persian = "۰۱۲۳۴۵۶۷۸۹"
    return buildString(value.length) {
        value.forEach { ch ->
            val index = latin.indexOf(ch)
            append(if (index >= 0) persian[index] else ch)
        }
    }
}
