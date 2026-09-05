package com.cyprienbrisset.myportal.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.myportal.ui.sumi.HankoSeal
import com.cyprienbrisset.myportal.ui.theme.Mincho
import com.cyprienbrisset.myportal.ui.theme.Shu

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onTiles: () -> Unit,
    onAlarms: () -> Unit,
    onWeather: () -> Unit,
    onStore: () -> Unit = {},
    onInstalledApps: () -> Unit = {},
) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            HankoSeal("朱", size = 40.dp, onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Text("Réglages", fontFamily = Mincho, color = MaterialTheme.colorScheme.onBackground, fontSize = 22.sp)
        }
        SettingRow("Tuiles", onTiles)
        SettingRow("Alarmes", onAlarms)
        SettingRow("Ville météo", onWeather)
        SettingRow("FukkaStore", onStore)
        SettingRow("Applications installées", onInstalledApps)
        SettingRow("Réglages système") {
            ctx.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}

@Composable
private fun SettingRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text("›", color = Shu, fontSize = 22.sp)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
}
