package com.telegramreader.fa.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSnifferTest {
    @Test
    fun pdfMagicOverridesGenericHeader() {
        val bytes = "%PDF-1.7".toByteArray()
        assertEquals(
            "application/pdf",
            MediaSniffer.chooseMime(
                contentType = "application/octet-stream",
                bytes = bytes,
                fileName = "file.bin",
                originalUrl = "https://example.com/file",
            ),
        )
    }

    @Test
    fun apkUsesPackageMimeWhenZipMagicAndApkName() {
        val bytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        assertEquals(
            "application/vnd.android.package-archive",
            MediaSniffer.chooseMime(
                contentType = "application/octet-stream",
                bytes = bytes,
                fileName = "app.apk",
                originalUrl = "https://example.com/download",
            ),
        )
    }

    @Test
    fun oggMagicIsDetected() {
        assertEquals(
            "audio/ogg",
            MediaSniffer.sniff("OggS0000".toByteArray(), "voice.bin"),
        )
    }

    @Test
    fun contentDispositionUtf8NameIsDecoded() {
        assertEquals(
            "دفترچه.pdf",
            MediaSniffer.parseContentDispositionFileName(
                "attachment; filename*=UTF-8''%D8%AF%D9%81%D8%AA%D8%B1%DA%86%D9%87.pdf",
            ),
        )
    }
}
