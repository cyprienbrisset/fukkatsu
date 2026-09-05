# Communication Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Intégrer contacts récents Messenger/WhatsApp (via NotificationListener), Google Meet/Chat (WebView persistante), et Google Agenda (flux ICS) dans l'interface Fukkatsu.

**Architecture:**
- `RecentContactsRepository` (singleton object) est mis à jour par `MediaListenerService` et expose un `StateFlow<List<RecentContact>>` consommé par `HomeViewModel`.
- `PersistentWebViewPool` (singleton object) garde une instance `WebView` par clé (`"meet"`, `"chat"`). `GoogleWebSheet` l'affiche en overlay plein écran via `AndroidView`.
- `IcsCalendarRepository` (singleton object) fetch + parse les événements ICS via OkHttp. `GoogleViewModel` choisit la source (ICS si URL configurée, native sinon).

**Tech Stack:** Kotlin, Jetpack Compose, AndroidViewModel, DataStore, OkHttp (déjà en dépendance), NotificationListenerService (déjà déclaré)

---

## Fichiers touchés

| Fichier | Action |
|---|---|
| `app/src/main/AndroidManifest.xml` | Supprimer GET_ACCOUNTS + MANAGE_ACCOUNTS |
| `data/settings/SettingsRepository.kt` | Ajouter clé + flow + setter `google_ics_url` |
| `system/MediaListenerService.kt` | Intercepter notifs Messenger/WhatsApp → RecentContactsRepository |
| `integration/RecentContactsRepository.kt` | **Créer** — StateFlow<List<RecentContact>>, max 6, dédupliqué |
| `integration/IcsCalendarRepository.kt` | **Créer** — OkHttp fetch + parser VEVENT minimal |
| `ui/home/RecentContactsStrip.kt` | **Créer** — bande de contacts (paysage + portrait) |
| `ui/home/HomeViewModel.kt` | Exposer `recentContacts: StateFlow` |
| `ui/home/HomeScreen.kt` | Insérer `RecentContactsStrip` paysage + portrait |
| `ui/google/PersistentWebViewPool.kt` | **Créer** — cache `WebView` par clé |
| `ui/google/GoogleWebSheet.kt` | **Créer** — overlay plein écran avec WebView persistante |
| `ui/google/GoogleViewModel.kt` | Ajouter source ICS + events depuis ICS |
| `ui/google/GoogleScreen.kt` | Tuiles Meet/Chat + hint ICS |
| `ui/settings/IcsSettingsScreen.kt` | **Créer** — champ URL + bouton Vérifier + ViewModel |
| `ui/settings/SettingsScreen.kt` | Ajouter ligne "Agenda Google" |
| `ui/AppNav.kt` | Ajouter route `ICS_SETTINGS` |

---

## Task 1: Nettoyage Manifest + commit initial des changements en cours

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Étape 1 : Supprimer les permissions GMS orphelines**

Dans `AndroidManifest.xml`, supprimer les deux lignes :
```xml
<uses-permission android:name="android.permission.GET_ACCOUNTS" />
<uses-permission android:name="android.permission.MANAGE_ACCOUNTS" />
```

- [ ] **Étape 2 : Committer tous les changements en cours + nettoyage**

```bash
git add app/src/main/AndroidManifest.xml \
        app/src/main/java/com/cyprienbrisset/myportal/ui/AppNav.kt \
        app/src/main/java/com/cyprienbrisset/myportal/ui/google/GoogleScreen.kt \
        app/src/main/java/com/cyprienbrisset/myportal/ui/google/GoogleViewModel.kt \
        app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeShell.kt \
        app/src/main/java/com/cyprienbrisset/myportal/ui/settings/SettingsScreen.kt \
        app/src/main/java/com/cyprienbrisset/myportal/integration/AppShortcuts.kt \
        app/src/main/java/com/cyprienbrisset/myportal/ui/settings/InstalledAppsScreen.kt
git commit -m "feat: HomeShell tabs, Google page, installed apps, system settings shortcuts"
```

---

## Task 2: SettingsRepository — clé ICS URL

**Files:**
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/data/settings/SettingsRepository.kt`

- [ ] **Étape 1 : Ajouter clé DataStore + flow + setter**

Dans `SettingsRepository`, ajouter après les clés météo existantes :
```kotlin
private val ICS_URL = stringPreferencesKey("google_ics_url")

val googleIcsUrl: Flow<String?> = context.dataStore.data.map { it[ICS_URL] }

suspend fun setGoogleIcsUrl(url: String?) {
    context.dataStore.edit { prefs ->
        if (url.isNullOrBlank()) prefs.remove(ICS_URL) else prefs[ICS_URL] = url
    }
}
```

- [ ] **Étape 2 : Build de vérification**

```bash
export JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:compileDebugKotlin
```
Résultat attendu : BUILD SUCCESSFUL.

- [ ] **Étape 3 : Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/data/settings/SettingsRepository.kt
git commit -m "feat(ics): add google_ics_url key to SettingsRepository"
```

---

## Task 3: RecentContactsRepository

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/integration/RecentContactsRepository.kt`

- [ ] **Étape 1 : Créer le fichier**

```kotlin
package com.cyprienbrisset.myportal.integration

import android.app.PendingIntent
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class RecentContact(
    val key: String,
    val name: String,
    val avatar: Bitmap?,
    val packageName: String,
    val lastSeenMs: Long,
    val tapIntent: PendingIntent?,
)

object RecentContactsRepository {

    private val _contacts = MutableStateFlow<List<RecentContact>>(emptyList())
    val contacts: StateFlow<List<RecentContact>> = _contacts

    private const val MAX = 6

    fun onNotification(contact: RecentContact) {
        val current = _contacts.value.toMutableList()
        current.removeAll { it.key == contact.key }
        current.add(0, contact)
        _contacts.value = current.take(MAX)
    }
}
```

- [ ] **Étape 2 : Build de vérification**

```bash
export JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:compileDebugKotlin
```
Résultat attendu : BUILD SUCCESSFUL.

- [ ] **Étape 3 : Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/integration/RecentContactsRepository.kt
git commit -m "feat(contacts): RecentContactsRepository with StateFlow and dedup logic"
```

---

## Task 4: MediaListenerService — capture Messenger/WhatsApp

**Files:**
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/system/MediaListenerService.kt`

- [ ] **Étape 1 : Remplacer le corps du fichier**

```kotlin
package com.cyprienbrisset.myportal.system

import android.app.Notification
import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.cyprienbrisset.myportal.integration.RecentContact
import com.cyprienbrisset.myportal.integration.RecentContactsRepository

class MediaListenerService : NotificationListenerService() {

    companion object {
        private val COMM_PACKAGES = setOf(
            "com.facebook.aloha.app.whatsapp",
            "com.facebook.aloha.app.messenger",
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in COMM_PACKAGES) return
        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return

        val name = extras.getString(Notification.EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: return
        val key = "${sbn.packageName}:$name"

        val avatar: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notif.getLargeIcon()?.loadDrawable(this)?.let { (it as? BitmapDrawable)?.bitmap }
        } else {
            @Suppress("DEPRECATION")
            (notif.largeIcon as? Bitmap)
        }

        // Prefer a call action; fall back to content intent (opens conversation).
        val tapIntent: PendingIntent? = notif.actions
            ?.firstOrNull { a -> a.title?.toString()?.contains("appel", ignoreCase = true) == true
                    || a.title?.toString()?.contains("call", ignoreCase = true) == true }
            ?.actionIntent
            ?: notif.contentIntent

        RecentContactsRepository.onNotification(
            RecentContact(
                key = key,
                name = name,
                avatar = avatar,
                packageName = sbn.packageName,
                lastSeenMs = sbn.postTime,
                tapIntent = tapIntent,
            )
        )
    }
}
```

- [ ] **Étape 2 : Build de vérification**

```bash
export JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:compileDebugKotlin
```
Résultat attendu : BUILD SUCCESSFUL.

- [ ] **Étape 3 : Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/system/MediaListenerService.kt
git commit -m "feat(contacts): extend MediaListenerService to capture Messenger/WhatsApp contacts"
```

---

## Task 5: RecentContactsStrip composable

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/RecentContactsStrip.kt`

- [ ] **Étape 1 : Créer le composable**

```kotlin
package com.cyprienbrisset.myportal.ui.home

import android.app.PendingIntent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.myportal.integration.RecentContact
import com.cyprienbrisset.myportal.ui.theme.Kinari
import com.cyprienbrisset.myportal.ui.theme.Mincho
import com.cyprienbrisset.myportal.ui.theme.Shu
import com.cyprienbrisset.myportal.ui.theme.SumiSurface

@Composable
fun RecentContactsStrip(contacts: List<RecentContact>, modifier: Modifier = Modifier) {
    if (contacts.isEmpty()) {
        Box(modifier.height(0.dp))
        return
    }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(contacts, key = { it.key }) { contact ->
            ContactBubble(contact)
        }
    }
}

@Composable
private fun ContactBubble(contact: RecentContact) {
    Column(
        modifier = Modifier.width(60.dp).clickable {
            contact.tapIntent?.let {
                try { it.send() } catch (_: PendingIntent.CanceledException) {}
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AvatarCircle(name = contact.name, avatar = contact.avatar)
        Text(
            contact.name,
            color = Kinari,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AvatarCircle(name: String, avatar: Bitmap?) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(SumiSurface),
        contentAlignment = Alignment.Center,
    ) {
        if (avatar != null) {
            Image(
                bitmap = avatar.asImageBitmap(),
                contentDescription = name,
                modifier = Modifier.size(52.dp).clip(CircleShape),
            )
        } else {
            Text(
                name.take(1).uppercase(),
                color = Shu,
                fontFamily = Mincho,
                fontSize = 22.sp,
            )
        }
    }
}
```

- [ ] **Étape 2 : Build de vérification**

```bash
export JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:compileDebugKotlin
```
Résultat attendu : BUILD SUCCESSFUL.

- [ ] **Étape 3 : Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/home/RecentContactsStrip.kt
git commit -m "feat(contacts): RecentContactsStrip composable (avatar circle + name)"
```

---

## Task 6: HomeViewModel + HomeScreen — intégration contacts récents

**Files:**
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeScreen.kt`

- [ ] **Étape 1 : Exposer contacts dans HomeViewModel**

Dans `HomeViewModel.kt`, ajouter après les imports et avant la classe (ou dans le `init`) :

```kotlin
import com.cyprienbrisset.myportal.integration.RecentContactsRepository

// Dans la classe HomeViewModel :
val recentContacts = RecentContactsRepository.contacts
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

La `stateIn` s'ajoute comme les autres flows déjà présents dans la classe.

- [ ] **Étape 2 : Insérer RecentContactsStrip dans HomeScreen**

Dans `HomeScreen.kt`, ajouter l'import en haut :
```kotlin
import com.cyprienbrisset.myportal.ui.home.RecentContactsStrip
```

Ajouter la collecte du flow dans la fonction `HomeScreen` (avec les autres `collectAsStateWithLifecycle`) :
```kotlin
val recentContacts by vm.recentContacts.collectAsStateWithLifecycle()
```

**Placement paysage** — dans le `Box(Modifier.fillMaxHeight().weight(0.38f))`, dans le `Column` centré, après le `VolumeSlider()` et son `Spacer` :
```kotlin
if (recentContacts.isNotEmpty()) {
    Spacer(Modifier.height(16.dp))
    RecentContactsStrip(recentContacts)
}
```

**Placement portrait** — dans le `Column` portrait, après le bloc `VolumeSlider` et avant `SectionLabel("アプリ", "MES APPS")` :
```kotlin
if (recentContacts.isNotEmpty()) {
    Spacer(Modifier.height(16.dp))
    RecentContactsStrip(recentContacts)
    Spacer(Modifier.height(16.dp))
}
```

- [ ] **Étape 3 : Build de vérification**

```bash
export JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:compileDebugKotlin
```
Résultat attendu : BUILD SUCCESSFUL.

- [ ] **Étape 4 : Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeViewModel.kt \
        app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeScreen.kt
git commit -m "feat(contacts): expose and display recent contacts strip in HomeScreen"
```

---

## Task 7: IcsCalendarRepository

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/integration/IcsCalendarRepository.kt`

- [ ] **Étape 1 : Créer le repository**

```kotlin
package com.cyprienbrisset.myportal.integration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

object IcsCalendarRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val MEET_REGEX = Regex("https://meet\\.google\\.com/[a-z0-9-]+", RegexOption.IGNORE_CASE)

    /**
     * Fetches [url] and returns upcoming CalEvents (now..now+[days] days), limit [limit].
     * Returns null on network/parse error.
     */
    suspend fun upcoming(url: String, nowMs: Long, days: Int = 7, limit: Int = 8): List<CalEvent>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(url).build()
                val body = client.newCall(req).execute().use { it.body?.string() } ?: return@runCatching null
                parseIcs(body, nowMs, days, limit)
            }.getOrNull()
        }

    private fun parseIcs(ics: String, nowMs: Long, days: Int, limit: Int): List<CalEvent> {
        val endMs = nowMs + days * 24L * 60 * 60 * 1000
        val events = mutableListOf<CalEvent>()
        var inEvent = false
        var summary = ""; var dtstart = ""; var dtend = ""
        var location: String? = null; var description: String? = null; var url: String? = null
        var uid = 0L

        for (line in ics.lines()) {
            when {
                line.startsWith("BEGIN:VEVENT") -> {
                    inEvent = true
                    summary = ""; dtstart = ""; dtend = ""; location = null; description = null; url = null
                    uid++
                }
                !inEvent -> {}
                line.startsWith("SUMMARY:") -> summary = line.removePrefix("SUMMARY:").trim()
                line.startsWith("DTSTART") -> dtstart = line.substringAfter(":").trim()
                line.startsWith("DTEND") -> dtend = line.substringAfter(":").trim()
                line.startsWith("LOCATION:") -> location = line.removePrefix("LOCATION:").trim().takeIf { it.isNotBlank() }
                line.startsWith("DESCRIPTION:") -> description = line.removePrefix("DESCRIPTION:").trim().replace("\\n", "\n").takeIf { it.isNotBlank() }
                line.startsWith("URL:") -> url = line.removePrefix("URL:").trim().takeIf { it.isNotBlank() }
                line == "END:VEVENT" -> {
                    inEvent = false
                    if (dtstart.isBlank()) return@for
                    val allDay = !dtstart.contains("T")
                    val beginMs = parseDateTime(dtstart) ?: return@for
                    val endEventMs = if (dtend.isNotBlank()) parseDateTime(dtend) ?: beginMs + 3600_000L else beginMs + 3600_000L
                    if (beginMs < nowMs || beginMs > endMs) return@for
                    val meetText = "${location.orEmpty()} ${description.orEmpty()} ${url.orEmpty()}"
                    val meetUrl = MEET_REGEX.find(meetText)?.value
                    events += CalEvent(uid, summary.ifBlank { "(Sans titre)" }, beginMs, endEventMs, allDay, location, meetUrl)
                    if (events.size >= limit) return events.sortedBy { it.begin }
                }
            }
        }
        return events.sortedBy { it.begin }
    }

    private fun parseDateTime(s: String): Long? {
        return runCatching {
            when {
                s.endsWith("Z") ->
                    ZonedDateTime.parse(s, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC")))
                        .toInstant().toEpochMilli()
                s.contains("T") ->
                    LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                else -> // DATE only (all-day)
                    LocalDateTime.parse("${s}T000000", DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }.getOrNull()
    }
}
```

- [ ] **Étape 2 : Build de vérification**

```bash
export JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:compileDebugKotlin
```
Résultat attendu : BUILD SUCCESSFUL.

- [ ] **Étape 3 : Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/integration/IcsCalendarRepository.kt
git commit -m "feat(ics): IcsCalendarRepository with OkHttp fetch and VEVENT parser"
```

---

## Task 8: IcsSettingsScreen

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/settings/IcsSettingsScreen.kt`

- [ ] **Étape 1 : Créer l'écran**

```kotlin
package com.cyprienbrisset.myportal.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.myportal.data.settings.SettingsRepository
import com.cyprienbrisset.myportal.integration.IcsCalendarRepository
import com.cyprienbrisset.myportal.ui.sumi.HankoSeal
import com.cyprienbrisset.myportal.ui.sumi.SumiPrimaryButton
import com.cyprienbrisset.myportal.ui.theme.Kinari
import com.cyprienbrisset.myportal.ui.theme.Mincho
import com.cyprienbrisset.myportal.ui.theme.SumiLine
import com.cyprienbrisset.myportal.ui.theme.SumiMuted
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IcsSettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)

    val savedUrl = repo.googleIcsUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _verifyResult = mutableStateOf<String?>(null)
    val verifyResult: String? get() = _verifyResult.value

    private val _verifying = mutableStateOf(false)
    val verifying: Boolean get() = _verifying.value

    fun save(url: String) {
        viewModelScope.launch { repo.setGoogleIcsUrl(url.trim()) }
    }

    fun clear() {
        viewModelScope.launch { repo.setGoogleIcsUrl(null) }
        _verifyResult.value = null
    }

    fun verify(url: String) {
        if (url.isBlank()) return
        _verifying.value = true
        _verifyResult.value = null
        viewModelScope.launch {
            val events = IcsCalendarRepository.upcoming(url.trim(), System.currentTimeMillis())
            _verifyResult.value = if (events == null) "Erreur : URL inaccessible ou format invalide."
            else "${events.size} événement(s) trouvé(s) dans les 7 prochains jours."
            _verifying.value = false
        }
    }
}

@Composable
fun IcsSettingsScreen(onBack: () -> Unit, vm: IcsSettingsViewModel = viewModel()) {
    val savedUrl by vm.savedUrl.collectAsStateWithLifecycle()
    var draft by rememberSaveable(savedUrl) { mutableStateOf(savedUrl ?: "") }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            HankoSeal("朱", size = 40.dp, onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Text("Agenda Google", fontFamily = Mincho, color = Kinari, fontSize = 22.sp)
        }
        Text(
            "Colle l'URL ICS privée de ton agenda Google ci-dessous. Tu la trouves dans Agenda Google → Paramètres → [ton agenda] → URL secrète au format iCal.",
            color = SumiMuted, fontSize = 14.sp,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("URL ICS", color = SumiMuted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.save(draft) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = com.cyprienbrisset.myportal.ui.theme.Shu,
                unfocusedBorderColor = SumiLine,
                focusedTextColor = Kinari,
                unfocusedTextColor = Kinari,
            ),
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            SumiPrimaryButton("Enregistrer", onClick = { vm.save(draft) })
            SumiPrimaryButton("Vérifier", onClick = { vm.verify(draft) })
            if (savedUrl != null) {
                SumiPrimaryButton("Effacer", onClick = { draft = ""; vm.clear() })
            }
        }
        vm.verifyResult?.let { result ->
            Spacer(Modifier.height(16.dp))
            Text(result, color = if (result.startsWith("Erreur")) com.cyprienbrisset.myportal.ui.theme.Shu else Kinari, fontSize = 14.sp)
        }
        if (vm.verifying) {
            Spacer(Modifier.height(12.dp))
            Text("Vérification…", color = SumiMuted, fontSize = 14.sp)
        }
    }
}
```

- [ ] **Étape 2 : Build de vérification**

```bash
export JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:compileDebugKotlin
```
Résultat attendu : BUILD SUCCESSFUL.

- [ ] **Étape 3 : Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/settings/IcsSettingsScreen.kt
git commit -m "feat(ics): IcsSettingsScreen with URL input, verify and clear"
```

---

## Task 9: SettingsScreen + AppNav — route ICS_SETTINGS

**Files:**
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/AppNav.kt`

- [ ] **Étape 1 : Ajouter paramètre onAgenda dans SettingsScreen**

Dans la signature de `SettingsScreen`, ajouter `onAgenda: () -> Unit = {}` après `onInstalledApps`.

Dans le corps, ajouter avant la ligne "Comptes Google" :
```kotlin
SettingRow("Agenda Google", null, onAgenda)
```

- [ ] **Étape 2 : Ajouter route dans AppNav**

Dans `Routes`, ajouter :
```kotlin
const val ICS_SETTINGS = "ics_settings"
```

Dans le `NavHost`, ajouter un composable :
```kotlin
composable(Routes.ICS_SETTINGS) {
    com.cyprienbrisset.myportal.ui.settings.IcsSettingsScreen(onBack = { nav.popBackStack() })
}
```

Dans le bloc `composable(Routes.SETTINGS)`, passer `onAgenda = { nav.navigate(Routes.ICS_SETTINGS) }` à `SettingsScreen`.

- [ ] **Étape 3 : Build de vérification**

```bash
export JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:compileDebugKotlin
```
Résultat attendu : BUILD SUCCESSFUL.

- [ ] **Étape 4 : Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/settings/SettingsScreen.kt \
        app/src/main/java/com/cyprienbrisset/myportal/ui/AppNav.kt
git commit -m "feat(ics): add Agenda Google settings row and ICS_SETTINGS nav route"
```

---

## Task 10: PersistentWebViewPool

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/google/PersistentWebViewPool.kt`

- [ ] **Étape 1 : Créer le singleton**

```kotlin
package com.cyprienbrisset.myportal.ui.google

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

object PersistentWebViewPool {

    @SuppressLint("StaticFieldLeak")
    private val cache = mutableMapOf<String, WebView>()

    fun get(key: String, context: Context): WebView = cache.getOrPut(key) { create(context) }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(context: Context): WebView = WebView(context.applicationContext).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webViewClient = WebViewClient()
    }
}
```

- [ ] **Étape 2 : Build de vérification**

```bash
export JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:compileDebugKotlin
```
Résultat attendu : BUILD SUCCESSFUL.

- [ ] **Étape 3 : Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/google/PersistentWebViewPool.kt
git commit -m "feat(webview): PersistentWebViewPool singleton with per-key WebView cache"
```

---

## Task 11: GoogleWebSheet composable

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/google/GoogleWebSheet.kt`

- [ ] **Étape 1 : Créer le composable**

```kotlin
package com.cyprienbrisset.myportal.ui.google

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cyprienbrisset.myportal.ui.sumi.HankoSeal
import com.cyprienbrisset.myportal.ui.theme.Sumi

/**
 * Full-screen dialog showing a persistent WebView for [url] identified by [poolKey].
 * The WebView is taken from [PersistentWebViewPool] and returned on dismiss (no destroy).
 */
@Composable
fun GoogleWebSheet(poolKey: String, url: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true),
    ) {
        Box(
            Modifier.fillMaxSize().background(Sumi).statusBarsPadding()
        ) {
            val webView = PersistentWebViewPool.get(poolKey, ctx)
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize(),
                update = { wv ->
                    // Detach from previous parent if needed.
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    if (wv.url.isNullOrBlank() || wv.url == "about:blank") {
                        wv.loadUrl(url)
                    }
                },
            )
            HankoSeal(
                "朱",
                size = 44.dp,
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            )
        }
    }
}
```

- [ ] **Étape 2 : Build de vérification**

```bash
export JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:compileDebugKotlin
```
Résultat attendu : BUILD SUCCESSFUL.

- [ ] **Étape 3 : Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/google/GoogleWebSheet.kt
git commit -m "feat(webview): GoogleWebSheet full-screen overlay with persistent WebView"
```

---

## Task 12: GoogleViewModel — source ICS

**Files:**
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/google/GoogleViewModel.kt`

- [ ] **Étape 1 : Ajouter la source ICS**

Remplacer le contenu de `GoogleViewModel.kt` :

```kotlin
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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

    @OptIn(ExperimentalCoroutinesApi::class)
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
```

- [ ] **Étape 2 : Build de vérification**

```bash
export JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:compileDebugKotlin
```
Résultat attendu : BUILD SUCCESSFUL.

- [ ] **Étape 3 : Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/google/GoogleViewModel.kt
git commit -m "feat(ics): GoogleViewModel loads events from ICS URL when configured"
```

---

## Task 13: GoogleScreen — tuiles Meet/Chat + hint ICS

**Files:**
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/google/GoogleScreen.kt`

- [ ] **Étape 1 : Ajouter les tuiles Meet/Chat et le hint ICS**

Dans `GoogleScreen`, ajouter les imports supplémentaires :
```kotlin
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
```

Dans la fonction `GoogleScreen`, collecter icsUrl :
```kotlin
val icsUrl by vm.icsUrl.collectAsStateWithLifecycle()
```

Ajouter une variable d'état pour le WebSheet :
```kotlin
var webSheetKey by remember { mutableStateOf<String?>(null) }
```

Dans le `LazyColumn`, **avant** le bloc des shortcuts apps existants, ajouter un `item` avec les tuiles WebView :
```kotlin
item {
    Spacer(Modifier.height(4.dp))
    SectionLabel("つながり", "COMMUNICATION")
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        WebTile("Meet", onClick = { webSheetKey = "meet" }, modifier = Modifier.weight(1f))
        WebTile("Chat", onClick = { webSheetKey = "chat" }, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(20.dp))
}
```

Après la `LazyColumn` (mais toujours dans la `Column` externe), ajouter le WebSheet :
```kotlin
webSheetKey?.let { key ->
    val url = if (key == "meet") "https://meet.google.com" else "https://chat.google.com"
    GoogleWebSheet(poolKey = key, url = url, onDismiss = { webSheetKey = null })
}
```

Dans la section événements agenda, remplacer le `LaunchedEffect(hasCalendarPerm)` pour aussi déclencher lors d'une URL ICS :
```kotlin
LaunchedEffect(hasCalendarPerm, icsUrl) {
    if (hasCalendarPerm || !icsUrl.isNullOrBlank()) vm.loadEvents(System.currentTimeMillis())
}
```

Dans le bloc `if (vm.calendarInstalled)`, ajouter une variante pour ICS — afficher la section agenda si ICS configurée OU si appli installée. Changer :
```kotlin
if (vm.calendarInstalled) {
```
en :
```kotlin
if (vm.calendarInstalled || !icsUrl.isNullOrBlank()) {
```

Et dans le bloc `!hasCalendarPerm`, ajouter une condition : ne montrer la demande de permission que si l'appli est installée (pas nécessaire pour ICS seul) :
```kotlin
!hasCalendarPerm && vm.calendarInstalled -> Column { /* ... demande permission ... */ }
```

Enfin, si ni l'appli ni l'URL ICS ne sont configurées, afficher un hint sous les tuiles. Dans le `item` de communication, sous les tuiles :
```kotlin
if (!vm.calendarInstalled && icsUrl.isNullOrBlank()) {
    Spacer(Modifier.height(12.dp))
    Text(
        "Ajoute ton URL d'agenda Google dans les Réglages pour voir tes événements.",
        color = SumiMuted, fontSize = 14.sp,
    )
}
```

Ajouter le composable `WebTile` en bas du fichier :
```kotlin
@Composable
private fun WebTile(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(SumiSurface)
            .border(1.dp, SumiLine, RoundedCornerShape(16.dp))
            .clickable { onClick() }.padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Kinari, fontFamily = Mincho, fontSize = 20.sp)
    }
}
```

- [ ] **Étape 2 : Build complet**

```bash
export JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:clean :app:assembleDebug
```
Résultat attendu : BUILD SUCCESSFUL, APK généré dans `app/build/outputs/apk/debug/`.

- [ ] **Étape 3 : Déploiement sur le Portal**

```bash
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Résultat attendu : `Success`

- [ ] **Étape 4 : Vérifications manuelles**

- [ ] Onglet Google → deux tuiles "Meet" et "Chat" visibles
- [ ] Tap sur "Meet" → GoogleWebSheet s'ouvre avec meet.google.com
- [ ] Fermer avec le sceau "朱" → retour à l'onglet Google sans rechargement au deuxième tap
- [ ] Réglages → "Agenda Google" → IcsSettingsScreen s'ouvre
- [ ] Coller une URL ICS valide → "Vérifier" → compte des événements affiché
- [ ] Après enregistrement, onglet Google → section PROCHAINS ÉVÉNEMENTS avec les events ICS
- [ ] HomeScreen : pas de bande contacts visible (normal, aucune notif Messenger/WhatsApp reçue)
- [ ] Envoyer un message WhatsApp au Portal → bande contacts apparaît avec l'avatar

- [ ] **Étape 5 : Commit final**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/google/GoogleScreen.kt
git commit -m "feat(google): Meet/Chat WebView tiles and ICS calendar hint in GoogleScreen"
```

---

## Résumé des commits attendus

1. `feat: HomeShell tabs, Google page, installed apps, system settings shortcuts`
2. `feat(ics): add google_ics_url key to SettingsRepository`
3. `feat(contacts): RecentContactsRepository with StateFlow and dedup logic`
4. `feat(contacts): extend MediaListenerService to capture Messenger/WhatsApp contacts`
5. `feat(contacts): RecentContactsStrip composable (avatar circle + name)`
6. `feat(contacts): expose and display recent contacts strip in HomeScreen`
7. `feat(ics): IcsCalendarRepository with OkHttp fetch and VEVENT parser`
8. `feat(ics): IcsSettingsScreen with URL input, verify and clear`
9. `feat(ics): add Agenda Google settings row and ICS_SETTINGS nav route`
10. `feat(webview): PersistentWebViewPool singleton with per-key WebView cache`
11. `feat(webview): GoogleWebSheet full-screen overlay with persistent WebView`
12. `feat(ics): GoogleViewModel loads events from ICS URL when configured`
13. `feat(google): Meet/Chat WebView tiles and ICS calendar hint in GoogleScreen`
