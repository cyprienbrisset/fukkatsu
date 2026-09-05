package com.cyprienbrisset.myportal.integration

import android.content.Context
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CalEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String?,
    /** A Google Meet link found on the event, if any. */
    val meetUrl: String?,
)

object CalendarRepository {

    private val MEET_REGEX = Regex("https://meet\\.google\\.com/[a-z0-9-]+", RegexOption.IGNORE_CASE)

    /**
     * Upcoming events across all local calendars in the next [days] days. Requires READ_CALENDAR
     * to be granted; returns an empty list if there's no synced calendar or on any query failure.
     */
    suspend fun upcoming(context: Context, nowMs: Long, days: Int = 7, limit: Int = 8): List<CalEvent> =
        withContext(Dispatchers.IO) {
            val end = nowMs + days * 24L * 60 * 60 * 1000
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(nowMs.toString())
                .appendPath(end.toString())
                .build()
            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.DESCRIPTION,
            )
            val result = mutableListOf<CalEvent>()
            runCatching {
                context.contentResolver.query(
                    uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC",
                )?.use { c ->
                    while (c.moveToNext() && result.size < limit) {
                        val id = c.getLong(0)
                        val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(Sans titre)"
                        val begin = c.getLong(2)
                        val ev = c.getLong(3)
                        val allDay = c.getInt(4) == 1
                        val location = c.getString(5)
                        val description = c.getString(6)
                        val meet = (location ?: "").plus(" ").plus(description ?: "")
                            .let { MEET_REGEX.find(it)?.value }
                        result += CalEvent(id, title, begin, ev, allDay, location, meet)
                    }
                }
            }
            result
        }
}
