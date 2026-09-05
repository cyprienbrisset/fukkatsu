# FukkaStore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build FukkaStore — an integrated app store in Fukkatsu (login once with Google, search, install real Play Store apps) reusing Aurora's proven auth (already working in the spike) + gplayapi + PackageInstaller, with a Sumi UI.

**Architecture:** The auth engine (`store/FukkaAuth.kt`) from the GO spike stays. Add account persistence, a store client (search/files), an APK downloader + PackageInstaller, and a Sumi store UI gated by login. Entry from Settings.

**Tech Stack:** gplayapi 3.2.6 (JitPack), OkHttp/Coil, Android PackageInstaller, DataStore, Jetpack Compose (Sumi).

---

## Environment (critical)

- No `java` on PATH. Prefix Gradle: `JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew ...`
- Devices: Portal `adb -s 2221B01C9C02NQ` (API 29), emulator `emulator-5554` (network). `adb` = `/Users/cyprienbrisset/Library/Android/sdk/platform-tools/adb`. Network verification best on the Portal (where the user is logged in) or emulator.
- Verify `:app:assembleDebug`; JVM tests `:app:testDebugUnitTest`. Do NOT stage `.claude/oxygen-status.json`. Do NOT import `androidx.compose.foundation.layout.weight`.
- Branch `feat/fukkastore-spike` (has FukkaAuth.kt, FukkaLoginActivity.kt, gplayapi dep, `fukka_device.properties`).
- **Aurora source** for API reference: `/tmp/AuroraStore` (read `PurchaseHelper`/download usage when needed).
- gplayapi 3.2.6 facts (confirmed): `AuthHelper.build(email, aasToken, properties: Properties, locale: Locale): AuthData`; `SearchHelper(authData).searchResults(query).appList: List<App>` with `App.packageName/displayName/developerName/iconArtwork.url/versionCode`; `AppDetailsHelper(authData).getAppByPackageName(pkg): App`.

---

## PHASE 1 — Store client + account persistence

### Task 1: StoreApp/ApkFile models + extend FukkaAuth with search()/files()

**Files:** `store/StoreModels.kt` (new), `store/FukkaAuth.kt` (modify)

- [ ] **Step 1: StoreModels.kt**
```kotlin
package com.cyprienbrisset.myportal.store

import android.graphics.Bitmap

data class StoreApp(
    val packageName: String,
    val title: String,
    val developer: String,
    val iconUrl: String,
    val versionCode: Int,
)

data class ApkFile(val name: String, val url: String, val size: Long)

class StoreException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

- [ ] **Step 2: Add `search()` and `files()` to FukkaAuth.kt**
Add these functions (keep `exchangeAasToken`, `buildAuthDataFromAas`, `searchTitles`):
```kotlin
suspend fun search(authData: com.aurora.gplayapi.data.models.AuthData, query: String): List<StoreApp> =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        com.aurora.gplayapi.helpers.SearchHelper(authData).searchResults(query).appList.map {
            StoreApp(
                packageName = it.packageName,
                title = it.displayName,
                developer = it.developerName,
                iconUrl = it.iconArtwork.url,
                versionCode = it.versionCode,
            )
        }
    }

suspend fun files(authData: com.aurora.gplayapi.data.models.AuthData, packageName: String, versionCode: Int): List<ApkFile> =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val details = com.aurora.gplayapi.helpers.AppDetailsHelper(authData).getAppByPackageName(packageName)
        // Confirm PurchaseHelper API against gplayapi 3.2.6 + /tmp/AuroraStore usage.
        // Aurora: PurchaseHelper(authData).purchase(packageName, versionCode, offerType) -> List<gplayapi File>
        val gFiles = com.aurora.gplayapi.helpers.PurchaseHelper(authData)
            .purchase(details.packageName, details.versionCode, details.offerType)
        gFiles.map { ApkFile(name = it.name, url = it.url, size = it.size) }
    }
```
IMPORTANT: The `PurchaseHelper.purchase(...)` signature and the gplayapi `File` fields (`name`, `url`, `size`) must be confirmed for 3.2.6 — read `/tmp/AuroraStore/app/src/main/java/com/aurora/store/data/**` (grep `PurchaseHelper`) and/or the resolved jar. Adjust to the real API; keep the `files(...)` return type `List<ApkFile>`.

- [ ] **Step 3: Verify + commit**
Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/store/StoreModels.kt app/src/main/java/com/cyprienbrisset/myportal/store/FukkaAuth.kt
git commit -m "feat(fukkastore): StoreApp/ApkFile models + search()/files() on FukkaAuth"
```

---

### Task 2: FukkaAccount (persist login, rebuild AuthData)

**Files:** `store/FukkaAccount.kt` (new)

- [ ] **Step 1: Implement** — persist email+aasToken via DataStore; rebuild+cache AuthData.
```kotlin
package com.cyprienbrisset.myportal.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aurora.gplayapi.data.models.AuthData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.storeDataStore by preferencesDataStore(name = "fukkastore")

class FukkaAccount(private val context: Context) {
    private val EMAIL = stringPreferencesKey("email")
    private val AAS = stringPreferencesKey("aas")
    @Volatile private var cached: AuthData? = null

    val isLoggedIn = context.storeDataStore.data.map { it[EMAIL] != null && it[AAS] != null }

    suspend fun save(email: String, aasToken: String) {
        context.storeDataStore.edit { it[EMAIL] = email; it[AAS] = aasToken }
        cached = null
    }

    suspend fun logout() {
        context.storeDataStore.edit { it.remove(EMAIL); it.remove(AAS) }
        cached = null
    }

    /** Returns a usable AuthData (cached), rebuilding from the saved aasToken if needed. */
    suspend fun authData(): AuthData? {
        cached?.let { return it }
        val prefs = context.storeDataStore.data.first()
        val email = prefs[EMAIL] ?: return null
        val aas = prefs[AAS] ?: return null
        return buildAuthDataFromAas(context, email, aas).also { cached = it }
    }
}
```
(`buildAuthDataFromAas(context, email, aas): AuthData` already exists in FukkaAuth.kt from the spike.)

- [ ] **Step 2: Verify + commit**
Run: `:app:assembleDebug` → SUCCESS.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/store/FukkaAccount.kt
git commit -m "feat(fukkastore): persistent account (email+aasToken) with AuthData cache"
```

---

## PHASE 2 — Download + install

### Task 3: ApkDownloader

**Files:** `store/ApkDownloader.kt` (new)
```kotlin
package com.cyprienbrisset.myportal.store

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class ApkDownloader(private val context: Context, private val http: OkHttpClient = OkHttpClient()) {
    suspend fun download(pkg: String, files: List<ApkFile>, onProgress: (Int) -> Unit): List<File> =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "apks/$pkg").apply { deleteRecursively(); mkdirs() }
            val total = files.sumOf { it.size }.coerceAtLeast(1L)
            var done = 0L
            files.map { f ->
                val out = File(dir, if (f.name.endsWith(".apk")) f.name else "${f.name}.apk")
                http.newCall(Request.Builder().url(f.url).build()).execute().use { resp ->
                    val body = resp.body ?: throw StoreException("Téléchargement vide (${f.name})")
                    out.outputStream().use { os ->
                        val buf = ByteArray(64 * 1024)
                        body.byteStream().use { ins ->
                            while (true) {
                                val n = ins.read(buf); if (n < 0) break
                                os.write(buf, 0, n); done += n
                                onProgress(((done * 100) / total).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                }
                out
            }
        }
}
```
- [ ] Verify `:app:assembleDebug` → SUCCESS; commit `feat(fukkastore): APK downloader with progress`.

---

### Task 4: ApkInstaller + InstallResultReceiver + permission

**Files:** `store/ApkInstaller.kt`, `store/InstallResultReceiver.kt`, `AndroidManifest.xml`

- [ ] **Step 1: InstallResultReceiver.kt**
```kotlin
package com.cyprienbrisset.myportal.store

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirm != null) context.startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> Toast.makeText(context, "Installé", Toast.LENGTH_SHORT).show()
            else -> Toast.makeText(context, "Échec install : ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}", Toast.LENGTH_LONG).show()
        }
    }
    companion object { const val ACTION = "com.cyprienbrisset.myportal.INSTALL_STATUS" }
}
```

- [ ] **Step 2: ApkInstaller.kt**
```kotlin
package com.cyprienbrisset.myportal.store

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

class ApkInstaller(private val context: Context) {
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun requestPermission() {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(apks: List<File>) {
        val pi = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = pi.createSession(params)
        pi.openSession(sessionId).use { s ->
            apks.forEach { apk ->
                s.openWrite(apk.name, 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    s.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(
                context, sessionId,
                Intent(InstallResultReceiver.ACTION).setPackage(context.packageName), flags,
            )
            s.commit(pending.intentSender)
        }
    }
}
```

- [ ] **Step 3: Manifest** — under `<manifest>`: `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />`; inside `<application>`:
```xml
<receiver android:name=".store.InstallResultReceiver" android:exported="false">
    <intent-filter><action android:name="com.cyprienbrisset.myportal.INSTALL_STATUS" /></intent-filter>
</receiver>
```
- [ ] Verify `:app:assembleDebug` → SUCCESS; commit `feat(fukkastore): PackageInstaller (base+splits) + status receiver + permission`.

---

## PHASE 3 — UI + wiring

### Task 5: Persist account on login success (FukkaLoginActivity)

**Files:** `store/FukkaLoginActivity.kt` (modify)

- [ ] **Step 1:** After a successful login (email + aasToken obtained), call `FukkaAccount(this).save(email, aasToken)` (on a coroutine) and `finish()` back to the caller with `setResult(RESULT_OK)`. Keep the existing WebView + AC2DM flow; just persist on success (currently it only shows the status text). Read the file and add the persistence + finish; keep the on-screen status for errors.
- [ ] Verify + commit `feat(fukkastore): persist account after successful login`.

---

### Task 6: StoreViewModel

**Files:** `ui/store/StoreViewModel.kt` (new)
```kotlin
package com.cyprienbrisset.myportal.ui.store

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyprienbrisset.myportal.store.*
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
    private val _progress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val progress: StateFlow<Map<String, Int>> = _progress

    fun logout() = viewModelScope.launch { account.logout() }

    fun search(query: String) {
        if (query.isBlank()) { _ui.value = StoreUi.Idle; return }
        _ui.value = StoreUi.Loading
        viewModelScope.launch {
            _ui.value = try {
                val ad = account.authData() ?: return@launch run { _ui.value = StoreUi.Error("Non connecté") }
                StoreUi.Results(search(ad, query))
            } catch (e: StoreException) { StoreUi.Error(e.message ?: "Erreur") }
            catch (e: Exception) { StoreUi.Error("Erreur réseau") }
        }
    }

    fun install(appItem: StoreApp) {
        if (!installer.canInstall()) { installer.requestPermission(); return }
        viewModelScope.launch {
            try {
                val ad = account.authData() ?: throw StoreException("Non connecté")
                val files = files(ad, appItem.packageName, appItem.versionCode)
                val local = downloader.download(appItem.packageName, files) { pct ->
                    _progress.value = _progress.value + (appItem.packageName to pct)
                }
                installer.install(local)
            } catch (e: Exception) {
                _progress.value = _progress.value + (appItem.packageName to -1)
            }
        }
    }
}
```
Note: `search(ad, query)` and `files(ad, ...)` are the top-level functions in `store/FukkaAuth.kt` — import them (`com.cyprienbrisset.myportal.store.search`, `.files`). If Kotlin overload resolution clashes with the VM's own `search`, rename the VM method (e.g. `runSearch`) or import with alias.
- [ ] Verify `:app:assembleDebug` → SUCCESS; commit `feat(fukkastore): StoreViewModel`.

---

### Task 7: StoreScreen (Sumi) + entry point + route

**Files:** `ui/store/StoreScreen.kt` (new), `ui/settings/SettingsScreen.kt` (modify — replace temp test row with "FukkaStore"), `ui/AppNav.kt` (modify)

- [ ] **Step 1: StoreScreen.kt** — login gate → search → result cards → install.
```kotlin
package com.cyprienbrisset.myportal.ui.store

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cyprienbrisset.myportal.store.FukkaLoginActivity
import com.cyprienbrisset.myportal.ui.sumi.HankoSeal
import com.cyprienbrisset.myportal.ui.sumi.SumiPrimaryButton
import com.cyprienbrisset.myportal.ui.theme.Kinari
import com.cyprienbrisset.myportal.ui.theme.Mincho
import com.cyprienbrisset.myportal.ui.theme.SumiMuted
import com.cyprienbrisset.myportal.ui.theme.SumiSurface

@Composable
fun StoreScreen(onBack: () -> Unit, vm: StoreViewModel = viewModel()) {
    val ctx = LocalContext.current
    val loggedIn by vm.isLoggedIn.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            HankoStore(onBack)
            Spacer(Modifier.width(14.dp))
            Text("FukkaStore", fontFamily = Mincho, color = Kinari, fontSize = 22.sp)
        }
        if (!loggedIn) {
            Text("Connecte-toi avec ton compte Google pour installer des apps.", color = SumiMuted)
            Spacer(Modifier.height(16.dp))
            SumiPrimaryButton("Se connecter", onClick = { ctx.startActivity(Intent(ctx, FukkaLoginActivity::class.java)) })
        } else {
            OutlinedTextField(query, { query = it }, label = { Text("Rechercher une app") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SumiPrimaryButton("Rechercher", onClick = { vm.search(query) })
            Spacer(Modifier.height(14.dp))
            when (val s = ui) {
                is StoreUi.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
                is StoreUi.Error -> Text(s.message, color = SumiMuted)
                is StoreUi.Results -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.apps, key = { it.packageName }) { app ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SumiSurface).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            AsyncImage(app.iconUrl, null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
                            Column(Modifier.weight(1f)) {
                                Text(app.title, color = Kinari, fontSize = 16.sp, maxLines = 1)
                                Text(app.developer, color = SumiMuted, fontSize = 12.sp, maxLines = 1)
                            }
                            when (val pct = progress[app.packageName]) {
                                null -> SumiPrimaryButton("Installer", onClick = { vm.install(app) }, modifier = Modifier.widthIn(max = 130.dp))
                                in 0..99 -> Text("$pct %", color = Kinari)
                                100 -> Text("Installation…", color = SumiMuted)
                                else -> Text("Échec", color = SumiMuted)
                            }
                        }
                    }
                }
                StoreUi.Idle -> {}
            }
        }
    }
}

@Composable
private fun HankoStore(onBack: () -> Unit) { HankoSeal("店", size = 40.dp, onClick = onBack) }
```

- [ ] **Step 2: SettingsScreen** — replace the temporary `SettingRow("FukkaStore login (test)", ...)` row with `SettingRow("FukkaStore", null, onStore)`. Add `onStore: () -> Unit = {}` param. Keep the rest.

- [ ] **Step 3: AppNav** — add `Routes.STORE = "store"`; the Settings `onStore` navigates to it; register:
```kotlin
composable(Routes.STORE) { com.cyprienbrisset.myportal.ui.store.StoreScreen(onBack = { nav.popBackStack() }) }
```
Wire `SettingsScreen(..., onStore = { nav.navigate(Routes.STORE) })`. Remove the temp `onFukkaLogin`/`onStoreLogin` login-test wiring; the login is now launched from within StoreScreen.

- [ ] **Step 4: Build + on-device end-to-end (Portal)**
Run: `:app:assembleDebug` → SUCCESS; `:app:testDebugUnitTest` → pass.
Install on the Portal. Réglages → FukkaStore → (login if needed, persists) → search "vlc" → Installer → grant unknown-sources if prompted → system install → app installs. Reopen app → still logged in.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/store app/src/main/java/com/cyprienbrisset/myportal/ui/settings/SettingsScreen.kt app/src/main/java/com/cyprienbrisset/myportal/ui/AppNav.kt
git commit -m "feat(fukkastore): Sumi store screen (login gate, search, install) + entry point"
```

**END:** FukkaStore works end-to-end on the Portal; login persists; apps install.

---

## Self-Review (author checklist, completed)

**Spec coverage:** models+client search/files (T1), account persistence (T2), downloader (T3), installer+permission (T4), persist-on-login (T5), StoreViewModel (T6), Sumi StoreScreen + entry/route + remove temp test (T7). ✅
**Type consistency:** `StoreApp(packageName,title,developer,iconUrl,versionCode)`, `ApkFile(name,url,size)`, `FukkaAuth.search(authData,query):List<StoreApp>` + `files(authData,pkg,versionCode):List<ApkFile>` (top-level funcs), `FukkaAccount.isLoggedIn/save/logout/authData()`, `ApkDownloader.download(pkg,files,onProgress):List<File>`, `ApkInstaller.canInstall/requestPermission/install`, `StoreViewModel.isLoggedIn/ui/progress/search/install/logout`. ✅
**Isolated uncertainty:** only `PurchaseHelper.purchase(...)` + gplayapi `File` fields in `files()` (T1) need confirming against 3.2.6/Aurora — flagged with a concrete reference (`/tmp/AuroraStore`). Everything else is concrete.
**No placeholders:** every step has code/commands; the one gplayapi confirmation is a real discovery step with a named reference, not a vague TODO.
**GPLv3:** the auth flow is derived from Aurora (GPLv3) — keep attribution in `FukkaAuth.kt`.
```
