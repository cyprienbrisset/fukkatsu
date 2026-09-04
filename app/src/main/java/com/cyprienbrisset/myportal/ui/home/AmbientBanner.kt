package com.cyprienbrisset.myportal.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.myportal.data.weather.Weather
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
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    val dateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
    val greeting = when (now.hour) {
        in 5..11 -> "Bonjour"
        in 12..17 -> "Bon après-midi"
        else -> "Bonsoir"
    }

    @Composable
    fun ClockText(sizeSp: Int) {
        Text(now.format(timeFmt), fontSize = sizeSp.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground)
    }
    @Composable
    fun GreetingText() {
        Text("$greeting · ${now.format(dateFmt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    @Composable
    fun WeatherText() {
        if (weather != null) {
            Text("${weather.temperatureC}° · ${weather.description}",
                fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
        }
    }
    @Composable
    fun NextAlarmText() {
        if (nextAlarm != null) {
            Text("⏰ ${nextAlarm.format(DateTimeFormatter.ofPattern("EEE HH:mm", Locale.FRENCH))}",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (portrait) {
        Column(
            modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ClockText(72)
            GreetingText()
            WeatherText()
            NextAlarmText()
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                ClockText(64)
                GreetingText()
                NextAlarmText()
            }
            WeatherText()
        }
    }
}
