package com.telegramreader.fa.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

private const val PREFS = "telegram_reader_fa"
private const val READER_BACKEND_BASE_URL = "https://reader.duckpsycho.dev"
private val READER_HTML_MEDIA_ATTR =
    Regex("""\b(src|poster)=(["'])([^"']+)\2""", RegexOption.IGNORE_CASE)

private data class ProxySnapshot(
    val enabled: Boolean,
    val host: String,
    val port: Int,
    val type: String,
)

class TelegramRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val cookieJar = PersistentMediaCookieJar(context)

    private val mediaResolver by lazy {
        MediaResolver(
            clientProvider = { client() },
            cookieJar = cookieJar,
            backendBaseUrl = READER_BACKEND_BASE_URL,
        )
    }

    private val mediaDownloadManager by lazy {
        MediaDownloadManager(
            context = context,
            clientProvider = { client() },
        )
    }

    private val mediaTypeRouter by lazy {
        MediaTypeRouter(mediaDownloadManager)
    }

    private val httpCache by lazy {
        Cache(File(context.cacheDir, "http_cache"), 256L * 1024L * 1024L)
    }

    private val dispatcher by lazy {
        Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 12
        }
    }

    private val connectionPool by lazy {
        ConnectionPool(8, 5, TimeUnit.MINUTES)
    }

    @Volatile
    private var clientEntry: Pair<ProxySnapshot, OkHttpClient>? = null

    fun client(): OkHttpClient {
        val snapshot = proxySnapshot()
        clientEntry?.let { (saved, client) ->
            if (saved == snapshot) return client
        }

        return synchronized(this) {
            clientEntry?.let { (saved, client) ->
                if (saved == snapshot) return@synchronized client
            }

            val builder = OkHttpClient.Builder()
                .cache(httpCache)
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .cookieJar(cookieJar)

            if (snapshot.enabled && snapshot.host.isNotBlank() && snapshot.port in 1..65535) {
                val proxyType = if (snapshot.type == "SOCKS") Proxy.Type.SOCKS else Proxy.Type.HTTP
                builder.proxy(Proxy(proxyType, InetSocketAddress(snapshot.host, snapshot.port)))
            }

            builder.build().also { clientEntry = snapshot to it }
        }
    }

    fun invalidateClient() {
        synchronized(this) {
            clientEntry = null
        }
    }

    fun fetchPosts(channel: String, before: Long? = null): PostsPage {
        // The upstream Telegram Reader does not connect to Telegram directly from Android.
        // It calls its normal HTTPS REST backend and polls it periodically.  That backend
        // retrieves public Telegram content server-side, so the Android client itself does
        // not need a Telegram account, WebSocket, MTProto session or VPN.
        val backendResult = runCatching {
            fetchPostsFromReaderBackend(channel, before)
        }
        if (backendResult.isSuccess) {
            return backendResult.getOrThrow()
        }

        // Keep our old Telegram Web parser as a fallback in case the reader backend is
        // temporarily unavailable.
        return fetchPostsFromTelegramWeb(channel, before)
    }

    private fun fetchPostsFromReaderBackend(
        channel: String,
        before: Long?,
    ): PostsPage {
        val encoded = java.net.URLEncoder
            .encode(channel, Charsets.UTF_8.name())
            .replace("+", "%20")

        val url = buildString {
            append(READER_BACKEND_BASE_URL)
            append("/api/channels/")
            append(encoded)
            append("/posts")
            if (before != null) {
                append("?before=")
                append(before)
            }
        }

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Accept-Language", "en")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        client().newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("سرویس خوانش تلگرام پاسخ نداد: ${response.code}")
            }

            val raw = response.body?.string().orEmpty()
            if (raw.isBlank()) error("پاسخ سرویس خوانش خالی بود.")

            return parseReaderBackendPage(
                json = JSONObject(raw),
                requestedChannel = channel,
            )
        }
    }

    private fun parseReaderBackendPage(
        json: JSONObject,
        requestedChannel: String,
    ): PostsPage {
        val channelJson = json.optJSONObject("channel")
        val channel = channelJson?.let { item ->
            ChannelInfo(
                username = item.optString("username").ifBlank { requestedChannel },
                title = item.optString("title").ifBlank { requestedChannel },
                description = item.optStringOrNull("description"),
                subscriberCount = item.optStringOrNull("subscriberCount"),
                photoUrl = normalizeReaderUrl(item.optStringOrNull("photoUrl")),
            )
        }

        val postsJson = json.optJSONArray("posts")
        val posts = buildList {
            if (postsJson != null) {
                for (index in 0 until postsJson.length()) {
                    val item = postsJson.optJSONObject(index) ?: continue
                    val id = item.optLong("id", -1L)
                    if (id <= 0L) continue

                    val media = mutableListOf<TelegramMedia>()

                    item.optJSONObject("mediaGroup")
                        ?.optJSONArray("items")
                        ?.let { group ->
                            for (mediaIndex in 0 until group.length()) {
                                val mediaItem = group.optJSONObject(mediaIndex) ?: continue
                                val mediaUrl = normalizeReaderUrl(
                                    mediaItem.optStringOrNull("url"),
                                ) ?: continue

                                val kind = when (mediaItem.optString("type").lowercase()) {
                                    "video" -> MediaKind.VIDEO
                                    else -> MediaKind.PHOTO
                                }
                                media += TelegramMedia(kind = kind, url = mediaUrl)
                            }
                        }

                    if (media.isEmpty()) {
                        val mediaUrl = normalizeReaderUrl(
                            item.optStringOrNull("mediaUrl"),
                        )
                        val mediaType = item.optStringOrNull("mediaType")?.lowercase()
                        if (!mediaUrl.isNullOrBlank()) {
                            val videoAttrs = item.optJSONObject("mediaVideoAttrs")
                            val kind = when (mediaType) {
                                "photo" -> MediaKind.PHOTO
                                "sticker" -> MediaKind.STICKER
                                "videosticker" -> MediaKind.GIF
                                "video" -> {
                                    if (videoAttrs?.optBoolean("autoplay", false) == true) {
                                        MediaKind.GIF
                                    } else {
                                        MediaKind.VIDEO
                                    }
                                }
                                else -> null
                            }
                            if (kind != null) {
                                media += TelegramMedia(
                                    kind = kind,
                                    url = mediaUrl,
                                )
                            }
                        }
                    }

                    val documents = mutableListOf<TelegramDocument>()
                    item.optJSONObject("document")?.let { document ->
                        val documentUrl = normalizeReaderUrl(
                            document.optStringOrNull("url"),
                        )
                        if (!documentUrl.isNullOrBlank()) {
                            documents += TelegramDocument(
                                title = document.optString("title")
                                    .ifBlank { "فایل" },
                                extra = document.optStringOrNull("extra"),
                                url = documentUrl,
                            )
                        }
                    }

                    val backendMediaType = item.optStringOrNull("mediaType")?.lowercase()
                    val backendMediaUrl = normalizeReaderUrl(
                        item.optStringOrNull("mediaUrl"),
                    )
                    if (
                        documents.isEmpty() &&
                        !backendMediaUrl.isNullOrBlank() &&
                        backendMediaType in setOf("audio", "voice")
                    ) {
                        documents += TelegramDocument(
                            title = if (backendMediaType == "voice") {
                                "پیام صوتی.ogg"
                            } else {
                                "فایل صوتی.mp3"
                            },
                            extra = "پخش داخل برنامه",
                            url = backendMediaUrl,
                        )
                    }

                    add(
                        TelegramPost(
                            id = id,
                            text = item.optStringOrNull("text").orEmpty(),
                            html = absolutizeReaderHtml(
                                item.optStringOrNull("html"),
                            ),
                            date = item.optStringOrNull("date").orEmpty(),
                            views = item.optStringOrNull("views"),
                            postUrl = "https://t.me/$requestedChannel/$id",
                            forwardedFrom = item.optStringOrNull("forwardedFrom"),
                            media = media.distinctBy { it.kind.name + "|" + it.url },
                            documents = documents,
                        ),
                    )
                }
            }
        }.sortedByDescending { it.id }

        val nextBefore = if (json.has("nextBefore") && !json.isNull("nextBefore")) {
            json.optLong("nextBefore").takeIf { it > 0L }
        } else {
            null
        }

        return PostsPage(
            channel = channel,
            posts = posts,
            nextBefore = nextBefore,
            hasMore = json.optBoolean("hasMore", nextBefore != null),
        )
    }

    private fun fetchPostsFromTelegramWeb(
        channel: String,
        before: Long?,
    ): PostsPage {
        val url = buildString {
            append("https://t.me/s/")
            append(channel)
            if (before != null) {
                append("?before=")
                append(before)
            }
        }

        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.7")
            .header("Referer", "https://t.me/")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        client().newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("خطای شبکه: ${response.code}")
            val html = response.body?.string().orEmpty()
            if (html.isBlank()) error("پاسخی از تلگرام دریافت نشد.")

            val document = Jsoup.parse(html, "https://t.me")
            val channelInfo = parseChannelInfo(document, channel)
            val posts = document.select(".tgme_widget_message_wrap")
                .mapNotNull { parsePost(it, channel) }
                .distinctBy { it.id }
                .sortedByDescending { it.id }

            val nextBefore = posts.minOfOrNull { it.id }
            return PostsPage(
                channel = channelInfo,
                posts = posts,
                nextBefore = nextBefore,
                hasMore = posts.size >= 10 && nextBefore != null,
            )
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun normalizeReaderUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> READER_BACKEND_BASE_URL + url
            else -> url
        }
    }

    private fun absolutizeReaderHtml(html: String?): String? {
        if (html.isNullOrBlank()) return html
        return READER_HTML_MEDIA_ATTR.replace(html) { match ->
            val attr = match.groupValues[1]
            val quote = match.groupValues[2]
            val rawUrl = match.groupValues[3]
            val absolute = normalizeReaderUrl(rawUrl) ?: rawUrl
            "$attr=$quote$absolute$quote"
        }
    }

    /**
     * One entry point for every document. All callers share resolver, cookies, redirects,
     * MIME sniffing, cache and download semantics.
     */
    fun resolveMedia(
        url: String,
        title: String,
        expectedMime: String? = null,
    ): ResolvedMedia =
        mediaResolver.resolveMedia(
            documentUrl = url,
            suggestedName = title,
            expectedMime = expectedMime,
        )

    fun routeMedia(
        url: String,
        title: String,
        expectedMime: String? = null,
    ): MediaAction {
        val resolved = resolveMedia(url, title, expectedMime)
        return mediaTypeRouter.route(resolved)
    }

    fun downloadPdf(url: String, title: String): File {
        val action = routeMedia(
            url = url,
            title = title,
            expectedMime = "application/pdf",
        )
        return when (action) {
            is MediaAction.OpenInternalPdfViewer -> action.file
            else -> throw MediaResolutionException.InvalidContent(
                "این فایل به‌عنوان PDF معتبر تشخیص داده نشد.",
            )
        }
    }

    fun downloadFileToUri(
        url: String,
        title: String,
        destination: Uri,
    ) {
        val resolved = resolveMedia(
            url = url,
            title = title,
            expectedMime = MediaSniffer.mimeFromName(title),
        )
        mediaDownloadManager.downloadToUri(resolved, destination)
    }

    fun downloadFileToCache(
        url: String,
        title: String,
    ): File {
        val resolved = resolveMedia(
            url = url,
            title = title,
            expectedMime = MediaSniffer.mimeFromName(title),
        )
        return mediaDownloadManager.downloadToCache(resolved)
    }

    fun prepareAudio(
        url: String,
        title: String,
    ): MediaAction {
        val resolved = resolveMedia(
            url = url,
            title = title,
            expectedMime = MediaSniffer.mimeFromName(title) ?: "audio/mpeg",
        )
        val action = mediaTypeRouter.route(resolved)
        if (
            action !is MediaAction.PlayAudioStream &&
            action !is MediaAction.PlayAudioFile
        ) {
            throw MediaResolutionException.InvalidContent(
                "فایل دریافتی صوت معتبر تشخیص داده نشد.",
            )
        }
        return action
    }

    private fun proxySnapshot(): ProxySnapshot = ProxySnapshot(
        enabled = prefs.getBoolean("proxy_enabled", false),
        host = prefs.getString("proxy_host", "").orEmpty().trim(),
        port = prefs.getInt("proxy_port", 8080),
        type = prefs.getString("proxy_type", "HTTP").orEmpty().uppercase(),
    )

    private fun parseChannelInfo(
        document: org.jsoup.nodes.Document,
        username: String,
    ): ChannelInfo {
        val title = document.selectFirst(".tgme_channel_info_header_title")?.text()?.trim()
            ?: document.selectFirst(".tgme_channel_info_header_title span")?.text()?.trim()
            ?: username

        val description = document.selectFirst(".tgme_channel_info_description")
            ?.text()
            ?.trim()

        val subscriberCount = document
            .selectFirst(".tgme_channel_info_counter .counter_value")
            ?.text()
            ?.trim()
            ?: document.selectFirst(".tgme_channel_info_counter")
                ?.text()
                ?.trim()

        val photoUrl = document
            .selectFirst(".tgme_page_photo_image img, .tgme_channel_info_header img")
            ?.let { absoluteUrl(it, "src") }

        return ChannelInfo(
            username = username,
            title = title,
            description = description,
            subscriberCount = subscriberCount,
            photoUrl = photoUrl,
        )
    }

    private fun parsePost(wrap: Element, channel: String): TelegramPost? {
        val message = wrap.selectFirst(".tgme_widget_message") ?: return null
        val dataPost = message.attr("data-post")
        val id = dataPost.substringAfterLast("/", "").toLongOrNull()
            ?: message.selectFirst("a.tgme_widget_message_date")
                ?.attr("href")
                ?.substringAfterLast("/")
                ?.substringBefore("?")
                ?.toLongOrNull()
            ?: return null

        val textElement = message.selectFirst(".tgme_widget_message_text")
        val text = textElement?.text().orEmpty()
        val html = textElement?.html()?.takeIf { it.isNotBlank() }
        val date = message.selectFirst("time")?.attr("datetime").orEmpty()
        val views = message.selectFirst(".tgme_widget_message_views")?.text()?.trim()
        val postUrl = message
            .selectFirst("a.tgme_widget_message_date")
            ?.let { absoluteUrl(it, "href") }

        val forwardedFrom = message.selectFirst(
            ".tgme_widget_message_forwarded_from_name, .tgme_widget_message_forwarded_from",
        )?.text()?.trim()

        val media = mutableListOf<TelegramMedia>()

        message.select(".tgme_widget_message_photo_wrap").forEach { photo ->
            backgroundImageUrl(photo.attr("style"))?.let { url ->
                media += TelegramMedia(MediaKind.PHOTO, url)
            }
        }

        message.select("video").forEach { video ->
            val src = absoluteUrl(video, "src")
                ?: video.selectFirst("source")?.let { absoluteUrl(it, "src") }

            if (!src.isNullOrBlank()) {
                val poster = absoluteUrl(video, "poster")
                val isGifLike = video.hasAttr("loop") ||
                    video.hasAttr("muted") ||
                    video.classNames().any { it.contains("gif", ignoreCase = true) }

                media += TelegramMedia(
                    kind = if (isGifLike) MediaKind.GIF else MediaKind.VIDEO,
                    url = src,
                    previewUrl = poster,
                )
            }
        }

        message.select(
            "img.tgme_widget_message_sticker, .tgme_widget_message_sticker img",
        ).forEach { sticker ->
            absoluteUrl(sticker, "src")?.let { url ->
                media += TelegramMedia(MediaKind.STICKER, url)
            }
        }

        message.select(".tgme_widget_message_video_thumb").forEach { thumb ->
            val preview = backgroundImageUrl(thumb.attr("style"))
            val parentHref = thumb.closest("a")?.let { absoluteUrl(it, "href") }
            if (!parentHref.isNullOrBlank() && isLikelyMediaUrl(parentHref)) {
                media += TelegramMedia(
                    kind = MediaKind.VIDEO,
                    url = parentHref,
                    previewUrl = preview,
                )
            }
        }

        val documents = message.select(
            "a.tgme_widget_message_document_wrap, .tgme_widget_message_document_wrap a",
        ).mapNotNull { item ->
            val href = absoluteUrl(item, "href") ?: return@mapNotNull null
            if (!href.startsWith("http")) return@mapNotNull null

            val title = item.selectFirst(".tgme_widget_message_document_title")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: href.substringAfterLast("/")
                    .substringBefore("?")
                    .ifBlank { "فایل" }

            val extra = item.selectFirst(".tgme_widget_message_document_extra")
                ?.text()
                ?.trim()

            TelegramDocument(
                title = title,
                extra = extra,
                url = href,
            )
        }.distinctBy { it.url }.toMutableList()

        message.select("audio").forEach { audio ->
            val src = absoluteUrl(audio, "src")
                ?: audio.selectFirst("source")?.let { absoluteUrl(it, "src") }
            if (!src.isNullOrBlank() && documents.none { it.url == src }) {
                documents += TelegramDocument(
                    title = "فایل صوتی.mp3",
                    extra = "پخش داخل برنامه",
                    url = src,
                )
            }
        }

        val cleanMedia = media
            .filter { it.url.startsWith("http") }
            .distinctBy { it.kind.name + "|" + it.url }

        if (text.isBlank() && cleanMedia.isEmpty() && documents.isEmpty()) return null

        return TelegramPost(
            id = id,
            text = text,
            html = html,
            date = date,
            views = views,
            postUrl = postUrl,
            forwardedFrom = forwardedFrom,
            media = cleanMedia,
            documents = documents,
        )
    }

    private fun absoluteUrl(element: Element, attr: String): String? {
        val raw = element.attr(attr).trim()
        if (raw.isBlank()) return null
        return when {
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("/") -> "https://t.me$raw"
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            else -> element.absUrl(attr).ifBlank { null }
        }
    }

    private fun backgroundImageUrl(style: String): String? {
        val raw = Regex("""url\((['"]?)(.*?)\1\)""")
            .find(style)
            ?.groupValues
            ?.getOrNull(2)
            ?.replace("&amp;", "&")
            ?.trim()
            ?: return null

        return when {
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            else -> null
        }
    }

    private fun isLikelyMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".mp4") ||
            lower.contains(".webm") ||
            lower.contains(".mov") ||
            lower.contains("cdn") ||
            lower.contains("telegram-cdn")
    }

    companion object {
        private const val MAX_DOCUMENT_CACHE_BYTES = 512L * 1024L * 1024L
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128 Mobile Safari/537.36"

        private const val MAX_PDF_BYTES = 120L * 1024L * 1024L
        private const val MAX_PDF_CACHE_BYTES = 256L * 1024L * 1024L
    }
}
