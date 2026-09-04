package com.cyprienbrisset.myportal.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyprienbrisset.myportal.alarm.nextTriggerTime
import com.cyprienbrisset.myportal.data.AppDatabase
import com.cyprienbrisset.myportal.data.alarm.AlarmRepository
import com.cyprienbrisset.myportal.data.settings.SettingsRepository
import com.cyprienbrisset.myportal.data.tile.TileRepository
import com.cyprienbrisset.myportal.data.weather.Weather
import com.cyprienbrisset.myportal.data.weather.WeatherRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TileRepository(AppDatabase.get(app).tileDao())
    private val settings = SettingsRepository(app)
    private val weatherRepo = WeatherRepository()
    private val alarmRepo = AlarmRepository(AppDatabase.get(app).alarmDao())

    val tiles = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val now: StateFlow<LocalDateTime> = flow {
        while (true) { emit(LocalDateTime.now()); delay(1000) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalDateTime.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val weather: StateFlow<Weather?> = settings.weatherLocation
        .flatMapLatest { loc ->
            flow {
                while (true) {
                    emit(loc?.let { weatherRepo.currentWeather(it.lat, it.lon) })
                    delay(15 * 60 * 1000)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val nextAlarm: StateFlow<LocalDateTime?> = alarmRepo.observeAll().map { list ->
        list.filter { it.enabled }
            .map { nextTriggerTime(it, LocalDateTime.now()) }
            .minOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
