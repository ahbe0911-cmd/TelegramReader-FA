package com.telegramreader.fa.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Shared cache/download layer for every document type.
 *
 * - cache-first
 * - resumes partial downloads with Range when possible
 * - validates announced Content-Length
 * - reuses the exact resolver headers/cookies through the shared OkHttp client
 */
class MediaDownloadManager(
    private val context: Context,
    private val clientProvider: () -> OkHttpClient,
) {
    private val cacheDir: File by lazy {
        File(context.cacheDir, "resolved_media").also { it.mkdirs() }
    }

    fun downloadToCache(
        resolved: ResolvedMedia,
        verifyFull: Boolean = true,
    ): File {
        val key = MediaSniffer.cacheKey(
            resolved.originalUrl + "|" + resolved.finalUrl,
        )
        val fileName = MediaSniffer.safeFileName(resolved.fileName)
        val target = File(cacheDir, "${key.take(24)}_$fileName")
        val part = File(cacheDir, "${key.take(24)}.part")

        if (isComplete(target, resolved, verifyFull)) {
            target.setLastModified(System.currentTimeMillis())
            return target
        }

        if (target.exists()) target.delete()

        var existing = if (
            resolved.supportsRangeRequests &&
            part.exists() &&
            part.length() > 0L
        ) {
            part.length()
        } else {
            part.delete()
            0L
        }

        var request = buildRequest(
            resolved = resolved,
            rangeStart = existing.takeIf { it > 0L },
        )

        var response = clientProvider().newCall(request).execute()

        if (
            existing > 0L &&
            response.code != 206
        ) {
            response.close()
            part.delete()
            existing = 0L
            request = buildRequest(resolved, null)
            response = clientProvider().newCall(request).execute()
        }

        response.use { activeResponse ->
            if (!activeResponse.isSuccessful) {
                throw MediaResolutionException.InvalidContent(
                    "دانلود فایل با خطای HTTP ${activeResponse.code} متوقف شد.",
                )
            }

            val body = activeResponse.body
                ?: throw MediaResolutionException.InvalidContent(
                    "پاسخ دانلود فایل خالی بود.",
                )

            FileOutputStream(part, existing > 0L).buffered().use { output ->
                body.byteStream().buffered().use { input ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                }
            }
        }

        val finalLength = part.length()
        val expectedLength = resolved.contentLength

        if (
            verifyFull &&
            expectedLength != null &&
            expectedLength > 0L &&
            finalLength != expectedLength
        ) {
            throw MediaResolutionException.InvalidContent(
                "فایل ناقص دریافت شد. حجم مورد انتظار: $expectedLength، حجم فعلی: $finalLength",
            )
        }

        if (target.exists()) target.delete()
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }

        target.setLastModified(System.currentTimeMillis())
        enforceCacheLimit()
        return target
    }

    fun downloadToUri(
        resolved: ResolvedMedia,
        destination: Uri,
    ) {
        val cached = downloadToCache(resolved)
        context.contentResolver
            .openOutputStream(destination, "w")
            ?.buffered()
            ?.use { output ->
                cached.inputStream().buffered().use { input ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                }
            }
            ?: throw MediaResolutionException.InvalidContent(
                "محل ذخیره فایل قابل دسترسی نیست.",
            )
    }

    fun validatePdf(file: File): Boolean =
        MediaSniffer.isPdf(file)

    fun validatePackage(file: File): Boolean =
        MediaSniffer.isZipContainer(file)

    private fun isComplete(
        file: File,
        resolved: ResolvedMedia,
        verifyFull: Boolean,
    ): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        if (!verifyFull) return true

        val expected = resolved.contentLength
        return expected == null || expected <= 0L || file.length() == expected
    }

    private fun buildRequest(
        resolved: ResolvedMedia,
        rangeStart: Long?,
    ): Request {
        return Request.Builder()
            .url(resolved.finalUrl)
            .apply {
                resolved.requiredHeaders.forEach { (name, value) ->
                    header(name, value)
                }
                header("Accept-Encoding", "identity")
                if (rangeStart != null && rangeStart > 0L) {
                    header("Range", "bytes=$rangeStart-")
                }
            }
            .get()
            .build()
    }

    private fun enforceCacheLimit() {
        val files = cacheDir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") }
            ?.sortedBy { it.lastModified() }
            ?.toMutableList()
            ?: return

        var total = files.sumOf { it.length() }
        while (total > MAX_CACHE_BYTES && files.isNotEmpty()) {
            val oldest = files.removeAt(0)
            total -= oldest.length()
            oldest.delete()
        }

        cacheDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".part") }
            ?.filter {
                System.currentTimeMillis() - it.lastModified() >
                    PART_TTL_MS
            }
            ?.forEach { it.delete() }
    }

    companion object {
        private const val MAX_CACHE_BYTES = 512L * 1024L * 1024L
        private val PART_TTL_MS = TimeUnit.DAYS.toMillis(2)
    }
}
