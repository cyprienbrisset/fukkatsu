package com.cyprienbrisset.myportal.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cyprienbrisset.myportal.data.AppDatabase
import com.cyprienbrisset.myportal.data.alarm.AlarmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        if (alarmId < 0) return

        context.startActivity(
            Intent(context, AlarmRingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_ALARM_ID, alarmId)
        )

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AlarmRepository(AppDatabase.get(context).alarmDao())
                val scheduler = AlarmScheduler(context)
                val alarm = repo.byId(alarmId)
                if (alarm != null) {
                    if (alarm.repeatDays == 0) repo.setEnabled(alarm, false)
                    else scheduler.schedule(alarm)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.cyprienbrisset.myportal.ALARM_FIRE"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
