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

data class TelegramDocument(
    val title: String,
    val extra: String? = null,
    val url: String,
    val isPdf: Boolean = title.lowercase().endsWith(".pdf") || url.lowercase().contains(".pdf"),
)

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
