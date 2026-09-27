package com.telegramreader.fa

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.InetSocketAddress
import java.net.Proxy

private const val PREFS = "telegram_reader_fa"
private const val CHANNELS = "channels"

data class TelegramPost(
    val id: String,
    val text: String,
    val date: String,
    val imageUrl: String?,
    val postUrl: String?
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    val channels = remember {
        mutableStateListOf<String>().apply {
            addAll(prefs.getStringSet(CHANNELS, emptySet()).orEmpty().sorted())
        }
    }

    var darkMode by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf("home") }
    var selectedChannel by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = if (darkMode) darkColorScheme() else lightColorScheme()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                if (page == "feed" && selectedChannel != null) "@$selectedChannel"
                                else "تلگرام‌خوان فارسی"
                            )
                        },
                        navigationIcon = {
                            if (page == "feed") {
                                IconButton(onClick = { page = "channels" }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت")
                                }
                            }
                        }
                    )
                },
                bottomBar = {
                    if (page != "feed") {
                        NavigationBar {
                            NavigationBarItem(
                                selected = page == "home",
                                onClick = { page = "home" },
                                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                                label = { Text("خانه") }
                            )
                            NavigationBarItem(
                                selected = page == "channels",
                                onClick = { page = "channels" },
                                icon = { Icon(Icons.Default.RssFeed, contentDescription = null) },
                                label = { Text("کانال‌ها") }
                            )
                            NavigationBarItem(
                                selected = page == "settings",
                                onClick = { page = "settings" },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text("تنظیمات") }
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
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    when (page) {
                        "home" -> HomeScreen(
                            channels = channels,
                            onOpen = {
                                selectedChannel = it
                                page = "feed"
                            },
                            onAdd = { showAdd = true }
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
                            }
                        )
                        "settings" -> SettingsScreen(
                            darkMode = darkMode,
                            onDarkModeChange = { darkMode = it }
                        )
                        "feed" -> selectedChannel?.let { FeedScreen(it) }
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
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    channels: List<String>,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("کانال‌های شما", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                if (channels.isEmpty()) "برای شروع یک کانال عمومی تلگرام اضافه کنید."
                else "برای دیدن آخرین پست‌ها روی کانال بزنید.",
                style = MaterialTheme.typography.bodyMedium
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
    onRemove: (String) -> Unit
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        items(channels) { channel ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(channel) },
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("@$channel", fontWeight = FontWeight.Bold)
                        Text("کانال عمومی تلگرام", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onRemove(channel) }) {
                        Text("حذف")
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("@$channel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("مشاهده آخرین پست‌ها", style = MaterialTheme.typography.bodySmall)
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
                    label = { Text("@username یا t.me/username") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(value) }, enabled = normalizeChannel(value).isNotBlank()) {
                Text("افزودن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
private fun FeedScreen(channel: String) {
    val context = LocalContext.current
    var posts by remember(channel) { mutableStateOf<List<TelegramPost>>(emptyList()) }
    var loading by remember(channel) { mutableStateOf(true) }
    var error by remember(channel) { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(channel, refreshKey) {
        loading = true
        error = null
        runCatching { TelegramRepository.fetchPosts(context, channel) }
            .onSuccess { posts = it }
            .onFailure { error = it.message ?: "خطا در دریافت اطلاعات" }
        loading = false
    }

    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        error != null -> Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(error ?: "خطا")
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { refreshKey++ }) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("تلاش دوباره")
            }
        }
        posts.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("پستی پیدا نشد.")
        }
        else -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            items(posts, key = { it.id }) { post ->
                PostCard(post)
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun PostCard(post: TelegramPost) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            post.imageUrl?.let { url ->
                Image(
                    painter = rememberAsyncImagePainter(url),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                )
                Spacer(Modifier.height(12.dp))
            }

            if (post.text.isNotBlank()) {
                Text(
                    post.text,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 20,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
            }

            Text(
                post.date.ifBlank { "تلگرام" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    var enabled by remember { mutableStateOf(prefs.getBoolean("proxy_enabled", false)) }
    var host by remember { mutableStateOf(prefs.getString("proxy_host", "") ?: "") }
    var port by remember { mutableStateOf(prefs.getInt("proxy_port", 8080).toString()) }
    var type by remember { mutableStateOf(prefs.getString("proxy_type", "HTTP") ?: "HTTP") }
    var saved by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("ظاهر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("حالت تیره", modifier = Modifier.weight(1f))
                Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("پراکسی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("استفاده از پراکسی", modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = {
                    enabled = it
                    saved = false
                })
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { type = "HTTP"; saved = false },
                    modifier = Modifier.weight(1f)
                ) { Text(if (type == "HTTP") "✓ HTTP" else "HTTP") }
                OutlinedButton(
                    onClick = { type = "SOCKS"; saved = false },
                    modifier = Modifier.weight(1f)
                ) { Text(if (type == "SOCKS") "✓ SOCKS" else "SOCKS") }
            }
        }
        item {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it; saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("آدرس پراکسی") },
                singleLine = true
            )
        }
        item {
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit); saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("پورت") },
                singleLine = true
            )
        }
        item {
            Button(
                onClick = {
                    prefs.edit()
                        .putBoolean("proxy_enabled", enabled)
                        .putString("proxy_host", host.trim())
                        .putInt("proxy_port", port.toIntOrNull() ?: 8080)
                        .putString("proxy_type", type)
                        .apply()
                    saved = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (saved) "ذخیره شد" else "ذخیره تنظیمات")
            }
        }
        item {
            Text(
                "نسخه 0.1.0 — دریافت پست‌های کانال عمومی از نسخه وب تلگرام",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun normalizeChannel(raw: String): String {
    return raw.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .removePrefix("t.me/")
        .removePrefix("telegram.me/")
        .removePrefix("@")
        .substringBefore("?")
        .substringBefore("/")
        .filter { it.isLetterOrDigit() || it == '_' }
}

object TelegramRepository {
    suspend fun fetchPosts(context: Context, channel: String): List<TelegramPost> =
        withContext(Dispatchers.IO) {
            val client = buildClient(context)
            val request = Request.Builder()
                .url("https://t.me/s/$channel")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36"
                )
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("خطای شبکه: ${response.code}")
                }

                val html = response.body?.string().orEmpty()
                if (html.isBlank()) error("پاسخی از تلگرام دریافت نشد.")

                val document = Jsoup.parse(html, "https://t.me")
                document.select(".tgme_widget_message_wrap")
                    .mapNotNull { wrap ->
                        val message = wrap.selectFirst(".tgme_widget_message") ?: return@mapNotNull null
                        val id = message.attr("data-post").ifBlank {
                            message.selectFirst("a.tgme_widget_message_date")?.attr("href").orEmpty()
                        }
                        val text = message.selectFirst(".tgme_widget_message_text")?.text().orEmpty()
                        val date = message.selectFirst("time")?.attr("datetime").orEmpty()
                        val postUrl = message.selectFirst("a.tgme_widget_message_date")
                            ?.absUrl("href")
                            ?.ifBlank { null }
                        val media = message.selectFirst(
                            ".tgme_widget_message_photo_wrap, .tgme_widget_message_video_thumb"
                        )
                        val image = media?.attr("style")
                            ?.let(::extractBackgroundImage)

                        if (id.isBlank() && text.isBlank() && image == null) null
                        else TelegramPost(
                            id = id.ifBlank { "$channel-$date-$text".hashCode().toString() },
                            text = text,
                            date = date,
                            imageUrl = image,
                            postUrl = postUrl
                        )
                    }
                    .reversed()
            }
        }

    private fun buildClient(context: Context): OkHttpClient {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val builder = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)

        if (prefs.getBoolean("proxy_enabled", false)) {
            val host = prefs.getString("proxy_host", "").orEmpty().trim()
            val port = prefs.getInt("proxy_port", 8080)
            val proxyType = prefs.getString("proxy_type", "HTTP")
            if (host.isNotBlank() && port in 1..65535) {
                val type = if (proxyType == "SOCKS") Proxy.Type.SOCKS else Proxy.Type.HTTP
                builder.proxy(Proxy(type, InetSocketAddress(host, port)))
            }
        }

        return builder.build()
    }

    private fun extractBackgroundImage(style: String): String? {
        val regex = Regex("""background-image\s*:\s*url\(['"]?(.*?)['"]?\)""")
        return regex.find(style)?.groupValues?.getOrNull(1)
            ?.replace("&amp;", "&")
            ?.takeIf { it.startsWith("http") }
    }
}
