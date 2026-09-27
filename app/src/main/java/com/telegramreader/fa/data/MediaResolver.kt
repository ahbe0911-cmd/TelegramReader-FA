package com.telegramreader.fa.data

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URLEncoder
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup

/**
 * Resolves any raw Telegram/Reader document URL to a byte-serving URL.
 *
 * Important: this class does not download the full payload. It only probes headers and a
 * small byte range, follows redirects, keeps cookies, detects HTML interstitials and
 * extracts/asks for a real media URL.
 */
class MediaResolver(
    private val clientProvider: () -> OkHttpClient,
    private val cookieJar: PersistentMediaCookieJar,
    private val backendBaseUrl: String,
    private val maxDepth: Int = 3,
) {
    fun resolveMedia(
        documentUrl: String,
        suggestedName: String? = null,
        expectedMime: String? = null,
    ): ResolvedMedia {
        val clean = documentUrl.trim()
        if (clean.isBlank()) {
            throw MediaResolutionException.InvalidContent("آدرس فایل خالی است.")
        }

        return try {
            resolveInternal(
                originalUrl = clean,
                currentUrl = clean,
                suggestedName = suggestedName,
                expectedMime = expectedMime,
                depth = 0,
                visited = linkedSetOf(),
                forcedStrategy = null,
                inheritedHeaders = emptyMap(),
            )
        } catch (error: MediaResolutionException) {
            throw error
        } catch (error: Throwable) {
            throw MediaResolutionException.NetworkError(error)
        }
    }

    private fun resolveInternal(
        originalUrl: String,
        currentUrl: String,
        suggestedName: String?,
        expectedMime: String?,
        depth: Int,
        visited: MutableSet<String>,
        forcedStrategy: ResolutionStrategy?,
        inheritedHeaders: Map<String, String>,
    ): ResolvedMedia {
        if (depth > maxDepth || !visited.add(currentUrl)) {
            throw MediaResolutionException.UnresolvableInterstitial(currentUrl)
        }

        val probe = probeUrl(
            url = currentUrl,
            suggestedName = suggestedName,
            expectedMime = expectedMime,
            inheritedHeaders = inheritedHeaders,
        )

        if (probe.code == 404) {
            throw MediaResolutionException.NotFound(currentUrl)
        }

        if (probe.code !in 200..299) {
            throw MediaResolutionException.InvalidContent(
                "دریافت فایل با خطای HTTP ${probe.code} متوقف شد.",
            )
        }

        val headerMime = MediaSniffer.normalizeContentType(probe.contentType)
        val guessedName = chooseFileName(
            contentDisposition = probe.contentDisposition,
            finalUrl = probe.finalUrl,
            suggestedName = suggestedName,
            mimeHint = expectedMime ?: headerMime,
        )

        val finalMime = MediaSniffer.chooseMime(
            contentType = headerMime,
            bytes = probe.bytes,
            fileName = guessedName,
            originalUrl = probe.finalUrl,
        )

        val looksHtml = MediaSniffer.isHtml(headerMime) ||
            probe.looksLikeHtml ||
            (
                finalMime == "text/html" ||
                    finalMime == "application/xhtml+xml"
                )

        if (!looksHtml) {
            val strategy = forcedStrategy ?: if (probe.redirectCount > 0) {
                ResolutionStrategy.REDIRECT_FOLLOWED
            } else {
                ResolutionStrategy.DIRECT_MEDIA_URL
            }

            val requiredHeaders = linkedMapOf(
                "User-Agent" to TelegramRepository.USER_AGENT,
                "Accept" to "*/*",
                "Accept-Encoding" to "identity",
            )

            probe.referer?.takeIf { it.isNotBlank() }?.let {
                requiredHeaders["Referer"] = it
            }

            probe.finalUrl.toHttpUrlOrNull()?.let { finalHttpUrl ->
                cookieJar.cookieHeader(finalHttpUrl)?.let {
                    requiredHeaders["Cookie"] = it
                }
            }

            requiredHeaders.putAll(inheritedHeaders)

            val resolved = ResolvedMedia(
                originalUrl = originalUrl,
                finalUrl = probe.finalUrl,
                mimeType = if (
                    MediaSniffer.isGeneric(finalMime) &&
                    !expectedMime.isNullOrBlank()
                ) {
                    expectedMime
                } else {
                    finalMime
                },
                fileName = guessedName,
                contentLength = probe.contentLength,
                contentDisposition = probe.contentDisposition,
                requiredHeaders = requiredHeaders,
                supportsRangeRequests = probe.supportsRange,
                resolutionStrategy = strategy,
            )

            Log.i(
                TAG,
                "resolved strategy=${resolved.resolutionStrategy} " +
                    "mime=${resolved.mimeType} range=${resolved.supportsRangeRequests} " +
                    "url=${resolved.finalUrl}",
            )
            return resolved
        }

        val backendResolved = tryBackendResolve(currentUrl)
        if (backendResolved != null && backendResolved.url != currentUrl) {
            return resolveInternal(
                originalUrl = originalUrl,
                currentUrl = backendResolved.url,
                suggestedName = suggestedName,
                expectedMime = backendResolved.mimeType ?: expectedMime,
                depth = depth + 1,
                visited = visited,
                forcedStrategy = ResolutionStrategy.BACKEND_RESOLVE_ENDPOINT,
                inheritedHeaders = inheritedHeaders + backendResolved.headers,
            )
        }

        val extracted = extractRealUrlFromHtml(
            html = probe.htmlSnippet.orEmpty(),
            baseUrl = probe.finalUrl,
            suggestedName = suggestedName,
        )

        if (!extracted.isNullOrBlank() && extracted != currentUrl) {
            return resolveInternal(
                originalUrl = originalUrl,
                currentUrl = extracted,
                suggestedName = suggestedName,
                expectedMime = expectedMime,
                depth = depth + 1,
                visited = visited,
                forcedStrategy = ResolutionStrategy.HTML_INTERSTITIAL_PARSED,
                inheritedHeaders = inheritedHeaders,
            )
        }

        throw MediaResolutionException.UnresolvableInterstitial(currentUrl)
    }

    private fun probeUrl(
        url: String,
        suggestedName: String?,
        expectedMime: String?,
        inheritedHeaders: Map<String, String>,
    ): ProbeResult {
        val head = executeHead(url, inheritedHeaders)
        val headMime = MediaSniffer.normalizeContentType(head?.header("Content-Type"))
        val headName = MediaSniffer.parseContentDispositionFileName(
            head?.header("Content-Disposition"),
        ) ?: suggestedName

        val headClearlyDirect =
            head != null &&
                head.isSuccessful &&
                !MediaSniffer.isHtml(headMime) &&
                (
                    !MediaSniffer.isGeneric(headMime) ||
                        MediaSniffer.mimeFromName(headName ?: url) != null ||
                        !expectedMime.isNullOrBlank()
                    )

        if (headClearlyDirect) {
            val result = responseToProbe(
                response = head!!,
                bytes = byteArrayOf(),
                htmlSnippet = null,
                rangeTested = false,
                rangeSucceeded = head.header("Accept-Ranges")
                    ?.contains("bytes", ignoreCase = true) == true,
            )
            head.close()
            return result
        }

        head?.close()

        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-${PROBE_BYTES - 1}")
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .header("User-Agent", TelegramRepository.USER_AGENT)
            .apply {
                inheritedHeaders.forEach { (name, value) -> header(name, value) }
            }
            .get()
            .build()

        clientProvider().newCall(request).execute().use { response ->
            val bytes = response.body?.byteStream()?.use {
                readAtMost(it, PROBE_BYTES)
            } ?: byteArrayOf()

            val type = MediaSniffer.normalizeContentType(
                response.header("Content-Type"),
            )
            val html = if (
                MediaSniffer.isHtml(type) ||
                bytesLooksLikeHtml(bytes)
            ) {
                bytes.toString(Charsets.UTF_8)
            } else {
                null
            }

            return responseToProbe(
                response = response,
                bytes = bytes,
                htmlSnippet = html,
                rangeTested = true,
                rangeSucceeded = response.code == 206 ||
                    response.header("Accept-Ranges")
                        ?.contains("bytes", ignoreCase = true) == true,
            )
        }
    }

    private fun executeHead(
        url: String,
        inheritedHeaders: Map<String, String>,
    ): Response? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .header("User-Agent", TelegramRepository.USER_AGENT)
            .apply {
                inheritedHeaders.forEach { (name, value) -> header(name, value) }
            }
            .head()
            .build()

        return runCatching {
            clientProvider().newCall(request).execute()
        }.getOrNull()?.let { response ->
            if (response.code == 405 || response.code == 501) {
                response.close()
                null
            } else {
                response
            }
        }
    }

    private fun responseToProbe(
        response: Response,
        bytes: ByteArray,
        htmlSnippet: String?,
        rangeTested: Boolean,
        rangeSucceeded: Boolean,
    ): ProbeResult {
        val finalUrl = response.request.url.toString()
        val redirectCount = generateSequence(response.priorResponse) {
            it.priorResponse
        }.count()

        val referer = response.priorResponse?.request?.url?.toString()
            ?: response.request.header("Referer")
            ?: finalUrl.substringBeforeLast('/', finalUrl)

        val contentLength = parseTotalLength(response)

        return ProbeResult(
            code = response.code,
            finalUrl = finalUrl,
            contentType = response.header("Content-Type"),
            contentDisposition = response.header("Content-Disposition"),
            contentLength = contentLength,
            supportsRange =
                response.header("Accept-Ranges")
                    ?.contains("bytes", ignoreCase = true) == true ||
                    (rangeTested && rangeSucceeded),
            redirectCount = redirectCount,
            referer = referer,
            bytes = bytes,
            htmlSnippet = htmlSnippet,
            looksLikeHtml = bytesLooksLikeHtml(bytes),
        )
    }

    private fun parseTotalLength(response: Response): Long? {
        val range = response.header("Content-Range")
        val totalFromRange = range
            ?.substringAfterLast('/', "")
            ?.takeIf { it != "*" }
            ?.toLongOrNull()

        return totalFromRange
            ?: response.header("Content-Length")?.toLongOrNull()
    }

    private fun chooseFileName(
        contentDisposition: String?,
        finalUrl: String,
        suggestedName: String?,
        mimeHint: String?,
    ): String {
        val dispositionName = MediaSniffer.parseContentDispositionFileName(
            contentDisposition,
        )
        if (!dispositionName.isNullOrBlank()) {
            return MediaSniffer.safeFileName(dispositionName)
        }

        val urlName = finalUrl
            .toHttpUrlOrNull()
            ?.pathSegments
            ?.lastOrNull()
            ?.takeIf { it.contains('.') && it.isNotBlank() }

        if (!urlName.isNullOrBlank()) {
            return MediaSniffer.safeFileName(urlName)
        }

        if (!suggestedName.isNullOrBlank()) {
            return MediaSniffer.safeFileName(suggestedName)
        }

        val extension = extensionForMime(mimeHint)
        val hash = MediaSniffer.cacheKey(finalUrl).take(14)
        return "nabzak-$hash${extension?.let { ".$it" }.orEmpty()}"
    }

    private fun tryBackendResolve(rawUrl: String): BackendResolution? {
        val encoded = URLEncoder.encode(rawUrl, Charsets.UTF_8.name())
        val endpoint = "$backendBaseUrl/api/resolve?url=$encoded"

        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json")
            .header("User-Agent", TelegramRepository.USER_AGENT)
            .get()
            .build()

        return runCatching {
            clientProvider().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use null

                val json = JSONObject(body)
                val finalUrl = json.optString("finalUrl")
                    .ifBlank { json.optString("url") }
                    .takeIf { it.startsWith("http") }
                    ?: return@use null

                val headers = linkedMapOf<String, String>()
                json.optJSONObject("requiredHeaders")
                    ?.let { headerJson ->
                        headerJson.keys().forEach { key ->
                            val value = headerJson.optString(key)
                            if (value.isNotBlank()) headers[key] = value
                        }
                    }

                BackendResolution(
                    url = finalUrl,
                    mimeType = json.optString("mimeType")
                        .takeIf { it.isNotBlank() },
                    headers = headers,
                )
            }
        }.getOrNull()
    }

    private fun extractRealUrlFromHtml(
        html: String,
        baseUrl: String,
        suggestedName: String?,
    ): String? {
        if (html.isBlank()) return null

        val document = Jsoup.parse(html, baseUrl)

        document.select("meta[http-equiv=refresh]").forEach { meta ->
            val content = meta.attr("content")
            val candidate = Regex(
                """(?i)url\\s*=\\s*(.+)$""",
            ).find(content)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.trim('"', '\'')
                .orEmpty()
            if (candidate.isNotBlank()) {
                return document.baseUri()
                    .toHttpUrlOrNull()
                    ?.resolve(candidate)
                    ?.toString()
                    ?: candidate
            }
        }

        val expectedExtension = suggestedName
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.US)
            ?.takeIf { it.length in 2..8 }

        val candidates = mutableListOf<Pair<String, Int>>()

        document.select("a[href], source[src], video[src], audio[src]").forEach { element ->
            val attr = if (element.hasAttr("href")) "href" else "src"
            val absolute = element.absUrl(attr)
            if (absolute.isBlank() || !absolute.startsWith("http")) return@forEach

            val lower = absolute.lowercase(Locale.US)
            var score = 0
            if (element.hasAttr("download")) score += 20
            if (lower.contains("download")) score += 6
            if (lower.contains("file")) score += 3
            if (lower.contains("cdn")) score += 3
            if (MediaSniffer.mimeFromName(lower) != null) score += 10
            if (
                expectedExtension != null &&
                lower.contains(".$expectedExtension")
            ) {
                score += 12
            }
            candidates += absolute to score
        }

        document.select("[data-url], [data-file-url], [data-download-url]").forEach { element ->
            listOf("data-url", "data-file-url", "data-download-url").forEach { attr ->
                val raw = element.attr(attr)
                if (raw.startsWith("http")) {
                    candidates += raw to 12
                }
            }
        }

        val regexUrl = Regex(
            """https?://[^"'\s<>]+""",
            RegexOption.IGNORE_CASE,
        )
        regexUrl.findAll(html).forEach { match ->
            val value = match.value.replace("&amp;", "&")
            var score = 0
            if (MediaSniffer.mimeFromName(value) != null) score += 8
            if (value.contains("download", ignoreCase = true)) score += 4
            candidates += value to score
        }

        return candidates
            .filter { it.first != baseUrl }
            .maxByOrNull { it.second }
            ?.takeIf { it.second > 0 }
            ?.first
    }

    private fun bytesLooksLikeHtml(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val prefix = bytes
            .take(512)
            .toByteArray()
            .toString(Charsets.UTF_8)
            .trimStart()
            .lowercase(Locale.US)

        return prefix.startsWith("<!doctype html") ||
            prefix.startsWith("<html") ||
            prefix.contains("<body") ||
            prefix.contains("<head")
    }

    private fun extensionForMime(mime: String?): String? = when (
        MediaSniffer.normalizeContentType(mime)
    ) {
        "application/pdf" -> "pdf"
        "application/vnd.android.package-archive" -> "apk"
        "application/zip" -> "zip"
        "application/vnd.rar" -> "rar"
        "application/x-7z-compressed" -> "7z"
        "audio/mpeg" -> "mp3"
        "audio/mp4" -> "m4a"
        "audio/ogg" -> "ogg"
        "audio/wav" -> "wav"
        "audio/flac" -> "flac"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        else -> null
    }

    private fun readAtMost(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 8192))
        val buffer = ByteArray(4096)
        var remaining = limit

        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (read <= 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }

        return output.toByteArray()
    }

    private data class ProbeResult(
        val code: Int,
        val finalUrl: String,
        val contentType: String?,
        val contentDisposition: String?,
        val contentLength: Long?,
        val supportsRange: Boolean,
        val redirectCount: Int,
        val referer: String?,
        val bytes: ByteArray,
        val htmlSnippet: String?,
        val looksLikeHtml: Boolean,
    )

    private data class BackendResolution(
        val url: String,
        val mimeType: String?,
        val headers: Map<String, String>,
    )

    companion object {
        private const val TAG = "NabzakMediaResolver"
        private const val PROBE_BYTES = 64 * 1024
    }
}
