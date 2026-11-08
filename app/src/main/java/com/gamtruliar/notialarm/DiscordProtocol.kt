package com.gamtruliar.notialarm

import java.net.URI
import java.security.MessageDigest
import kotlinx.serialization.json.*

/** Pure protocol helpers shared by notification forwarding and the Settings test. */
object DiscordProtocol {
    private val webhookPath = Regex("/api(?:/v[0-9]+)?/webhooks/[0-9]{5,25}/[A-Za-z0-9_-]{20,200}")

    fun validWebhookUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.host == "discord.com" &&
            (uri.port == -1 || uri.port == 443) && uri.rawUserInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null && webhookPath.matches(uri.rawPath ?: "")
    }.getOrDefault(false)

    fun sourceKey(packageName: String, notificationKey: String, title: String, text: String): String =
        digest(listOf(packageName, notificationKey, title, text).joinToString("\u0000"))

    fun eventId(sourceKey: String, postedAt: Long, filterId: Int = 0): String = digest("$sourceKey\u0000$postedAt\u0000$filterId")

    fun content(
        type: String, eventId: String, filterId: Int, filterName: String,
        packageName: String, title: String, text: String, timestamp: Long
    ): String {
        var safeName = clip(filterName, 80)
        var safeTitle = clip(title, 250)
        var safeText = clip(text, 900)
        fun encode() = buildJsonObject {
            put("type", type)
            put("version", 1)
            put("event_id", eventId)
            put("filter_id", filterId)
            put("filter_name", safeName)
            put("package_name", clip(packageName, 150))
            put("title", safeTitle)
            put("text", safeText)
            put("timestamp", timestamp)
        }.toString()
        var encoded = encode()
        // Escaped control characters can expand sixfold; enforce Discord's content limit
        // after JSON encoding, without splitting a UTF-16 surrogate pair.
        while (encoded.length > 1900) {
            when {
                safeText.isNotEmpty() -> safeText = clip(safeText, (safeText.length * 3 / 4))
                safeTitle.isNotEmpty() -> safeTitle = clip(safeTitle, (safeTitle.length * 3 / 4))
                safeName.isNotEmpty() -> safeName = clip(safeName, (safeName.length * 3 / 4))
                else -> break
            }
            encoded = encode()
        }
        return encoded
    }

    fun requestBody(content: String): String = buildJsonObject {
        put("content", content)
        putJsonObject("allowed_mentions") { putJsonArray("parse") {} }
    }.toString()

    private fun clip(value: String, limit: Int): String {
        var end = limit.coerceIn(0, value.length)
        if (end > 0 && end < value.length && value[end - 1].isHighSurrogate()) end--
        return value.substring(0, end)
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

class DiscordDeduplicator(private val windowMillis: Long = 10 * 60_000L, private val capacity: Int = 128) {
    private val seen = linkedMapOf<String, Long>()

    @Synchronized fun claim(key: String, now: Long): Boolean {
        seen.entries.removeAll { now - it.value >= windowMillis || now < it.value }
        if (seen.containsKey(key)) return false
        while (seen.size >= capacity) seen.remove(seen.keys.first())
        seen[key] = now
        return true
    }

    @Synchronized fun forget(key: String) { seen.remove(key) }
}

data class DiscordNotificationOrigin(val packageName: String, val notificationKey: String, val postedAt: Long)
