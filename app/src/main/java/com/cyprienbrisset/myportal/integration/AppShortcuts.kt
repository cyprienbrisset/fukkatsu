package com.cyprienbrisset.myportal.integration

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process

data class AppShortcut(val pkg: String, val id: String, val label: String)

/**
 * Reads and launches other apps' shortcuts (the ones a launcher shows on long-press), via
 * [LauncherApps]. This only works while Fukkatsu is the default launcher — otherwise the platform
 * denies shortcut-host access and we degrade to a "set as default launcher" hint.
 */
object AppShortcuts {

    private fun launcherApps(context: Context): LauncherApps? =
        context.getSystemService(LauncherApps::class.java)

    /** True only when Fukkatsu is the current default launcher (required to read shortcuts). */
    fun canReadShortcuts(context: Context): Boolean =
        runCatching { launcherApps(context)?.hasShortcutHostPermission() == true }.getOrDefault(false)

    /** App shortcuts for [pkg], or null if we can't read them (not default launcher / error). */
    fun forPackage(context: Context, pkg: String): List<AppShortcut>? {
        val la = launcherApps(context) ?: return null
        if (!runCatching { la.hasShortcutHostPermission() }.getOrDefault(false)) return null
        val query = LauncherApps.ShortcutQuery().apply {
            setPackage(pkg)
            setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
        }
        return runCatching {
            (la.getShortcuts(query, Process.myUserHandle()) ?: emptyList())
                .filter { it.isEnabled }
                .map { AppShortcut(pkg, it.id, (it.longLabel ?: it.shortLabel ?: "").toString().trim()) }
                .filter { it.label.isNotBlank() }
        }.getOrNull()
    }

    fun iconDrawable(context: Context, pkg: String, id: String): Drawable? {
        val la = launcherApps(context) ?: return null
        return runCatching {
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(pkg)
                setShortcutIds(listOf(id))
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                )
            }
            val info = la.getShortcuts(query, Process.myUserHandle())?.firstOrNull() ?: return null
            la.getShortcutIconDrawable(info, context.resources.displayMetrics.densityDpi)
        }.getOrNull()
    }

    fun launch(context: Context, pkg: String, id: String) {
        val la = launcherApps(context) ?: return
        runCatching { la.startShortcut(pkg, id, null, null, Process.myUserHandle()) }
    }
}
