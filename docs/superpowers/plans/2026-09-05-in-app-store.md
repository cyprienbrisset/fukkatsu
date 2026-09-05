# Fukkatsu In-App Store Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An opt-in in-app app store in Fukkatsu (search + install real Play Store apps) powered by the gplayapi library with anonymous auth, installing via PackageInstaller — no Google Play Services.

**Architecture:** A `store/` package: `PlayStoreClient` wraps gplayapi (anonymous auth, search, file list); `ApkDownloader` + `ApkInstaller` handle download + PackageInstaller sessions (base + splits); `StoreViewModel` drives a Store section in the tile editor, gated by a DataStore flag + risk dialog.

**Tech Stack:** gplayapi (Aurora, via JitPack), OkHttp/Coil (present), Android PackageInstaller, Jetpack Compose (Sumi).

---

## Environment (unchanged — critical)

- No `java` on PATH. Every Gradle call MUST prefix JAVA_HOME:
  `JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew ...`
- Devices: Portal+ `adb -s 2221B01C9C02NQ` (Android 10/API 29), emulator `emulator-5554` (API 35, has network). Install builds with explicit `-s`. `adb` = `/Users/cyprienbrisset/Library/Android/sdk/platform-tools/adb`.
- Verify with `:app:assembleDebug`; JVM tests `:app:testDebugUnitTest`. Do NOT stage `.claude/oxygen-status.json`.
- Branch `feat/in-app-store` (created). Package root `com.cyprienbrisset.myportal`.
- **Network-dependent verification** runs best on the **emulator** (`emulator-5554`) which has internet.

---

## File Structure

```
settings.gradle.kts            # MOD — add JitPack repo
gradle/libs.versions.toml      # MOD — gplayapi version + lib
app/build.gradle.kts           # MOD — gplayapi dependency
store/PlayStoreModels.kt       # NEW — StoreApp, ApkFile, StoreException (+ pure dispenser parse)
store/PlayStoreClient.kt       # NEW — gplayapi wrapper (auth/search/files)
store/ApkDownloader.kt         # NEW — download files to cache with progress
store/ApkInstaller.kt          # NEW — PackageInstaller session (base+splits)
store/InstallResultReceiver.kt # NEW — PackageInstaller status callback
ui/store/StoreViewModel.kt     # NEW
ui/store/StoreSection.kt       # NEW — search + results Compose UI
ui/settings/SettingsScreen.kt  # MOD — "Store" toggle + risk dialog
ui/settings/TileEditScreen.kt  # MOD — 3rd segment "Store" when enabled
data/settings/SettingsRepository.kt # MOD — storeEnabled flag
AndroidManifest.xml            # MOD — REQUEST_INSTALL_PACKAGES + receiver
app/src/test/.../store/PlayStoreModelsTest.kt # NEW
```

---

## PHASE 1 — Spike (go/no-go)

> Goal: prove gplayapi resolves, anonymous auth works, and a search returns results. If it can't be made to work after genuine effort, STOP and report BLOCKED with findings — the controller will pivot to "install Aurora Store" instead. Do not proceed to Phase 2 until the spike is green.

### Task 1: Add gplayapi dependency (resolves + compiles)

**Files:** `settings.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`

- [ ] **Step 1: Add JitPack repo** in `settings.gradle.kts` under `dependencyResolutionManagement { repositories { ... } }`:
```kotlin
maven { url = uri("https://jitpack.io") }
```

- [ ] **Step 2: Add the library to the catalog** `gradle/libs.versions.toml`:
```toml
[versions]
gplayapi = "3.2.8"
[libraries]
gplayapi = { group = "com.gitlab.AuroraOSS", name = "gplayapi", version.ref = "gplayapi" }
```
Note: the exact coordinate/version may differ. If `com.gitlab.AuroraOSS:gplayapi:3.2.8` fails to resolve on JitPack, try these known alternatives in order and keep the first that resolves: `com.gitlab.AuroraOSS:gplayapi:3.2.9`, `com.gitlab.AuroraOSS:gplayapi:3.2.7`, `com.github.whyorean:gplayapi:3.2.8`. Report which coordinate+version resolved.

- [ ] **Step 3: Add the dependency** in `app/build.gradle.kts`:
```kotlin
implementation(libs.gplayapi)
```

- [ ] **Step 4: Verify resolution**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (gplayapi + transitive deps like protobuf resolve). If a transitive conflict occurs (e.g., protobuf/okhttp version), resolve minimally (exclude or align) and note it. If JitPack cannot build the artifact at all, report BLOCKED.

- [ ] **Step 5: Commit**
```bash
git add settings.gradle.kts gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add gplayapi (Aurora) via JitPack for in-app store spike"
```

---

### Task 2: Spike — anonymous auth + search (verified on emulator)

**Files:** Create a temporary `store/SpikeProbe.kt` (removed/refactored in Phase 2).

- [ ] **Step 1: Implement a probe** `app/src/main/java/com/cyprienbrisset/myportal/store/SpikeProbe.kt`

Goal: authenticate anonymously and run a search, returning a short summary string (used from a temporary trigger + logcat). The exact gplayapi API depends on the resolved version — inspect the library's classes (`com.aurora.gplayapi.helpers.*`, `com.aurora.gplayapi.data.models.*`) and adapt. The canonical flow (Aurora ~gplayapi 3.x):
```kotlin
package com.cyprienbrisset.myportal.store

import android.content.Context
import android.util.Log
import com.aurora.gplayapi.helpers.AuthHelper
import com.aurora.gplayapi.helpers.SearchHelper
import okhttp3.OkHttpClient
import okhttp3.Request

object SpikeProbe {
    private const val TAG = "StoreSpike"
    // Aurora anonymous token dispenser (verify current endpoint at spike time).
    private const val DISPENSER = "https://auroraoss.com/api/auth"

    suspend fun run(context: Context): String {
        // 1) Fetch an anonymous account (email + aasToken) from the dispenser.
        val body = OkHttpClient().newCall(
            Request.Builder().url(DISPENSER)
                .header("User-Agent", "com.aurora.store")
                .build()
        ).execute().use { it.body?.string() ?: "" }
        val email = Regex("\"email\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: return "NO_EMAIL: $body".take(200)
        val aas = Regex("\"auth\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            ?: Regex("\"aasToken\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            ?: return "NO_TOKEN: $body".take(200)

        // 2) Build AuthData (API name/shape may differ by version — adapt).
        val authData = AuthHelper.build(email, aas)

        // 3) Search.
        val results = SearchHelper(authData).searchResults("spotify")
        val names = results.appList.take(5).joinToString { it.displayName }
        Log.i(TAG, "AUTH ok email=$email results=[$names]")
        return "OK results=[$names]"
    }
}
```
This WILL likely need adjustment to the resolved gplayapi API (helper constructors, `AuthHelper.build` signature, `searchResults` return type/`appList` field). Use the library's actual classes. If the dispenser JSON keys differ, adjust the regexes. The success criterion is: **auth succeeds and a non-empty app list is returned.**

- [ ] **Step 2: Trigger it and observe (emulator, has network)**

Add a temporary call from `MainActivity.onCreate` guarded so it only runs in debug, on a background coroutine, logging the result — OR simpler, call it from a temporary unit-ish path. Recommended: in `MainActivity.onCreate`, add (temporarily):
```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    runCatching { com.cyprienbrisset.myportal.store.SpikeProbe.run(this@MainActivity) }
        .onSuccess { android.util.Log.i("StoreSpike", it) }
        .onFailure { android.util.Log.e("StoreSpike", "spike failed", it) }
}
```
Build, install on emulator, launch, and read logcat:
```
JAVA_HOME=... ./gradlew :app:installDebug   # or -s emulator-5554 install
adb -s emulator-5554 logcat -d | grep StoreSpike
```
Expected: `StoreSpike: OK results=[...]` with real app names.

- [ ] **Step 3: GO/NO-GO decision**
- **GO** (results returned): remove the temporary `MainActivity` probe call (keep `SpikeProbe.kt` for reference or delete), commit the working state, and proceed to Phase 2 — recording the exact working coordinate, dispenser endpoint, and gplayapi API used.
- **NO-GO** (cannot get auth/search working after real effort): revert the probe, and report **BLOCKED** with the specific failure (resolution? auth? API mismatch?). The controller pivots to "install Aurora Store."

- [ ] **Step 4: Commit the spike outcome**
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/store/SpikeProbe.kt app/src/main/java/com/cyprienbrisset/myportal/MainActivity.kt
git commit -m "spike: anonymous Play auth + search via gplayapi (verified)"
```

**PHASE 1 GATE:** Do not continue unless Step 3 = GO. The controller reviews the spike result before Phase 2.

---

## PHASE 2 — PlayStoreClient (clean interface)

### Task 3: Store models + pure dispenser parser (TDD)

**Files:** `store/PlayStoreModels.kt`, test `store/PlayStoreModelsTest.kt`

- [ ] **Step 1: Failing test** `app/src/test/java/com/cyprienbrisset/myportal/store/PlayStoreModelsTest.kt`
```kotlin
package com.cyprienbrisset.myportal.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayStoreModelsTest {
    @Test fun parsesDispenserEmailAndToken() {
        val json = """{"email":"anon@gmail.com","auth":"AAS_TOKEN_123"}"""
        val a = parseDispenser(json)
        assertEquals("anon@gmail.com", a?.email)
        assertEquals("AAS_TOKEN_123", a?.token)
    }
    @Test fun parsesAasTokenKeyVariant() {
        val json = """{"email":"x@y.com","aasToken":"T2"}"""
        assertEquals("T2", parseDispenser(json)?.token)
    }
    @Test fun returnsNullOnGarbage() {
        assertNull(parseDispenser("not json"))
    }
}
```
Run `:app:testDebugUnitTest --tests "*PlayStoreModelsTest*"` → FAIL.

- [ ] **Step 2: Implement** `app/src/main/java/com/cyprienbrisset/myportal/store/PlayStoreModels.kt`
```kotlin
package com.cyprienbrisset.myportal.store

data class StoreApp(
    val packageName: String,
    val title: String,
    val developer: String,
    val iconUrl: String,
    val versionCode: Int,
)

data class ApkFile(val name: String, val url: String, val size: Long)

class StoreException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class DispenserAccount(val email: String, val token: String)

/** Pure parse of the anonymous-dispenser JSON (email + aas/auth token). */
fun parseDispenser(json: String): DispenserAccount? {
    val email = Regex("\"email\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: return null
    val token = Regex("\"(?:auth|aasToken)\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: return null
    return DispenserAccount(email, token)
}
```
Run test → PASS (3). `:app:assembleDebug` → SUCCESS.

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/store/PlayStoreModels.kt app/src/test/java/com/cyprienbrisset/myportal/store/PlayStoreModelsTest.kt
git commit -m "feat(store): models + tested dispenser parser"
```

---

### Task 4: PlayStoreClient (auth/search/files)

**Files:** `store/PlayStoreClient.kt` (replaces the spike code)

- [ ] **Step 1: Implement** using the EXACT gplayapi API confirmed in the spike (Task 2). Interface is fixed; fill the gplayapi calls per the spike:
```kotlin
package com.cyprienbrisset.myportal.store

import android.content.Context
import com.aurora.gplayapi.helpers.AuthHelper
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import com.aurora.gplayapi.helpers.SearchHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class PlayStoreClient(private val context: Context, private val http: OkHttpClient = OkHttpClient()) {
    private companion object { const val DISPENSER = "https://auroraoss.com/api/auth" }
    private var authData: com.aurora.gplayapi.data.models.AuthData? = null

    suspend fun ensureAuth(): Boolean = withContext(Dispatchers.IO) {
        if (authData != null) return@withContext true
        val json = runCatching {
            http.newCall(Request.Builder().url(DISPENSER).header("User-Agent", "com.aurora.store").build())
                .execute().use { it.body?.string() ?: "" }
        }.getOrElse { throw StoreException("Dispenser injoignable", it) }
        val acc = parseDispenser(json) ?: throw StoreException("Compte anonyme indisponible")
        authData = runCatching { AuthHelper.build(acc.email, acc.token) }
            .getOrElse { throw StoreException("Auth échouée", it) }
        true
    }

    suspend fun search(query: String): List<StoreApp> = withContext(Dispatchers.IO) {
        ensureAuth()
        val ad = authData ?: throw StoreException("Non authentifié")
        val bundle = runCatching { SearchHelper(ad).searchResults(query) }
            .getOrElse { throw StoreException("Recherche échouée", it) }
        bundle.appList.map {
            StoreApp(
                packageName = it.packageName,
                title = it.displayName,
                developer = it.developerName,
                iconUrl = it.iconArtwork.url,
                versionCode = it.versionCode,
            )
        }
    }

    suspend fun files(packageName: String, versionCode: Int): List<ApkFile> = withContext(Dispatchers.IO) {
        val ad = authData ?: throw StoreException("Non authentifié")
        val details = runCatching { AppDetailsHelper(ad).getAppByPackageName(packageName) }
            .getOrElse { throw StoreException("Détails indisponibles", it) }
        val files = runCatching { PurchaseHelper(ad).purchase(details.packageName, details.versionCode, details.offerType) }
            .getOrElse { throw StoreException("Téléchargement refusé", it) }
        files.map { ApkFile(it.name, it.url, it.size) }
    }
}
```
Adjust field/method names to the spike-confirmed gplayapi surface (e.g. `iconArtwork.url`, `offerType`, `File.url/size`). Keep the public method signatures exactly as above.

- [ ] **Step 2: Verify + commit**
Run: `:app:assembleDebug` → SUCCESS.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/store/PlayStoreClient.kt
git commit -m "feat(store): PlayStoreClient (anonymous auth, search, files)"
```

---

## PHASE 3 — Download + install

### Task 5: ApkDownloader

**Files:** `store/ApkDownloader.kt`

- [ ] **Step 1: Implement** (OkHttp streaming to cache dir, progress via callback)
```kotlin
package com.cyprienbrisset.myportal.store

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class ApkDownloader(private val context: Context, private val http: OkHttpClient = OkHttpClient()) {
    /** Downloads all [files] into a per-package cache dir; returns the local Files (base + splits). */
    suspend fun download(pkg: String, files: List<ApkFile>, onProgress: (Int) -> Unit): List<File> =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "apks/$pkg").apply { deleteRecursively(); mkdirs() }
            val total = files.sumOf { it.size }.coerceAtLeast(1)
            var done = 0L
            files.map { f ->
                val out = File(dir, if (f.name.endsWith(".apk")) f.name else "${f.name}.apk")
                http.newCall(Request.Builder().url(f.url).build()).execute().use { resp ->
                    val bytes = resp.body ?: throw StoreException("Téléchargement vide (${f.name})")
                    out.outputStream().use { os ->
                        val buf = ByteArray(64 * 1024)
                        bytes.byteStream().use { ins ->
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

- [ ] **Step 2: Verify + commit**
Run: `:app:assembleDebug` → SUCCESS.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/store/ApkDownloader.kt
git commit -m "feat(store): APK downloader with progress"
```

---

### Task 6: ApkInstaller + status receiver + permission

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
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirm != null) context.startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS ->
                Toast.makeText(context, "Installé", Toast.LENGTH_SHORT).show()
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Toast.makeText(context, "Échec install : $msg", Toast.LENGTH_LONG).show()
            }
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
            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Installs base + splits in one session; the system shows a confirm prompt. */
    fun install(apks: List<File>) {
        val pi = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = pi.createSession(params)
        val session = pi.openSession(sessionId)
        session.use { s ->
            apks.forEach { apk ->
                s.openWrite(apk.name, 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    s.fsync(out)
                }
            }
            val intent = Intent(InstallResultReceiver.ACTION).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            s.commit(pending.intentSender)
        }
    }
}
```

- [ ] **Step 3: Manifest** — add under `<manifest>`:
```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```
and inside `<application>`:
```xml
<receiver android:name=".store.InstallResultReceiver" android:exported="false">
    <intent-filter><action android:name="com.cyprienbrisset.myportal.INSTALL_STATUS" /></intent-filter>
</receiver>
```

- [ ] **Step 4: Verify + commit**
Run: `:app:assembleDebug` → SUCCESS.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/store/ApkInstaller.kt app/src/main/java/com/cyprienbrisset/myportal/store/InstallResultReceiver.kt app/src/main/AndroidManifest.xml
git commit -m "feat(store): PackageInstaller (base+splits) + status receiver + permission"
```

---

## PHASE 4 — Settings gate + Store UI

### Task 7: storeEnabled setting + risk dialog

**Files:** `data/settings/SettingsRepository.kt`, `ui/settings/SettingsScreen.kt`

- [ ] **Step 1: SettingsRepository** — add a boolean flag. Add:
```kotlin
private val STORE = androidx.datastore.preferences.core.booleanPreferencesKey("store_enabled")
val storeEnabled: kotlinx.coroutines.flow.Flow<Boolean> = context.dataStore.data.map { it[STORE] ?: false }
suspend fun setStoreEnabled(on: Boolean) { context.dataStore.edit { it[STORE] = on } }
```
(Use the existing `dataStore` + imports already in the file.)

- [ ] **Step 2: SettingsScreen** — add a row with a `Switch` + risk dialog on enable. In `SettingsScreen`, collect a VM/flow or read via a small `SettingsViewModel`. Simplest: hoist to a `SettingsViewModel(app)` exposing `storeEnabled: StateFlow<Boolean>` and `setStoreEnabled`. Add a row:
```kotlin
var showRisk by remember { mutableStateOf(false) }
val storeOn by settingsVm.storeEnabled.collectAsStateWithLifecycle()
Row(Modifier.fillMaxWidth().heightIn(min = 68.dp), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
        Text("Store d'applications", color = Kinari, fontSize = 17.sp)
        Text("Sources non officielles", color = SumiMuted, fontSize = 12.sp)
    }
    Switch(checked = storeOn, onCheckedChange = { on -> if (on) showRisk = true else settingsVm.setStoreEnabled(false) })
}
if (showRisk) AlertDialog(
    onDismissRequest = { showRisk = false },
    confirmButton = { TextButton(onClick = { settingsVm.setStoreEnabled(true); showRisk = false }) { Text("J'ai compris, activer") } },
    dismissButton = { TextButton(onClick = { showRisk = false }) { Text("Annuler") } },
    title = { Text("Store — avertissement") },
    text = { Text("Ce store utilise une API Google Play non officielle et installe des applications tierces. Il peut cesser de fonctionner sans préavis et sort des conditions d'utilisation de Google. À utiliser en connaissance de cause.") },
)
```
Create `ui/settings/SettingsViewModel.kt`:
```kotlin
package com.cyprienbrisset.myportal.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyprienbrisset.myportal.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)
    val storeEnabled = repo.storeEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setStoreEnabled(on: Boolean) = viewModelScope.launch { repo.setStoreEnabled(on) }
}
```
Wire `settingsVm: SettingsViewModel = viewModel()` in `SettingsScreen`. Add needed imports (Switch, AlertDialog, TextButton, remember, mutableStateOf, getValue/setValue, collectAsStateWithLifecycle, heightIn, Row/Column, dp/sp, theme tokens).

- [ ] **Step 3: Verify + commit**
Run: `:app:assembleDebug` → SUCCESS.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/data/settings/SettingsRepository.kt app/src/main/java/com/cyprienbrisset/myportal/ui/settings/SettingsScreen.kt app/src/main/java/com/cyprienbrisset/myportal/ui/settings/SettingsViewModel.kt
git commit -m "feat(store): settings toggle + risk dialog"
```

---

### Task 8: StoreViewModel + Store section in tile editor

**Files:** `ui/store/StoreViewModel.kt`, `ui/store/StoreSection.kt`, `ui/settings/TileEditScreen.kt`

- [ ] **Step 1: StoreViewModel.kt**
```kotlin
package com.cyprienbrisset.myportal.ui.store

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyprienbrisset.myportal.store.ApkDownloader
import com.cyprienbrisset.myportal.store.ApkInstaller
import com.cyprienbrisset.myportal.store.PlayStoreClient
import com.cyprienbrisset.myportal.store.StoreApp
import com.cyprienbrisset.myportal.store.StoreException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface StoreUi {
    data object Idle : StoreUi
    data object Loading : StoreUi
    data class Results(val apps: List<StoreApp>) : StoreUi
    data class Error(val message: String) : StoreUi
}

class StoreViewModel(app: Application) : AndroidViewModel(app) {
    private val client = PlayStoreClient(app)
    private val downloader = ApkDownloader(app)
    private val installer = ApkInstaller(app)

    private val _ui = MutableStateFlow<StoreUi>(StoreUi.Idle)
    val ui: StateFlow<StoreUi> = _ui
    private val _progress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val progress: StateFlow<Map<String, Int>> = _progress

    fun search(query: String) {
        if (query.isBlank()) { _ui.value = StoreUi.Idle; return }
        _ui.value = StoreUi.Loading
        viewModelScope.launch {
            _ui.value = try { StoreUi.Results(client.search(query)) }
            catch (e: StoreException) { StoreUi.Error(e.message ?: "Erreur") }
            catch (e: Exception) { StoreUi.Error("Erreur réseau") }
        }
    }

    fun install(appItem: StoreApp) {
        if (!installer.canInstall()) { installer.requestPermission(); return }
        viewModelScope.launch {
            try {
                val files = client.files(appItem.packageName, appItem.versionCode)
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

- [ ] **Step 2: StoreSection.kt** (search field + result cards, Sumi)
```kotlin
package com.cyprienbrisset.myportal.ui.store

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cyprienbrisset.myportal.ui.sumi.SumiPrimaryButton
import com.cyprienbrisset.myportal.ui.theme.Kinari
import com.cyprienbrisset.myportal.ui.theme.SumiMuted
import com.cyprienbrisset.myportal.ui.theme.SumiSurface

@Composable
fun StoreSection(vm: StoreViewModel = viewModel(), modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
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
                        AsyncImage(app.iconUrl, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
                        Column(Modifier.weight(1f)) {
                            Text(app.title, color = Kinari, fontSize = 16.sp, maxLines = 1)
                            Text(app.developer, color = SumiMuted, fontSize = 12.sp, maxLines = 1)
                        }
                        val pct = progress[app.packageName]
                        when {
                            pct == null -> SumiPrimaryButton("Installer", onClick = { vm.install(app) }, modifier = Modifier.widthIn(max = 140.dp))
                            pct in 0..99 -> Text("$pct %", color = Kinari)
                            pct == 100 -> Text("Installation…", color = SumiMuted)
                            else -> Text("Échec", color = SumiMuted)
                        }
                    }
                }
            }
            StoreUi.Idle -> {}
        }
    }
}
```

- [ ] **Step 3: TileEditScreen** — add a 3rd segment "Store" when `storeEnabled`. Inject `SettingsViewModel` (or read the flag). Change the `SegmentedChoice` options to include Store only when enabled, and render `StoreSection()` for that mode. Concretely:
  - `val storeEnabled by settingsVm.storeEnabled.collectAsStateWithLifecycle()`
  - Build the segment list: `val segs = buildList { add(Segment("アプリ","Application")); add(Segment("ウェブ","Web")); if (storeEnabled) add(Segment("ストア","Store")) }`.
  - Keep `mode` in range if the flag flips.
  - When `mode == 2` (Store), render `StoreSection(modifier = Modifier.weight(1f))` instead of the app grid / web form.
  Add imports for `StoreSection`, `SettingsViewModel`, `viewModel`.

- [ ] **Step 4: Verify + on-device (emulator) end-to-end**
Run: `:app:assembleDebug` → SUCCESS; `:app:testDebugUnitTest` → all pass.
Install on emulator; Settings → enable Store (accept risk) → Tuiles → Store → search "vlc" → Installer → grant unknown-sources if prompted → system install prompt → app installs.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/store app/src/main/java/com/cyprienbrisset/myportal/ui/settings/TileEditScreen.kt
git commit -m "feat(store): StoreViewModel + Store section in tile editor (search/install)"
```

**PHASE 4 END:** opt-in store works end-to-end on the emulator; on the Portal, same flow (network required).

---

## Self-Review (author checklist, completed)

**Spec coverage:** JitPack+gplayapi (T1), anonymous auth+search spike/go-no-go (T2), models+dispenser parse (T3), PlayStoreClient (T4), downloader (T5), PackageInstaller+splits+permission (T6), settings toggle+risk dialog (T7), StoreViewModel+Store UI in tile editor (T8). ✅
**Spike-first + pivot:** Phase 1 gate with BLOCKED→Aurora-Store fallback explicitly stated. ✅
**Type consistency:** `PlayStoreClient.ensureAuth()/search(query):List<StoreApp>/files(pkg,versionCode):List<ApkFile>`, `StoreApp(packageName,title,developer,iconUrl,versionCode)`, `ApkFile(name,url,size)`, `ApkDownloader.download(pkg,files,onProgress):List<File>`, `ApkInstaller.canInstall/requestPermission/install(List<File>)`, `StoreViewModel.search/install + ui:StoreUi + progress:Map<String,Int>`, `SettingsRepository.storeEnabled/setStoreEnabled`, `SettingsViewModel.storeEnabled/setStoreEnabled` — consistent. ✅
**Uncertainty isolated:** gplayapi exact API confined to spike (T2) + `PlayStoreClient` (T4); everything else (models, downloader, installer, UI, settings) is concrete and gplayapi-independent.
**No placeholders:** network constants/API are explicitly spike-pinned (a deliberate discovery step, not a vague TODO); all other steps have concrete code.
