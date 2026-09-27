package com.telegramreader.fa

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Build
import android.text.Layout
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.telegramreader.fa.data.ChannelInfo
import com.telegramreader.fa.data.DocumentKind
import com.telegramreader.fa.data.MediaKind
import com.telegramreader.fa.data.PostsPage
import com.telegramreader.fa.data.TelegramDocument
import com.telegramreader.fa.data.TelegramPost
import com.telegramreader.fa.data.TelegramRepository
import com.telegramreader.fa.ui.FullScreenPhoto
import com.telegramreader.fa.ui.AudioDocumentPlayer
import com.telegramreader.fa.ui.PhotoMedia
import com.telegramreader.fa.ui.StickerMedia
import com.telegramreader.fa.ui.VideoMedia
import com.telegramreader.fa.ui.theme.Rooznameh
import com.telegramreader.fa.ui.theme.TelegramReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.Locale
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val PREFS = "telegram_reader_fa"
private const val LEGACY_CHANNELS = "channels"
private const val NEWS_CHANNELS = "channels_news"
private const val CAFENET_CHANNELS = "channels_cafenet"

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
    val legacyChannels = remember {
        prefs.getStringSet(LEGACY_CHANNELS, emptySet()).orEmpty()
    }
    val newsChannels = remember {
        mutableStateListOf<String>().apply {
            val saved = prefs.getStringSet(NEWS_CHANNELS, null)
            addAll((saved ?: legacyChannels).sorted())
        }
    }
    val cafeNetChannels = remember {
        mutableStateListOf<String>().apply {
            addAll(prefs.getStringSet(CAFENET_CHANNELS, emptySet()).orEmpty().sorted())
        }
    }

    var darkMode by rememberSaveable {
        mutableStateOf(prefs.getBoolean("dark_mode", false))
    }
    var page by rememberSaveable { mutableStateOf("news") }
    var selectedSection by rememberSaveable { mutableStateOf("news") }
    var selectedChannel by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var feedRefreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (!prefs.getBoolean("legacy_migrated_to_news", false) && legacyChannels.isNotEmpty()) {
            prefs.edit()
                .putStringSet(NEWS_CHANNELS, newsChannels.toSet())
                .putBoolean("legacy_migrated_to_news", true)
                .apply()
        }
    }

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
                                    "news" -> "اخبار"
                                    "cafenet" -> "کافی‌نت"
                                    else -> "نبضک"
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        navigationIcon = {
                            if (page == "feed") {
                                IconButton(
                                    onClick = { page = selectedSection },
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

                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                    )
                },
                bottomBar = {
                    if (page != "feed") {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 4.dp,
                        ) {
                            NavigationBarItem(
                                selected = page == "news",
                                onClick = { page = "news" },
                                icon = { Icon(Icons.Default.Newspaper, contentDescription = null) },
                                label = { Text("اخبار") },
                            )
                            NavigationBarItem(
                                selected = page == "cafenet",
                                onClick = { page = "cafenet" },
                                icon = { Icon(Icons.Default.Computer, contentDescription = null) },
                                label = { Text("کافی‌نت") },
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
                    if (page == "news" || page == "cafenet") {
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
                        "news" -> HomeScreen(
                            title = "اخبار",
                            subtitle = "کانال‌های خبری",
                            channels = newsChannels,
                            onOpen = {
                                selectedSection = "news"
                                selectedChannel = it
                                page = "feed"
                            },
                            onAdd = { showAdd = true },
                            onRemove = { channel ->
                                newsChannels.remove(channel)
                                prefs.edit()
                                    .putStringSet(NEWS_CHANNELS, newsChannels.toSet())
                                    .apply()
                            },
                        )

                        "cafenet" -> HomeScreen(
                            title = "کافی‌نت",
                            subtitle = "کانال‌های کافی‌نت",
                            channels = cafeNetChannels,
                            onOpen = {
                                selectedSection = "cafenet"
                                selectedChannel = it
                                page = "feed"
                            },
                            onAdd = { showAdd = true },
                            onRemove = { channel ->
                                cafeNetChannels.remove(channel)
                                prefs.edit()
                                    .putStringSet(CAFENET_CHANNELS, cafeNetChannels.toSet())
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
                                onOpenPdf = { document ->
                                    context.startActivity(
                                        PdfReaderActivity.intent(
                                            context = context,
                                            url = document.url,
                                            title = document.title,
                                        ),
                                    )
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
                        if (channel.isNotBlank()) {
                            if (page == "cafenet") {
                                if (channel !in cafeNetChannels) {
                                    cafeNetChannels.add(channel)
                                    prefs.edit()
                                        .putStringSet(CAFENET_CHANNELS, cafeNetChannels.toSet())
                                        .apply()
                                }
                            } else {
                                if (channel !in newsChannels) {
                                    newsChannels.add(channel)
                                    prefs.edit()
                                        .putStringSet(NEWS_CHANNELS, newsChannels.toSet())
                                        .apply()
                                }
                            }
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
    title: String,
    subtitle: String,
    channels: List<String>,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = 8.dp,
            bottom = 100.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(channel) },
                    shape = RoundedCornerShape(18.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChannelAvatar(channel)
                        Spacer(Modifier.size(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "@$channel",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "مشاهده پست‌ها",
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
            Spacer(Modifier.height(4.dp))
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
                Spacer(Modifier.height(8.dp))
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
    val context = LocalContext.current
    var pendingDownload by remember { mutableStateOf<TelegramDocument?>(null) }

    val saveFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val document = pendingDownload
        val uri = result.data?.data
        pendingDownload = null

        if (result.resultCode == Activity.RESULT_OK && document != null && uri != null) {
            scope.launch {
                Toast.makeText(context, "در حال دانلود فایل…", Toast.LENGTH_SHORT).show()
                runCatching {
                    withContext(Dispatchers.IO) {
                        repository.downloadFileToUri(
                            url = document.url,
                            title = document.title,
                            destination = uri,
                        )
                    }
                }.onSuccess {
                    Toast.makeText(context, "فایل ذخیره شد", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(
                        context,
                        it.message ?: "دانلود فایل ناموفق بود",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    val httpClient = remember(channel, refreshKey) { repository.client() }
    val networkAvailable = rememberNetworkAvailable()

    suspend fun fetchFirstPageWithRetry(): Result<PostsPage> {
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    repository.fetchPosts(channel)
                }
            }
            if (result.isSuccess) return result
            lastError = result.exceptionOrNull()
            if (attempt < 2) {
                delay(700L * (attempt + 1))
            }
        }
        return Result.failure(
            lastError ?: IllegalStateException("اتصال به تلگرام برقرار نشد."),
        )
    }

    LaunchedEffect(channel, refreshKey, networkAvailable) {
        if (!networkAvailable) {
            if (pageData == null) {
                loading = false
                error = "اینترنت در دسترس نیست. پس از وصل‌شدن، برنامه خودکار دوباره تلاش می‌کند."
            }
            return@LaunchedEffect
        }

        loading = pageData == null
        error = null

        fetchFirstPageWithRetry()
            .onSuccess { fresh ->
                val current = pageData
                pageData = if (current == null) {
                    fresh
                } else {
                    current.copy(
                        channel = fresh.channel ?: current.channel,
                        posts = (fresh.posts + current.posts)
                            .distinctBy { it.id }
                            .sortedByDescending { it.id },
                    )
                }
            }
            .onFailure {
                error = it.message ?: "خطا در دریافت اطلاعات"
            }

        loading = false
    }

    // While this feed is visible, keep it warm without requiring any account/login.
    // If connectivity drops, the effect stops; when Android reports the network back,
    // the LaunchedEffect above reconnects and refreshes automatically.
    LaunchedEffect(channel, networkAvailable) {
        if (!networkAvailable) return@LaunchedEffect

        while (isActive) {
            delay(60_000L)
            val fresh = runCatching {
                withContext(Dispatchers.IO) {
                    repository.fetchPosts(channel)
                }
            }.getOrNull() ?: continue

            val current = pageData
            pageData = if (current == null) {
                fresh
            } else {
                current.copy(
                    channel = fresh.channel ?: current.channel,
                    posts = (fresh.posts + current.posts)
                        .distinctBy { it.id }
                        .sortedByDescending { it.id },
                )
            }
        }
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
                    start = 8.dp,
                    end = 8.dp,
                    top = 4.dp,
                    bottom = 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(
                    items = current.posts,
                    key = { it.id },
                ) { post ->
                    PostCard(
                        repository = repository,
                        post = post,
                        httpClient = httpClient,
                        onOpenPdf = onOpenPdf,
                        onOpenSystem = { document ->
                            scope.launch {
                                Toast.makeText(
                                    context,
                                    "در حال آماده‌سازی فایل…",
                                    Toast.LENGTH_SHORT,
                                ).show()

                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        repository.downloadFileToCache(
                                            url = document.url,
                                            title = document.title,
                                        )
                                    }
                                }.onSuccess { file ->
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file,
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, mimeTypeForDocument(document))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    runCatching {
                                        context.startActivity(
                                            Intent.createChooser(intent, "باز کردن فایل با"),
                                        )
                                    }.onFailure {
                                        Toast.makeText(
                                            context,
                                            "برنامه‌ای برای باز کردن این نوع فایل پیدا نشد.",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        it.message ?: "آماده‌سازی فایل ناموفق بود",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        },
                        onDownloadDocument = { document ->
                            pendingDownload = document
                            val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = mimeTypeForDocument(document)
                                putExtra(Intent.EXTRA_TITLE, safeDownloadName(document))
                            }
                            saveFileLauncher.launch(saveIntent)
                        },
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
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
                        .size(54.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                ChannelAvatar(info.username)
            }

            Spacer(Modifier.size(10.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    info.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = Rooznameh,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                    ),
                    fontWeight = FontWeight.Normal,
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
                        Spacer(Modifier.height(5.dp))
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
    repository: TelegramRepository,
    post: TelegramPost,
    httpClient: OkHttpClient,
    onOpenPdf: (TelegramDocument) -> Unit,
    onOpenSystem: (TelegramDocument) -> Unit,
    onDownloadDocument: (TelegramDocument) -> Unit,
) {
    val context = LocalContext.current
    var openPhoto by remember { mutableStateOf<String?>(null) }
    var activeAudioUrl by remember(post.id) { mutableStateOf<String?>(null) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val palette = remember(post.id, dark) { newsCardPalette(post.id, dark) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = palette.second.copy(alpha = if (dark) 0.50f else 0.30f),
                shape = RoundedCornerShape(18.dp),
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = palette.first,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(palette.second),
        )

        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            post.forwardedFrom
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    Text(
                        "فوروارد از $it",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.second,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 1.dp),
                        textAlign = TextAlign.Right,
                    )
                    Spacer(Modifier.height(3.dp))
                }

            post.media.forEachIndexed { index, media ->
                if (index > 0) Spacer(Modifier.height(5.dp))

                when (media.kind) {
                    MediaKind.PHOTO -> PhotoMedia(
                        url = media.url,
                        modifier = Modifier.height(245.dp),
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
                Spacer(Modifier.height(6.dp))
            }

            if (!post.html.isNullOrBlank()) {
                RichPostText(post.html)
            } else if (post.text.isNotBlank()) {
                PlainPostText(post.text)
            }

            post.documents.forEach { document ->
                Spacer(Modifier.height(6.dp))
                DocumentCard(
                    document = document,
                    onClick = {
                        when {
                            document.isPdf -> onOpenPdf(document)
                            document.isAudio -> {
                                activeAudioUrl =
                                    if (activeAudioUrl == document.url) null else document.url
                            }
                            else -> onOpenSystem(document)
                        }
                    },
                    onOpenSystem = { onOpenSystem(document) },
                    onDownload = { onDownloadDocument(document) },
                )

                if (document.isAudio && activeAudioUrl == document.url) {
                    Spacer(Modifier.height(6.dp))
                    AudioDocumentPlayer(
                        repository = repository,
                        url = document.url,
                        title = document.title,
                    )
                }
            }

            if (post.date.isNotBlank() || post.views != null) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(
                    color = palette.second.copy(alpha = 0.22f),
                )
                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatDate(post.date),
                        style = MaterialTheme.typography.labelMedium,
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
                    modifier = Modifier
                        .align(Alignment.End)
                        .height(34.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "باز کردن پست",
                        style = MaterialTheme.typography.labelMedium,
                    )
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
    onOpenSystem: () -> Unit,
    onDownload: () -> Unit,
) {
    val accent = when (document.kind) {
        DocumentKind.PDF -> Color(0xFFD9485F)
        DocumentKind.AUDIO -> Color(0xFF6B5BD6)
        DocumentKind.PACKAGE -> Color(0xFF1C8B63)
        DocumentKind.ARCHIVE -> Color(0xFFD68122)
        DocumentKind.OFFICE -> Color(0xFF3678C8)
        DocumentKind.IMAGE -> Color(0xFFB84E93)
        DocumentKind.VIDEO -> Color(0xFF177C93)
        DocumentKind.OTHER -> MaterialTheme.colorScheme.primary
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = accent.copy(alpha = 0.085f),
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        accent.copy(alpha = 0.14f),
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when (document.kind) {
                        DocumentKind.PDF -> Icons.Default.PictureAsPdf
                        DocumentKind.AUDIO -> Icons.Default.Headphones
                        DocumentKind.PACKAGE -> Icons.Default.Android
                        DocumentKind.ARCHIVE -> Icons.Default.FolderZip
                        DocumentKind.OFFICE -> Icons.Default.Description
                        else -> Icons.Default.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.size(8.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = document.title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 21.sp),
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                document.extra?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = when (document.kind) {
                        DocumentKind.PDF -> "نمایش داخل برنامه • دانلود PDF"
                        DocumentKind.AUDIO -> "پخش داخل برنامه • دانلود صوت"
                        DocumentKind.PACKAGE -> "نصب یا دانلود APK"
                        DocumentKind.ARCHIVE -> "دانلود یا باز کردن"
                        DocumentKind.OFFICE -> "دانلود یا باز کردن"
                        else -> "دانلود یا باز کردن"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.size(4.dp))

            IconButton(
                onClick = onOpenSystem,
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        accent.copy(alpha = 0.10f),
                        CircleShape,
                    ),
            ) {
                Icon(
                    Icons.Default.OpenInNew,
                    contentDescription = "باز کردن با برنامه دیگر",
                    tint = accent,
                    modifier = Modifier.size(19.dp),
                )
            }

            Spacer(Modifier.size(4.dp))

            IconButton(
                onClick = onDownload,
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        accent.copy(alpha = 0.10f),
                        CircleShape,
                    ),
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "دانلود فایل",
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun PlainPostText(text: String) {
    val normalized = text.trim()
    if (normalized.isBlank()) return

    val titleEnd = headlineEnd(normalized)
    val styled = buildAnnotatedString {
        if (titleEnd > 0) {
            pushStyle(
                SpanStyle(
                    fontFamily = Rooznameh,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Normal,
                ),
            )
            append(normalized.substring(0, titleEnd).trim())
            pop()

            val body = normalized.substring(titleEnd).trim()
            if (body.isNotBlank()) {
                append("\n\n")
                append(body)
            }
        } else {
            append(normalized)
        }
    }

    Text(
        text = styled,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 17.sp,
            lineHeight = 31.sp,
        ),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun RichPostText(html: String) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val vazirmatn = remember {
        ResourcesCompat.getFont(context, R.font.vazirmatn_regular)
    }
    val rooznameh = remember {
        ResourcesCompat.getFont(context, R.font.a_rooznameh)
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        factory = { viewContext ->
            TextView(viewContext).apply {
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                textDirection = View.TEXT_DIRECTION_RTL
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                includeFontPadding = false
                breakStrategy = Layout.BREAK_STRATEGY_BALANCED
                hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
                movementMethod = LinkMovementMethod.getInstance()
                linksClickable = true
                setTextIsSelectable(true)
                setLineSpacing(4f, 1.14f)
                textSize = 17f
            }
        },
        update = { view ->
            val richText = SpannableStringBuilder(
                Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY),
            )
            val plain = richText.toString()
            val titleEnd = headlineEnd(plain)
            if (titleEnd > 0 && rooznameh != null) {
                richText.setSpan(
                    AppTypefaceSpan(rooznameh),
                    0,
                    titleEnd.coerceAtMost(richText.length),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }

            view.text = richText
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                view.justificationMode = Layout.JUSTIFICATION_MODE_NONE
            }
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            view.typeface = Typeface.create(vazirmatn, Typeface.NORMAL)
        },
    )
}

private fun headlineEnd(text: String): Int {
    val trimmedStart = text.indexOfFirst { !it.isWhitespace() }
        .takeIf { it >= 0 } ?: return 0
    val clean = text.substring(trimmedStart)

    val paragraphBreak = Regex("\\n\\s*\\n").find(clean)?.range?.first
    if (paragraphBreak != null && paragraphBreak in 1..180) {
        return trimmedStart + paragraphBreak
    }

    val firstLine = clean.indexOf('\n')
    if (firstLine in 1..140) {
        return trimmedStart + firstLine
    }

    return if (clean.length <= 110) text.length else 0
}

private class AppTypefaceSpan(
    private val customTypeface: Typeface,
) : MetricAffectingSpan() {
    override fun updateDrawState(textPaint: TextPaint) {
        textPaint.typeface = customTypeface
        textPaint.textSize *= 1.10f
    }

    override fun updateMeasureState(textPaint: TextPaint) {
        textPaint.typeface = customTypeface
        textPaint.textSize *= 1.10f
    }
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
                    value = "۰.۵.۰",
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

private fun safeDownloadName(document: TelegramDocument): String {
    val title = document.title.trim().ifBlank { "telegram-file" }
    return title.replace(Regex("""[\\/:*?"<>|]"""), "_")
}

private fun mimeTypeForDocument(document: TelegramDocument): String {
    val source = (document.title + " " + document.url).lowercase()
    return when {
        source.contains(".pdf") -> "application/pdf"
        source.contains(".apk") -> "application/vnd.android.package-archive"
        source.contains(".xapk") -> "application/octet-stream"
        source.contains(".zip") -> "application/zip"
        source.contains(".rar") -> "application/vnd.rar"
        source.contains(".7z") -> "application/x-7z-compressed"
        source.contains(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        source.contains(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        source.contains(".pptx") -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        source.contains(".doc") -> "application/msword"
        source.contains(".xls") -> "application/vnd.ms-excel"
        source.contains(".ppt") -> "application/vnd.ms-powerpoint"
        source.contains(".txt") -> "text/plain"
        source.contains(".csv") -> "text/csv"
        source.contains(".jpg") || source.contains(".jpeg") -> "image/jpeg"
        source.contains(".png") -> "image/png"
        source.contains(".webp") -> "image/webp"
        source.contains(".mp4") -> "video/mp4"
        source.contains(".mkv") -> "video/x-matroska"
        source.contains(".webm") -> "video/webm"
        source.contains(".mov") -> "video/quicktime"
        source.contains(".mp3") -> "audio/mpeg"
        source.contains(".m4a") -> "audio/mp4"
        source.contains(".aac") -> "audio/aac"
        source.contains(".ogg") || source.contains(".opus") -> "audio/ogg"
        source.contains(".wav") -> "audio/wav"
        source.contains(".flac") -> "audio/flac"
        source.contains(".gif") -> "image/gif"
        source.contains(".json") -> "application/json"
        source.contains(".xml") -> "application/xml"
        else -> "application/octet-stream"
    }
}

@Composable
private fun rememberNetworkAvailable(): Boolean {
    val context = LocalContext.current
    val connectivityManager = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    fun currentStatus(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    var available by remember { mutableStateOf(currentStatus()) }

    DisposableEffect(connectivityManager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                available = true
            }

            override fun onLost(network: Network) {
                available = currentStatus()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                available =
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)
        available = currentStatus()

        onDispose {
            runCatching {
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }
    }

    return available
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

    val localDateTime = runCatching {
        OffsetDateTime.parse(raw)
            .atZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime()
    }.getOrElse {
        runCatching {
            LocalDateTime.parse(raw.replace("Z", ""))
        }.getOrNull()
    } ?: return toPersianDigits(
        raw.replace("T", " ").replace("Z", "").take(16),
    )

    val jalali = gregorianToJalali(
        localDateTime.year,
        localDateTime.monthValue,
        localDateTime.dayOfMonth,
    )
    val monthNames = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )
    val dateText = "${jalali[2]} ${monthNames[jalali[1] - 1]} ${jalali[0]}"
    val timeText = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    return toPersianDigits("$dateText • $timeText")
}

private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): IntArray {
    val gDayNo = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 355666 +
        365 * gy +
        (gy2 + 3) / 4 -
        (gy2 + 99) / 100 +
        (gy2 + 399) / 400 +
        gd +
        gDayNo[gm - 1]

    var jy = -1595 + 33 * (days / 12053)
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461

    if (days > 365) {
        jy += (days - 1) / 365
        days = (days - 1) % 365
    }

    val jm: Int
    val jd: Int
    if (days < 186) {
        jm = 1 + days / 31
        jd = 1 + days % 31
    } else {
        jm = 7 + (days - 186) / 30
        jd = 1 + (days - 186) % 30
    }

    return intArrayOf(jy, jm, jd)
}

private fun newsCardPalette(postId: Long, dark: Boolean): Pair<Color, Color> {
    val light = listOf(
        Color(0xFFF0F7FF) to Color(0xFF3678C8),
        Color(0xFFF3F0FF) to Color(0xFF6B5BD6),
        Color(0xFFFFF3EA) to Color(0xFFD68122),
        Color(0xFFECFAF4) to Color(0xFF1C8B63),
        Color(0xFFFFEEF3) to Color(0xFFD9485F),
        Color(0xFFF0FAFC) to Color(0xFF177C93),
    )
    val darkPalette = listOf(
        Color(0xFF182330) to Color(0xFF71A6E4),
        Color(0xFF211E34) to Color(0xFF9C8FED),
        Color(0xFF30251B) to Color(0xFFE2A251),
        Color(0xFF172B24) to Color(0xFF55B98F),
        Color(0xFF321E25) to Color(0xFFE77B8C),
        Color(0xFF18292D) to Color(0xFF58AABD),
    )
    val palette = if (dark) darkPalette else light
    val index = ((postId % palette.size + palette.size) % palette.size).toInt()
    return palette[index]
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
