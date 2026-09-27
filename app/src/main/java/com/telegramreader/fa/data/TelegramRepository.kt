package com.telegramreader.fa.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

private const val PREFS = "telegram_reader_fa"

private data class ProxySnapshot(
    val enabled: Boolean,
    val host: String,
    val port: Int,
    val type: String,
)

class TelegramRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
            if (!response.isSuccessful) error("خطای شبکه: \${response.code}")
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

    fun downloadPdf(url: String, title: String): File {
        val safeName = sanitizeFileName(
            title.ifBlank { "document.pdf" },
        ).let { if (it.lowercase().endsWith(".pdf")) it else "$it.pdf" }

        val key = sha256(url).take(20)
        val dir = File(context.cacheDir, "pdf").also { it.mkdirs() }
        val target = File(dir, "${key}_$safeName")

        if (target.exists() && target.length() > 5L && looksLikePdf(target)) {
            target.setLastModified(System.currentTimeMillis())
            return target
        }

        target.delete()
        val temp = File(dir, "$key.download").also { it.delete() }

        downloadToFile(
            url = url,
            title = safeName,
            target = temp,
            expectedPdf = true,
        )

        if (!looksLikePdf(temp)) {
            temp.delete()
            error("فایل دریافت‌شده PDF معتبر نیست.")
        }

        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }

        trimPdfCache(dir)
        return target
    }

    fun downloadFileToUri(
        url: String,
        title: String,
        destination: Uri,
    ) {
        val resolver = context.contentResolver
        resolver.openOutputStream(destination, "w")?.buffered()?.use { output ->
            openResolvedDownload(url, title).use { response ->
                val body = response.body ?: error("فایل خالی است.")
                body.byteStream().buffered().use { input ->
                    input.copyTo(output)
                }
            }
        } ?: error("محل ذخیره فایل قابل دسترسی نیست.")
    }

    private fun downloadToFile(
        url: String,
        title: String,
        target: File,
        expectedPdf: Boolean,
    ) {
        openResolvedDownload(url, title).use { response ->
            val body = response.body ?: error("فایل خالی است.")
            val contentLength = body.contentLength()

            if (expectedPdf && contentLength > MAX_PDF_BYTES) {
                error("حجم PDF برای نمایش داخل برنامه بیش از حد زیاد است.")
            }

            target.outputStream().buffered().use { output ->
                body.byteStream().buffered().use { input ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun openResolvedDownload(
        initialUrl: String,
        title: String,
        depth: Int = 0,
    ): okhttp3.Response {
        val request = Request.Builder()
            .url(initialUrl)
            .header("Accept", "*/*")
            .header("Referer", "https://t.me/")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        val response = client().newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            error("دانلود فایل ناموفق بود: $code")
        }

        val type = response.header("Content-Type").orEmpty().lowercase()
        val isHtml = type.contains("text/html") || type.contains("application/xhtml")
        if (!isHtml || depth >= 2) return response

        val body = response.body ?: return response
        val html = body.string()
        val baseUrl = response.request.url.toString()
        response.close()

        val expectedExt = title.substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.length in 2..8 }

        val document = Jsoup.parse(html, baseUrl)
        val candidates = document.select("a[href]").mapNotNull { anchor ->
            val href = anchor.absUrl("href").ifBlank { null } ?: return@mapNotNull null
            if (!href.startsWith("http")) return@mapNotNull null

            val lower = href.lowercase()
            var score = 0
            if (anchor.hasAttr("download")) score += 8
            if (anchor.classNames().any { it.contains("document", ignoreCase = true) }) score += 6
            if (lower.contains("download")) score += 4
            if (expectedExt != null && lower.contains(".$expectedExt")) score += 10
            if (looksLikeKnownFileUrl(lower)) score += 5
            if (href == initialUrl) score -= 50

            href to score
        }.sortedByDescending { it.second }

        val candidate = candidates.firstOrNull { it.second > 0 }?.first
        if (candidate != null && candidate != initialUrl) {
            return openResolvedDownload(candidate, title, depth + 1)
        }

        error("لینک تلگرام به فایل مستقیم تبدیل نشد. فایل را از پست اصلی دوباره امتحان کنید.")
    }

    private fun looksLikeKnownFileUrl(lowerUrl: String): Boolean {
        val extensions = listOf(
            ".pdf", ".zip", ".rar", ".7z", ".apk", ".xapk",
            ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".txt", ".csv", ".json", ".xml", ".epub",
            ".mp3", ".m4a", ".ogg", ".wav", ".mp4", ".mkv", ".webm",
            ".jpg", ".jpeg", ".png", ".webp", ".gif",
        )
        return extensions.any { lowerUrl.contains(it) }
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
        }.distinctBy { it.url }

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

    private fun looksLikePdf(file: File): Boolean {
        if (!file.exists() || file.length() < 5L) return false
        return runCatching {
            file.inputStream().buffered().use { input ->
                val header = ByteArray(5)
                if (input.read(header) != header.size) return@use false
                String(header, Charsets.US_ASCII) == "%PDF-"
            }
        }.getOrDefault(false)
    }

    private fun trimPdfCache(dir: File) {
        val files = dir.listFiles()
            ?.filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
            ?.sortedBy { it.lastModified() }
            ?.toMutableList()
            ?: return

        var total = files.sumOf { it.length() }
        while (total > MAX_PDF_CACHE_BYTES && files.isNotEmpty()) {
            val file = files.removeAt(0)
            total -= file.length()
            file.delete()
        }
    }

    private fun sanitizeFileName(input: String): String =
        input.replace(Regex("""[\\/:*?"<>|]"""), "_")
            .take(120)
            .ifBlank { "document.pdf" }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128 Mobile Safari/537.36"

        private const val MAX_PDF_BYTES = 120L * 1024L * 1024L
        private const val MAX_PDF_CACHE_BYTES = 256L * 1024L * 1024L
    }
}
