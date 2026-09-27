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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
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
import com.telegramreader.fa.ui.theme.TelegramReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.Locale

private const val PREFS = "telegram_reader_fa"
private const val CHANNELS = "channels"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Locale.setDefault(Locale("fa", "IR"))
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        setContent {
            TelegramReaderApp()
        }
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
    var page by rememberSaveable { mutableStateOf("home") }
    var selectedChannel by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPdf by remember { mutableStateOf<TelegramDocument?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var feedRefreshKey by remember { mutableIntStateOf(0) }

    TelegramReaderTheme(darkTheme = darkMode) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                when (page) {
                                    "feed" -> selectedChannel?.let { "@$it" } ?: "کانال"
                                    "pdf" -> selectedPdf?.title ?: "نمایش PDF"
                                    else -> "تلگرام‌خوان فارسی"
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        navigationIcon = {
                            if (page == "feed" || page == "pdf") {
                                IconButton(
                                    onClick = {
                                        if (page == "pdf") {
                                            selectedPdf = null
                                            page = "feed"
                                        } else {
                                            page = "channels"
                                        }
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.ArrowBack,
                                        contentDescription = "بازگشت",
                                    )
                                }
                            }
                        },
                        actions = {
                            if (page == "feed") {
                                IconButton(onClick = { feedRefreshKey++ }) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "تازه‌سازی",
                                    )
                                }
                            }

                            if (page == "pdf" && selectedPdf != null) {
                                IconButton(
                                    onClick = {
                                        openExternal(context, selectedPdf!!.url)
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.OpenInNew,
                                        contentDescription = "باز کردن با برنامه دیگر",
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                    )
                },
                bottomBar = {
                    if (page != "feed" && page != "pdf") {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp,
                        ) {
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
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
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
                                prefs.edit()
                                    .putStringSet(CHANNELS, channels.toSet())
                                    .apply()
                            },
                        )

                        "settings" -> SettingsScreen(
                            repository = repository,
                            darkMode = darkMode,
                            onDarkModeChange = {
                                darkMode = it
                                prefs.edit().putBoolean("dark_mode", it).apply()
                            },
                        )

                        "feed" -> selectedChannel?.let { channel ->
                            FeedScreen(
                                repository = repository,
                                channel = channel,
                                refreshKey = feedRefreshKey,
                                onOpenPdf = {
                                    selectedPdf = it
                                    page = "pdf"
                                },
                            )
                        }

                        "pdf" -> selectedPdf?.let { document ->
                            PdfViewer(
                                repository = repository,
                                url = document.url,
                                title = document.title,
                                onOpenExternal = {
                                    openExternal(context, document.url)
                                },
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
                            prefs.edit()
                                .putStringSet(CHANNELS, channels.toSet())
                                .apply()
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
    val gradient = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary,
        ),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 10.dp,
            bottom = 100.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(gradient)
                    .padding(22.dp),
            ) {
                Column {
                    Text(
                        "مطالب تلگرام، مرتب و فارسی",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "کانال‌های عمومی را بدون شلوغی اضافه دنبال کنید؛ عکس، ویدئو و PDF در خود برنامه باز می‌شوند.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FeaturePill(
                            icon = Icons.Default.VideoLibrary,
                            text = "ویدئوی روان",
                        )
                        FeaturePill(
                            icon = Icons.Default.Description,
                            text = "PDF داخلی",
                        )
                        FeaturePill(
                            icon = Icons.Default.Speed,
                            text = "کش سریع",
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "کانال‌های شما",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    toPersianDigits(channels.size.toString()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (channels.isEmpty()) {
            item {
                EmptyChannelsCard(onAdd)
            }
        } else {
            items(channels, key = { it }) { channel ->
                ChannelCard(
                    channel = channel,
                    onClick = { onOpen(channel) },
                )
            }
        }
    }
}

@Composable
private fun FeaturePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun EmptyChannelsCard(onAdd: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.RssFeed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "هنوز کانالی اضافه نکرده‌اید",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "نام کاربری یا لینک یک کانال عمومی تلگرام را اضافه کنید.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("افزودن کانال")
            }
        }
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
            Text(
                "هنوز کانالی اضافه نشده است.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 100.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(channels, key = { it }) { channel ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(channel) },
                shape = RoundedCornerShape(22.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChannelAvatar(channel)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "@$channel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "کانال عمومی",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onRemove(channel) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "حذف",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(
    channel: String,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelAvatar(channel)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "@$channel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "مشاهده آخرین پست‌ها",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChannelAvatar(channel: String) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(
                MaterialTheme.colorScheme.primaryContainer,
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            channel.firstOrNull()?.uppercase() ?: "@",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun AddChannelDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن کانال") },
        text = {
            Column {
                Text(
                    "نام کاربری یا لینک کانال عمومی را وارد کنید.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
            ) {
                Text("افزودن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        },
    )
}

@Composable
private fun FeedScreen(
    repository: TelegramRepository,
    channel: String,
    refreshKey: Int,
    onOpenPdf: (TelegramDocument) -> Unit,
) {
    var pageData by remember(channel) { mutableStateOf<PostsPage?>(null) }
    var loading by remember(channel) { mutableStateOf(true) }
    var loadingMore by remember(channel) { mutableStateOf(false) }
    var error by remember(channel) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val httpClient = remember(channel, refreshKey) { repository.client() }

    LaunchedEffect(channel, refreshKey) {
        loading = true
        error = null

        runCatching {
            withContext(Dispatchers.IO) {
                repository.fetchPosts(channel)
            }
        }.onSuccess {
            pageData = it
        }.onFailure {
            error = it.message ?: "خطا در دریافت اطلاعات"
        }

        loading = false
    }

    when {
        loading && pageData == null -> FeedLoading()

        error != null && pageData == null -> FeedError(
            message = error ?: "خطا در دریافت اطلاعات",
        )

        else -> {
            val current = pageData
            if (current == null || current.posts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "پستی پیدا نشد.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 10.dp,
                    end = 10.dp,
                    top = 6.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                current.channel?.let { info ->
                    item(key = "channel_header") {
                        ChannelHeader(info)
                    }
                }

                items(
                    items = current.posts,
                    key = { it.id },
                ) { post ->
                    PostCard(
                        post = post,
                        httpClient = httpClient,
                        onOpenPdf = onOpenPdf,
                    )
                }

                if (current.hasMore && current.nextBefore != null) {
                    item(key = "load_more") {
                        OutlinedButton(
                            onClick = {
                                if (loadingMore) return@OutlinedButton
                                loadingMore = true

                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            repository.fetchPosts(
                                                channel = channel,
                                                before = current.nextBefore,
                                            )
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
            }
        }
    }
}

@Composable
private fun FeedLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                "در حال دریافت پست‌ها…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeedError(message: String) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "دریافت اطلاعات ممکن نشد",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "اتصال اینترنت یا پراکسی را بررسی کنید و از دکمه تازه‌سازی استفاده کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun ChannelHeader(info: ChannelInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!info.photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(info.photoUrl)
                        .crossfade(100)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                ChannelAvatar(info.username)
            }

            Spacer(Modifier.size(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    info.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "@\${info.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                )

                info.subscriberCount?.let {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        toPersianDigits(it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                info.description
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        Spacer(Modifier.height(7.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
            }
        }
    }
}

@Composable
private fun PostCard(
    post: TelegramPost,
    httpClient: OkHttpClient,
    onOpenPdf: (TelegramDocument) -> Unit,
) {
    val context = LocalContext.current
    var openPhoto by remember { mutableStateOf<String?>(null) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(11.dp)) {
            post.forwardedFrom
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    Text(
                        "فوروارد از $it",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        textAlign = TextAlign.Right,
                    )
                    Spacer(Modifier.height(6.dp))
                }

            post.media.forEachIndexed { index, media ->
                if (index > 0) Spacer(Modifier.height(8.dp))

                when (media.kind) {
                    MediaKind.PHOTO -> PhotoMedia(
                        url = media.url,
                        modifier = Modifier.height(280.dp),
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
                RichPostText(post.html)
            } else if (post.text.isNotBlank()) {
                Text(
                    post.text,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
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
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatDate(post.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )

                    post.views?.let {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Default.RemoveRedEye,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                toPersianDigits(it),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            post.postUrl?.let { url ->
                TextButton(
                    onClick = { openExternal(context, url) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("باز کردن پست")
                }
            }
        }
    }

    openPhoto?.let { url ->
        FullScreenPhoto(
            url = url,
            onDismiss = { openPhoto = null },
        )
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
            containerColor = if (document.isPdf) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (document.isPdf) {
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        },
                        RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (document.isPdf) {
                        Icons.Default.PictureAsPdf
                    } else {
                        Icons.Default.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = if (document.isPdf) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }

            Spacer(Modifier.size(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    document.title,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                )
                document.extra?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (document.isPdf) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "برای نمایش داخل برنامه لمس کنید",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RichPostText(html: String) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val vazirmatn = remember {
        ResourcesCompat.getFont(context, R.font.vazirmatn_regular)
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        factory = { viewContext ->
            TextView(viewContext).apply {
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                textDirection = View.TEXT_DIRECTION_RTL
                textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                gravity = Gravity.END or Gravity.TOP
                movementMethod = LinkMovementMethod.getInstance()
                linksClickable = true
                setTextIsSelectable(true)
                setLineSpacing(0f, 1.18f)
                textSize = 16f
            }
        },
        update = { view ->
            view.text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            view.typeface = Typeface.create(vazirmatn, Typeface.NORMAL)
        },
    )
}

@Composable
private fun SettingsScreen(
    repository: TelegramRepository,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    var proxyEnabled by remember {
        mutableStateOf(prefs.getBoolean("proxy_enabled", false))
    }
    var host by remember {
        mutableStateOf(prefs.getString("proxy_host", "") ?: "")
    }
    var port by remember {
        mutableStateOf(prefs.getInt("proxy_port", 8080).toString())
    }
    var type by remember {
        mutableStateOf(prefs.getString("proxy_type", "HTTP") ?: "HTTP")
    }
    var saved by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsSection(title = "ظاهر") {
                SettingSwitch(
                    title = "حالت تیره",
                    subtitle = "تم روشن و تیره با طراحی بهینه برای مطالعه",
                    checked = darkMode,
                    onCheckedChange = onDarkModeChange,
                )
                HorizontalDivider()
                SettingInfo(
                    title = "فونت برنامه",
                    value = "Vazirmatn — وزیرمتن",
                )
                HorizontalDivider()
                SettingInfo(
                    title = "جهت رابط",
                    value = "فارسی و راست‌به‌چپ",
                )
            }
        }

        item {
            SettingsSection(title = "پراکسی") {
                SettingSwitch(
                    title = "استفاده از پراکسی",
                    subtitle = "برای اتصال به تلگرام در شبکه‌های محدود",
                    checked = proxyEnabled,
                    onCheckedChange = {
                        proxyEnabled = it
                        saved = false
                    },
                )

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            type = "HTTP"
                            saved = false
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (type == "HTTP") "✓ HTTP" else "HTTP")
                    }

                    OutlinedButton(
                        onClick = {
                            type = "SOCKS"
                            saved = false
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (type == "SOCKS") "✓ SOCKS" else "SOCKS")
                    }
                }

                Spacer(Modifier.height(10.dp))

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

                Spacer(Modifier.height(8.dp))

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

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        prefs.edit()
                            .putBoolean("proxy_enabled", proxyEnabled)
                            .putString("proxy_host", host.trim())
                            .putInt("proxy_port", port.toIntOrNull() ?: 8080)
                            .putString("proxy_type", type)
                            .apply()

                        repository.invalidateClient()
                        saved = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (saved) "ذخیره شد" else "ذخیره تنظیمات پراکسی")
                }
            }
        }

        item {
            SettingsSection(title = "عملکرد") {
                SettingInfo(
                    title = "کش شبکه",
                    value = "۲۵۶ مگابایت",
                )
                HorizontalDivider()
                SettingInfo(
                    title = "کش PDF",
                    value = "۲۵۶ مگابایت",
                )
                HorizontalDivider()
                SettingInfo(
                    title = "نسخه",
                    value = "۰.۳.۰",
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    Spacer(Modifier.height(6.dp))
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SettingInfo(
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }
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

private fun openExternal(
    context: Context,
    url: String,
) {
    runCatching {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun formatDate(raw: String): String {
    if (raw.isBlank()) return ""
    val clean = raw
        .replace("T", " ")
        .replace("Z", "")
        .take(16)
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
