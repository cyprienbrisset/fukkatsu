package com.cyprienbrisset.myportal.alarm

import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.myportal.ui.theme.MyPortalTheme

class AlarmRingActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        startRinging()

        val alarmId = intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1)

        setContent {
            MyPortalTheme {
                Column(
                    Modifier.fillMaxSize().padding(48.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Réveil", fontSize = 48.sp)
                    Spacer(Modifier.height(48.dp))
                    Button(onClick = { stopAndFinish() }) { Text("Arrêter") }
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { snooze(alarmId) }) { Text("Snooze 10 min") }
                }
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun startRinging() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(applicationContext, uri).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
            streamType = AudioManager.STREAM_ALARM
            play()
        }
    }

    private fun snooze(alarmId: Long) {
        if (alarmId >= 0) AlarmSnooze.schedule(this, alarmId, minutes = 10)
        stopAndFinish()
    }

    private fun stopAndFinish() {
        ringtone?.stop(); ringtone = null; finish()
    }

    override fun onDestroy() {
        ringtone?.stop(); super.onDestroy()
    }
}
