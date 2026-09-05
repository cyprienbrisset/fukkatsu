package com.cyprienbrisset.myportal.store

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

class ApkInstaller(private val context: Context) {
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun requestPermission() {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(packageName: String, apks: List<File>) {
        val pi = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            setInstallLocation(android.content.pm.PackageInfo.INSTALL_LOCATION_AUTO)
            setOriginatingUid(android.os.Process.myUid())
            setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_USER)
            val total = apks.sumOf { it.length() }
            if (total > 0) setSize(total)
        }
        val sessionId = pi.createSession(params)
        pi.openSession(sessionId).use { s ->
            apks.forEach { apk ->
                s.openWrite(apk.name, 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    s.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(
                context, sessionId,
                Intent(InstallResultReceiver.ACTION).setPackage(context.packageName), flags,
            )
            s.commit(pending.intentSender)
        }
    }
}
