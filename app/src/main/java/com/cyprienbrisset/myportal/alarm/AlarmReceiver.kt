package com.cyprienbrisset.myportal.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Fleshed out in Task 15.
    }

    companion object {
        const val ACTION_FIRE = "com.cyprienbrisset.myportal.ALARM_FIRE"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
