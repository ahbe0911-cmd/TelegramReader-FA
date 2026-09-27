package com.telegramreader.fa.data

import android.content.Context
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.MessageDigest
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

private const val PREFS = "telegram_reader_fa"

class TelegramRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val httpCache by lazy {
        Cache(File(context.cacheDir, "http_cache"), 128L * 1024L * 1024L)
    }

    fun client(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cache(httpCache)
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

    fun downloadDocument(url: String, title: String): File {
        val safeName = sanitizeFileName(title.ifBlank { "document" })
        val key = sha256(url).take(16)
        val dir = File(context.cacheDir, "documents").also { it.mkdirs() }
        val target = File(dir, "\${key}_\${safeName}")
        if (target.exists() && target.length() > 0L) return target

        val request = Request.Builder()
            .url(url)
            .header("Accept", "*/*")
            .header("Referer", "https://t.me/")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        client().newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("دانلود فایل ناموفق بود: \${response.code}")
            val body = response.body ?: error("فایل خالی است.")
            target.outputStream().use { output ->
                body.byteStream().use { input -> input.copyTo(output) }
            }
        }
        return target
    }

    private fun parseChannelInfo(document: org.jsoup.nodes.Document, username: String): ChannelInfo? {
        val title = document.selectFirst(".tgme_channel_info_header_title")?.text()?.trim()
            ?: document.selectFirst(".tgme_channel_info_header_title span")?.text()?.trim()
            ?: username

        val description = document.selectFirst(".tgme_channel_info_description")?.text()?.trim()
        val subscriberCount = document.selectFirst(".tgme_channel_info_counter .counter_value")?.text()?.trim()
            ?: document.selectFirst(".tgme_channel_info_counter")?.text()?.trim()
        val photoUrl = document.selectFirst(".tgme_page_photo_image img, .tgme_channel_info_header img")
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
        val postUrl = message.selectFirst("a.tgme_widget_message_date")?.let { absoluteUrl(it, "href") }
        val forwardedFrom = message.selectFirst(
            ".tgme_widget_message_forwarded_from_name, .tgme_widget_message_forwarded_from"
        )?.text()?.trim()

        val media = mutableListOf<TelegramMedia>()

        message.select(".tgme_widget_message_photo_wrap").forEach { photo ->
            backgroundImageUrl(photo.attr("style"))?.let {
                media += TelegramMedia(MediaKind.PHOTO, it)
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

        message.select("img.tgme_widget_message_sticker, .tgme_widget_message_sticker img").forEach { sticker ->
            absoluteUrl(sticker, "src")?.let {
                media += TelegramMedia(MediaKind.STICKER, it)
            }
        }

        message.select(".tgme_widget_message_video_thumb").forEach { thumb ->
            val styleUrl = backgroundImageUrl(thumb.attr("style"))
            val parentHref = thumb.closest("a")?.let { absoluteUrl(it, "href") }
            if (!parentHref.isNullOrBlank() && isLikelyMediaUrl(parentHref)) {
                media += TelegramMedia(MediaKind.VIDEO, parentHref, styleUrl)
            }
        }

        val documents = message.select(
            "a.tgme_widget_message_document_wrap, .tgme_widget_message_document_wrap a"
        ).mapNotNull { item ->
            val href = absoluteUrl(item, "href") ?: return@mapNotNull null
            if (!href.startsWith("http")) return@mapNotNull null
            val title = item.selectFirst(".tgme_widget_message_document_title")?.text()?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: href.substringAfterLast("/").substringBefore("?").ifBlank { "فایل" }
            val extra = item.selectFirst(".tgme_widget_message_document_extra")?.text()?.trim()
            TelegramDocument(title = title, extra = extra, url = href)
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

    private fun sanitizeFileName(input: String): String =
        input.replace(Regex("""[\\/:*?"<>|]"""), "_").take(120).ifBlank { "document" }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128 Mobile Safari/537.36"
    }
}
