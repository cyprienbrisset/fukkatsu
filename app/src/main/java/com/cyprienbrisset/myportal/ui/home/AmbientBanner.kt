package com.cyprienbrisset.myportal.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.myportal.data.weather.Weather
import com.cyprienbrisset.myportal.ui.theme.Kinari
import com.cyprienbrisset.myportal.ui.theme.Mincho
import com.cyprienbrisset.myportal.ui.theme.Shu
import com.cyprienbrisset.myportal.ui.theme.SumiMuted
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AmbientBanner(
    now: LocalDateTime,
    weather: Weather?,
    modifier: Modifier = Modifier,
    nextAlarm: LocalDateTime? = null,
    portrait: Boolean = false,
) {
    val time = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH))
    val align = if (portrait) Alignment.CenterHorizontally else Alignment.Start

    Column(modifier, horizontalAlignment = align) {
        Text("復活", fontFamily = Mincho, fontWeight = FontWeight.Medium, color = Shu,
            fontSize = if (portrait) 84.sp else 120.sp)
        Text("F U K K A T S U", color = SumiMuted, fontSize = 13.sp, letterSpacing = 6.sp)
        Spacer(Modifier.height(if (portrait) 8.dp else 10.dp))
        Text(time, fontFamily = Mincho, fontWeight = FontWeight.Normal, color = Kinari, fontSize = if (portrait) 72.sp else 92.sp)
        Spacer(Modifier.height(12.dp))
        Text(date, color = Kinari, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        val wx = buildString {
            if (weather != null) append("${weather.temperatureC}°  ${weather.description}")
            if (nextAlarm != null) {
                if (isNotEmpty()) append("   ·   ")
                append("⏰ " + nextAlarm.format(DateTimeFormatter.ofPattern("EEE HH:mm", Locale.FRENCH)))
            }
        }
        if (wx.isNotEmpty()) Text(wx, color = SumiMuted, fontSize = 14.sp, textAlign = if (portrait) TextAlign.Center else TextAlign.Start)
    }
}
