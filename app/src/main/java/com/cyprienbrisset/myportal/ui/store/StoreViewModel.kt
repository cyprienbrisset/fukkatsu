package com.cyprienbrisset.myportal.ui.store

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyprienbrisset.myportal.store.ApkDownloader
import com.cyprienbrisset.myportal.store.ApkInstaller
import com.cyprienbrisset.myportal.store.FukkaAccount
import com.cyprienbrisset.myportal.store.StoreApp
import com.cyprienbrisset.myportal.store.StoreException
import com.cyprienbrisset.myportal.store.files as fukkaFiles
import com.cyprienbrisset.myportal.store.search as fukkaSearch
import com.cyprienbrisset.myportal.store.topApps as fukkaTopApps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface StoreUi {
    data object Idle : StoreUi
    data object Loading : StoreUi
    data class Results(val apps: List<StoreApp>) : StoreUi
    data class Error(val message: String) : StoreUi
}

class StoreViewModel(app: Application) : AndroidViewModel(app) {
    private val account = FukkaAccount(app)
    private val downloader = ApkDownloader(app)
    private val installer = ApkInstaller(app)

    val isLoggedIn = account.isLoggedIn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    private val _ui = MutableStateFlow<StoreUi>(StoreUi.Idle)
    val ui: StateFlow<StoreUi> = _ui
    /** Curated "top compatible apps" shown when no search is active. */
    private val _home = MutableStateFlow<StoreUi>(StoreUi.Idle)
    val home: StateFlow<StoreUi> = _home
    private val _progress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val progress: StateFlow<Map<String, Int>> = _progress

    fun logout() = viewModelScope.launch { account.logout() }

    /** Loads the curated home once (cached). Safe to call on every screen entry. */
    fun loadHome() {
        if (_home.value is StoreUi.Results || _home.value is StoreUi.Loading) return
        _home.value = StoreUi.Loading
        viewModelScope.launch {
            _home.value = try {
                val ad = account.authData() ?: return@launch run { _home.value = StoreUi.Error("Non connecté") }
                StoreUi.Results(fukkaTopApps(ad))
            } catch (e: StoreException) { StoreUi.Error(e.message ?: "Erreur") }
            catch (e: Exception) { StoreUi.Error("Erreur réseau") }
        }
    }

    /** Clears the active search so the curated home is shown again. */
    fun clearSearch() { _ui.value = StoreUi.Idle }

    fun search(query: String) {
        if (query.isBlank()) { _ui.value = StoreUi.Idle; return }
        _ui.value = StoreUi.Loading
        viewModelScope.launch {
            _ui.value = try {
                val ad = account.authData() ?: return@launch run { _ui.value = StoreUi.Error("Non connecté") }
                StoreUi.Results(fukkaSearch(ad, query))
            } catch (e: StoreException) { StoreUi.Error(e.message ?: "Erreur") }
            catch (e: Exception) { StoreUi.Error("Erreur réseau") }
        }
    }

    fun install(appItem: StoreApp) {
        if (!installer.canInstall()) { installer.requestPermission(); return }
        viewModelScope.launch {
            try {
                val ad = account.authData() ?: throw StoreException("Non connecté")
                val files = fukkaFiles(ad, appItem.packageName, appItem.versionCode)
                val local = downloader.download(appItem.packageName, files) { pct ->
                    _progress.value = _progress.value + (appItem.packageName to pct)
                }
                installer.install(appItem.packageName, local)
            } catch (e: Exception) {
                _progress.value = _progress.value + (appItem.packageName to -1)
            }
        }
    }
}
