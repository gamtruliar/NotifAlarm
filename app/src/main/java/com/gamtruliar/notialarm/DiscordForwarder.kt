package com.gamtruliar.notialarm

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.gamtruliar.notialarm.Data.FilterData
import java.net.URL
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class DiscordSettings(val enabled: Boolean = false, val webhookUrl: String = "") {
    companion object {
        // Both Android backup formats exclude this file, including device transfer.
        const val PREFERENCES = "discord_private"
        fun read(context: Context): DiscordSettings {
            val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            return DiscordSettings(prefs.getBoolean("enabled", false), prefs.getString("webhook_url", "") ?: "")
        }
        fun save(context: Context, enabled: Boolean, webhookUrl: String) {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putBoolean("enabled", enabled).putString("webhook_url", webhookUrl).apply()
        }
    }
}

object DiscordForwarder {
    enum class Kind { SENT, INVALID_URL, NETWORK_ERROR, HTTP_ERROR, QUEUE_FULL }
    data class Result(val kind: Kind, val httpCode: Int = 0)
    private val executor = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(32), { runnable -> Thread(runnable, "DiscordForwarder").apply { isDaemon = true } })
    private val dedup = DiscordDeduplicator()
    private val main = Handler(Looper.getMainLooper())
    // Accessed only by the single network worker. Keep a rate-limited webhook quiet
    // even when other queued events arrive after a long Retry-After response.
    private val cooldowns = linkedMapOf<String, Long>()

    fun forwardMatched(context: Context, filter: FilterData, origin: DiscordNotificationOrigin, title: String, text: String) {
        // Receiving our own relay message on the phone must never start another relay.
        if (origin.packageName == "com.discord" || origin.packageName == context.packageName) return
        val settings = DiscordSettings.read(context)
        if (!settings.enabled || !DiscordProtocol.validWebhookUrl(settings.webhookUrl)) return
        val key = DiscordProtocol.sourceKey(origin.packageName, origin.notificationKey, title, text)
        val dedupKey = "$key:${filter.filerUID}"
        if (!dedup.claim(dedupKey, SystemClock.elapsedRealtime())) return
        val content = DiscordProtocol.content("notifalarm.trigger", DiscordProtocol.eventId(key, origin.postedAt, filter.filerUID),
            filter.filerUID, filter.filterName, origin.packageName, title, text, System.currentTimeMillis())
        submit(settings.webhookUrl, content) { result ->
            if (result.kind != Kind.SENT) dedup.forget(dedupKey)
        }
    }

    fun sendTest(context: Context, webhookUrl: String, callback: (Result) -> Unit) {
        val content = DiscordProtocol.content("notifalarm.test", UUID.randomUUID().toString(), 0,
            "Discord test", context.packageName, "NotifAlarm connection test",
            "Connection test only; this message does not trigger recording.", System.currentTimeMillis())
        submit(webhookUrl, content) { result -> main.post { callback(result) } }
    }

    private fun submit(webhookUrl: String, content: String, callback: (Result) -> Unit) {
        if (!DiscordProtocol.validWebhookUrl(webhookUrl)) {
            callback(Result(Kind.INVALID_URL))
            return
        }
        try {
            executor.execute { callback(send(webhookUrl, DiscordProtocol.requestBody(content))) }
        } catch (_: RejectedExecutionException) {
            callback(Result(Kind.QUEUE_FULL))
        }
    }

    private fun send(webhookUrl: String, body: String): Result {
        repeat(3) { attempt ->
            val remainingCooldown = (cooldowns[webhookUrl] ?: 0) - SystemClock.elapsedRealtime()
            if (remainingCooldown > 15_000) return Result(Kind.HTTP_ERROR, 429)
            if (remainingCooldown > 0) {
                try { Thread.sleep(remainingCooldown) } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return Result(Kind.NETWORK_ERROR)
                }
            }
            var connection: HttpsURLConnection? = null
            var delayMillis = (attempt + 1) * 1000L
            try {
                // Do not follow redirects: the webhook token must only go to discord.com.
                connection = URL("$webhookUrl?wait=true").openConnection() as HttpsURLConnection
                connection.instanceFollowRedirects = false
                connection.requestMethod = "POST"
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("User-Agent", "NotifAlarm (Android, Discord relay)")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                if (code in 200..299) return Result(Kind.SENT)
                if (code == 429) {
                    val error = connection.errorStream?.use { String(it.readNBytesCompat(4096), Charsets.UTF_8) } ?: ""
                    val retrySeconds = runCatching {
                        Json.parseToJsonElement(error).jsonObject["retry_after"]?.jsonPrimitive?.doubleOrNull
                    }.getOrNull() ?: connection.getHeaderField("Retry-After")?.toDoubleOrNull() ?: 1.0
                    // A long rate limit is reported rather than blocking the worker indefinitely.
                    delayMillis = if (retrySeconds.isFinite())
                        (retrySeconds.coerceIn(0.5, 86_400.0) * 1000).toLong() else 60_000L
                    cooldowns.entries.removeAll { it.value <= SystemClock.elapsedRealtime() }
                    if (cooldowns.size >= 8 && !cooldowns.containsKey(webhookUrl)) cooldowns.remove(cooldowns.keys.first())
                    cooldowns[webhookUrl] = SystemClock.elapsedRealtime() + delayMillis
                    if (delayMillis > 15_000) return Result(Kind.HTTP_ERROR, code)
                } else if (code !in 500..599) {
                    return Result(Kind.HTTP_ERROR, code)
                }
                if (attempt == 2) return Result(Kind.HTTP_ERROR, code)
            } catch (_: Exception) {
                // Exception text may contain the token URL; never log or expose it.
                if (attempt == 2) return Result(Kind.NETWORK_ERROR)
            } finally {
                connection?.disconnect()
            }
            try { Thread.sleep(delayMillis) } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return Result(Kind.NETWORK_ERROR)
            }
        }
        return Result(Kind.NETWORK_ERROR)
    }

    // InputStream.readNBytes is not available on every supported Android version.
    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (output.size() < limit) {
            val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
