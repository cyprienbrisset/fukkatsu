# Home Controls + Media Player Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a top-right seal-button cluster (Do-Not-Disturb, screen-off, settings-with-red-background) and a now-playing media mini-player with prev/play-pause/next under the clock.

**Architecture:** Presentation + thin system-service wrappers. A reusable `SealIconButton` (vermilion seal + icon) drives the cluster. DND uses `NotificationManager.setInterruptionFilter`; screen-off uses a `DeviceAdminReceiver` + `DevicePolicyManager.lockNow()`; the player reads/controls the active `MediaSession` via a `NotificationListenerService` + `MediaSessionManager`. Each capability redirects to its system permission screen when not yet granted.

**Tech Stack:** Jetpack Compose (Sumi tokens), Android NotificationManager / DevicePolicyManager / MediaSessionManager, existing MVVM.

---

## Environment (unchanged — critical)

- No `java` on PATH. Every Gradle call MUST prefix JAVA_HOME:
  `JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew ...`
- Devices: Portal+ `adb -s 2221B01C9C02NQ` (Android 10/API 29), emulator `emulator-5554`. Install with explicit `-s`. `adb` = `/Users/cyprienbrisset/Library/Android/sdk/platform-tools/adb`.
- Verify each task with `:app:assembleDebug`; JVM tests with `:app:testDebugUnitTest`. Do NOT stage `.claude/oxygen-status.json`.
- Branch `feat/home-controls-player` (already created). Package root `com.cyprienbrisset.myportal`.

---

## File Structure

```
ui/sumi/SealIconButton.kt        # NEW — vermilion seal + ImageVector icon (red bg), optional active state
ui/home/HomeScreen.kt            # MOD — cluster Row (DND/screen-off/settings) + NowPlayingBar under clock
ui/home/AmbientBanner.kt         # MOD — expose a slot under the date/weather (or NowPlayingBar placed in HomeScreen hero column)
ui/home/HomeViewModel.kt         # MOD — nowPlaying StateFlow
ui/home/NowPlayingBar.kt         # NEW — Compose player UI
system/DndController.kt          # NEW — DND grant/toggle/state
system/ScreenLock.kt             # NEW — device-admin lock
system/MyDeviceAdminReceiver.kt  # NEW — DeviceAdminReceiver
system/MediaListenerService.kt   # NEW — NotificationListenerService (enables session access)
media/NowPlaying.kt              # NEW — NowPlaying data + pure helpers (isPlaying)
media/NowPlayingController.kt    # NEW — MediaSessionManager wrapper -> StateFlow + actions
res/xml/device_admin.xml         # NEW — force-lock policy
AndroidManifest.xml              # MOD — permission + service + receiver
app/src/test/.../media/NowPlayingTest.kt  # NEW
```

---

## PHASE 1 — Seal button + red settings

### Task 1: SealIconButton + restore red settings button

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/sumi/SealIconButton.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeScreen.kt`

- [ ] **Step 1: Create SealIconButton.kt**

```kotlin
package com.cyprienbrisset.myportal.ui.sumi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyprienbrisset.myportal.ui.theme.OnShu
import com.cyprienbrisset.myportal.ui.theme.Shu

@Composable
fun SealIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    active: Boolean = false,
) {
    val shape = RoundedCornerShape(size / 5)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Shu)
            .then(if (active) Modifier.border(BorderStroke(2.dp, OnShu), shape) else Modifier)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = OnShu, modifier = Modifier.size(size * 0.55f))
    }
}
```

- [ ] **Step 2: Use it for the settings button (restores red background)**

In `HomeScreen.kt`, replace the current settings `IconButton { Icon(Icons.Rounded.Settings ...) }` block with:
```kotlin
SealIconButton(
    icon = androidx.compose.material.icons.Icons.Rounded.Settings,
    contentDescription = "Réglages",
    onClick = onOpenSettings,
    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 16.dp, end = 30.dp),
)
```
Remove the now-unused imports `androidx.compose.material3.Icon`, `androidx.compose.material3.IconButton`, and `com.cyprienbrisset.myportal.ui.theme.SumiMuted` ONLY IF nothing else in the file uses them (check — SumiMuted may be unused now; Icon/IconButton likely unused). Add `import com.cyprienbrisset.myportal.ui.sumi.SealIconButton`. Keep `Icons.Rounded.Settings` import.

- [ ] **Step 3: Build + commit**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/sumi/SealIconButton.kt app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeScreen.kt
git commit -m "feat: SealIconButton (red seal + icon); restore red settings button"
```

---

## PHASE 2 — Do Not Disturb

### Task 2: DndController + DND button

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/system/DndController.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: DndController.kt**

```kotlin
package com.cyprienbrisset.myportal.system

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

object DndController {
    fun isGranted(ctx: Context): Boolean =
        ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun isDndOn(ctx: Context): Boolean {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        return nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }

    /** Toggles DND; if access not granted, opens the system grant screen instead. */
    fun toggleOrRequest(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            ctx.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        nm.setInterruptionFilter(
            if (isDndOn(ctx)) NotificationManager.INTERRUPTION_FILTER_ALL
            else NotificationManager.INTERRUPTION_FILTER_NONE
        )
    }
}
```

- [ ] **Step 2: Add the DND button in the cluster**

In `HomeScreen.kt`, wrap the settings button in a `Row` (the cluster), aligned `TopEnd`, and put the DND button first. Read DND state keyed on the ticking `now` so the icon reflects state:
```kotlin
val ctx = LocalContext.current  // already present
val dndOn = remember(now) { DndController.isDndOn(ctx) && DndController.isGranted(ctx) }
Row(
    Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 16.dp, end = 30.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    SealIconButton(
        icon = if (dndOn) androidx.compose.material.icons.Icons.Rounded.NotificationsOff
               else androidx.compose.material.icons.Icons.Rounded.Notifications,
        contentDescription = "Ne pas déranger",
        active = dndOn,
        onClick = { DndController.toggleOrRequest(ctx) },
    )
    // screen-off button added in Task 3
    SealIconButton(
        icon = androidx.compose.material.icons.Icons.Rounded.Settings,
        contentDescription = "Réglages",
        onClick = onOpenSettings,
    )
}
```
Remove the standalone settings `SealIconButton` from Task 1 (now inside the Row). Add imports: `com.cyprienbrisset.myportal.system.DndController`, `androidx.compose.runtime.remember`, `androidx.compose.foundation.layout.Arrangement` (already imported), `androidx.compose.material.icons.rounded.Notifications`, `androidx.compose.material.icons.rounded.NotificationsOff`.

- [ ] **Step 3: Manifest permission**

Add under `<manifest>`:
```xml
<uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />
```

- [ ] **Step 4: Build + commit**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/system/DndController.kt app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeScreen.kt app/src/main/AndroidManifest.xml
git commit -m "feat: Do-Not-Disturb toggle button (with grant redirect)"
```

---

## PHASE 3 — Screen off (device admin)

### Task 3: DeviceAdminReceiver + ScreenLock + button

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/system/MyDeviceAdminReceiver.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/system/ScreenLock.kt`
- Create: `app/src/main/res/xml/device_admin.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeScreen.kt`

- [ ] **Step 1: MyDeviceAdminReceiver.kt**

```kotlin
package com.cyprienbrisset.myportal.system

import android.app.admin.DeviceAdminReceiver

class MyDeviceAdminReceiver : DeviceAdminReceiver()
```

- [ ] **Step 2: device_admin.xml**

`app/src/main/res/xml/device_admin.xml`:
```xml
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-policies>
        <force-lock />
    </uses-policies>
</device-admin>
```

- [ ] **Step 3: ScreenLock.kt**

```kotlin
package com.cyprienbrisset.myportal.system

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object ScreenLock {
    private fun admin(ctx: Context) = ComponentName(ctx, MyDeviceAdminReceiver::class.java)

    fun isActive(ctx: Context): Boolean =
        ctx.getSystemService(DevicePolicyManager::class.java).isAdminActive(admin(ctx))

    /** Locks (turns off) the screen; if admin not active, opens the enable-admin screen. */
    fun lockOrRequest(ctx: Context) {
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        if (dpm.isAdminActive(admin(ctx))) {
            dpm.lockNow()
        } else {
            ctx.startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(ctx))
                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Autorise MyPortal à éteindre l'écran.")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
```

- [ ] **Step 4: Register the receiver in the manifest**

Inside `<application>`:
```xml
<receiver
    android:name=".system.MyDeviceAdminReceiver"
    android:exported="true"
    android:permission="android.permission.BIND_DEVICE_ADMIN">
    <meta-data android:name="android.app.device_admin" android:resource="@xml/device_admin" />
    <intent-filter>
        <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
    </intent-filter>
</receiver>
```

- [ ] **Step 5: Add the screen-off button to the cluster**

In `HomeScreen.kt`, between the DND and settings buttons in the Row:
```kotlin
SealIconButton(
    icon = androidx.compose.material.icons.Icons.Rounded.PowerSettingsNew,
    contentDescription = "Éteindre l'écran",
    onClick = { ScreenLock.lockOrRequest(ctx) },
)
```
Add imports: `com.cyprienbrisset.myportal.system.ScreenLock`, `androidx.compose.material.icons.rounded.PowerSettingsNew`.

- [ ] **Step 6: Build + commit**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/system app/src/main/res/xml/device_admin.xml app/src/main/AndroidManifest.xml app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeScreen.kt
git commit -m "feat: screen-off button via device-admin lockNow (with enable redirect)"
```

**PHASE 2+3 END — on-device:** install; tap DND → grants + toggles (icon changes); tap screen-off → prompts device-admin enable, then locks the Portal screen.

---

## PHASE 4 — Media player

### Task 4: NowPlaying model + pure helper (TDD)

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/media/NowPlaying.kt`
- Test: `app/src/test/java/com/cyprienbrisset/myportal/media/NowPlayingTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cyprienbrisset.myportal.media

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingTest {
    @Test fun playingStateIsPlaying() {
        assertTrue(isPlaying(PlaybackState.STATE_PLAYING))
    }
    @Test fun otherStatesAreNotPlaying() {
        assertFalse(isPlaying(PlaybackState.STATE_PAUSED))
        assertFalse(isPlaying(PlaybackState.STATE_STOPPED))
        assertFalse(isPlaying(PlaybackState.STATE_NONE))
    }
    @Test fun indexOfActivePicksFirst() {
        assertEquals(0, indexOfActive(listOf(true, false)))
        assertEquals(1, indexOfActive(listOf(false, true)))
        assertEquals(0, indexOfActive(listOf(false, false))) // fallback: first
        assertEquals(-1, indexOfActive(emptyList()))
    }
}
```
Run: `JAVA_HOME=... ./gradlew :app:testDebugUnitTest --tests "*NowPlayingTest*"` → FAIL.
Note: `PlaybackState.STATE_*` are plain int constants available on the JVM classpath (android.jar) at unit-test compile — they resolve. If the unit test cannot see `android.media.session.PlaybackState` constants, replace them in the test with their literal values (PLAYING=3, PAUSED=2, STOPPED=1, NONE=0) and add a comment; keep `isPlaying(Int)` semantics.

- [ ] **Step 2: Implement NowPlaying.kt**

```kotlin
package com.cyprienbrisset.myportal.media

import android.graphics.Bitmap
import android.media.session.PlaybackState

data class NowPlaying(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val art: Bitmap? = null,
)

fun isPlaying(state: Int): Boolean = state == PlaybackState.STATE_PLAYING

/** First "active" (playing) index, else 0 if any exist, else -1. */
fun indexOfActive(playing: List<Boolean>): Int {
    if (playing.isEmpty()) return -1
    val i = playing.indexOfFirst { it }
    return if (i >= 0) i else 0
}
```
Run the test → PASS (3 tests).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/media/NowPlaying.kt app/src/test/java/com/cyprienbrisset/myportal/media/NowPlayingTest.kt
git commit -m "feat: NowPlaying model + tested isPlaying/indexOfActive helpers"
```

---

### Task 5: MediaListenerService + NowPlayingController

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/system/MediaListenerService.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/media/NowPlayingController.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: MediaListenerService.kt** (empty listener — its presence + grant enables session access)

```kotlin
package com.cyprienbrisset.myportal.system

import android.service.notification.NotificationListenerService

class MediaListenerService : NotificationListenerService()
```

- [ ] **Step 2: NowPlayingController.kt**

```kotlin
package com.cyprienbrisset.myportal.media

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.app.NotificationManagerCompat
import com.cyprienbrisset.myportal.system.MediaListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NowPlayingController(private val context: Context) {
    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state

    private val component = ComponentName(context, MediaListenerService::class.java)
    private val msm = context.getSystemService(MediaSessionManager::class.java)
    private var controller: MediaController? = null

    private val cb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refreshFromController()
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = refreshFromController()
        override fun onSessionDestroyed() = refresh()
    }

    fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun refresh() {
        if (!hasAccess()) { detach(); _state.value = null; return }
        val sessions = try { msm.getActiveSessions(component) } catch (e: SecurityException) { emptyList() }
        val playingFlags = sessions.map { isPlaying(it.playbackState?.state ?: PlaybackState.STATE_NONE) }
        val idx = indexOfActive(playingFlags)
        val next = sessions.getOrNull(idx)
        if (next?.sessionToken != controller?.sessionToken) {
            detach(); controller = next; controller?.registerCallback(cb)
        }
        refreshFromController()
    }

    private fun refreshFromController() {
        val c = controller
        if (c == null) { _state.value = null; return }
        val md = c.metadata
        val title = md?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = md?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val art = md?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
        val playing = isPlaying(c.playbackState?.state ?: PlaybackState.STATE_NONE)
        _state.value = if (title.isBlank() && artist.isBlank()) null else NowPlaying(title, artist, playing, art)
    }

    fun toggle() { val c = controller ?: return
        if (isPlaying(c.playbackState?.state ?: PlaybackState.STATE_NONE)) c.transportControls.pause()
        else c.transportControls.play() }
    fun next() { controller?.transportControls?.skipToNext() }
    fun prev() { controller?.transportControls?.skipToPrevious() }

    private fun detach() { controller?.unregisterCallback(cb) }
    fun dispose() { detach(); controller = null }
}
```

- [ ] **Step 3: Register the listener service in the manifest**

Inside `<application>`:
```xml
<service
    android:name=".system.MediaListenerService"
    android:exported="false"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

- [ ] **Step 4: Build + commit**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/system/MediaListenerService.kt app/src/main/java/com/cyprienbrisset/myportal/media/NowPlayingController.kt app/src/main/AndroidManifest.xml
git commit -m "feat: MediaListenerService + NowPlayingController (active session read/control)"
```

---

### Task 6: NowPlayingBar UI + wire into home

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/NowPlayingBar.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeScreen.kt`

- [ ] **Step 1: Extend HomeViewModel with nowPlaying**

Add to `HomeViewModel`:
```kotlin
// imports: NowPlayingController, NowPlaying, SharingStarted, stateIn already present for others
private val nowPlayingController = com.cyprienbrisset.myportal.media.NowPlayingController(app)
val nowPlaying = nowPlayingController.state

fun refreshNowPlaying() = nowPlayingController.refresh()
fun mediaToggle() = nowPlayingController.toggle()
fun mediaNext() = nowPlayingController.next()
fun mediaPrev() = nowPlayingController.prev()
fun hasMediaAccess() = nowPlayingController.hasAccess()

override fun onCleared() { nowPlayingController.dispose(); super.onCleared() }
```
(`HomeViewModel` is an `AndroidViewModel(app)`. Ensure `onCleared` calls super.)

- [ ] **Step 2: NowPlayingBar.kt**

```kotlin
package com.cyprienbrisset.myportal.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.myportal.media.NowPlaying
import com.cyprienbrisset.myportal.ui.theme.Kinari
import com.cyprienbrisset.myportal.ui.theme.SumiMuted
import com.cyprienbrisset.myportal.ui.theme.SumiSurface

@Composable
fun NowPlayingBar(np: NowPlaying, onPrev: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(SumiSurface), contentAlignment = Alignment.Center) {
            val art = np.art
            if (art != null) Image(art.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f)) {
            Text(np.title.ifBlank { "Lecture" }, color = Kinari, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (np.artist.isNotBlank()) Text(np.artist, color = SumiMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.SkipPrevious, "Précédent", tint = Kinari, modifier = Modifier.size(30.dp).clickable { onPrev() })
        Icon(if (np.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", tint = Kinari,
            modifier = Modifier.size(38.dp).clickable { onToggle() })
        Icon(Icons.Rounded.SkipNext, "Suivant", tint = Kinari, modifier = Modifier.size(30.dp).clickable { onNext() })
    }
}
```

- [ ] **Step 3: Place it under the clock + refresh on tick**

In `HomeScreen.kt`, collect `nowPlaying` and place the bar in the hero column, after `AmbientBanner(...)`:
```kotlin
val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
LaunchedEffect(now) { vm.refreshNowPlaying() }  // re-poll each second tick (cheap; also catches new sessions)
// in the landscape hero Box/Column and portrait column, below AmbientBanner:
val np = nowPlaying
if (np != null) {
    Spacer(Modifier.height(18.dp))
    NowPlayingBar(np, onPrev = { vm.mediaPrev() }, onToggle = { vm.mediaToggle() }, onNext = { vm.mediaNext() },
        modifier = Modifier.fillMaxWidth(if (landscape) 0.9f else 1f))
}
```
Because the hero in landscape is a `Box(contentAlignment = CenterStart)` wrapping `AmbientBanner`, change that hero to a `Column` so the banner and the player stack: replace `Box(...) { AmbientBanner(...) }` with `Column(Modifier.fillMaxHeight().weight(0.38f), verticalArrangement = Arrangement.Center) { AmbientBanner(...); <player block> }`. In portrait, add the player block right after the `AmbientBanner(...)` call inside the existing Column.
Add imports: `androidx.compose.runtime.LaunchedEffect`, `androidx.compose.foundation.layout.fillMaxWidth`, `NowPlayingBar` is same package (no import).

- [ ] **Step 4: Build + on-device verify**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
Install on the Portal. Grant "Notification access" to MyPortal (Réglages système → Accès aux notifications), start Spotify playback, return to MyPortal home → the player shows title/artist/art under the clock and the transport buttons control playback.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/home
git commit -m "feat: now-playing media bar under the clock (prev/play-pause/next)"
```

**PHASE 4 END — full run:** `JAVA_HOME=... ./gradlew :app:testDebugUnitTest` (all pass). On the Portal: player appears/controls while Spotify plays; hidden when nothing plays.

---

## Self-Review (author checklist, completed)

**Spec coverage:** SealIconButton + red settings (T1), DND toggle + grant (T2), screen-off device-admin (T3), NowPlaying model/helpers (T4), listener service + controller (T5), player UI + wiring (T6). ✅
**Permissions:** ACCESS_NOTIFICATION_POLICY (T2), BIND_DEVICE_ADMIN + device_admin.xml (T3), BIND_NOTIFICATION_LISTENER_SERVICE (T5) — all present, all user-granted via redirects. ✅
**Type consistency:** `SealIconButton(icon,contentDescription,onClick,modifier,size,active)`, `DndController.toggleOrRequest/isDndOn/isGranted`, `ScreenLock.lockOrRequest/isActive`, `NowPlayingController.state/refresh/toggle/next/prev/hasAccess/dispose`, `isPlaying(Int)`, `indexOfActive(List<Boolean>)`, `NowPlaying(title,artist,isPlaying,art)`, `NowPlayingBar(np,onPrev,onToggle,onNext,modifier)` — consistent. ✅
**Ordering seams:** T1 adds a standalone settings SealIconButton; T2 folds it into the cluster Row (noted). Hero Box→Column change for the player is in T6 (noted). MediaListenerService referenced by controller (T5) created in the same task.
**No placeholders:** every step has concrete code/commands.
