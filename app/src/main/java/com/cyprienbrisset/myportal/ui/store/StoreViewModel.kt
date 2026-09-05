package com.cyprienbrisset.myportal.ui.store

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyprienbrisset.myportal.store.ApkDownloader
import com.cyprienbrisset.myportal.store.ApkInstaller
import com.cyprienbrisset.myportal.store.FukkaAccount
import com.cyprienbrisset.myportal.store.InstallEvents
import com.cyprienbrisset.myportal.store.StoreApp
import com.cyprienbrisset.myportal.store.StoreCategory
import com.cyprienbrisset.myportal.store.StoreException
import com.cyprienbrisset.myportal.store.categories as fukkaCategories
import com.cyprienbrisset.myportal.store.categoryApps as fukkaCategoryApps
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

/** Install progress sentinels stored in [StoreViewModel.progress] per package. */
object InstallProgress {
    const val INSTALLING = 100 // downloaded, system installer running
    const val INSTALLED = 101  // system reported success
    const val FAILED = -1
}

class StoreViewModel(app: Application) : AndroidViewModel(app) {
    private val account = FukkaAccount(app)
    private val downloader = ApkDownloader(app)
    private val installer = ApkInstaller(app)

    val isLoggedIn = account.isLoggedIn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Chips: "Populaires" first, then real categories once loaded. */
    private val _categories = MutableStateFlow(listOf(StoreCategory(TOP_KEY, "Populaires", null)))
    val categories: StateFlow<List<StoreCategory>> = _categories
    private val _selectedKey = MutableStateFlow(TOP_KEY)
    val selectedKey: StateFlow<String> = _selectedKey

    /** The app grid content (Populaires / a category / search results). */
    private val _content = MutableStateFlow<StoreUi>(StoreUi.Idle)
    val content: StateFlow<StoreUi> = _content

    private val _progress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val progress: StateFlow<Map<String, Int>> = _progress

    init {
        viewModelScope.launch {
            InstallEvents.events.collect { e ->
                val pkg = e.packageName ?: return@collect
                _progress.value = _progress.value +
                    (pkg to if (e.success) InstallProgress.INSTALLED else InstallProgress.FAILED)
            }
        }
    }

    /** Loads categories + the default "Populaires" grid once. Safe to call on every entry. */
    fun loadHome() {
        loadCategoriesIfNeeded()
        if (_content.value is StoreUi.Idle) selectByKey(TOP_KEY, null)
    }

    private fun loadCategoriesIfNeeded() {
        if (_categories.value.size > 1) return
        viewModelScope.launch {
            runCatching {
                val ad = account.authData() ?: return@launch
                _categories.value = listOf(StoreCategory(TOP_KEY, "Populaires", null)) + fukkaCategories(ad)
            }
        }
    }

    fun selectCategory(cat: StoreCategory) = selectByKey(cat.key, cat.browseUrl)

    private fun selectByKey(key: String, browseUrl: String?) {
        _selectedKey.value = key
        _content.value = StoreUi.Loading
        viewModelScope.launch {
            _content.value = try {
                val ad = account.authData()
                    ?: return@launch run { _content.value = StoreUi.Error("Non connecté") }
                val apps = if (key == TOP_KEY) fukkaTopApps(ad) else fukkaCategoryApps(ad, browseUrl!!)
                if (apps.isEmpty()) StoreUi.Error("Aucune application compatible.") else StoreUi.Results(apps)
            } catch (e: StoreException) { StoreUi.Error(e.message ?: "Erreur") }
            catch (e: Exception) { StoreUi.Error("Erreur réseau") }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) { selectByKey(TOP_KEY, null); return }
        _selectedKey.value = SEARCH_KEY
        _content.value = StoreUi.Loading
        viewModelScope.launch {
            _content.value = try {
                val ad = account.authData()
                    ?: return@launch run { _content.value = StoreUi.Error("Non connecté") }
                val apps = fukkaSearch(ad, query)
                if (apps.isEmpty()) StoreUi.Error("Aucun résultat.") else StoreUi.Results(apps)
            } catch (e: StoreException) { StoreUi.Error(e.message ?: "Erreur") }
            catch (e: Exception) { StoreUi.Error("Erreur réseau") }
        }
    }

    fun logout() = viewModelScope.launch { account.logout() }

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
                _progress.value = _progress.value + (appItem.packageName to InstallProgress.FAILED)
            }
        }
    }

    companion object {
        const val TOP_KEY = "__top__"
        const val SEARCH_KEY = "__search__"
    }
}
