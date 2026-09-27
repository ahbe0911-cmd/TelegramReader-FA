package com.telegramreader.fa.data

import java.io.File

sealed interface MediaAction {
    data class OpenInternalPdfViewer(
        val file: File,
        val resolved: ResolvedMedia,
    ) : MediaAction

    data class PlayAudioStream(
        val resolved: ResolvedMedia,
    ) : MediaAction

    data class PlayAudioFile(
        val file: File,
        val resolved: ResolvedMedia,
    ) : MediaAction

    data class InstallPackage(
        val file: File,
        val resolved: ResolvedMedia,
    ) : MediaAction

    data class OpenWithSystem(
        val file: File,
        val resolved: ResolvedMedia,
    ) : MediaAction
}

class MediaTypeRouter(
    private val downloadManager: MediaDownloadManager,
) {
    fun route(resolved: ResolvedMedia): MediaAction {
        val mime = MediaSniffer.normalizeContentType(resolved.mimeType)
            ?: "application/octet-stream"
        val name = resolved.fileName.lowercase()

        return when {
            mime == "application/pdf" || name.endsWith(".pdf") -> {
                val file = downloadManager.downloadToCache(resolved)
                if (!downloadManager.validatePdf(file)) {
                    throw MediaResolutionException.InvalidContent(
                        "فایل دریافت‌شده PDF معتبر نیست (%PDF- پیدا نشد).",
                    )
                }
                MediaAction.OpenInternalPdfViewer(file, resolved)
            }

            mime.startsWith("audio/") || isAudioName(name) -> {
                if (resolved.supportsRangeRequests) {
                    MediaAction.PlayAudioStream(resolved)
                } else {
                    val file = downloadManager.downloadToCache(resolved)
                    MediaAction.PlayAudioFile(file, resolved)
                }
            }

            mime == "application/vnd.android.package-archive" ||
                name.endsWith(".apk") -> {
                val file = downloadManager.downloadToCache(
                    resolved = resolved.copy(
                        mimeType = "application/vnd.android.package-archive",
                    ),
                    verifyFull = true,
                )
                if (!downloadManager.validatePackage(file)) {
                    throw MediaResolutionException.InvalidContent(
                        "فایل APK معتبر نیست یا دانلود ناقص است.",
                    )
                }
                MediaAction.InstallPackage(file, resolved)
            }

            else -> {
                val file = downloadManager.downloadToCache(resolved)
                MediaAction.OpenWithSystem(file, resolved)
            }
        }
    }

    private fun isAudioName(name: String): Boolean =
        AUDIO_EXTENSIONS.any { name.endsWith(it) }

    companion object {
        private val AUDIO_EXTENSIONS = setOf(
            ".mp3",
            ".m4a",
            ".aac",
            ".ogg",
            ".opus",
            ".wav",
            ".flac",
        )
    }
}
