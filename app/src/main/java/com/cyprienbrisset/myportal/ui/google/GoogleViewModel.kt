package com.cyprienbrisset.myportal.ui.google

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyprienbrisset.myportal.integration.CalEvent
import com.cyprienbrisset.myportal.integration.CalendarRepository
import com.cyprienbrisset.myportal.integration.GoogleApps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GoogleViewModel(app: Application) : AndroidViewModel(app) {

    val calendarInstalled: Boolean get() = GoogleApps.isInstalled(getApplication(), GoogleApps.CALENDAR)
    val chatInstalled: Boolean get() = GoogleApps.isInstalled(getApplication(), GoogleApps.CHAT)
    val meetPackage: String? get() = GoogleApps.meetPackage(getApplication())

    val anyInstalled: Boolean get() = calendarInstalled || chatInstalled || meetPackage != null

    private val _events = MutableStateFlow<List<CalEvent>>(emptyList())
    val events: StateFlow<List<CalEvent>> = _events
    private val _loadedOnce = MutableStateFlow(false)
    val loadedOnce: StateFlow<Boolean> = _loadedOnce

    fun loadEvents(nowMs: Long) {
        viewModelScope.launch {
            _events.value = CalendarRepository.upcoming(getApplication(), nowMs)
            _loadedOnce.value = true
        }
    }
}
