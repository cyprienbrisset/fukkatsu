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
) {
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    val dateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
    val greeting = when (now.hour) {
        in 5..11 -> "Bonjour"
        in 12..17 -> "Bon après-midi"
        else -> "Bonsoir"
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(now.format(timeFmt), fontSize = 64.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground)
            Text("$greeting · ${now.format(dateFmt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (nextAlarm != null) {
                Text("⏰ ${nextAlarm.format(DateTimeFormatter.ofPattern("EEE HH:mm", Locale.FRENCH))}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (weather != null) {
            Text("${weather.temperatureC}° · ${weather.description}",
                fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}
