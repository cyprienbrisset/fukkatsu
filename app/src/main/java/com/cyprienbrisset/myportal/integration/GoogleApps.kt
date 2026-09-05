package com.cyprienbrisset.myportal.integration

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Detection + launch helpers for the Google apps we integrate. Everything degrades gracefully:
 * if an app isn't installed, its card simply doesn't appear.
 */
object GoogleApps {
    const val CALENDAR = "com.google.android.calendar"
    const val CHAT = "com.google.android.apps.dynamite"
    private val MEET_CANDIDATES = listOf("com.google.android.apps.tachyon", "com.google.android.apps.meetings")

    fun isInstalled(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)

    /** First installed Meet package, or null. */
    fun meetPackage(context: Context): String? =
        MEET_CANDIDATES.firstOrNull { isInstalled(context, it) }

    fun open(context: Context, pkg: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Start a brand-new Meet meeting (the Meet app handles meet.google.com links). */
    fun startMeet(context: Context) {
        viewUrl(context, "https://meet.google.com/new")
    }

    /** Open a URL (Meet join link, etc.) in whatever app handles it. */
    fun viewUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Open a specific calendar event in the calendar app. */
    fun openEvent(context: Context, eventId: Long, begin: Long) {
        val uri = Uri.parse("content://com.android.calendar/events/$eventId")
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .putExtra("beginTime", begin)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
