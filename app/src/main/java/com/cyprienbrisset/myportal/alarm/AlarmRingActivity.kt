package com.cyprienbrisset.myportal.alarm

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.cyprienbrisset.myportal.data.AppDatabase
import com.cyprienbrisset.myportal.data.alarm.AlarmRepository
import com.cyprienbrisset.myportal.ui.theme.MyPortalTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmRingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        val alarmId = intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1)

        setContent {
            var snoozeMinutes by remember { mutableStateOf(10) }
            if (alarmId >= 0) {
                androidx.compose.runtime.LaunchedEffect(alarmId) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val a = AlarmRepository(AppDatabase.get(this@AlarmRingActivity).alarmDao()).byId(alarmId)
                        if (a != null) snoozeMinutes = a.snoozeMinutes
                    }
                }
            }
            MyPortalTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(48.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Réveil", fontSize = 48.sp)
                        Spacer(Modifier.height(48.dp))
                        Button(onClick = { AlarmForegroundService.stop(this@AlarmRingActivity); finish() }) {
                            Text("Arrêter")
                        }
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = {
                            if (alarmId >= 0) AlarmForegroundService.snooze(this@AlarmRingActivity, alarmId, snoozeMinutes)
                            finish()
                        }) { Text("Snooze $snoozeMinutes min") }
                    }
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
}
