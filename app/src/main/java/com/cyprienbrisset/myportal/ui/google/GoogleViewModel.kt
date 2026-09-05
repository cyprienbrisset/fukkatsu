package com.cyprienbrisset.myportal.ui.google

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyprienbrisset.myportal.data.settings.SettingsRepository
import com.cyprienbrisset.myportal.integration.AppShortcut
import com.cyprienbrisset.myportal.integration.AppShortcuts
import com.cyprienbrisset.myportal.integration.CalEvent
import com.cyprienbrisset.myportal.integration.CalendarRepository
import com.cyprienbrisset.myportal.integration.GoogleApps
import com.cyprienbrisset.myportal.integration.IcsCalendarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GoogleAppEntry(val pkg: String, val name: String, val shortcuts: List<AppShortcut>)

class GoogleViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository(app)

    val calendarInstalled: Boolean get() = GoogleApps.isInstalled(getApplication(), GoogleApps.CALENDAR)
    val chatInstalled: Boolean get() = GoogleApps.isInstalled(getApplication(), GoogleApps.CHAT)
    val meetPackage: String? get() = GoogleApps.meetPackage(getApplication())
    val anyInstalled: Boolean get() = calendarInstalled || chatInstalled || meetPackage != null

    private val _entries = MutableStateFlow<List<GoogleAppEntry>>(emptyList())
    val entries: StateFlow<List<GoogleAppEntry>> = _entries
    private val _canReadShortcuts = MutableStateFlow(true)
    val canReadShortcuts: StateFlow<Boolean> = _canReadShortcuts

    private val _events = MutableStateFlow<List<CalEvent>>(emptyList())
    val events: StateFlow<List<CalEvent>> = _events
    private val _loadedOnce = MutableStateFlow(false)
    val loadedOnce: StateFlow<Boolean> = _loadedOnce

    val icsUrl: StateFlow<String?> = settings.googleIcsUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun loadShortcuts() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            _canReadShortcuts.value = AppShortcuts.canReadShortcuts(ctx)
            val wanted = buildList {
                if (calendarInstalled) add(GoogleApps.CALENDAR to "Agenda")
                if (chatInstalled) add(GoogleApps.CHAT to "Chat")
                meetPackage?.let { add(it to "Meet") }
            }
            _entries.value = wanted.map { (pkg, name) ->
                GoogleAppEntry(pkg, name, AppShortcuts.forPackage(ctx, pkg) ?: emptyList())
            }
        }
    }

    fun loadEvents(nowMs: Long) {
        viewModelScope.launch {
            val url = icsUrl.value
            _events.value = if (!url.isNullOrBlank()) {
                IcsCalendarRepository.upcoming(url, nowMs) ?: CalendarRepository.upcoming(getApplication(), nowMs)
            } else {
                CalendarRepository.upcoming(getApplication(), nowMs)
            }
            _loadedOnce.value = true
        }
    }
}
