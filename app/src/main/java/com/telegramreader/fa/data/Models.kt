package com.telegramreader.fa.data

enum class MediaKind {
    PHOTO,
    VIDEO,
    GIF,
    STICKER,
}

data class TelegramMedia(
    val kind: MediaKind,
    val url: String,
    val previewUrl: String? = null,
)

enum class DocumentKind {
    PDF,
    AUDIO,
    PACKAGE,
    ARCHIVE,
    OFFICE,
    IMAGE,
    VIDEO,
    OTHER,
}

data class TelegramDocument(
    val title: String,
    val extra: String? = null,
    val url: String,
) {
    private val source: String
        get() = (title + " " + url).lowercase()

    val kind: DocumentKind
        get() = when {
            source.contains(".pdf") -> DocumentKind.PDF
            source.contains(".mp3") ||
                source.contains(".m4a") ||
                source.contains(".aac") ||
                source.contains(".ogg") ||
                source.contains(".opus") ||
                source.contains(".wav") ||
                source.contains(".flac") -> DocumentKind.AUDIO
            source.contains(".apk") || source.contains(".xapk") -> DocumentKind.PACKAGE
            source.contains(".zip") ||
                source.contains(".rar") ||
                source.contains(".7z") -> DocumentKind.ARCHIVE
            source.contains(".doc") ||
                source.contains(".docx") ||
                source.contains(".xls") ||
                source.contains(".xlsx") ||
                source.contains(".ppt") ||
                source.contains(".pptx") -> DocumentKind.OFFICE
            source.contains(".jpg") ||
                source.contains(".jpeg") ||
                source.contains(".png") ||
                source.contains(".webp") ||
                source.contains(".gif") -> DocumentKind.IMAGE
            source.contains(".mp4") ||
                source.contains(".mkv") ||
                source.contains(".webm") ||
                source.contains(".mov") -> DocumentKind.VIDEO
            else -> DocumentKind.OTHER
        }

    val isPdf: Boolean get() = kind == DocumentKind.PDF
    val isAudio: Boolean get() = kind == DocumentKind.AUDIO
    val isPackage: Boolean get() = kind == DocumentKind.PACKAGE
}

data class TelegramPost(
    val id: Long,
    val text: String,
    val html: String?,
    val date: String,
    val views: String?,
    val postUrl: String?,
    val forwardedFrom: String?,
    val media: List<TelegramMedia>,
    val documents: List<TelegramDocument>,
)

data class ChannelInfo(
    val username: String,
    val title: String,
    val description: String? = null,
    val subscriberCount: String? = null,
    val photoUrl: String? = null,
)

data class PostsPage(
    val channel: ChannelInfo?,
    val posts: List<TelegramPost>,
    val nextBefore: Long?,
    val hasMore: Boolean,
)
