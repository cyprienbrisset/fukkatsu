package com.cyprienbrisset.myportal.store

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirm != null) context.startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> Toast.makeText(context, "Installé", Toast.LENGTH_SHORT).show()
            else -> Toast.makeText(context, "Échec install : ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}", Toast.LENGTH_LONG).show()
        }
    }
    companion object { const val ACTION = "com.cyprienbrisset.myportal.INSTALL_STATUS" }
}
