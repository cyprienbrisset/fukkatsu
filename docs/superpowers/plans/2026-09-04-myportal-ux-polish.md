# MyPortal v2 — UX/UI Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish MyPortal for Portal+ gen 1 & 2 — real tile icons, a responsive (landscape + portrait) "banner + row" home, a reliable alarm built on a foreground service + full-screen-intent notification, and per-alarm ringtone/snooze.

**Architecture:** Extends the existing single-module Compose app. Home gets a reusable `TileIcon` and an orientation-aware layout chooser. The alarm subsystem moves sound to an `AlarmForegroundService` (with volume ramp + wakelock) triggered via a full-screen-intent notification; the ring Activity becomes UI-only and commands the service. Room migrates to v3 for per-alarm `ringtoneUri` + `snoozeMinutes`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Coil (icons/favicons), Room 2.7.2 (+KSP), AlarmManager, foreground Service + NotificationCompat.

---

## Environment (same as v1 — critical)

- **No `java` on PATH.** Every Gradle call MUST prefix JAVA_HOME:
  `JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew ...`
- **Two devices are connected now:** the Portal+ 2nd gen (`adb -s 2221B01C9C02NQ`, Android 10 / API 29, 2160×1440) and the emulator `emulator-5554` (`MyPortal_Emu`, API 35). `adb` = `/Users/cyprienbrisset/Library/Android/sdk/platform-tools/adb`. Because multiple devices are attached, install with an explicit target, e.g. `adb -s emulator-5554 install -r ...`.
- Verify each task with `:app:assembleDebug`; run JVM tests with `:app:testDebugUnitTest`. On-device/emulator visual + alarm checks happen at phase ends.
- Package root: `com.cyprienbrisset.myportal`. Source: `app/src/main/java/com/cyprienbrisset/myportal/`, unit tests: `app/src/test/java/com/cyprienbrisset/myportal/`.
- Do NOT stage `.claude/oxygen-status.json` in any commit.
- Current state: v1 is on `main`. Work happens on a new branch `feat/ux-polish` (created before Task 1).

---

## File Structure

```
ui/home/
  TileIcon.kt        # NEW — app icon / favicon / monogram, with pure helpers
  HomeLayout.kt      # NEW — HomeLayoutMode enum + pure homeLayoutFor(widthDp, isLandscape)
  HomeScreen.kt      # MOD — responsive banner+row/grid, uses TileIcon
  AmbientBanner.kt   # MOD — landscape vs portrait arrangement
  TileGrid.kt        # MOD — icon tiles + adaptive columns + "Ajouter" tile
alarm/
  AlarmNotifications.kt      # NEW — channel + full-screen-intent notification
  AlarmForegroundService.kt  # NEW — sound loop, volume ramp, wakelock, START/STOP/SNOOZE
  VolumeRamp.kt              # NEW — pure ramp math (testable)
  AlarmReceiver.kt           # MOD — start service + post notification
  AlarmRingActivity.kt       # MOD — UI-only; buttons command the service
  AlarmScheduler.kt          # MOD — snooze uses per-alarm minutes
data/alarm/
  AlarmEntity.kt     # MOD — + ringtoneUri, snoozeMinutes (DB v3)
  AlarmDao.kt        # (unchanged)
data/AppDatabase.kt  # MOD — version 3
ui/alarms/
  AlarmsScreen.kt      # MOD — ringtone picker + snooze duration
  AlarmsViewModel.kt   # MOD — persist ringtone/snooze
MainActivity.kt        # MOD — do not lock orientation (already unlocked; confirm)
AndroidManifest.xml    # MOD — service + FGS/notification permissions
app/src/test/.../
  ui/home/TileIconHelpersTest.kt   # NEW
  ui/home/HomeLayoutTest.kt        # NEW
  alarm/VolumeRampTest.kt          # NEW
```

---

## PHASE 1 — Home polish (tile icons + responsive layout)

### Task 1: TileIcon with pure helpers (monogram + favicon URL)

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/TileIcon.kt`
- Test: `app/src/test/java/com/cyprienbrisset/myportal/ui/home/TileIconHelpersTest.kt`

- [ ] **Step 1: Write the failing test for the pure helpers**

`app/src/test/java/com/cyprienbrisset/myportal/ui/home/TileIconHelpersTest.kt`:
```kotlin
package com.cyprienbrisset.myportal.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileIconHelpersTest {
    @Test fun monogramLetterIsFirstNonBlankUpper() {
        assertEquals("N", monogramLetter("netflix"))
        assertEquals("J", monogramLetter("  jellyfin"))
        assertEquals("?", monogramLetter("   "))
    }

    @Test fun monogramColorIsDeterministicAndStable() {
        assertEquals(monogramColor("Netflix"), monogramColor("Netflix"))
        assertTrue(monogramColor("Netflix") != monogramColor("Jellyfin"))
    }

    @Test fun faviconUrlExtractsHostAndSize() {
        assertEquals(
            "https://www.google.com/s2/favicons?sz=128&domain=jellyfin.local",
            faviconUrl("http://jellyfin.local:8096/web/index.html"),
        )
        assertEquals(
            "https://www.google.com/s2/favicons?sz=128&domain=youtube.com",
            faviconUrl("https://youtube.com"),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=... ./gradlew :app:testDebugUnitTest --tests "*TileIconHelpersTest*"`
Expected: FAIL (unresolved references).

- [ ] **Step 3: Implement TileIcon.kt (pure helpers + composable)**

```kotlin
package com.cyprienbrisset.myportal.ui.home

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cyprienbrisset.myportal.data.tile.TileEntity
import com.cyprienbrisset.myportal.data.tile.TileType

/** First non-blank character, uppercased; "?" if none. */
fun monogramLetter(label: String): String =
    label.trim().firstOrNull()?.uppercase() ?: "?"

/** Deterministic pleasant color derived from the label (HSL-ish via fixed hue table). */
fun monogramColor(label: String): Long {
    val palette = longArrayOf(
        0xFF4C5FD5, 0xFFD54C7A, 0xFF3FA34D, 0xFFD58A4C,
        0xFF8A4CD5, 0xFF4CB5D5, 0xFFD5C24C, 0xFFD54C4C,
    )
    val idx = (label.trim().lowercase().hashCode() and 0x7FFFFFFF) % palette.size
    return palette[idx]
}

/** Google favicon service URL for the host of [url], size 128. */
fun faviconUrl(url: String): String {
    val noScheme = url.substringAfter("://", url)
    val host = noScheme.substringBefore('/').substringBefore(':')
    return "https://www.google.com/s2/favicons?sz=128&domain=$host"
}

@Composable
fun TileIcon(tile: TileEntity, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(size / 4)

    // Custom icon wins.
    val custom = tile.iconRef
    if (custom != null) {
        AsyncImage(model = custom, contentDescription = tile.label,
            modifier = modifier.size(size).clip(shape))
        return
    }

    when (tile.type) {
        TileType.APP -> {
            val pkg = tile.packageName
            val drawable = remember(pkg) {
                if (pkg == null) null
                else runCatching { ctx.packageManager.getApplicationIcon(pkg) }.getOrNull()
            }
            if (drawable != null) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(drawable).build(),
                    contentDescription = tile.label,
                    modifier = modifier.size(size).clip(shape),
                )
            } else {
                Monogram(tile.label, size, modifier)
            }
        }
        TileType.WEB -> {
            val url = tile.url
            if (url == null) {
                Monogram(tile.label, size, modifier)
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(faviconUrl(url)).crossfade(true).build(),
                    contentDescription = tile.label,
                    modifier = modifier.size(size).clip(shape),
                )
            }
        }
    }
}

@Composable
private fun Monogram(label: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(size / 4))
            .background(Color(monogramColor(label))),
        contentAlignment = Alignment.Center,
    ) {
        Text(monogramLetter(label), color = Color.White, fontSize = (size.value / 2).sp)
    }
}
```
Note: Coil 2.7 accepts a `Drawable` via `ImageRequest.data(drawable)`. If the compiler rejects a `Drawable` as `data`, wrap it: convert to bitmap with `drawable.toBitmap()` (androidx-core `androidx.core.graphics.drawable.toBitmap`) and pass the Bitmap. Prefer whatever compiles; note the choice.

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=... ./gradlew :app:testDebugUnitTest --tests "*TileIconHelpersTest*"`
Expected: PASS (3 tests).

- [ ] **Step 5: Verify compile + commit**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/home/TileIcon.kt app/src/test/java/com/cyprienbrisset/myportal/ui/home/TileIconHelpersTest.kt
git commit -m "feat: TileIcon (app icon / favicon / monogram) with tested helpers"
```

---

### Task 2: Icon tiles + "Ajouter" tile in TileGrid

**Files:**
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/TileGrid.kt`

- [ ] **Step 1: Rewrite TileGrid to show icons, names, and an add tile**

```kotlin
package com.cyprienbrisset.myportal.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cyprienbrisset.myportal.data.tile.TileEntity

@Composable
fun TileGrid(
    tiles: List<TileEntity>,
    minTileWidth: androidx.compose.ui.unit.Dp,
    onTileClick: (TileEntity) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minTileWidth),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(tiles, key = { it.id }) { tile ->
            TileCard(label = tile.label, onClick = { onTileClick(tile) }) {
                TileIcon(tile = tile, size = 64.dp)
            }
        }
        item(key = "__add__") {
            TileCard(label = "Ajouter", onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = "Ajouter",
                    modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TileCard(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.clickable { onClick() },
    ) {
        Column(
            Modifier.fillMaxSize().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            icon()
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
```
Note: `GridCells.Adaptive` cells have no intrinsic height here; give the `Card` a fixed height by adding `.height(150.dp)` on the `Card`'s Modifier chain (append to `Modifier.clickable{...}`) so tiles are uniform. Add `import androidx.compose.foundation.layout.height` and apply `.height(150.dp)`.

- [ ] **Step 2: Verify compile**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (Callers `HomeScreen` will be updated in Task 3 — if `HomeScreen` still calls the old `TileGrid(tiles, onTileClick)` signature, this build fails; that's expected and fixed in Task 3. If you want a green build at this task boundary, update `HomeScreen`'s call site minimally now to pass `minTileWidth = 160.dp` and `onAddClick = {}`. Recommended: do that minimal call-site update here.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/home/TileGrid.kt app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeScreen.kt
git commit -m "feat: icon tiles + add tile in TileGrid"
```

---

### Task 3: Responsive home layout (landscape/portrait chooser)

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeLayout.kt`
- Test: `app/src/test/java/com/cyprienbrisset/myportal/ui/home/HomeLayoutTest.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/AmbientBanner.kt`

- [ ] **Step 1: Write the failing test for the pure chooser**

`app/src/test/java/com/cyprienbrisset/myportal/ui/home/HomeLayoutTest.kt`:
```kotlin
package com.cyprienbrisset.myportal.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutTest {
    @Test fun landscapeWhenWide() {
        assertEquals(HomeLayoutMode.LANDSCAPE, homeLayoutFor(widthDp = 1280, isLandscape = true))
    }
    @Test fun portraitWhenTallAndNarrow() {
        assertEquals(HomeLayoutMode.PORTRAIT, homeLayoutFor(widthDp = 800, isLandscape = false))
    }
    @Test fun narrowLandscapeStillLandscape() {
        // Orientation is the primary signal; a wide-enough landscape stays landscape.
        assertEquals(HomeLayoutMode.LANDSCAPE, homeLayoutFor(widthDp = 900, isLandscape = true))
    }
    @Test fun tileWidthsDifferPerMode() {
        assertEquals(180, HomeLayoutMode.LANDSCAPE.minTileWidthDp)
        assertEquals(160, HomeLayoutMode.PORTRAIT.minTileWidthDp)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=... ./gradlew :app:testDebugUnitTest --tests "*HomeLayoutTest*"`
Expected: FAIL.

- [ ] **Step 3: Implement HomeLayout.kt**

```kotlin
package com.cyprienbrisset.myportal.ui.home

enum class HomeLayoutMode(val minTileWidthDp: Int) {
    LANDSCAPE(180),
    PORTRAIT(160),
}

/** Orientation is the primary signal; width is a tiebreaker for square-ish screens. */
fun homeLayoutFor(widthDp: Int, isLandscape: Boolean): HomeLayoutMode =
    if (isLandscape || widthDp >= 900) HomeLayoutMode.LANDSCAPE else HomeLayoutMode.PORTRAIT
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=... ./gradlew :app:testDebugUnitTest --tests "*HomeLayoutTest*"`
Expected: PASS (4 tests).

- [ ] **Step 5: Make AmbientBanner orientation-aware**

Add a `portrait: Boolean = false` parameter to `AmbientBanner`. When `portrait` is true, arrange the clock/date/greeting in a centered `Column` (clock ~72sp) with weather + next-alarm below; when false keep the current `Row` (clock left, weather right). Keep the existing content/formatting. Concretely, wrap the existing body in `if (portrait) { Column(horizontalAlignment = CenterHorizontally, modifier = fillMaxWidth) { ... } } else { Row(...) { ... } }`, reusing the same Texts.

- [ ] **Step 6: Make HomeScreen responsive**

In `HomeScreen`, wrap content in `BoxWithConstraints`. Compute:
```kotlin
val isLandscape = maxWidth > maxHeight
val mode = homeLayoutFor(widthDp = maxWidth.value.toInt(), isLandscape = isLandscape)
```
Render a top row with the settings `IconButton` aligned to the end, then `AmbientBanner(now, weather, nextAlarm = nextAlarm, portrait = (mode == HomeLayoutMode.PORTRAIT))`, then `Text("Mes apps", style = labelMedium, color = onSurfaceVariant, modifier = padding)`, then `TileGrid(tiles, minTileWidth = mode.minTileWidthDp.dp, onTileClick = {...existing launch logic...}, onAddClick = { /* open settings→tiles: navigate via a new onAddTile lambda param */ })`.
Add an `onAddTile: () -> Unit` parameter to `HomeScreen` and wire it in `AppNav` to `nav.navigate(Routes.TILE_EDIT)`. Keep the existing APP/WEB launch behavior in `onTileClick`.

- [ ] **Step 7: Verify compile + emulator smoke**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
Optional emulator check: `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk && adb -s emulator-5554 shell am start -n com.cyprienbrisset.myportal/.MainActivity`, rotate with `adb -s emulator-5554 shell settings put system accelerometer_rotation 1` then `adb -s emulator-5554 emu rotate`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/home app/src/main/java/com/cyprienbrisset/myportal/ui/AppNav.kt app/src/test/java/com/cyprienbrisset/myportal/ui/home/HomeLayoutTest.kt
git commit -m "feat: responsive home layout (landscape/portrait) with Mes apps label"
```

---

### Task 4: Unlock orientation

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Ensure MainActivity is not orientation-locked**

Confirm the `.MainActivity` `<activity>` has NO `android:screenOrientation` attribute (so it follows the device). Add `android:configChanges="orientation|screenSize|keyboardHidden|smallestScreenSize"` to `.MainActivity` so rotation does not needlessly recreate the Compose tree (Compose reads `BoxWithConstraints` on resize either way). Leave `.WebAppActivity` as-is (already handles config changes).

- [ ] **Step 2: Verify compile + commit**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat: let MainActivity follow device orientation"
```

**PHASE 1 END — Manual verify (emulator + Portal):** install and rotate; tiles show real app icons / web favicons / monograms; landscape shows dense grid, portrait shows 2-col centered banner. Commands:
`adb -s 2221B01C9C02NQ install -r app/build/outputs/apk/debug/app-debug.apk` (Portal), same with `emulator-5554`.

---

## PHASE 2 — Robust alarm (foreground service + full-screen-intent)

### Task 5: Room v3 — per-alarm ringtone + snooze

**Files:**
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/data/alarm/AlarmEntity.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/data/AppDatabase.kt`

- [ ] **Step 1: Add fields to AlarmEntity**

Add to the data class:
```kotlin
val ringtoneUri: String? = null,   // null = default alarm sound
val snoozeMinutes: Int = 10,
```
(Append after `enabled`.)

- [ ] **Step 2: Bump DB to version 3**

In `AppDatabase.kt` change `version = 2` to `version = 3`. `fallbackToDestructiveMigration()` is already set, so no manual migration is needed (dev data is disposable).

- [ ] **Step 3: Verify compile + commit**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL (Room regenerates).
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/data
git commit -m "feat: alarm ringtoneUri + snoozeMinutes (DB v3)"
```

---

### Task 6: VolumeRamp pure helper

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/alarm/VolumeRamp.kt`
- Test: `app/src/test/java/com/cyprienbrisset/myportal/alarm/VolumeRampTest.kt`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/cyprienbrisset/myportal/alarm/VolumeRampTest.kt`:
```kotlin
package com.cyprienbrisset.myportal.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeRampTest {
    @Test fun startsLowReachesMaxAtEnd() {
        // 10 steps to max=10 over the ramp
        assertEquals(1, volumeAtStep(step = 0, totalSteps = 10, maxVolume = 10))
        assertEquals(10, volumeAtStep(step = 9, totalSteps = 10, maxVolume = 10))
        assertEquals(10, volumeAtStep(step = 20, totalSteps = 10, maxVolume = 10)) // clamps
    }
    @Test fun monotonicNonDecreasing() {
        var prev = 0
        for (s in 0..9) {
            val v = volumeAtStep(s, 10, 10)
            assert(v >= prev)
            prev = v
        }
    }
    @Test fun neverZeroSoAlarmIsAudible() {
        assertEquals(1, volumeAtStep(step = 0, totalSteps = 30, maxVolume = 7))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=... ./gradlew :app:testDebugUnitTest --tests "*VolumeRampTest*"`
Expected: FAIL.

- [ ] **Step 3: Implement VolumeRamp.kt**

```kotlin
package com.cyprienbrisset.myportal.alarm

import kotlin.math.max
import kotlin.math.min

/**
 * Volume at a given ramp [step] (0-based) out of [totalSteps], climbing to [maxVolume].
 * Always at least 1 so the alarm is immediately audible; clamps at maxVolume.
 */
fun volumeAtStep(step: Int, totalSteps: Int, maxVolume: Int): Int {
    if (maxVolume <= 0) return 0
    if (totalSteps <= 1) return maxVolume
    val progress = (step + 1).toDouble() / totalSteps
    val v = Math.round(progress * maxVolume).toInt()
    return min(maxVolume, max(1, v))
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=... ./gradlew :app:testDebugUnitTest --tests "*VolumeRampTest*"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/alarm/VolumeRamp.kt app/src/test/java/com/cyprienbrisset/myportal/alarm/VolumeRampTest.kt
git commit -m "feat: tested volume-ramp helper for alarm"
```

---

### Task 7: AlarmNotifications (channel + full-screen-intent)

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/alarm/AlarmNotifications.kt`

- [ ] **Step 1: Implement the notification helper**

```kotlin
package com.cyprienbrisset.myportal.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object AlarmNotifications {
    const val CHANNEL_ID = "alarm"
    const val NOTIF_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Réveil", NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Alarmes de MyPortal"
                setBypassDnd(true)
                setSound(null, null) // sound handled by the foreground service
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** Full-screen-intent notification that opens the ring screen. */
    fun buildRinging(context: Context, alarmId: Long, label: String): Notification {
        ensureChannel(context)
        val fullScreen = PendingIntent.getActivity(
            context, alarmId.toInt(),
            Intent(context, AlarmRingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (label.isBlank()) "Réveil" else label)
            .setContentText("Appuyez pour ouvrir")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .build()
    }
}
```
Note: `androidx.core:core-ktx` (already a dependency) provides `NotificationCompat` via `androidx.core.app`. If `androidx.core.app.NotificationCompat` is unresolved, add `implementation(libs.androidx.core.ktx)` — it is already present, so it should resolve.

- [ ] **Step 2: Verify compile + commit**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/alarm/AlarmNotifications.kt
git commit -m "feat: alarm notification channel + full-screen-intent"
```

---

### Task 8: AlarmForegroundService (sound + volume ramp + wakelock)

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/alarm/AlarmForegroundService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Implement the service**

```kotlin
package com.cyprienbrisset.myportal.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

class AlarmForegroundService : Service() {
    private var ringtone: Ringtone? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var rampStep = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopEverything(); return START_NOT_STICKY }
            ACTION_SNOOZE -> {
                val id = intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1)
                val minutes = intent.getIntExtra(EXTRA_SNOOZE_MIN, 10)
                if (id >= 0) AlarmSnooze.schedule(this, id, minutes)
                stopEverything(); return START_NOT_STICKY
            }
        }
        val alarmId = intent?.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1) ?: -1
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: ""
        val ringUri = intent?.getStringExtra(EXTRA_RINGTONE)

        startForeground(AlarmNotifications.NOTIF_ID, AlarmNotifications.buildRinging(this, alarmId, label))
        acquireWakeLock()
        startRinging(ringUri)
        return START_STICKY
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "myportal:alarm",
        ).also { it.acquire(10 * 60 * 1000L) }
    }

    private fun startRinging(ringUri: String?) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        am.setStreamVolume(AudioManager.STREAM_ALARM, volumeAtStep(0, RAMP_STEPS, maxVol), 0)

        val uri: Uri = ringUri?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(applicationContext, uri).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
            @Suppress("DEPRECATION")
            streamType = AudioManager.STREAM_ALARM
            play()
        }
        scheduleRamp(am, maxVol)
    }

    private fun scheduleRamp(am: AudioManager, maxVol: Int) {
        val tick = object : Runnable {
            override fun run() {
                rampStep++
                am.setStreamVolume(AudioManager.STREAM_ALARM, volumeAtStep(rampStep, RAMP_STEPS, maxVol), 0)
                if (rampStep < RAMP_STEPS) handler.postDelayed(this, RAMP_INTERVAL_MS)
            }
        }
        handler.postDelayed(tick, RAMP_INTERVAL_MS)
    }

    private fun stopEverything() {
        handler.removeCallbacksAndMessages(null)
        ringtone?.stop(); ringtone = null
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() { stopEverything(); super.onDestroy() }

    companion object {
        const val ACTION_STOP = "com.cyprienbrisset.myportal.ALARM_STOP"
        const val ACTION_SNOOZE = "com.cyprienbrisset.myportal.ALARM_SNOOZE"
        const val EXTRA_LABEL = "label"
        const val EXTRA_RINGTONE = "ringtone"
        const val EXTRA_SNOOZE_MIN = "snooze_min"
        const val RAMP_STEPS = 30
        const val RAMP_INTERVAL_MS = 1000L

        fun start(context: Context, alarmId: Long, label: String, ringtoneUri: String?) {
            val i = Intent(context, AlarmForegroundService::class.java)
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_RINGTONE, ringtoneUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }
        fun stop(context: Context) {
            context.startService(Intent(context, AlarmForegroundService::class.java).apply { action = ACTION_STOP })
        }
        fun snooze(context: Context, alarmId: Long, minutes: Int) {
            context.startService(Intent(context, AlarmForegroundService::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_SNOOZE_MIN, minutes)
            })
        }
    }
}
```

- [ ] **Step 2: Register service + permissions in the manifest**

Add permissions (children of `<manifest>`):
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
```
(`WAKE_LOCK`, `USE_FULL_SCREEN_INTENT`, `POST_NOTIFICATIONS` already declared in v1.)
Inside `<application>`:
```xml
<service
    android:name=".alarm.AlarmForegroundService"
    android:exported="false"
    android:foregroundServiceType="mediaPlayback" />
```

- [ ] **Step 3: Verify compile + commit**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/alarm/AlarmForegroundService.kt app/src/main/AndroidManifest.xml
git commit -m "feat: AlarmForegroundService with looping sound, volume ramp, wakelock"
```

---

### Task 9: Rewire AlarmReceiver + AlarmRingActivity to the service

**Files:**
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/alarm/AlarmReceiver.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/alarm/AlarmRingActivity.kt`

- [ ] **Step 1: AlarmReceiver starts the service (instead of the activity directly)**

Replace the `context.startActivity(...)` block with a service start; keep the goAsync reschedule. The service posts the full-screen-intent notification (which opens the ring activity). Read the alarm to pass label/ringtone:
```kotlin
override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != ACTION_FIRE) return
    val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
    if (alarmId < 0) return

    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val repo = AlarmRepository(AppDatabase.get(context).alarmDao())
            val scheduler = AlarmScheduler(context)
            val alarm = repo.byId(alarmId)
            // Start the ringing service (sound + full-screen-intent notification).
            AlarmForegroundService.start(
                context, alarmId,
                alarm?.label ?: "",
                alarm?.ringtoneUri,
            )
            if (alarm != null) {
                if (alarm.repeatDays == 0) repo.setEnabled(alarm, false)
                else scheduler.schedule(alarm)
            }
        } finally {
            pending.finish()
        }
    }
}
```
Keep the existing imports plus `AlarmForegroundService`.

- [ ] **Step 2: AlarmRingActivity becomes UI-only, commands the service**

Remove the ringtone playback from the activity (the service owns sound now). Keep the over-lockscreen flags and Compose UI. The buttons call the service:
- Load the alarm's `snoozeMinutes` (pass it in via the fullScreenIntent extra, OR read from DB). Simplest: read snoozeMinutes from DB on a background thread and hold in state (default 10 until loaded).
- **Arrêter** → `AlarmForegroundService.stop(this); finish()`.
- **Snooze** → `AlarmForegroundService.snooze(this, alarmId, snoozeMinutes); finish()`.
Concretely: delete `startRinging()`, `ringtone`, `stopAndFinish()`'s ringtone bits, and `snooze()`'s `AlarmSnooze` call; replace with service calls. Keep `showOverLockScreen()`. Read snooze minutes:
```kotlin
private var snoozeMinutes by mutableStateOf(10)
// in onCreate, after alarmId:
if (alarmId >= 0) {
    lifecycleScope.launch(Dispatchers.IO) {
        val a = AlarmRepository(AppDatabase.get(this@AlarmRingActivity).alarmDao()).byId(alarmId)
        if (a != null) snoozeMinutes = a.snoozeMinutes
    }
}
```
Button handlers:
```kotlin
Button(onClick = { AlarmForegroundService.stop(this@AlarmRingActivity); finish() }) { Text("Arrêter") }
OutlinedButton(onClick = { if (alarmId >= 0) AlarmForegroundService.snooze(this@AlarmRingActivity, alarmId, snoozeMinutes); finish() }) {
    Text("Snooze $snoozeMinutes min")
}
```
Add imports: `androidx.compose.runtime.getValue/setValue/mutableStateOf`, `androidx.lifecycle.lifecycleScope`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.launch`, `AlarmRepository`, `AppDatabase`. Keep the `Surface` background wrapper added earlier.

- [ ] **Step 3: Verify compile + commit**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/alarm/AlarmReceiver.kt app/src/main/java/com/cyprienbrisset/myportal/alarm/AlarmRingActivity.kt
git commit -m "feat: route alarm ringing through the foreground service"
```

---

### Task 10: Request POST_NOTIFICATIONS at runtime (33+)

**Files:**
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/MainActivity.kt`

- [ ] **Step 1: Ask for notification permission on launch (no-op < 33)**

In `MainActivity.onCreate`, register and launch a permission request guarded by SDK:
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}
```
Register the launcher as a field/before setContent (registerForActivityResult must be called before the activity is STARTED — declare it as a property initialized in onCreate before super? Use the property pattern: `private val notifPermission = registerForActivityResult(...)` at class level, then `.launch(...)` in onCreate). Add imports `androidx.activity.result.contract.ActivityResultContracts`, `android.os.Build`, `android.content.pm.PackageManager`.

- [ ] **Step 2: Verify compile + commit**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/MainActivity.kt
git commit -m "feat: request POST_NOTIFICATIONS on Android 13+"
```

**PHASE 2 END — On-device verify (Portal + emulator):** set an alarm ~1 min out; confirm it rings via the service with the screen OFF and ON, volume ramps up, the full-screen ring UI appears, Arrêter stops sound, Snooze reschedules. On the Portal (API 29) no runtime permission prompts appear (expected).

---

## PHASE 3 — Per-alarm ringtone + snooze UI

### Task 11: Ringtone picker + snooze duration in alarm editor

**Files:**
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/alarms/AlarmsViewModel.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/alarms/AlarmsScreen.kt`

- [ ] **Step 1: Extend the ViewModel save signature**

Change `save(...)` to accept ringtone + snooze and persist them:
```kotlin
fun save(hour: Int, minute: Int, repeatDays: Int, label: String, ringtoneUri: String?, snoozeMinutes: Int, id: Long = 0) = viewModelScope.launch {
    val newId = repo.upsert(AlarmEntity(
        id = id, hour = hour, minute = minute, repeatDays = repeatDays, label = label,
        enabled = true, ringtoneUri = ringtoneUri, snoozeMinutes = snoozeMinutes,
    ))
    repo.byId(newId)?.let { scheduler.schedule(it) }
}
```

- [ ] **Step 2: Ringtone picker + snooze chips in the add dialog**

In `AlarmsScreen`, inside the add-alarm dialog:
- Add state: `var ringtoneUri by remember { mutableStateOf<String?>(null) }`, `var ringtoneName by remember { mutableStateOf("Par défaut") }`, `var snoozeMin by remember { mutableStateOf(10) }`.
- Register a launcher for the system ringtone picker:
```kotlin
val ctx = LocalContext.current
val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
    val uri = res.data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
    ringtoneUri = uri?.toString()
    ringtoneName = uri?.let { RingtoneManager.getRingtone(ctx, it)?.getTitle(ctx) } ?: "Par défaut"
}
```
- A row "Sonnerie : $ringtoneName" with a button that launches:
```kotlin
val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Sonnerie de l'alarme")
    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri?.let { android.net.Uri.parse(it) })
}
picker.launch(intent)
```
- Snooze duration as `FilterChip`s (5 / 10 / 15) setting `snoozeMin`.
- Update the confirm button call to `vm.save(state.hour, state.minute, days, "", ringtoneUri, snoozeMin)`.
Add imports: `androidx.activity.compose.rememberLauncherForActivityResult`, `androidx.activity.result.contract.ActivityResultContracts`, `android.content.Intent`, `android.media.RingtoneManager`, `androidx.compose.ui.platform.LocalContext`.

- [ ] **Step 3: Show snooze/ringtone in the alarm list row (optional supporting text)**

In each `ListItem`, extend `supportingContent` to include the snooze, e.g. `"$repeat · Snooze ${a.snoozeMinutes}m"`. Keep it concise.

- [ ] **Step 4: Verify compile + commit**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/alarms
git commit -m "feat: per-alarm ringtone picker + snooze duration"
```

**PHASE 3 END — Full unit suite + on-device:** `JAVA_HOME=... ./gradlew :app:testDebugUnitTest` (all pass). On device: create an alarm with a custom ringtone + 5-min snooze; confirm it rings with that ringtone and snoozes 5 min.

---

## Self-Review (author checklist, completed)

**Spec coverage:**
- A — Tile icons (app icon + favicon + monogram) → Tasks 1–2 ✅
- B — Responsive banner+row (landscape/portrait) + unlock orientation → Tasks 3–4 ✅
- C — Robust alarm (foreground service, volume ramp, full-screen-intent notification, permissions) → Tasks 5–10 ✅
- D — Per-alarm ringtone + adjustable snooze → Tasks 5 (DB), 9 (snooze wiring), 11 (UI) ✅

**Type consistency:** `AlarmForegroundService.start/stop/snooze`, `AlarmNotifications.buildRinging/ensureChannel/NOTIF_ID`, `AlarmReceiver.EXTRA_ALARM_ID/ACTION_FIRE`, `volumeAtStep(step,totalSteps,maxVolume)`, `homeLayoutFor(widthDp,isLandscape)` + `HomeLayoutMode.minTileWidthDp`, `TileIcon(tile,size)`, `AlarmEntity(...,ringtoneUri,snoozeMinutes)`, `save(...,ringtoneUri,snoozeMinutes,id)` — consistent across tasks.

**Placeholder scan:** No TBD/TODO; every code step has concrete code. UI steps that describe edits (Tasks 3,9,11) reference exact symbols/signatures defined in earlier tasks.

**Ordering seams:** Task 2 notes the `HomeScreen` call-site update so the build stays green; Tasks 7–9 build the notification → service → rewire in dependency order; DB v3 (Task 5) precedes service/UI that read the new fields.

**Devices:** Portal+ 2nd gen = API 29 (no runtime notif/full-screen-intent prompts; FGS type ignored) and emulator API 35 (prompts apply) — both covered by SDK-guarded code.
