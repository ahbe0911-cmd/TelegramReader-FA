package com.telegramreader.fa.data

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small persistent CookieJar shared by API, resolver, downloader and Media3.
 * This prevents a resolved media URL from losing the session/cookies that made it valid.
 */
class PersistentMediaCookieJar(context: Context) : CookieJar {
    private val prefs = context.getSharedPreferences(
        "nabzak_media_cookies",
        Context.MODE_PRIVATE,
    )
    private val lock = Any()
    private val store = mutableMapOf<String, MutableList<Cookie>>()

    init {
        restore()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(lock) {
            val hostCookies = store.getOrPut(url.host) { mutableListOf() }
            val now = System.currentTimeMillis()

            cookies.forEach { incoming ->
                hostCookies.removeAll {
                    it.name == incoming.name &&
                        it.domain == incoming.domain &&
                        it.path == incoming.path
                }
                if (incoming.expiresAt > now || !incoming.persistent) {
                    hostCookies += incoming
                }
            }

            hostCookies.removeAll { it.persistent && it.expiresAt <= now }
            persist()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        val now = System.currentTimeMillis()
        var changed = false

        store.values.forEach { list ->
            changed = list.removeAll {
                it.persistent && it.expiresAt <= now
            } || changed
        }

        if (changed) persist()

        store.values
            .flatten()
            .filter { it.matches(url) }
    }

    fun cookieHeader(url: HttpUrl): String? {
        val cookies = loadForRequest(url)
        if (cookies.isEmpty()) return null
        return cookies.joinToString("; ") { "${it.name}=${it.value}" }
    }

    private fun persist() {
        val array = JSONArray()
        store.forEach { (host, cookies) ->
            cookies.forEach { cookie ->
                array.put(
                    JSONObject()
                        .put("host", host)
                        .put("name", cookie.name)
                        .put("value", cookie.value)
                        .put("domain", cookie.domain)
                        .put("path", cookie.path)
                        .put("expiresAt", cookie.expiresAt)
                        .put("secure", cookie.secure)
                        .put("httpOnly", cookie.httpOnly)
                        .put("hostOnly", cookie.hostOnly)
                        .put("persistent", cookie.persistent),
                )
            }
        }
        prefs.edit().putString("cookies", array.toString()).apply()
    }

    private fun restore() {
        val raw = prefs.getString("cookies", null) ?: return
        runCatching {
            val array = JSONArray(raw)
            val now = System.currentTimeMillis()

            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val persistent = item.optBoolean("persistent", true)
                val expiresAt = item.optLong("expiresAt", Long.MAX_VALUE)
                if (persistent && expiresAt <= now) continue

                val builder = Cookie.Builder()
                    .name(item.getString("name"))
                    .value(item.getString("value"))
                    .path(item.optString("path", "/"))
                    .expiresAt(expiresAt)

                val domain = item.getString("domain")
                if (item.optBoolean("hostOnly", false)) {
                    builder.hostOnlyDomain(domain)
                } else {
                    builder.domain(domain)
                }

                if (item.optBoolean("secure", false)) builder.secure()
                if (item.optBoolean("httpOnly", false)) builder.httpOnly()

                store.getOrPut(item.getString("host")) { mutableListOf() }
                    .add(builder.build())
            }
        }.onFailure {
            prefs.edit().remove("cookies").apply()
            store.clear()
        }
    }
}
