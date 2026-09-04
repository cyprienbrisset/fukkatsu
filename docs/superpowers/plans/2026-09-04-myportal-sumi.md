# MyPortal v3 — 墨 Sumi Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Re-skin the entire MyPortal UI in the "Sumi" (ink & vermilion) Japanese design language — new tokens, fonts, medallion icons, asymmetric home, touch-first dropdown-free screens — without changing the launcher/alarm/data logic.

**Architecture:** All work is in the presentation layer. New design tokens (`ui/theme`), a reusable component package (`ui/sumi`), and rewritten screen composables. Business logic (Room, alarm service, scheduler, WebView, weather repo) is untouched except the ringtone selector, which becomes an in-app tap-card list querying `RingtoneManager`.

**Tech Stack:** Jetpack Compose (Material 3 as substrate), bundled Google Fonts (Shippori Mincho, Zen Kaku Gothic New), existing Coil/Room/AlarmManager stack.

---

## Environment (unchanged from v1/v2 — critical)

- **No `java` on PATH.** Every Gradle call MUST prefix JAVA_HOME:
  `JAVA_HOME="/Users/cyprienbrisset/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew ...`
- Two devices connected: Portal+ 2nd gen `adb -s 2221B01C9C02NQ` (Android 10/API 29, 2160×1440) and emulator `emulator-5554` (API 35). `adb` = `/Users/cyprienbrisset/Library/Android/sdk/platform-tools/adb`. Install with explicit `-s <serial>`.
- Verify each task with `:app:assembleDebug`; JVM tests with `:app:testDebugUnitTest`. Visual checks at phase ends (screenshot on emulator + Portal).
- Package root `com.cyprienbrisset.myportal`. Do NOT stage `.claude/oxygen-status.json`.
- Work on branch `feat/sumi-design` (already created).

---

## File Structure

```
app/src/main/res/font/
  shippori_mincho_regular.ttf, shippori_mincho_medium.ttf      # NEW (downloaded)
  zen_kaku_gothic_new_regular.ttf, zen_kaku_gothic_new_medium.ttf  # NEW (downloaded)
ui/theme/
  Color.kt   # MOD — Sumi palette
  Type.kt    # MOD — Mincho/Gothic FontFamily + type scale
  Theme.kt   # MOD — map dark scheme to Sumi tokens
ui/sumi/                     # NEW component package
  HankoSeal.kt, VermilionRule.kt, SectionLabel.kt, WatermarkKanji.kt
  Medallion.kt
  SumiButton.kt              # SumiPrimaryButton
  SegmentedChoice.kt, SumiChoiceChip.kt, Stepper.kt
  JapaneseText.kt            # pure helpers: weekdayKanji(), stepHour(), stepMinute()
ui/home/
  AmbientBanner.kt   # MOD — hero, mincho, kanji, watermark
  MedallionGrid.kt   # NEW (replaces TileGrid usage on home)
  HomeScreen.kt      # MOD — asymmetric landscape / stacked portrait + hanko settings
ui/settings/
  SettingsScreen.kt, TileEditScreen.kt, WeatherSettingsScreen.kt  # MOD — Sumi + touch-first
ui/alarms/
  AlarmsScreen.kt        # MOD — list restyle
  AlarmEditScreen.kt     # NEW — full-screen touch editor (replaces AlertDialog)
  RingtonePicker.kt      # NEW — tap-card ringtone list (RingtoneManager)
alarm/AlarmRingActivity.kt   # MOD — Sumi ensō ring screen
ui/AppNav.kt          # MOD — route to AlarmEditScreen
app/src/test/.../ui/sumi/JapaneseTextTest.kt   # NEW
```

---

## PHASE 1 — Foundation (tokens, fonts, components)

### Task 1: Sumi colors + bundled fonts + type scale

**Files:**
- Create: `app/src/main/res/font/*.ttf` (4 files)
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/theme/Type.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/theme/Theme.kt`

- [ ] **Step 1: Download the fonts** (res/font names must be lowercase, no hyphens)

```bash
cd /Users/cyprienbrisset/AndroidStudioProjects/MyPortal
mkdir -p app/src/main/res/font
BASE="https://github.com/google/fonts/raw/main/ofl"
curl -fsSL "$BASE/shipporimincho/ShipporiMincho-Regular.ttf" -o app/src/main/res/font/shippori_mincho_regular.ttf
curl -fsSL "$BASE/shipporimincho/ShipporiMincho-Medium.ttf"  -o app/src/main/res/font/shippori_mincho_medium.ttf
curl -fsSL "$BASE/zenkakugothicnew/ZenKakuGothicNew-Regular.ttf" -o app/src/main/res/font/zen_kaku_gothic_new_regular.ttf
curl -fsSL "$BASE/zenkakugothicnew/ZenKakuGothicNew-Medium.ttf"  -o app/src/main/res/font/zen_kaku_gothic_new_medium.ttf
ls -l app/src/main/res/font/
```
Expected: 4 `.ttf` files, each > 1MB. If a URL 404s, find the correct path under `google/fonts/ofl/<family>/` (the family folder is lowercase, file is CamelCase-Weight.ttf) and adjust.

- [ ] **Step 2: Rewrite `Color.kt`**

```kotlin
package com.cyprienbrisset.myportal.ui.theme

import androidx.compose.ui.graphics.Color

val Sumi = Color(0xFF0D0E12)      // background 墨
val Ink2 = Color(0xFF15171C)
val SumiSurface = Color(0xFF191C23)
val SumiLine = Color(0xFF242832)
val Kinari = Color(0xFFECE7DD)    // primary text 生成
val SumiMuted = Color(0xFF9A9488)
val Shu = Color(0xFFC1272D)       // accent 朱
val OnShu = Color(0xFFF6EEE0)
```

- [ ] **Step 3: Rewrite `Type.kt`** (FontFamily + scale)

```kotlin
package com.cyprienbrisset.myportal.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.myportal.R

val Mincho = FontFamily(
    Font(R.font.shippori_mincho_regular, FontWeight.Normal),
    Font(R.font.shippori_mincho_medium, FontWeight.Medium),
)
val Gothic = FontFamily(
    Font(R.font.zen_kaku_gothic_new_regular, FontWeight.Normal),
    Font(R.font.zen_kaku_gothic_new_medium, FontWeight.Medium),
)

/** Material typography defaults to Gothic; Mincho is applied explicitly on display text. */
val PortalTypography = Typography(
    titleLarge = TextStyle(fontFamily = Mincho, fontWeight = FontWeight.Medium, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Gothic, fontWeight = FontWeight.Medium, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = Gothic, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Gothic, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = Gothic, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 3.sp),
    labelMedium = TextStyle(fontFamily = Gothic, fontSize = 11.sp, letterSpacing = 2.5.sp),
)
```

- [ ] **Step 4: Rewrite `Theme.kt`** to map the Sumi tokens

```kotlin
package com.cyprienbrisset.myportal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SumiColors = darkColorScheme(
    primary = Shu,
    onPrimary = OnShu,
    background = Sumi,
    onBackground = Kinari,
    surface = SumiSurface,
    onSurface = Kinari,
    surfaceVariant = SumiSurface,
    onSurfaceVariant = SumiMuted,
    outline = SumiLine,
)

@Composable
fun MyPortalTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SumiColors, typography = PortalTypography, content = content)
}
```
Note: this drops the `darkTheme` param. If any caller passes `darkTheme = ...`, remove that argument at the call site (MainActivity/AlarmRingActivity call `MyPortalTheme { ... }` with no args — verify).

- [ ] **Step 5: Verify build + commit**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
```bash
git add app/src/main/res/font app/src/main/java/com/cyprienbrisset/myportal/ui/theme
git commit -m "feat(sumi): palette, bundled Mincho/Gothic fonts, type scale, theme mapping"
```

---

### Task 2: Pure Japanese helpers (weekday kanji + stepper math)

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/sumi/JapaneseText.kt`
- Test: `app/src/test/java/com/cyprienbrisset/myportal/ui/sumi/JapaneseTextTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cyprienbrisset.myportal.ui.sumi

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class JapaneseTextTest {
    @Test fun weekdayKanjiMapsAllDays() {
        assertEquals("月曜日", weekdayKanji(DayOfWeek.MONDAY))
        assertEquals("水曜日", weekdayKanji(DayOfWeek.WEDNESDAY))
        assertEquals("日曜日", weekdayKanji(DayOfWeek.SUNDAY))
    }
    @Test fun stepHourWraps() {
        assertEquals(0, stepHour(23, +1))
        assertEquals(23, stepHour(0, -1))
        assertEquals(8, stepHour(7, +1))
    }
    @Test fun stepMinuteWraps() {
        assertEquals(0, stepMinute(59, +1))
        assertEquals(59, stepMinute(0, -1))
        assertEquals(31, stepMinute(30, +1))
    }
}
```
Run `:app:testDebugUnitTest --tests "*JapaneseTextTest*"` → FAIL.

- [ ] **Step 2: Implement `JapaneseText.kt`**

```kotlin
package com.cyprienbrisset.myportal.ui.sumi

import java.time.DayOfWeek

fun weekdayKanji(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "月曜日"
    DayOfWeek.TUESDAY -> "火曜日"
    DayOfWeek.WEDNESDAY -> "水曜日"
    DayOfWeek.THURSDAY -> "木曜日"
    DayOfWeek.FRIDAY -> "金曜日"
    DayOfWeek.SATURDAY -> "土曜日"
    DayOfWeek.SUNDAY -> "日曜日"
}

fun stepHour(current: Int, delta: Int): Int = ((current + delta) % 24 + 24) % 24
fun stepMinute(current: Int, delta: Int): Int = ((current + delta) % 60 + 60) % 60
```
Run the test → PASS (3 tests).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/sumi/JapaneseText.kt app/src/test/java/com/cyprienbrisset/myportal/ui/sumi/JapaneseTextTest.kt
git commit -m "feat(sumi): tested weekday-kanji + stepper helpers"
```

---

### Task 3: Primitive components — HankoSeal, VermilionRule, SectionLabel, WatermarkKanji, SumiPrimaryButton

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/sumi/HankoSeal.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/sumi/VermilionRule.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/sumi/SectionLabel.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/sumi/WatermarkKanji.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/sumi/SumiButton.kt`

- [ ] **Step 1: HankoSeal.kt**

```kotlin
package com.cyprienbrisset.myportal.ui.sumi

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.myportal.ui.theme.OnShu
import com.cyprienbrisset.myportal.ui.theme.Shu

@Composable
fun HankoSeal(
    char: String,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    onClick: (() -> Unit)? = null,
) {
    val base = Modifier
        .size(size)
        .clip(RoundedCornerShape(size / 5))
        .background(Shu)
        .let { if (onClick != null) it.clickable { onClick() } else it }
    Box(modifier.then(base), contentAlignment = Alignment.Center) {
        Text(char, color = OnShu, fontWeight = FontWeight.Bold, fontSize = (size.value / 2.1f).sp)
    }
}
```

- [ ] **Step 2: VermilionRule.kt**

```kotlin
package com.cyprienbrisset.myportal.ui.sumi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyprienbrisset.myportal.ui.theme.Shu

@Composable
fun HorizontalVermilionRule(modifier: Modifier = Modifier, length: Dp = 120.dp, thickness: Dp = 1.dp) {
    Box(modifier.width(length).height(thickness)
        .background(Brush.horizontalGradient(listOf(Shu, Color.Transparent))))
}

@Composable
fun VerticalVermilionRule(modifier: Modifier = Modifier, length: Dp = 120.dp, thickness: Dp = 1.dp) {
    Box(modifier.width(thickness).height(length)
        .background(Brush.verticalGradient(listOf(Color.Transparent, Shu, Color.Transparent))))
}
```

- [ ] **Step 3: SectionLabel.kt**

```kotlin
package com.cyprienbrisset.myportal.ui.sumi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cyprienbrisset.myportal.ui.theme.SumiLine
import com.cyprienbrisset.myportal.ui.theme.SumiMuted

@Composable
fun SectionLabel(kana: String, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("$kana  $text", style = MaterialTheme.typography.labelLarge, color = SumiMuted)
        Spacer(Modifier.padding(horizontal = 6.dp))
        androidx.compose.foundation.layout.Box(Modifier.weight(1f).height(1.dp).background(SumiLine))
    }
}
```

- [ ] **Step 4: WatermarkKanji.kt**

```kotlin
package com.cyprienbrisset.myportal.ui.sumi

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.myportal.ui.theme.Kinari
import com.cyprienbrisset.myportal.ui.theme.Mincho

@Composable
fun WatermarkKanji(char: String, modifier: Modifier = Modifier, size: TextUnit = 320.sp) {
    Text(char, modifier = modifier.alpha(0.05f), color = Kinari,
        fontFamily = Mincho, fontWeight = FontWeight.Bold, fontSize = size)
}
```

- [ ] **Step 5: SumiButton.kt**

```kotlin
package com.cyprienbrisset.myportal.ui.sumi

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.myportal.ui.theme.Mincho
import com.cyprienbrisset.myportal.ui.theme.OnShu
import com.cyprienbrisset.myportal.ui.theme.Shu

@Composable
fun SumiPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(16.dp))
            .background(Shu).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = OnShu, fontFamily = Mincho, fontSize = 18.sp)
    }
}
```

- [ ] **Step 6: Verify build + commit**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/sumi
git commit -m "feat(sumi): hanko seal, vermilion rules, section label, watermark, primary button"
```

---

### Task 4: Medallion + interactive components (SegmentedChoice, SumiChoiceChip, Stepper)

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/sumi/Medallion.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/sumi/SegmentedChoice.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/sumi/SumiChoiceChip.kt`
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/sumi/Stepper.kt`

- [ ] **Step 1: Medallion.kt** (circular disc + optional focus ring; content slot)

```kotlin
package com.cyprienbrisset.myportal.ui.sumi

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import com.cyprienbrisset.myportal.ui.theme.Kinari
import com.cyprienbrisset.myportal.ui.theme.Shu
import com.cyprienbrisset.myportal.ui.theme.SumiLine
import com.cyprienbrisset.myportal.ui.theme.SumiSurface

@Composable
fun Medallion(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    discSize: Dp = 72.dp,
    focused: Boolean = false,
    dashed: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        val disc = Modifier
            .size(discSize)
            .clip(CircleShape)
            .background(if (dashed) androidx.compose.ui.graphics.Color.Transparent else SumiSurface)
            .border(
                BorderStroke(if (focused) 2.dp else 1.dp, if (focused) Shu else SumiLine),
                CircleShape,
            )
        Box(disc, contentAlignment = Alignment.Center) { content() }
        Spacer(Modifier.height(12.dp))
        Text(
            label, style = MaterialTheme.typography.titleMedium, color = Kinari,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}
```

- [ ] **Step 2: SegmentedChoice.kt** (2+ big buttons, single select)

```kotlin
package com.cyprienbrisset.myportal.ui.sumi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import com.cyprienbrisset.myportal.ui.theme.*

data class Segment(val kana: String, val text: String)

@Composable
fun SegmentedChoice(options: List<Segment>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        options.forEachIndexed { i, seg ->
            val on = i == selectedIndex
            Column(
                Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (on) Shu else SumiSurface)
                    .border(BorderStroke(1.dp, if (on) Shu else SumiLine), RoundedCornerShape(14.dp))
                    .clickable { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(seg.kana, color = if (on) OnShu else SumiMuted, fontSize = 11.sp, letterSpacing = 2.sp)
                Text(seg.text, color = if (on) OnShu else Kinari, fontWeight = FontWeight.Medium, fontSize = 17.sp)
            }
        }
    }
}
```

- [ ] **Step 3: SumiChoiceChip.kt** (round day toggle + rect pill; shared visual)

```kotlin
package com.cyprienbrisset.myportal.ui.sumi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import com.cyprienbrisset.myportal.ui.theme.*

@Composable
fun SumiChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    circle: Boolean = false,
) {
    val shape: Shape = if (circle) CircleShape else RoundedCornerShape(14.dp)
    Box(
        modifier.height(if (circle) 52.dp else 56.dp).clip(shape)
            .background(if (selected) Shu else SumiSurface)
            .border(BorderStroke(1.dp, if (selected) Shu else SumiLine), shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) OnShu else Kinari, fontSize = if (circle) 15.sp else 16.sp)
    }
}
```

- [ ] **Step 4: Stepper.kt** (▲ / big value / ▼, uses stepHour/stepMinute)

```kotlin
package com.cyprienbrisset.myportal.ui.sumi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import com.cyprienbrisset.myportal.ui.theme.*

@Composable
fun Stepper(value: Int, onUp: () -> Unit, onDown: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Chevron("▲", onUp)
        Text("%02d".format(value), fontFamily = Mincho, color = Kinari, fontSize = 68.sp,
            textAlign = TextAlign.Center, modifier = Modifier.width(104.dp))
        Chevron("▼", onDown)
    }
}

@Composable
private fun Chevron(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier.width(72.dp).height(44.dp).clip(RoundedCornerShape(12.dp))
            .background(SumiSurface).border(BorderStroke(1.dp, SumiLine), RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = SumiMuted, fontSize = 18.sp) }
}
```

- [ ] **Step 5: Verify build + commit**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/sumi
git commit -m "feat(sumi): medallion, segmented choice, choice chip, stepper"
```

**PHASE 1 END:** foundation compiles. No visual change yet (components unused).

---

## PHASE 2 — Home

### Task 5: AmbientBanner (Sumi hero)

**Files:**
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/AmbientBanner.kt`

- [ ] **Step 1: Rewrite AmbientBanner** — hero composition, Mincho clock, FR date + weekday kanji, weather + next alarm line. Signature stays `AmbientBanner(now, weather, modifier, nextAlarm, portrait)`.

```kotlin
package com.cyprienbrisset.myportal.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.myportal.data.weather.Weather
import com.cyprienbrisset.myportal.ui.sumi.weekdayKanji
import com.cyprienbrisset.myportal.ui.theme.Kinari
import com.cyprienbrisset.myportal.ui.theme.Mincho
import com.cyprienbrisset.myportal.ui.theme.Shu
import com.cyprienbrisset.myportal.ui.theme.SumiMuted
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AmbientBanner(
    now: LocalDateTime,
    weather: Weather?,
    modifier: Modifier = Modifier,
    nextAlarm: LocalDateTime? = null,
    portrait: Boolean = false,
) {
    val time = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH))
    val kanji = weekdayKanji(now.dayOfWeek)
    val align = if (portrait) Alignment.CenterHorizontally else Alignment.Start

    Column(modifier, horizontalAlignment = align) {
        Text("M Y P O R T A L", color = SumiMuted, fontSize = 11.sp, letterSpacing = 5.sp)
        Spacer(Modifier.height(if (portrait) 12.dp else 18.dp))
        Text(time, fontFamily = Mincho, fontWeight = FontWeight.Normal,
            color = Kinari, fontSize = if (portrait) 72.sp else 92.sp)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(kanji, color = Shu, fontFamily = Mincho, fontSize = 16.sp)
            Text("  ·  $date", color = Kinari, fontSize = 16.sp)
        }
        Spacer(Modifier.height(6.dp))
        val wx = buildString {
            if (weather != null) append("${weather.temperatureC}°  ${weather.description}")
            if (nextAlarm != null) {
                if (isNotEmpty()) append("   ·   ")
                append("⏰ " + nextAlarm.format(DateTimeFormatter.ofPattern("EEE HH:mm", Locale.FRENCH)))
            }
        }
        if (wx.isNotEmpty()) Text(wx, color = SumiMuted, fontSize = 14.sp,
            textAlign = if (portrait) TextAlign.Center else TextAlign.Start)
    }
}
```

- [ ] **Step 2: Verify build + commit**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/home/AmbientBanner.kt
git commit -m "feat(sumi): ambient banner hero (mincho clock, weekday kanji)"
```

---

### Task 6: MedallionGrid + HomeScreen asymmetric layout

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/MedallionGrid.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/home/HomeScreen.kt`
- Delete usage of old `TileGrid.kt` on home (leave the file; it's unused now).

- [ ] **Step 1: MedallionGrid.kt** — reuses `TileIcon` inside a `Medallion`

```kotlin
package com.cyprienbrisset.myportal.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyprienbrisset.myportal.data.tile.TileEntity
import com.cyprienbrisset.myportal.ui.sumi.Medallion
import com.cyprienbrisset.myportal.ui.theme.SumiMuted

@Composable
fun MedallionGrid(
    tiles: List<TileEntity>,
    minCellWidth: Dp,
    onTileClick: (TileEntity) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minCellWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(tiles, key = { it.id }) { tile ->
            Medallion(label = tile.label, onClick = { onTileClick(tile) }) {
                TileIcon(tile = tile, size = 46.dp)
            }
        }
        item(key = "__add__") {
            Medallion(label = "Ajouter", onClick = onAddClick, dashed = true) {
                Text("＋", color = SumiMuted, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
```

- [ ] **Step 2: Rewrite HomeScreen** — asymmetric landscape (hero column + app zone) / stacked portrait; hanko settings seal top-right; watermark. Keep existing launch logic and VM flows.

```kotlin
package com.cyprienbrisset.myportal.ui.home

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.myportal.data.tile.TileEntity
import com.cyprienbrisset.myportal.data.tile.TileType
import com.cyprienbrisset.myportal.launch.LaunchIntentResolver
import com.cyprienbrisset.myportal.ui.sumi.HankoSeal
import com.cyprienbrisset.myportal.ui.sumi.SectionLabel
import com.cyprienbrisset.myportal.ui.sumi.VerticalVermilionRule
import com.cyprienbrisset.myportal.ui.sumi.WatermarkKanji
import com.cyprienbrisset.myportal.web.WebAppActivity

@Composable
fun HomeScreen(onOpenSettings: () -> Unit, onAddTile: () -> Unit, vm: HomeViewModel = viewModel()) {
    val ctx = LocalContext.current
    val tiles by vm.tiles.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val nextAlarm by vm.nextAlarm.collectAsStateWithLifecycle()

    val launch: (TileEntity) -> Unit = { tile ->
        when (tile.type) {
            TileType.APP -> {
                val pkg = tile.packageName
                if (pkg == null || !LaunchIntentResolver.launch(ctx, pkg))
                    Toast.makeText(ctx, "App introuvable : ${tile.label}", Toast.LENGTH_SHORT).show()
            }
            TileType.WEB -> ctx.startActivity(
                Intent(ctx, WebAppActivity::class.java).putExtra(WebAppActivity.EXTRA_URL, tile.url)
            )
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        WatermarkKanji("墨", Modifier.align(Alignment.BottomEnd).offset(x = 40.dp, y = 60.dp))
        HankoSeal("朱", Modifier.align(Alignment.TopEnd).padding(26.dp), onClick = onOpenSettings)

        if (landscape) {
            Row(Modifier.fillMaxSize().padding(start = 46.dp, top = 44.dp, bottom = 40.dp, end = 90.dp)) {
                Box(Modifier.fillMaxHeight().weight(0.38f), contentAlignment = Alignment.CenterStart) {
                    AmbientBanner(now, weather, nextAlarm = nextAlarm, portrait = false)
                }
                VerticalVermilionRule(Modifier.align(Alignment.CenterVertically).padding(horizontal = 8.dp), length = 220.dp)
                Column(Modifier.fillMaxHeight().weight(0.62f).padding(start = 30.dp), verticalArrangement = Arrangement.Center) {
                    SectionLabel("アプリ", "MES APPS")
                    Spacer(Modifier.height(22.dp))
                    MedallionGrid(tiles, minCellWidth = 108.dp, onTileClick = launch, onAddClick = onAddTile)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(20.dp))
                AmbientBanner(now, weather, nextAlarm = nextAlarm, portrait = true)
                Spacer(Modifier.height(28.dp))
                SectionLabel("アプリ", "MES APPS")
                Spacer(Modifier.height(18.dp))
                MedallionGrid(tiles, minCellWidth = 104.dp, onTileClick = launch, onAddClick = onAddTile, modifier = Modifier.weight(1f))
            }
        }
    }
}
```

- [ ] **Step 3: `AppNav.kt`** — HOME already passes `onOpenSettings` + `onAddTile` (from v2). Confirm it compiles with the new `HomeScreen` signature (same params). No change expected; if the old call omitted `onAddTile`, add it.

- [ ] **Step 4: Build + emulator + Portal visual check**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug`
Install: `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk` and same for `2221B01C9C02NQ`.
Expected: Sumi home — hero clock (Mincho), weekday kanji in vermilion, vertical rule, medallion tiles with focus-capable rings, hanko settings seal top-right, watermark 墨. Rotate emulator to verify portrait stacked layout.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/home app/src/main/java/com/cyprienbrisset/myportal/ui/AppNav.kt
git commit -m "feat(sumi): asymmetric home (hero + medallion grid), hanko settings"
```

---

## PHASE 3 — Settings, Tiles, Weather

### Task 7: SettingsScreen (Sumi, big tappable rows)

**Files:**
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Rewrite SettingsScreen** — hanko header, big rows (≥64 dp) kana+FR + vermilion chevron.

```kotlin
package com.cyprienbrisset.myportal.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.myportal.ui.sumi.HankoSeal
import com.cyprienbrisset.myportal.ui.theme.*

@Composable
fun SettingsScreen(onBack: () -> Unit, onTiles: () -> Unit, onAlarms: () -> Unit, onWeather: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            HankoSeal("朱", size = 40.dp, onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Text("設定 · Réglages", fontFamily = Mincho, color = Kinari, fontSize = 22.sp)
        }
        SettingRow("アプリ", "Tuiles", null, onTiles)
        SettingRow("目覚まし", "Alarmes", null, onAlarms)
        SettingRow("天気", "Ville météo", null, onWeather)
    }
}

@Composable
private fun SettingRow(kana: String, text: String, value: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(kana, color = SumiMuted, fontSize = 11.sp, letterSpacing = 2.sp)
            Text(text, color = Kinari, fontSize = 17.sp)
        }
        if (value != null) Text(value, color = SumiMuted, fontSize = 14.sp)
        Spacer(Modifier.width(10.dp))
        Text("›", color = Shu, fontSize = 22.sp)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(SumiLine))
}
```
(Import `androidx.compose.foundation.background` for the divider.)

- [ ] **Step 2: Build + commit**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/settings/SettingsScreen.kt
git commit -m "feat(sumi): settings screen with hanko header + big rows"
```

---

### Task 8: TileEditScreen (segmented + medallion grid tap-to-add + web form)

**Files:**
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/settings/TileEditScreen.kt`

- [ ] **Step 1: Rewrite TileEditScreen** — no AlertDialog; `SegmentedChoice` App|Web; App = medallion grid of installed apps (tap adds); Web = big name+URL fields + SumiPrimaryButton. Existing tiles listed as medallions with a tap-to-delete affordance (long-press deletes with a confirm row is overkill — use a small delete on each). Keep the ViewModel API (`tiles`, `installedApps()`, `addApp`, `addWeb`, `delete`, `moveUp`, `moveDown`).

```kotlin
package com.cyprienbrisset.myportal.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.myportal.launch.LaunchIntentResolver
import com.cyprienbrisset.myportal.ui.sumi.*
import com.cyprienbrisset.myportal.ui.theme.*

@Composable
fun TileEditScreen(onBack: () -> Unit, vm: TileEditViewModel = viewModel()) {
    val tiles by vm.tiles.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(0) } // 0=App, 1=Web
    var apps by remember { mutableStateOf<List<LaunchIntentResolver.InstalledApp>>(emptyList()) }
    var webLabel by remember { mutableStateOf("") }
    var webUrl by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = vm.installedApps() }

    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            HankoSeal("＋", size = 40.dp, onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Text("タイル · Tuiles", fontFamily = Mincho, color = Kinari, fontSize = 22.sp)
        }

        SegmentedChoice(
            listOf(Segment("アプリ", "Application"), Segment("ウェブ", "Web")),
            selectedIndex = mode, onSelect = { mode = it },
        )
        Spacer(Modifier.height(22.dp))

        if (mode == 0) {
            SectionLabel("追加", "TOUCHEZ POUR AJOUTER")
            Spacer(Modifier.height(16.dp))
            LazyVerticalGrid(columns = GridCells.Adaptive(100.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.weight(1f)) {
                items(apps, key = { it.packageName }) { a ->
                    Medallion(label = a.label, onClick = { vm.addApp(a.label, a.packageName) }) {
                        Text(a.label.trim().take(1).uppercase(), color = Kinari, fontFamily = Mincho, fontSize = 24.sp)
                    }
                }
            }
        } else {
            OutlinedTextField(webLabel, { webLabel = it }, label = { Text("Nom") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(webUrl, { webUrl = it }, label = { Text("URL (ex. jellyfin.local)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            SumiPrimaryButton("保存 · Ajouter", onClick = {
                if (webLabel.isNotBlank() && webUrl.isNotBlank()) { vm.addWeb(webLabel, webUrl); webLabel = ""; webUrl = "" }
            })
            Spacer(Modifier.weight(1f))
        }
    }
}
```
Note: the app medallion here uses the app's first letter as a lightweight glyph (the full icon load is on Home; the picker stays light). Existing-tile management (reorder/delete) can remain reachable — if you want delete here, add a second `SectionLabel("現在","VOS TUILES")` + a medallion row of `tiles` with an on-click that calls `vm.delete`. Keep this task focused on add; a follow-up can add delete UI. Since v2 had reorder/delete, DO include a compact existing-tiles strip with tap-to-delete to avoid regressing functionality: render `tiles` as medallions labeled with a small "✕ supprimer" hint, calling `vm.delete(tile)` on click. Implement that strip below the add area (both modes) so delete is never lost.

- [ ] **Step 2: Build + commit**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/settings/TileEditScreen.kt
git commit -m "feat(sumi): touch-first tile editor (segmented + medallion grid, no dialog)"
```

---

### Task 9: WeatherSettingsScreen (search + big city cards)

**Files:**
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/settings/WeatherSettingsScreen.kt`

- [ ] **Step 1: Rewrite** — search field + big tappable city cards (already card-like; restyle to Sumi rows ≥64 dp). Keep `WeatherSettingsViewModel` API (`results`, `search`, `select`).

```kotlin
package com.cyprienbrisset.myportal.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.myportal.ui.sumi.HankoSeal
import com.cyprienbrisset.myportal.ui.theme.*

@Composable
fun WeatherSettingsScreen(onBack: () -> Unit, vm: WeatherSettingsViewModel = viewModel()) {
    var query by remember { mutableStateOf("") }
    val results by vm.results.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            HankoSeal("天", size = 40.dp, onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Text("天気 · Ville météo", fontFamily = Mincho, color = Kinari, fontSize = 22.sp)
        }
        OutlinedTextField(query, { query = it; vm.search(it) }, label = { Text("Rechercher une ville") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(results) { r ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(14.dp))
                        .background(SumiSurface).clickable { vm.select(r); onBack() }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text(r.label, color = Kinari, fontSize = 17.sp) }
            }
        }
    }
}
```

- [ ] **Step 2: Build + Phase 3 visual check + commit**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL. Install on emulator + Portal; verify Réglages → Tuiles (segmented + grid), Ville météo (search + cards) render in Sumi, all touch-first.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/settings/WeatherSettingsScreen.kt
git commit -m "feat(sumi): weather city screen (search + tappable cards)"
```

---

## PHASE 4 — Alarms

### Task 10: AlarmEditScreen (full-screen touch editor) + list restyle + route

**Files:**
- Create: `app/src/main/java/com/cyprienbrisset/myportal/ui/alarms/AlarmEditScreen.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/alarms/AlarmsScreen.kt`
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/ui/AppNav.kt`

- [ ] **Step 1: AlarmEditScreen.kt** — Stepper time, 7 day circles, snooze pills, ringtone placeholder (Task 11 fills it), save. Uses `AlarmsViewModel.save(hour, minute, repeatDays, label, ringtoneUri, snoozeMinutes)`.

```kotlin
package com.cyprienbrisset.myportal.ui.alarms

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.myportal.ui.sumi.*
import com.cyprienbrisset.myportal.ui.theme.*

@Composable
fun AlarmEditScreen(onDone: () -> Unit, vm: AlarmsViewModel = viewModel()) {
    var hour by remember { mutableStateOf(7) }
    var minute by remember { mutableStateOf(0) }
    var days by remember { mutableStateOf(0) }
    var snooze by remember { mutableStateOf(10) }
    var ringtoneUri by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp).padding(top = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HankoSeal("鈴", size = 40.dp, onClick = onDone)
            Spacer(Modifier.width(14.dp))
            Text("目覚まし · Nouvelle alarme", fontFamily = Mincho, color = Kinari, fontSize = 22.sp)
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Stepper(hour, onUp = { hour = stepHour(hour, +1) }, onDown = { hour = stepHour(hour, -1) })
            Text(":", color = SumiMuted, fontFamily = Mincho, fontSize = 60.sp, modifier = Modifier.padding(horizontal = 8.dp))
            Stepper(minute, onUp = { minute = stepMinute(minute, +1) }, onDown = { minute = stepMinute(minute, -1) })
        }
        Spacer(Modifier.height(28.dp))
        SectionLabel("繰り返し", "RÉPÉTER")
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("L","M","M","J","V","S","D").forEachIndexed { i, d ->
                SumiChoiceChip(d, selected = (days and (1 shl i)) != 0, circle = true,
                    onClick = { days = days xor (1 shl i) }, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(24.dp))
        SectionLabel("スヌーズ", "SNOOZE")
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(5,10,15).forEach { m ->
                SumiChoiceChip("$m min", selected = snooze == m, onClick = { snooze = m }, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(24.dp))
        RingtonePicker(selectedUri = ringtoneUri, onSelect = { ringtoneUri = it })  // Task 11
        Spacer(Modifier.weight(1f))
        SumiPrimaryButton("保存 · Enregistrer", onClick = {
            vm.save(hour, minute, days, "", ringtoneUri, snooze); onDone()
        })
        Spacer(Modifier.height(20.dp))
    }
}
```
Note: `RingtonePicker` is created in Task 11. For this task to compile standalone, create a minimal stub `RingtonePicker(selectedUri, onSelect)` that renders nothing (or a placeholder `SectionLabel`), then flesh it in Task 11. Recommended: create the stub now.

- [ ] **Step 2: Restyle AlarmsScreen** — Sumi list; the FAB becomes a hanko "＋" that navigates to the edit screen (via a new `onAdd` param). Remove the in-screen `AlertDialog`/TimePicker. Keep the toggle + delete.

```kotlin
package com.cyprienbrisset.myportal.ui.alarms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.myportal.ui.sumi.HankoSeal
import com.cyprienbrisset.myportal.ui.theme.*

@Composable
fun AlarmsScreen(onBack: () -> Unit, onAdd: () -> Unit, vm: AlarmsViewModel = viewModel()) {
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            HankoSeal("鈴", size = 40.dp, onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Text("目覚まし · Alarmes", fontFamily = Mincho, color = Kinari, fontSize = 22.sp)
            Spacer(Modifier.weight(1f))
            HankoSeal("＋", size = 44.dp, onClick = onAdd)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            items(alarms, key = { it.id }) { a ->
                Row(Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("%02d:%02d".format(a.hour, a.minute), fontFamily = Mincho, color = Kinari, fontSize = 30.sp)
                        Text((if (a.repeatDays == 0) "Une fois" else repeatLabel(a.repeatDays)) + " · Snooze ${a.snoozeMinutes}m",
                            color = SumiMuted, fontSize = 13.sp)
                    }
                    Switch(checked = a.enabled, onCheckedChange = { vm.toggle(a, it) })
                    Spacer(Modifier.width(8.dp))
                    Text("✕", color = Shu, fontSize = 20.sp, modifier = Modifier
                        .padding(8.dp)
                        .let { m -> m }
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(SumiLine))
            }
        }
    }
}

private fun repeatLabel(mask: Int): String {
    val l = listOf("Lun","Mar","Mer","Jeu","Ven","Sam","Dim")
    return l.filterIndexed { i, _ -> (mask and (1 shl i)) != 0 }.joinToString(" ")
}
```
Note: wire the ✕ delete with a clickable modifier: replace the last `Text("✕"...)` with a clickable calling `vm.delete(a)` — `Modifier.padding(8.dp).clickable { vm.delete(a) }` (add the `clickable` import). Keep `repeatLabel` private here (it was in the old file).

- [ ] **Step 3: Routes in `AppNav.kt`** — add `ALARM_EDIT` route; wire Alarms `onAdd` → navigate to it; edit screen `onDone` pops back.

```kotlin
// in Routes: const val ALARM_EDIT = "alarm_edit"
composable(Routes.ALARMS) {
    com.cyprienbrisset.myportal.ui.alarms.AlarmsScreen(
        onBack = { nav.popBackStack() },
        onAdd = { nav.navigate(Routes.ALARM_EDIT) },
    )
}
composable(Routes.ALARM_EDIT) {
    com.cyprienbrisset.myportal.ui.alarms.AlarmEditScreen(onDone = { nav.popBackStack() })
}
```

- [ ] **Step 4: Build + commit**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL (with the RingtonePicker stub).
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/alarms app/src/main/java/com/cyprienbrisset/myportal/ui/AppNav.kt
git commit -m "feat(sumi): full-screen touch alarm editor + restyled list"
```

---

### Task 11: RingtonePicker (tap-cards, RingtoneManager, preview)

**Files:**
- Create/replace: `app/src/main/java/com/cyprienbrisset/myportal/ui/alarms/RingtonePicker.kt`

- [ ] **Step 1: Implement RingtonePicker** — lists system ALARM ringtones as Sumi tap-cards; tap plays a short preview and selects; selection shows a vermilion border. No system picker Activity, no dropdown.

```kotlin
package com.cyprienbrisset.myportal.ui.alarms

import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import com.cyprienbrisset.myportal.ui.sumi.SectionLabel
import com.cyprienbrisset.myportal.ui.theme.*

private data class Tone(val title: String, val uri: String)

@Composable
fun RingtonePicker(selectedUri: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val tones = remember {
        val mgr = RingtoneManager(ctx).apply { setType(RingtoneManager.TYPE_ALARM) }
        val cur = mgr.cursor
        buildList {
            add(Tone("Par défaut", ""))
            var pos = 0
            while (cur.moveToNext()) {
                val title = mgr.getRingtone(pos).getTitle(ctx)
                val uri = mgr.getRingtoneUri(pos).toString()
                add(Tone(title, uri)); pos++
                if (pos >= 20) break
            }
        }
    }
    var preview by remember { mutableStateOf<Ringtone?>(null) }
    DisposableEffect(Unit) { onDispose { preview?.stop() } }

    Column(modifier) {
        SectionLabel("音", "SONNERIE — TOUCHEZ POUR ÉCOUTER")
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(tones) { t ->
                val on = (selectedUri ?: "") == t.uri
                Row(
                    Modifier.height(60.dp).clip(RoundedCornerShape(14.dp)).background(SumiSurface)
                        .border(BorderStroke(1.dp, if (on) Shu else SumiLine), RoundedCornerShape(14.dp))
                        .clickable {
                            onSelect(if (t.uri.isEmpty()) null else t.uri)
                            preview?.stop()
                            val uri = if (t.uri.isEmpty())
                                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) else Uri.parse(t.uri)
                            preview = RingtoneManager.getRingtone(ctx, uri)?.also { it.play() }
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("▶", color = Shu, fontSize = 12.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(t.title, color = Kinari, fontSize = 14.sp)
                }
            }
        }
    }
}
```
Note: if `AlarmEditScreen` created a stub `RingtonePicker`, this replaces it. Keep the signature `RingtonePicker(selectedUri: String?, onSelect: (String?) -> Unit, modifier)` identical.

- [ ] **Step 2: Build + commit**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/ui/alarms/RingtonePicker.kt
git commit -m "feat(sumi): tap-card ringtone picker with preview (no system dialog)"
```

---

### Task 12: AlarmRingActivity (Sumi ensō)

**Files:**
- Modify: `app/src/main/java/com/cyprienbrisset/myportal/alarm/AlarmRingActivity.kt`

- [ ] **Step 1: Restyle the ring UI** — keep the lifecycle/service logic and `showOverLockScreen()`; replace the Compose body with the Sumi ring: watermark 鈴, kanji 目覚まし (vermilion), Mincho time, ensō "止 Arrêter" (circle, vermilion border) + snooze pill. Only the `setContent { MyPortalTheme { ... } }` body changes.

```kotlin
setContent {
    var snoozeMinutes by remember { mutableStateOf(10) }
    if (alarmId >= 0) {
        androidx.compose.runtime.LaunchedEffect(alarmId) {
            val a = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.cyprienbrisset.myportal.data.alarm.AlarmRepository(
                    com.cyprienbrisset.myportal.data.AppDatabase.get(this@AlarmRingActivity).alarmDao()
                ).byId(alarmId)
            }
            if (a != null) snoozeMinutes = a.snoozeMinutes
        }
    }
    com.cyprienbrisset.myportal.ui.theme.MyPortalTheme {
        androidx.compose.material3.Surface(
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            color = androidx.compose.material3.MaterialTheme.colorScheme.background,
        ) {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                com.cyprienbrisset.myportal.ui.sumi.WatermarkKanji("鈴", size = 260.sp)
                androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    androidx.compose.material3.Text("目覚まし · RÉVEIL",
                        color = com.cyprienbrisset.myportal.ui.theme.Shu,
                        fontFamily = com.cyprienbrisset.myportal.ui.theme.Mincho, fontSize = 15.sp, letterSpacing = 4.sp)
                    androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(10.dp))
                    androidx.compose.material3.Text(intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID)?.let { "" } ?: java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                        fontFamily = com.cyprienbrisset.myportal.ui.theme.Mincho,
                        color = com.cyprienbrisset.myportal.ui.theme.Kinari, fontSize = 78.sp)
                    androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(36.dp))
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp)) {
                        com.cyprienbrisset.myportal.ui.sumi.SumiChoiceChip("Snooze $snoozeMinutes", selected = false,
                            onClick = { if (alarmId >= 0) AlarmForegroundService.snooze(this@AlarmRingActivity, alarmId, snoozeMinutes); finish() })
                        androidx.compose.foundation.layout.Box(
                            androidx.compose.ui.Modifier.size(104.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .border(androidx.compose.foundation.BorderStroke(3.dp, com.cyprienbrisset.myportal.ui.theme.Shu), androidx.compose.foundation.shape.CircleShape)
                                .clickable { AlarmForegroundService.stop(this@AlarmRingActivity); finish() },
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) {
                            androidx.compose.material3.Text("止\nArrêter", color = com.cyprienbrisset.myportal.ui.theme.Kinari,
                                fontFamily = com.cyprienbrisset.myportal.ui.theme.Mincho, fontSize = 15.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
```
Add the necessary top-level imports (or use fully-qualified names as above). Keep `showOverLockScreen()` and the `alarmId` read. Remove now-unused old imports.

- [ ] **Step 2: Build + Phase 4 on-device alarm test + commit**

Run: `JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL. Install on emulator; seed an alarm ~2 min out (or use the editor), verify: Sumi editor is fully touch (steppers/day circles/snooze pills/ringtone cards), the alarm rings via the service, and the ring screen shows the Sumi ensō. Also install on the Portal.
```bash
git add app/src/main/java/com/cyprienbrisset/myportal/alarm/AlarmRingActivity.kt
git commit -m "feat(sumi): ensō alarm ring screen"
```

**PHASE 4 END — full run:** `JAVA_HOME=... ./gradlew :app:testDebugUnitTest` (all pass). Visual pass on emulator + Portal across Home / Settings / Tiles / Weather / Alarms / Ring, landscape + portrait.

---

## Self-Review (author checklist, completed)

**Spec coverage:** tokens+fonts (T1), helpers (T2), primitives (T3), medallion+interactive (T4), banner (T5), home asymmetric (T6), settings (T7), tile editor touch/no-dialog (T8), weather cards (T9), alarm editor full-screen touch + list (T10), ringtone tap-cards no-system-picker (T11), ensō ring (T12). ✅ All spec screens/components covered.

**No-dropdown check:** SegmentedChoice (type), Medallion grid (app pick), Stepper (time), day circles, snooze pills, ringtone tap-cards, city cards — zero dropdown/combo/spinner. ✅

**Type consistency:** `Medallion(label,onClick,modifier,discSize,focused,dashed,content)`, `SegmentedChoice(options:List<Segment>,selectedIndex,onSelect)`, `SumiChoiceChip(text,selected,onClick,modifier,circle)`, `Stepper(value,onUp,onDown,modifier)`, `HankoSeal(char,modifier,size,onClick)`, `RingtonePicker(selectedUri,onSelect,modifier)`, `weekdayKanji/stepHour/stepMinute`, VM APIs unchanged (`save(hour,minute,repeatDays,label,ringtoneUri,snoozeMinutes)`, `installedApps()`, `LaunchIntentResolver.InstalledApp`) — consistent. ✅

**Ordering seams:** RingtonePicker stub created in T10, fleshed in T11 (noted). Old `TileGrid.kt` left unused after T6 (noted). Fonts must download in T1 before any Mincho usage.

**Logic untouched:** alarm service/scheduler/receivers, Room, WebView, weather repo — unchanged. Ringtone selection moves from system picker to in-app tap-cards (writes the same `ringtoneUri`).
