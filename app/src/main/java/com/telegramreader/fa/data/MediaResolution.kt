package com.telegramreader.fa.data

import android.webkit.MimeTypeMap
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

data class ResolvedMedia(
    val originalUrl: String,
    val finalUrl: String,
    val mimeType: String,
    val fileName: String,
    val contentLength: Long?,
    val contentDisposition: String?,
    val requiredHeaders: Map<String, String>,
    val supportsRangeRequests: Boolean,
    val resolutionStrategy: ResolutionStrategy,
)

enum class ResolutionStrategy {
    DIRECT_MEDIA_URL,
    REDIRECT_FOLLOWED,
    BACKEND_RESOLVE_ENDPOINT,
    HTML_INTERSTITIAL_PARSED,
}

sealed class MediaResolutionException(message: String) : Exception(message) {
    class NetworkError(cause: Throwable) :
        MediaResolutionException(cause.message ?: "خطای شبکه") {
        init {
            initCause(cause)
        }
    }

    class NotFound(val url: String) :
        MediaResolutionException("فایل پیدا نشد: $url")

    class UnresolvableInterstitial(val url: String) :
        MediaResolutionException("صفحه واسط به فایل واقعی تبدیل نشد.")

    class InvalidContent(reason: String) :
        MediaResolutionException(reason)
}

internal object MediaSniffer {
    private val knownExtensions = mapOf(
        "pdf" to "application/pdf",
        "apk" to "application/vnd.android.package-archive",
        "xapk" to "application/octet-stream",
        "zip" to "application/zip",
        "rar" to "application/vnd.rar",
        "7z" to "application/x-7z-compressed",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "aac" to "audio/aac",
        "ogg" to "audio/ogg",
        "opus" to "audio/ogg",
        "wav" to "audio/wav",
        "flac" to "audio/flac",
        "mp4" to "video/mp4",
        "webm" to "video/webm",
        "mkv" to "video/x-matroska",
        "mov" to "video/quicktime",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "webp" to "image/webp",
        "gif" to "image/gif",
        "txt" to "text/plain",
        "csv" to "text/csv",
        "json" to "application/json",
        "xml" to "application/xml",
    )

    fun normalizeContentType(raw: String?): String? =
        raw
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.US)
            ?.takeIf { it.isNotBlank() }

    fun isHtml(mime: String?): Boolean =
        mime == "text/html" || mime == "application/xhtml+xml"

    fun isGeneric(mime: String?): Boolean =
        mime == null ||
            mime == "application/octet-stream" ||
            mime == "binary/octet-stream" ||
            mime == "application/download"

    fun mimeFromName(nameOrUrl: String?): String? {
        if (nameOrUrl.isNullOrBlank()) return null
        val clean = nameOrUrl.substringBefore('?').substringBefore('#')
        val ext = clean.substringAfterLast('.', "").lowercase(Locale.US)
        if (ext.isBlank()) return null
        return knownExtensions[ext]
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    fun sniff(bytes: ByteArray, expectedName: String? = null): String? {
        if (bytes.size >= 5 && bytes.copyOfRange(0, 5).decodeToString() == "%PDF-") {
            return "application/pdf"
        }

        if (
            bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()
        ) {
            return when (mimeFromName(expectedName)) {
                "application/vnd.android.package-archive" ->
                    "application/vnd.android.package-archive"
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ->
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "application/vnd.openxmlformats-officedocument.presentationml.presentation" ->
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                else -> "application/zip"
            }
        }

        if (
            bytes.size >= 3 &&
            bytes[0] == 'I'.code.toByte() &&
            bytes[1] == 'D'.code.toByte() &&
            bytes[2] == '3'.code.toByte()
        ) {
            return "audio/mpeg"
        }

        if (
            bytes.size >= 4 &&
            bytes[0] == 'O'.code.toByte() &&
            bytes[1] == 'g'.code.toByte() &&
            bytes[2] == 'g'.code.toByte() &&
            bytes[3] == 'S'.code.toByte()
        ) {
            return "audio/ogg"
        }

        if (
            bytes.size >= 12 &&
            bytes.copyOfRange(0, 4).decodeToString() == "RIFF"
        ) {
            val type = bytes.copyOfRange(8, 12).decodeToString()
            if (type == "WAVE") return "audio/wav"
            if (type == "WEBP") return "image/webp"
        }

        if (
            bytes.size >= 8 &&
            bytes[4] == 'f'.code.toByte() &&
            bytes[5] == 't'.code.toByte() &&
            bytes[6] == 'y'.code.toByte() &&
            bytes[7] == 'p'.code.toByte()
        ) {
            return when (mimeFromName(expectedName)) {
                "audio/mp4" -> "audio/mp4"
                else -> "video/mp4"
            }
        }

        if (
            bytes.size >= 4 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) {
            return "image/png"
        }

        if (
            bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return "image/jpeg"
        }

        if (
            bytes.size >= 6 &&
            (
                bytes.copyOfRange(0, 6).decodeToString() == "GIF87a" ||
                    bytes.copyOfRange(0, 6).decodeToString() == "GIF89a"
                )
        ) {
            return "image/gif"
        }

        if (
            bytes.size >= 2 &&
            bytes[0] == 0xFF.toByte() &&
            (bytes[1].toInt() and 0xE0) == 0xE0
        ) {
            return "audio/mpeg"
        }

        return null
    }

    fun chooseMime(
        contentType: String?,
        bytes: ByteArray?,
        fileName: String?,
        originalUrl: String?,
    ): String {
        val header = normalizeContentType(contentType)
        if (!isHtml(header) && !isGeneric(header)) return header!!

        val magic = bytes?.let { sniff(it, fileName ?: originalUrl) }
        if (magic != null) return magic

        return mimeFromName(fileName)
            ?: mimeFromName(originalUrl)
            ?: header
            ?: "application/octet-stream"
    }

    fun parseContentDispositionFileName(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        val encoded = Regex(
            """filename\*\s*=\s*UTF-8''([^;]+)""",
            RegexOption.IGNORE_CASE,
        ).find(raw)?.groupValues?.getOrNull(1)

        if (!encoded.isNullOrBlank()) {
            return runCatching {
                URLDecoder.decode(encoded.trim(), StandardCharsets.UTF_8.name())
            }.getOrNull()
        }

        return Regex(
            """filename\s*=\s*"?([^";]+)"?""",
            RegexOption.IGNORE_CASE,
        ).find(raw)?.groupValues?.getOrNull(1)?.trim()
    }

    fun safeFileName(input: String): String =
        input
            .substringAfterLast('/')
            .substringBefore('?')
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .take(160)
            .ifBlank { "nabzak-file" }

    fun cacheKey(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun isPdf(file: File): Boolean =
        runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(5)
                input.read(header) == 5 &&
                    header.decodeToString() == "%PDF-"
            }
        }.getOrDefault(false)

    fun isZipContainer(file: File): Boolean =
        runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(4)
                input.read(header) == 4 &&
                    header[0] == 0x50.toByte() &&
                    header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() &&
                    header[3] == 0x04.toByte()
            }
        }.getOrDefault(false)
}
