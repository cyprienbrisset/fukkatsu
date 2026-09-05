package com.cyprienbrisset.myportal.integration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object IcsCalendarRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val MEET_REGEX = Regex("https://meet\\.google\\.com/[a-z0-9-]+", RegexOption.IGNORE_CASE)

    suspend fun upcoming(url: String, nowMs: Long, days: Int = 7, limit: Int = 8): List<CalEvent>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(url).build()
                val body = client.newCall(req).execute().use { it.body?.string() } ?: return@runCatching null
                parseIcs(body, nowMs, days, limit)
            }.getOrNull()
        }

    private fun parseIcs(ics: String, nowMs: Long, days: Int, limit: Int): List<CalEvent> {
        val endMs = nowMs + days * 24L * 60 * 60 * 1000
        val events = mutableListOf<CalEvent>()
        var inEvent = false
        var summary = ""; var dtstart = ""; var dtend = ""
        var location: String? = null; var description: String? = null; var url: String? = null
        var uid = 0L

        lines@ for (line in ics.lines()) {
            when {
                line.startsWith("BEGIN:VEVENT") -> {
                    inEvent = true
                    summary = ""; dtstart = ""; dtend = ""
                    location = null; description = null; url = null
                    uid++
                }
                !inEvent -> {}
                line.startsWith("SUMMARY:") -> summary = line.removePrefix("SUMMARY:").trim()
                line.startsWith("DTSTART") -> dtstart = line.substringAfter(":").trim()
                line.startsWith("DTEND") -> dtend = line.substringAfter(":").trim()
                line.startsWith("LOCATION:") -> location = line.removePrefix("LOCATION:").trim().takeIf { it.isNotBlank() }
                line.startsWith("DESCRIPTION:") -> description = line.removePrefix("DESCRIPTION:").trim().replace("\\n", "\n").takeIf { it.isNotBlank() }
                line.startsWith("URL:") -> url = line.removePrefix("URL:").trim().takeIf { it.isNotBlank() }
                line == "END:VEVENT" -> {
                    inEvent = false
                    if (dtstart.isBlank()) continue@lines
                    val allDay = !dtstart.contains("T")
                    val beginMs = parseDateTime(dtstart) ?: continue@lines
                    val endEventMs = if (dtend.isNotBlank()) parseDateTime(dtend) ?: beginMs + 3600_000L else beginMs + 3600_000L
                    if (beginMs < nowMs || beginMs > endMs) continue@lines
                    val meetText = "${location.orEmpty()} ${description.orEmpty()} ${url.orEmpty()}"
                    val meetUrl = MEET_REGEX.find(meetText)?.value
                    events += CalEvent(uid, summary.ifBlank { "(Sans titre)" }, beginMs, endEventMs, allDay, location, meetUrl)
                    if (events.size >= limit) return events.sortedBy { it.begin }
                }
            }
        }
        return events.sortedBy { it.begin }
    }

    private fun parseDateTime(s: String): Long? = runCatching {
        when {
            s.endsWith("Z") ->
                ZonedDateTime.parse(s, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC")))
                    .toInstant().toEpochMilli()
            s.contains("T") ->
                LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            else ->
                LocalDateTime.parse("${s}T000000", DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }.getOrNull()
}
